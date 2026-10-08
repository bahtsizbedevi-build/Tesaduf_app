package com.tesaduf.app.data

import com.tesaduf.app.model.AdminReport
import com.tesaduf.app.model.BlockedUser
import com.tesaduf.app.model.BootstrapResult
import com.tesaduf.app.model.Profile
import com.tesaduf.app.model.Reaction
import com.tesaduf.app.model.ReadResult
import com.tesaduf.app.model.UnblockResult
import com.tesaduf.app.model.ChatMessage
import com.tesaduf.app.model.ChatSummary
import com.tesaduf.app.model.Decision
import com.tesaduf.app.model.HeartbeatResult
import com.tesaduf.app.model.Match
import com.tesaduf.app.model.MatchmakerResult
import com.tesaduf.app.model.MessagesPage
import com.tesaduf.app.model.ReportReason
import com.tesaduf.app.model.SafetyActionResult
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.AppException
import com.tesaduf.app.network.JsonMediaType
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.network.ServerClock
import com.tesaduf.app.network.TesadufJson
import com.tesaduf.app.network.await
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class Envelope(
    val ok: Boolean = false,
    val data: JsonElement? = null,
    val error: EnvelopeError? = null,
    @SerialName("server_time") val serverTime: String? = null,
)

@Serializable
private data class EnvelopeError(val code: String? = null)

/** Typed client for the TESADÜF Edge Functions. */
class TesadufApi(
    private val http: OkHttpClient,
    private val config: SupabaseConfig,
    private val session: SessionManager,
    private val clock: ServerClock,
) {
    suspend fun bootstrap(): Outcome<BootstrapResult> =
        call("bootstrap", JsonObject(emptyMap()), BootstrapResult.serializer())

    suspend fun joinQueue(): Outcome<MatchmakerResult> =
        call("matchmaker", buildJsonObject { put("action", "join"); put("mode", "text") }, MatchmakerResult.serializer())

    suspend fun cancelQueue(): Outcome<Unit> =
        call("matchmaker", buildJsonObject { put("action", "cancel") }, JsonElement.serializer()).ignoreValue()

    suspend fun matchStatus(matchId: String): Outcome<Match> =
        call("match-status", matchBody(matchId), Match.serializer())

    suspend fun heartbeat(matchId: String?): Outcome<HeartbeatResult> =
        call("heartbeat", buildJsonObject { matchId?.let { put("match_id", it) } }, HeartbeatResult.serializer())

    suspend fun messages(matchId: String, after: Long, limit: Int = 100): Outcome<MessagesPage> =
        call(
            "messages",
            buildJsonObject { put("match_id", matchId); put("after", after); put("limit", limit) },
            MessagesPage.serializer(),
        )

    suspend fun sendMessage(matchId: String, body: String, clientId: String): Outcome<ChatMessage> =
        call(
            "send-message",
            buildJsonObject { put("match_id", matchId); put("body", body); put("client_id", clientId) },
            ChatMessage.serializer(),
        )

    suspend fun decide(matchId: String, decision: Decision): Outcome<Match> =
        call(
            "destiny-decision",
            buildJsonObject { put("match_id", matchId); put("decision", decision.wire) },
            Match.serializer(),
        )

    suspend fun endMatch(matchId: String): Outcome<Match> =
        call("end-match", matchBody(matchId), Match.serializer())

    suspend fun updateAvatar(avatar: String): Outcome<Profile> =
        call("update-profile", buildJsonObject { put("avatar", avatar) }, Profile.serializer())

    suspend fun blockedUsers(): Outcome<List<BlockedUser>> =
        call("blocked-users", buildJsonObject { put("action", "list") }, ListSerializer(BlockedUser.serializer()))

    suspend fun unblock(blockId: Long): Outcome<UnblockResult> =
        call("blocked-users", buildJsonObject { put("action", "unblock"); put("block_id", blockId) }, UnblockResult.serializer())

    suspend fun markRead(matchId: String, lastId: Long): Outcome<ReadResult> =
        call("mark-read", buildJsonObject { put("match_id", matchId); put("last_id", lastId) }, ReadResult.serializer())

    suspend fun react(messageId: Long, reaction: Reaction?): Outcome<ChatMessage> =
        call(
            "react-message",
            buildJsonObject { put("message_id", messageId); put("reaction", reaction?.wire) },
            ChatMessage.serializer(),
        )

    suspend fun adminReports(): Outcome<List<AdminReport>> =
        call("admin", buildJsonObject { put("action", "reports") }, ListSerializer(AdminReport.serializer()))

    suspend fun adminResolve(reportId: Long, suspend: Boolean): Outcome<Unit> =
        call(
            "admin",
            buildJsonObject { put("action", "resolve"); put("report_id", reportId); put("decision", if (suspend) "suspend" else "dismiss") },
            JsonElement.serializer(),
        ).ignoreValue()

    suspend fun myChats(limit: Int = 50): Outcome<List<ChatSummary>> =
        call("my-chats", buildJsonObject { put("limit", limit) }, ListSerializer(ChatSummary.serializer()))

    suspend fun block(matchId: String): Outcome<SafetyActionResult> =
        call("block-user", matchBody(matchId), SafetyActionResult.serializer())

    suspend fun report(matchId: String, reason: ReportReason): Outcome<SafetyActionResult> =
        call(
            "report-user",
            buildJsonObject { put("match_id", matchId); put("reason", reason.wire) },
            SafetyActionResult.serializer(),
        )

    private fun matchBody(matchId: String) = buildJsonObject { put("match_id", matchId) }

    private suspend fun <T> call(function: String, body: JsonObject, serializer: KSerializer<T>): Outcome<T> {
        if (!config.isConfigured) return Outcome.Failure(AppError.NotConfigured)
        return try {
            var response = execute(function, body, session.accessToken())
            if (response.status == 401) {
                // Token expired/revoked mid-flight: refresh once and retry.
                response = execute(function, body, session.accessToken(forceRefresh = true))
            }
            val envelope = response.envelope
            if (envelope?.ok == true && envelope.data != null) {
                Outcome.Success(TesadufJson.decodeFromJsonElement(serializer, envelope.data))
            } else {
                Outcome.Failure(
                    envelope?.error?.code?.let(AppError::fromCode)
                        ?: if (response.status >= 500) AppError.Server("HTTP_${response.status}")
                        else AppError.fromCode("HTTP_${response.status}"),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppException) {
            Outcome.Failure(e.error)
        } catch (e: kotlinx.serialization.SerializationException) {
            Outcome.Failure(AppError.Server("PARSE"))
        } catch (e: Exception) {
            Outcome.Failure(AppError.fromThrowable(e))
        }
    }

    private class Response(val status: Int, val envelope: Envelope?)

    private suspend fun execute(function: String, body: JsonObject, token: String): Response {
        val request = Request.Builder()
            .url("${config.functionsUrl}/$function")
            .header("Authorization", "Bearer $token")
            .header("apikey", config.anonKey)
            .post(TesadufJson.encodeToString(JsonObject.serializer(), body).toRequestBody(JsonMediaType))
            .build()
        val start = System.currentTimeMillis()
        val result = http.newCall(request).await()
        val end = System.currentTimeMillis()
        val envelope = runCatching { TesadufJson.decodeFromString(Envelope.serializer(), result.body) }.getOrNull()
        clock.onServerTime(envelope?.serverTime, start, end)
        return Response(result.code, envelope)
    }
}

private fun <T> Outcome<T>.ignoreValue(): Outcome<Unit> = when (this) {
    is Outcome.Success -> Outcome.Success(Unit)
    is Outcome.Failure -> this
}
