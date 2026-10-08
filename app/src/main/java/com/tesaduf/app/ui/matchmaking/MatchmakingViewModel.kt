package com.tesaduf.app.ui.matchmaking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.repository.TesadufRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MatchmakingUiState(
    val searchStartedAtMs: Long = System.currentTimeMillis(),
    val othersWaiting: Int = 0,
    val showNotFoundHint: Boolean = false,
    val error: AppError? = null,
    val matchedId: String? = null,
    /** Searched long enough without anyone: polling stopped, queue left. */
    val timedOut: Boolean = false,
)

/**
 * Polls the matchmaker while the screen is visible. Each poll also keeps our queue entry
 * fresh on the server; if the app goes to background, polling stops and the entry goes
 * stale on its own, so nobody gets matched with a phone in a pocket.
 */
class MatchmakingViewModel(private val repository: TesadufRepository) : ViewModel() {

    private val _state = MutableStateFlow(MatchmakingUiState())
    val state: StateFlow<MatchmakingUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var cancelled = false

    fun onVisible() {
        if (pollJob?.isActive == true || cancelled || _state.value.matchedId != null || _state.value.timedOut) return
        pollJob = viewModelScope.launch {
            var failures = 0
            while (isActive) {
                when (val result = repository.joinQueue()) {
                    is Outcome.Success -> {
                        failures = 0
                        val value = result.value
                        val match = value.match
                        if (value.state == "matched" && match != null) {
                            repository.onLiveMatchChanged(match)
                            _state.update { it.copy(matchedId = match.id, error = null) }
                            return@launch
                        }
                        val waited = System.currentTimeMillis() - _state.value.searchStartedAtMs
                        if (waited > GIVE_UP_MS) {
                            // Don't keep people waiting forever: stop, leave the queue, tell them.
                            repository.cancelQueue()
                            _state.update { it.copy(timedOut = true, showNotFoundHint = false, error = null) }
                            pollJob = null
                            return@launch
                        }
                        _state.update {
                            it.copy(
                                othersWaiting = value.othersWaiting,
                                error = null,
                                showNotFoundHint = it.showNotFoundHint || waited > NOT_FOUND_HINT_MS,
                            )
                        }
                        // Jitter de-synchronizes clients polling at the same moment.
                        delay(POLL_MS + Random.nextLong(0, POLL_JITTER_MS))
                    }
                    is Outcome.Failure -> {
                        failures++
                        _state.update { it.copy(error = result.error) }
                        delay(minOf(MAX_BACKOFF_MS, POLL_MS * (1L shl minOf(failures, 3))))
                    }
                }
            }
        }
    }

    fun onHidden() {
        pollJob?.cancel()
        pollJob = null
    }

    fun keepSearching() {
        _state.update { it.copy(showNotFoundHint = false, searchStartedAtMs = System.currentTimeMillis()) }
    }

    /** Start a fresh search after a timeout. */
    fun restart() {
        _state.value = MatchmakingUiState()
        onVisible()
    }

    fun retryNow() {
        onHidden()
        onVisible()
    }

    fun cancel() {
        cancelled = true
        onHidden()
        repository.cancelQueue()
    }

    override fun onCleared() {
        // Left the screen without a match (system back, process kill aside): leave the queue.
        if (_state.value.matchedId == null && !cancelled) repository.cancelQueue()
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val POLL_JITTER_MS = 700L
        const val MAX_BACKOFF_MS = 15_000L
        const val NOT_FOUND_HINT_MS = 40_000L
        const val GIVE_UP_MS = 120_000L
    }
}
