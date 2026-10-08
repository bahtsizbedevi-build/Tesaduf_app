package com.tesaduf.app.ui.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesaduf.app.R
import com.tesaduf.app.data.RealtimeEvent
import com.tesaduf.app.model.ChatMessage
import com.tesaduf.app.model.Decision
import com.tesaduf.app.model.Match
import com.tesaduf.app.model.MatchStatus
import com.tesaduf.app.model.ReportReason
import com.tesaduf.app.model.SafetyActionResult
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.network.parseInstantMillis
import com.tesaduf.app.repository.TesadufRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class SendState { SENT, SENDING, FAILED }

data class UiMessage(
    /** Stable list key: the client id, identical before and after the server confirms. */
    val key: String,
    val body: String,
    val mine: Boolean,
    val createdAt: String?,
    val state: SendState,
)

data class ChatUiState(
    val match: Match? = null,
    val messages: List<UiMessage> = emptyList(),
    val loading: Boolean = true,
    val realtimeConnected: Boolean = false,
    val error: AppError? = null,
    val fatalError: AppError? = null,
    val decisionInFlight: Boolean = false,
    val actionInFlight: Boolean = false,
    @param:StringRes val notice: Int? = null,
    /** Both sides chose to continue while this screen was open: show the Destiny moment once. */
    val celebrateDestiny: Boolean = false,
    /** The user just blocked the partner: show the confirmation screen. */
    val showBlockedConfirmation: Boolean = false,
)

/**
 * Chat state machine for one match. All mutable state is touched on the main thread
 * only (viewModelScope), so no locking is needed. Network work is lifecycle bound:
 * [onVisible]/[onHidden] start and stop polling, heartbeat and the realtime socket.
 */
class ChatViewModel(
    private val matchId: String,
    private val repository: TesadufRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val serverMessages = sortedMapOf<Long, ChatMessage>()
    private val pending = linkedMapOf<String, PendingMessage>()
    private var lastServerId = 0L

    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private val jobs = mutableListOf<Job>()

    private data class PendingMessage(val body: String, val failed: Boolean)

    init {
        viewModelScope.launch { for (request in syncRequests) sync() }
    }

    fun onVisible() {
        if (jobs.isNotEmpty() || isEnded()) return
        requestSync()
        jobs += viewModelScope.launch {
            repository.realtimeEvents(matchId).collect { event ->
                when (event) {
                    RealtimeEvent.Joined -> {
                        _state.update { it.copy(realtimeConnected = true) }
                        requestSync()
                    }
                    RealtimeEvent.Changed -> requestSync()
                    RealtimeEvent.Disconnected -> _state.update { it.copy(realtimeConnected = false) }
                }
            }
        }
        // Safety-net polling: slow when realtime pushes changes, faster otherwise.
        jobs += viewModelScope.launch {
            while (isActive) {
                delay(pollIntervalMs())
                requestSync()
            }
        }
        jobs += viewModelScope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                (repository.heartbeat(matchId) as? Outcome.Success)?.value?.match?.let(::applyMatch)
            }
        }
        // Server decides expiry; we just ask promptly when our (server-synced) clock says it's time.
        jobs += viewModelScope.launch {
            while (isActive) {
                delay(EXPIRY_CHECK_MS)
                val match = _state.value.match ?: continue
                val now = repository.clock.now()
                val boundary = when (match.status) {
                    MatchStatus.ACTIVE -> parseInstantMillis(match.expiresAt)
                    MatchStatus.DECIDING -> parseInstantMillis(match.decisionDeadline)
                    else -> null
                }
                if (boundary != null && now >= boundary) requestSync()
            }
        }
    }

    fun onHidden() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        _state.update { it.copy(realtimeConnected = false) }
    }

    fun onConnectivityRestored() {
        requestSync()
        pending.filterValues { it.failed }.keys.forEach(::retry)
    }

    private fun pollIntervalMs(): Long {
        val s = _state.value
        val waitingForPartner = s.match?.status == MatchStatus.DECIDING && s.match.myDecision != null
        return when {
            s.realtimeConnected -> POLL_WITH_REALTIME_MS
            waitingForPartner -> POLL_DECIDING_MS
            else -> POLL_MS
        }
    }

    private fun requestSync() {
        syncRequests.trySend(Unit)
    }

    private suspend fun sync() {
        if (_state.value.fatalError != null) return
        when (val result = repository.messages(matchId, lastServerId)) {
            is Outcome.Success -> {
                val page = result.value
                applyMatch(page.match)
                mergeServerMessages(page.messages)
                _state.update { it.copy(loading = false, error = null) }
                if (page.messages.size >= PAGE_SIZE) requestSync()
            }
            is Outcome.Failure -> {
                if (result.error is AppError.MatchNotFound) {
                    _state.update { it.copy(loading = false, fatalError = result.error) }
                    onHidden()
                } else {
                    _state.update { it.copy(loading = false, error = result.error) }
                }
            }
        }
    }

    private fun mergeServerMessages(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        for (m in messages) {
            serverMessages[m.id] = m
            pending.remove(m.clientId)
            if (m.id > lastServerId) lastServerId = m.id
        }
        publishMessages()
    }

    private fun publishMessages() {
        val confirmed = serverMessages.values.map {
            UiMessage(it.clientId, it.body, it.mine, it.createdAt, SendState.SENT)
        }
        val local = pending.map { (clientId, p) ->
            UiMessage(clientId, p.body, true, null, if (p.failed) SendState.FAILED else SendState.SENDING)
        }
        _state.update { it.copy(messages = confirmed + local) }
    }

    /** Applies a match snapshot, ignoring stale responses that would move the state backwards. */
    private fun applyMatch(incoming: Match) {
        val current = _state.value.match
        if (current != null && rank(incoming.status) < rank(current.status)) return
        val merged = if (current != null && incoming.status == current.status &&
            incoming.myDecision == null && current.myDecision != null
        ) incoming.copy(myDecision = current.myDecision) else incoming
        val becameDestiny = current != null && current.status != MatchStatus.DESTINY && merged.status == MatchStatus.DESTINY
        _state.update { it.copy(match = merged, celebrateDestiny = it.celebrateDestiny || becameDestiny) }
        repository.onLiveMatchChanged(merged)
        if (merged.status == MatchStatus.ENDED) onHidden()
    }

    private fun rank(status: MatchStatus) = when (status) {
        MatchStatus.ACTIVE -> 0
        MatchStatus.DECIDING -> 1
        MatchStatus.DESTINY -> 2
        MatchStatus.ENDED -> 3
        MatchStatus.UNKNOWN -> -1
    }

    private fun isEnded() = _state.value.match?.status == MatchStatus.ENDED || _state.value.fatalError != null

    fun send(text: String): Boolean {
        val body = text.trim()
        val status = _state.value.match?.status
        if (body.isEmpty() || body.length > MAX_MESSAGE_LENGTH) return false
        if (status != MatchStatus.ACTIVE && status != MatchStatus.DESTINY) return false
        val clientId = UUID.randomUUID().toString()
        pending[clientId] = PendingMessage(body, failed = false)
        publishMessages()
        deliver(clientId)
        return true
    }

    fun retry(clientId: String) {
        val p = pending[clientId] ?: return
        pending[clientId] = p.copy(failed = false)
        publishMessages()
        deliver(clientId)
    }

    private fun deliver(clientId: String) {
        val body = pending[clientId]?.body ?: return
        viewModelScope.launch {
            // Same client id on every retry: the server stores the message at most once.
            when (val result = repository.send(matchId, body, clientId)) {
                is Outcome.Success -> {
                    // Do not advance the fetch cursor here: a partner message with a lower id
                    // may not have been fetched yet.
                    serverMessages[result.value.id] = result.value
                    pending.remove(clientId)
                    publishMessages()
                    requestSync()
                }
                is Outcome.Failure -> {
                    if (pending.containsKey(clientId)) pending[clientId] = PendingMessage(body, failed = true)
                    publishMessages()
                    if (result.error is AppError.MatchClosed || result.error is AppError.RateLimited) {
                        _state.update { it.copy(error = result.error) }
                        requestSync()
                    }
                }
            }
        }
    }

    fun decide(decision: Decision) {
        if (_state.value.decisionInFlight) return
        _state.update { it.copy(decisionInFlight = true) }
        viewModelScope.launch {
            when (val result = repository.decide(matchId, decision)) {
                is Outcome.Success -> applyMatch(result.value)
                is Outcome.Failure -> {
                    _state.update { it.copy(error = result.error) }
                    requestSync()
                }
            }
            _state.update { it.copy(decisionInFlight = false) }
        }
    }

    fun endMatch() = runAction(null) { repository.endMatch(matchId).toSafety() }

    fun block() = runAction(null, onSuccess = { _state.update { it.copy(showBlockedConfirmation = true) } }) {
        repository.block(matchId)
    }

    fun dismissDestiny() {
        _state.update { it.copy(celebrateDestiny = false) }
    }

    fun report(reason: ReportReason) = runAction(R.string.report_done) { repository.report(matchId, reason) }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun runAction(
        @StringRes successNotice: Int?,
        onSuccess: () -> Unit = {},
        call: suspend () -> Outcome<SafetyActionResult>,
    ) {
        if (_state.value.actionInFlight) return
        _state.update { it.copy(actionInFlight = true) }
        viewModelScope.launch {
            when (val result = call()) {
                is Outcome.Success -> {
                    result.value.match?.let(::applyMatch)
                    _state.update { it.copy(notice = successNotice) }
                    onSuccess()
                }
                is Outcome.Failure -> _state.update { it.copy(error = result.error) }
            }
            _state.update { it.copy(actionInFlight = false) }
        }
    }

    private fun Outcome<Match>.toSafety(): Outcome<SafetyActionResult> = when (this) {
        is Outcome.Success -> Outcome.Success(SafetyActionResult(match = value))
        is Outcome.Failure -> this
    }

    override fun onCleared() {
        syncRequests.close()
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 1000
        private const val PAGE_SIZE = 100
        private const val POLL_MS = 4_000L
        private const val POLL_DECIDING_MS = 3_000L
        private const val POLL_WITH_REALTIME_MS = 20_000L
        private const val HEARTBEAT_MS = 20_000L
        private const val EXPIRY_CHECK_MS = 1_500L
    }
}
