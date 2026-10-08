package com.tesaduf.app.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesaduf.app.model.ChatSummary
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.repository.TesadufRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads the user's tesadüfs for the Sohbetler tab (my-chats). */
data class ChatListUiState(
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val error: AppError? = null,
)

class ChatListViewModel(private val repository: TesadufRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()
    private var loading = false

    /** [silent] keeps the current list on screen (no skeleton) during background refreshes. */
    fun refresh(silent: Boolean = false) {
        if (loading) return
        loading = true
        if (!silent) _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = repository.myChats()) {
                is Outcome.Success -> _state.value = ChatListUiState(loading = false, chats = result.value)
                is Outcome.Failure -> if (!silent) _state.update { it.copy(loading = false, error = result.error) }
            }
            loading = false
        }
    }
}
