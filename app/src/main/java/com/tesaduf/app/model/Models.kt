package com.tesaduf.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The caller's own anonymous identity. No real personal data exists anywhere. */
@Serializable
data class Profile(
    @SerialName("anonymous_id") val anonymousId: String,
    val avatar: String,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val displayId: String get() = "#$anonymousId"
}

@Serializable
data class Partner(
    @SerialName("anonymous_id") val anonymousId: String,
    val avatar: String,
    val online: Boolean = false,
) {
    val displayId: String get() = "#$anonymousId"
}

enum class MatchStatus { ACTIVE, DECIDING, DESTINY, ENDED, UNKNOWN;

    companion object {
        fun from(raw: String?): MatchStatus = when (raw) {
            "active" -> ACTIVE
            "deciding" -> DECIDING
            "destiny" -> DESTINY
            "ended" -> ENDED
            else -> UNKNOWN
        }
    }
}

enum class Decision(val wire: String) { CONTINUE("continue"), END("end") }

enum class ReportReason(val wire: String) {
    SPAM("spam"), INSULT("insult"), HARASSMENT("harassment"), INAPPROPRIATE("inappropriate"), OTHER("other")
}

@Serializable
data class Match(
    val id: String,
    val mode: String = "text",
    @SerialName("status") val rawStatus: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("decision_deadline") val decisionDeadline: String,
    @SerialName("destiny_at") val destinyAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("ended_by_me") val endedByMe: Boolean = false,
    @SerialName("my_decision") val myDecision: String? = null,
    @SerialName("partner_decided") val partnerDecided: Boolean = false,
    val partner: Partner,
    @SerialName("server_time") val serverTime: String? = null,
) {
    val status: MatchStatus get() = MatchStatus.from(rawStatus)
    val isOpen: Boolean get() = status == MatchStatus.ACTIVE || status == MatchStatus.DECIDING || status == MatchStatus.DESTINY
}

@Serializable
data class ProfileStats(
    @SerialName("tesaduf_count") val tesadufCount: Int = 0,
    @SerialName("destiny_count") val destinyCount: Int = 0,
    @SerialName("active_days") val activeDays: Int = 0,
)

@Serializable
data class BootstrapResult(
    val profile: Profile,
    val stats: ProfileStats = ProfileStats(),
    @SerialName("live_match") val liveMatch: Match? = null,
)

@Serializable
data class BlockedUser(
    val id: Long,
    @SerialName("anonymous_id") val anonymousId: String,
    val avatar: String,
    @SerialName("created_at") val createdAt: String,
) {
    val displayId: String get() = "#$anonymousId"
}

@Serializable
data class UnblockResult(val unblocked: Boolean = false)

@Serializable
data class MatchmakerResult(
    val state: String,
    val match: Match? = null,
    @SerialName("waiting_since") val waitingSince: String? = null,
    @SerialName("others_waiting") val othersWaiting: Int = 0,
)

@Serializable
data class ChatMessage(
    val id: Long,
    @SerialName("match_id") val matchId: String,
    @SerialName("client_id") val clientId: String,
    val mine: Boolean,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MessagesPage(
    val match: Match,
    val messages: List<ChatMessage>,
)

@Serializable
data class HeartbeatResult(
    val ok: Boolean = true,
    val match: Match? = null,
)

@Serializable
data class LastMessage(
    val body: String,
    val mine: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ChatSummary(
    val id: String,
    val mode: String = "text",
    @SerialName("status") val rawStatus: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("decision_deadline") val decisionDeadline: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("end_reason") val endReason: String? = null,
    @SerialName("is_destiny") val isDestiny: Boolean = false,
    val partner: Partner,
    @SerialName("last_message") val lastMessage: LastMessage? = null,
) {
    val status: MatchStatus get() = MatchStatus.from(rawStatus)
    val canOpen: Boolean
        get() = status == MatchStatus.ACTIVE || status == MatchStatus.DECIDING || status == MatchStatus.DESTINY
}

@Serializable
data class SafetyActionResult(
    val blocked: Boolean = false,
    val reported: Boolean = false,
    val match: Match? = null,
)
