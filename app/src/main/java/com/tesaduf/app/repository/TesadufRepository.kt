package com.tesaduf.app.repository

import com.tesaduf.app.data.AppPreferences
import com.tesaduf.app.data.RealtimeClient
import com.tesaduf.app.data.RealtimeEvent
import com.tesaduf.app.data.SessionManager
import com.tesaduf.app.data.TesadufApi
import com.tesaduf.app.model.BlockedUser
import com.tesaduf.app.model.BootstrapResult
import com.tesaduf.app.model.ChatMessage
import com.tesaduf.app.model.ChatSummary
import com.tesaduf.app.model.Decision
import com.tesaduf.app.model.HeartbeatResult
import com.tesaduf.app.model.Match
import com.tesaduf.app.model.MatchStatus
import com.tesaduf.app.model.MatchmakerResult
import com.tesaduf.app.model.MessagesPage
import com.tesaduf.app.model.Profile
import com.tesaduf.app.model.ProfileStats
import com.tesaduf.app.model.Reaction
import com.tesaduf.app.model.ReportReason
import com.tesaduf.app.model.SafetyActionResult
import com.tesaduf.app.model.UnblockResult
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.network.ServerClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data class Ready(
        val profile: Profile,
        val stats: ProfileStats,
        val liveMatch: Match?,
        val liveCount: Int = 0,
        val isAdmin: Boolean = false,
    ) : SessionState
    data class Failed(val error: AppError) : SessionState
}

/** Single entry point for UI code: backend calls plus app-wide session state. */
class TesadufRepository(
    private val api: TesadufApi,
    private val realtime: RealtimeClient,
    private val sessionManager: SessionManager,
    val preferences: AppPreferences,
    val clock: ServerClock,
    private val appScope: CoroutineScope,
) {
    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private var bootstrapJob: Job? = null

    /** Starts (or restarts after a failure) the anonymous bootstrap. Safe to call repeatedly. */
    fun ensureSession() {
        if (bootstrapJob?.isActive == true) return
        if (_session.value is SessionState.Ready) return
        refreshSession(showLoading = true)
    }

    /** Re-fetches profile, stats and live match. Keeps the old data on failure. */
    fun refreshSession(showLoading: Boolean = false) {
        if (bootstrapJob?.isActive == true) return
        bootstrapJob = appScope.launch {
            if (showLoading || _session.value !is SessionState.Ready) _session.value = SessionState.Loading
            when (val result = api.bootstrap()) {
                is Outcome.Success -> _session.value = result.value.toReady()
                is Outcome.Failure -> if (_session.value !is SessionState.Ready || showLoading) {
                    _session.value = SessionState.Failed(result.error)
                }
            }
        }
    }

    private fun BootstrapResult.toReady() = SessionState.Ready(profile, stats, liveMatch?.takeIf { it.isOpen }, liveCount, isAdmin)

    /** Keeps Home's "return to chat" card in sync without another request. */
    fun onLiveMatchChanged(match: Match) {
        val current = _session.value as? SessionState.Ready ?: return
        val stillLive = match.status == MatchStatus.ACTIVE || match.status == MatchStatus.DECIDING
        val newLive = when {
            stillLive -> match
            current.liveMatch?.id == match.id -> null
            else -> current.liveMatch
        }
        if (newLive != current.liveMatch) _session.value = current.copy(liveMatch = newLive)
    }

    suspend fun updateAvatar(avatar: String): Outcome<Profile> {
        val result = api.updateAvatar(avatar)
        if (result is Outcome.Success) {
            (_session.value as? SessionState.Ready)?.let { _session.value = it.copy(profile = result.value) }
        }
        return result
    }

    /** Forgets the anonymous identity on this device; a new one is created on next start. */
    suspend fun signOut() {
        sessionManager.signOut()
        preferences.onboardingDone = false
        bootstrapJob?.cancel()
        _session.value = SessionState.Loading
    }

    suspend fun joinQueue(): Outcome<MatchmakerResult> = api.joinQueue()

    /** Fire-and-forget: must survive the screen that triggered it. */
    fun cancelQueue() {
        appScope.launch { api.cancelQueue() }
    }

    suspend fun matchStatus(matchId: String): Outcome<Match> = api.matchStatus(matchId)
    suspend fun heartbeat(matchId: String): Outcome<HeartbeatResult> = api.heartbeat(matchId)
    suspend fun messages(matchId: String, after: Long): Outcome<MessagesPage> = api.messages(matchId, after)
    suspend fun send(matchId: String, body: String, clientId: String): Outcome<ChatMessage> =
        api.sendMessage(matchId, body, clientId)

    suspend fun decide(matchId: String, decision: Decision): Outcome<Match> = api.decide(matchId, decision)
    suspend fun endMatch(matchId: String): Outcome<Match> = api.endMatch(matchId)
    suspend fun block(matchId: String): Outcome<SafetyActionResult> = api.block(matchId)
    suspend fun report(matchId: String, reason: ReportReason): Outcome<SafetyActionResult> =
        api.report(matchId, reason)

    suspend fun myChats(): Outcome<List<ChatSummary>> = api.myChats()
    suspend fun adminReports() = api.adminReports()
    suspend fun adminResolve(reportId: Long, suspend: Boolean) = api.adminResolve(reportId, suspend)
    suspend fun markRead(matchId: String, lastId: Long) = api.markRead(matchId, lastId)
    suspend fun react(messageId: Long, reaction: Reaction?): Outcome<ChatMessage> = api.react(messageId, reaction)
    fun sendTyping(matchId: String) = realtime.sendTyping(matchId)
    suspend fun blockedUsers(): Outcome<List<BlockedUser>> = api.blockedUsers()
    suspend fun unblock(blockId: Long): Outcome<UnblockResult> = api.unblock(blockId)

    fun realtimeEvents(matchId: String): Flow<RealtimeEvent> = realtime.matchEvents(matchId)
}
