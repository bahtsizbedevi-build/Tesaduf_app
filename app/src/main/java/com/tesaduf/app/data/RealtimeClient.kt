package com.tesaduf.app.data

import com.tesaduf.app.network.TesadufJson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface RealtimeEvent {
    /** Subscribed: the server will push changes, polling can slow down. */
    data object Joined : RealtimeEvent
    /** A message was inserted or the match row changed — fetch the delta. */
    data object Changed : RealtimeEvent
    data object Disconnected : RealtimeEvent
}

/**
 * Minimal Supabase Realtime (Phoenix protocol v1) client used purely as a "something
 * changed" signal for one match. Data is always fetched through the Edge Functions, so
 * if Realtime is unavailable the app keeps working on (slower) polling.
 * Row Level Security applies: only participants receive events.
 */
class RealtimeClient(
    private val http: OkHttpClient,
    private val config: SupabaseConfig,
    private val session: SessionManager,
) {
    fun matchEvents(matchId: String): Flow<RealtimeEvent> = flow {
        var attempt = 0
        while (true) {
            val token = runCatching { session.accessToken() }.getOrNull()
            if (token != null && config.isConfigured) {
                connectOnce(matchId, token).collect { event ->
                    if (event == RealtimeEvent.Joined) attempt = 0
                    emit(event)
                }
            }
            emit(RealtimeEvent.Disconnected)
            attempt++
            delay(minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl minOf(attempt, 5)))
        }
    }

    private fun connectOnce(matchId: String, accessToken: String): Flow<RealtimeEvent> = callbackFlow {
        val topic = "realtime:tesaduf-match-$matchId"
        var ref = 0
        fun nextRef() = (++ref).toString()

        val url = config.realtimeUrl.replaceFirst("wss://", "https://").toHttpUrl().newBuilder()
            .addQueryParameter("apikey", config.anonKey)
            .addQueryParameter("vsn", "1.0.0")
            .build()
        val request = Request.Builder().url(url).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val joinRef = nextRef()
                webSocket.send(
                    buildJsonObject {
                        put("topic", topic)
                        put("event", "phx_join")
                        put("ref", joinRef)
                        put("join_ref", joinRef)
                        putJsonObject("payload") {
                            put("access_token", accessToken)
                            putJsonObject("config") {
                                put("private", false)
                                putJsonObject("broadcast") { put("self", false) }
                                putJsonObject("presence") { put("key", "") }
                                put("postgres_changes", buildJsonArray {
                                    add(change("INSERT", "messages", "match_id=eq.$matchId"))
                                    add(change("UPDATE", "matches", "id=eq.$matchId"))
                                })
                            }
                        }
                    }.toString(),
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { TesadufJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                if (message["topic"]?.jsonPrimitive?.content != topic) return
                when (message["event"]?.jsonPrimitive?.content) {
                    "phx_reply" -> {
                        val status = message["payload"]?.jsonObject?.get("status")?.jsonPrimitive?.content
                        if (status == "ok" && message["ref"]?.jsonPrimitive?.content == "1") {
                            trySend(RealtimeEvent.Joined)
                        }
                    }
                    "postgres_changes" -> trySend(RealtimeEvent.Changed)
                    "phx_error", "phx_close" -> webSocket.close(NORMAL_CLOSE, null)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSE, null)
                channel.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                channel.close()
            }
        }

        val socket = http.newWebSocket(request, listener)
        val heartbeat = launch {
            while (true) {
                delay(HEARTBEAT_MS)
                val sent = socket.send(
                    buildJsonObject {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JsonObject(emptyMap()))
                        put("ref", nextRef())
                    }.toString(),
                )
                if (!sent) break
            }
        }

        awaitClose {
            heartbeat.cancel()
            socket.close(NORMAL_CLOSE, null)
        }
    }

    private fun change(event: String, table: String, filter: String) = buildJsonObject {
        put("event", event)
        put("schema", "public")
        put("table", table)
        put("filter", filter)
    }

    private companion object {
        const val NORMAL_CLOSE = 1000
        const val HEARTBEAT_MS = 25_000L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
