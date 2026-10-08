package com.tesaduf.app.ui.history

import com.tesaduf.app.ui.design.TIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tesaduf.app.R
import com.tesaduf.app.model.ChatSummary
import com.tesaduf.app.model.MatchStatus
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.network.parseInstantMillis
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.design.TesadufAvatar
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufBadge
import com.tesaduf.app.ui.design.TesadufLastMessage
import com.tesaduf.app.ui.design.TesadufEmptyState
import com.tesaduf.app.ui.design.TesadufGlassCard
import com.tesaduf.app.ui.design.TesadufIconButton
import com.tesaduf.app.ui.design.TesadufInlineMessage
import com.tesaduf.app.ui.design.TesadufSegmented
import com.tesaduf.app.ui.design.TesadufSkeletonRow
import com.tesaduf.app.ui.design.TesadufTopBar
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatDayTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loading: Boolean = true,
    val chats: List<ChatSummary> = emptyList(),
    val error: AppError? = null,
)

class HistoryViewModel(private val repository: TesadufRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()
    private var loading = false

    /** [silent] keeps the current list on screen (no skeleton) during background refreshes. */
    fun refresh(silent: Boolean = false) {
        if (loading) return
        loading = true
        if (!silent) _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = repository.myChats()) {
                is Outcome.Success -> _state.value = HistoryUiState(loading = false, chats = result.value)
                is Outcome.Failure -> if (!silent) _state.update { it.copy(loading = false, error = result.error) }
            }
            loading = false
        }
    }
}

enum class HistoryFilter { ALL, DESTINY, COMPLETED }

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onStartFirst: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val visible = state.chats.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.DESTINY -> it.isDestiny
            HistoryFilter.COMPLETED -> it.status == MatchStatus.ENDED
        }
    }

    TesadufBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TesadufTopBar(
                title = stringResource(R.string.history_title),
                onBack = onBack,
                actions = {
                    TesadufIconButton(TIcons.Refresh, stringResource(R.string.retry), viewModel::refresh, enabled = !state.loading, tint = TesadufColors.TextSecondary)
                },
            )
            TesadufSegmented(
                options = listOf(
                    HistoryFilter.ALL to stringResource(R.string.filter_all),
                    HistoryFilter.DESTINY to stringResource(R.string.filter_destiny),
                    HistoryFilter.COMPLETED to stringResource(R.string.filter_completed),
                ),
                selected = filter,
                onSelect = { filter = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            state.error?.let { TesadufInlineMessage(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), onRetry = viewModel::refresh) }
            when {
                state.loading && state.chats.isEmpty() -> Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { repeat(4) { TesadufSkeletonRow() } }
                state.chats.isEmpty() && state.error == null -> TesadufEmptyState(
                    title = stringResource(R.string.history_empty_title),
                    body = stringResource(R.string.history_empty_sub),
                    actionLabel = stringResource(R.string.history_empty_action),
                    onAction = onStartFirst,
                    modifier = Modifier.fillMaxSize(),
                )
                visible.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                    Text(stringResource(R.string.history_filter_empty), style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { chat -> HistoryRow(chat, onOpen, Modifier.animateItem()) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(chat: ChatSummary, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val (label, color) = when {
        chat.isDestiny -> stringResource(R.string.badge_destiny) to TesadufColors.Pink
        chat.status == MatchStatus.ACTIVE -> stringResource(R.string.badge_active) to TesadufColors.Success
        chat.status == MatchStatus.DECIDING -> stringResource(R.string.badge_deciding) to TesadufColors.Warning
        else -> stringResource(R.string.badge_completed) to TesadufColors.Cyan
    }
    val start = parseInstantMillis(chat.startedAt)
    val end = parseInstantMillis(chat.endedAt)
    val duration = if (start != null && end != null) {
        stringResource(R.string.duration_minutes, ((end - start) / 60_000L).coerceAtLeast(1).toInt())
    } else {
        stringResource(R.string.history_ongoing)
    }
    TesadufGlassCard(
        modifier.fillMaxWidth(),
        onClick = if (chat.canOpen) ({ onOpen(chat.id) }) else null,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TesadufAvatar(chat.partner.avatar, 48.dp, alive = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(chat.partner.displayId, style = IdTextStyle.copy(fontSize = 15.sp))
                Text("${formatDayTime(chat.startedAt)} • $duration", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                chat.lastMessage?.takeIf { chat.canOpen }?.let { last ->
                    TesadufLastMessage(last.body, last.mine, chat.partner.displayId, color = TesadufColors.TextMuted)
                }
            }
            Spacer(Modifier.width(8.dp))
            TesadufBadge(label, color, icon = if (chat.isDestiny) TIcons.Heart else null)
        }
    }
}

