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
import com.tesaduf.app.model.Reaction
import com.tesaduf.app.model.ReportReason
import com.tesaduf.app.model.SafetyActionResult
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.network.parseInstantMillis
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.design.AvatarMood
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
    val serverId: Long?,
    val body: String,
    val mine: Boolean,
    val createdAt: String?,
    val state: SendState,
    val reaction: Reaction? = null,
    /** Flagged by the server's language filter: shown hidden until tapped (recipient only). */
    val flagged: Boolean = false,
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
    /** Both sides chose to continue while this screen was open: show the Kader moment once. */
    val celebrateDestiny: Boolean = false,
    /** The user just blocked the partner: show the confirmation screen. */
    val showBlockedConfirmation: Boolean = false,
    /** The partner is typing right now (realtime broadcast). */
    val partnerTyping: Boolean = false,
    /** Same ice-breaker for both sides of a match (derived from the match id). */
    val icebreakerIndex: Int = 0,
    val partnerMood: AvatarMood = AvatarMood.Idle,
    val myMood: AvatarMood = AvatarMood.Idle,
)

/**
 * Chat state machine for one match. All mutable state is touched on the main thread
 * only (viewModelScope), so no locking is needed. Network work is lifecycle bound:
 * [onVisible]/[onHidden] start and stop polling, heartbeat and the realtime socket.
 */
class ChatViewModel(
    private val matchId: String,
    private val repository: TesadufRepository,
    icebreakerCount: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(icebreakerIndex = Math.floorMod(matchId.hashCode(), icebreakerCount.coerceAtLeast(1))),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val serverMessages = sortedMapOf<Long, ChatMessage>()
    private val pending = linkedMapOf<String, PendingMessage>()
    private var lastServerId = 0L
    private var lastMarkedRead = 0L
    private var visible = false
    private var lastTypingSentAt = 0L
    private var typingClearJob: Job? = null
    private var partnerMoodJob: Job? = null
    private var myMoodJob: Job? = null
    private var lastActivityAt = System.currentTimeMillis()
    private val knownReactions = mutableMapOf<Long, String?>()

    /** true = also re-read the latest window (reactions changed on existing messages). */
    private val syncRequests = Channel<Boolean>(Channel.CONFLATED)
    private val jobs = mutableListOf<Job>()

    private data class PendingMessage(val body: String, val failed: Boolean)

    init {
        viewModelScope.launch { for (window in syncRequests) sync(window) }
    }

    fun onVisible() {
        visible = true
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
                    RealtimeEvent.MessagesUpdated -> requestSync(window = true)
                    RealtimeEvent.Typing -> showPartnerTyping()
                    RealtimeEvent.Disconnected -> _state.update { it.copy(realtimeConnected = false) }
                }
            }
        }
        // Safety-net polling: slow when realtime pushes changes, faster otherwise.
        jobs += viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(pollIntervalMs())
                // Every few polls re-read the window so reactions show up even without realtime.
                requestSync(window = ++tick % 4 == 0)
            }
        }
        jobs += viewModelScope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                (repository.heartbeat(matchId) as? Outcome.Success)?.value?.match?.let(::applyMatch)
            }
        }
        // Quiet for a while -> both avatars doze off; any activity wakes them up.
        jobs += viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val quiet = System.currentTimeMillis() - lastActivityAt > SLEEPY_AFTER_MS
                val s = _state.value
                if (quiet && s.match?.status == MatchStatus.ACTIVE && s.partnerMood == AvatarMood.Idle) {
                    _state.update { it.copy(partnerMood = AvatarMood.Sleepy, myMood = AvatarMood.Sleepy) }
                }
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
        visible = false
        jobs.forEach { it.cancel() }
        jobs.clear()
        _state.update { it.copy(realtimeConnected = false, partnerTyping = false) }
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

    private fun requestSync(window: Boolean = false) {
        syncRequests.trySend(window)
    }

    private suspend fun sync(window: Boolean) {
        if (_state.value.fatalError != null) return
        val after = if (window) 0L else lastServerId
        when (val result = repository.messages(matchId, after)) {
            is Outcome.Success -> {
                val page = result.value
                applyMatch(page.match)
                mergeServerMessages(page.messages)
                _state.update { it.copy(loading = false, error = null) }
                if (!window && page.messages.size >= PAGE_SIZE) requestSync()
                markReadIfVisible()
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

    /** Read receipt for the newest partner message while the chat is on screen. */
    private fun markReadIfVisible() {
        if (!visible) return
        val newestPartner = serverMessages.values.lastOrNull { !it.mine }?.id ?: return
        if (newestPartner <= lastMarkedRead) return
        lastMarkedRead = newestPartner
        viewModelScope.launch { repository.markRead(matchId, newestPartner) }
    }

    private fun mergeServerMessages(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        // A new heart on one of my messages: the partner's avatar falls in love for a moment.
        val newHeartOnMine = messages.any { m ->
            m.mine && m.reaction == "heart" && knownReactions.containsKey(m.id) && knownReactions[m.id] != "heart"
        }
        messages.forEach { knownReactions[it.id] = it.reaction }
        if (newHeartOnMine) flashPartnerMood(AvatarMood.Love)
        if (messages.any { it.id > lastServerId }) wake()
        for (m in messages) {
            serverMessages[m.id] = m
            pending.remove(m.clientId)
            if (m.id > lastServerId) lastServerId = m.id
        }
        if (messages.any { !it.mine }) _state.update { it.copy(partnerTyping = false) }
        publishMessages()
    }

    private fun publishMessages() {
        val confirmed = serverMessages.values.map {
            UiMessage(
                key = it.clientId, serverId = it.id, body = it.body, mine = it.mine, createdAt = it.createdAt,
                state = SendState.SENT, reaction = Reaction.from(it.reaction), flagged = it.flagged && !it.mine,
            )
        }
        val local = pending.map { (clientId, p) ->
            UiMessage(clientId, null, p.body, true, null, if (p.failed) SendState.FAILED else SendState.SENDING)
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
        if (becameDestiny) {
            flashPartnerMood(AvatarMood.Joy, 3_000)
            flashMyMood(AvatarMood.Joy, 3_000)
        }
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

    private fun wake() {
        lastActivityAt = System.currentTimeMillis()
        _state.update {
            it.copy(
                partnerMood = if (it.partnerMood == AvatarMood.Sleepy) AvatarMood.Idle else it.partnerMood,
                myMood = if (it.myMood == AvatarMood.Sleepy) AvatarMood.Idle else it.myMood,
            )
        }
    }

    private fun flashPartnerMood(mood: AvatarMood, ms: Long = 2_200) {
        partnerMoodJob?.cancel()
        _state.update { it.copy(partnerMood = mood) }
        partnerMoodJob = viewModelScope.launch {
            delay(ms)
            _state.update { it.copy(partnerMood = AvatarMood.Idle) }
        }
    }

    private fun flashMyMood(mood: AvatarMood, ms: Long = 1_200) {
        myMoodJob?.cancel()
        _state.update { it.copy(myMood = mood) }
        myMoodJob = viewModelScope.launch {
            delay(ms)
            _state.update { it.copy(myMood = AvatarMood.Idle) }
        }
    }

    private fun showPartnerTyping() {
        wake()
        _state.update { it.copy(partnerTyping = true) }
        typingClearJob?.cancel()
        typingClearJob = viewModelScope.launch {
            delay(TYPING_VISIBLE_MS)
            _state.update { it.copy(partnerTyping = false) }
        }
    }

    /** Called on every keystroke; broadcasts at most once per [TYPING_THROTTLE_MS]. */
    fun onTyping() {
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < TYPING_THROTTLE_MS) return
        lastTypingSentAt = now
        repository.sendTyping(matchId)
    }

    fun send(text: String): Boolean {
        val body = text.trim()
        val status = _state.value.match?.status
        if (body.isEmpty() || body.length > MAX_MESSAGE_LENGTH) return false
        if (status != MatchStatus.ACTIVE && status != MatchStatus.DESTINY) return false
        val clientId = UUID.randomUUID().toString()
        wake()
        flashMyMood(AvatarMood.Joy)
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

    /** Toggle a reaction on the partner's message (optimistic, reverted on failure). */
    fun react(message: UiMessage, reaction: Reaction) {
        val id = message.serverId ?: return
        if (message.mine) return
        val old = serverMessages[id] ?: return
        val newValue = if (Reaction.from(old.reaction) == reaction) null else reaction
        wake()
        if (newValue == Reaction.HEART) flashMyMood(AvatarMood.Love, 1_800)
        serverMessages[id] = old.copy(reaction = newValue?.wire)
        publishMessages()
        viewModelScope.launch {
            when (val result = repository.react(id, newValue)) {
                is Outcome.Success -> {
                    serverMessages[id] = result.value
                    publishMessages()
                }
                is Outcome.Failure -> {
                    serverMessages[id] = old
                    publishMessages()
                    _state.update { it.copy(error = result.error) }
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
        private const val TYPING_THROTTLE_MS = 1_500L
        private const val TYPING_VISIBLE_MS = 3_500L
        private const val SLEEPY_AFTER_MS = 60_000L
    }
}
