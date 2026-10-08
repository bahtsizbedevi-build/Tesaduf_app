package com.tesaduf.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tesaduf.app.R
import com.tesaduf.app.model.ChatSummary
import com.tesaduf.app.model.MatchStatus
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.components.AnonAvatar
import com.tesaduf.app.ui.components.ErrorBanner
import com.tesaduf.app.ui.components.GlassSurface
import com.tesaduf.app.ui.components.NeonSpinner
import com.tesaduf.app.ui.components.TesadufBackground
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

    fun refresh() {
        if (loading) return
        loading = true
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = repository.myChats()) {
                is Outcome.Success -> _state.value = HistoryUiState(loading = false, chats = result.value)
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = result.error) }
            }
            loading = false
        }
    }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    TesadufBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_back), tint = TesadufColors.TextPrimary)
                }
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.history_refresh), tint = TesadufColors.TextSecondary)
                }
            }
            state.error?.let { ErrorBanner(it, Modifier.padding(horizontal = 16.dp), onRetry = viewModel::refresh) }
            when {
                state.loading && state.chats.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { NeonSpinner(40.dp) }
                state.chats.isEmpty() && state.error == null -> EmptyHistory()
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.chats, key = { it.id }) { chat ->
                        HistoryRow(chat, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(chat: ChatSummary, onOpen: (String) -> Unit) {
    val (label, color) = when {
        chat.status == MatchStatus.DESTINY -> R.string.status_destiny to TesadufColors.Pink
        chat.status == MatchStatus.ACTIVE -> R.string.status_active to TesadufColors.Success
        chat.status == MatchStatus.DECIDING -> R.string.status_deciding to TesadufColors.Warning
        chat.isDestiny -> R.string.status_ended_destiny to TesadufColors.Purple
        else -> R.string.status_ended to TesadufColors.TextMuted
    }
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .then(if (chat.canOpen) Modifier.clickable { onOpen(chat.id) } else Modifier),
        shape = RoundedCornerShape(20.dp),
        fill = if (chat.status == MatchStatus.DESTINY) TesadufColors.Pink.copy(alpha = 0.08f) else TesadufColors.Glass,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AnonAvatar(chat.partner.avatar, 44.dp, glow = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(chat.partner.displayId, style = IdTextStyle.copy(fontSize = 15.sp))
                Text(formatDayTime(chat.startedAt), style = MaterialTheme.typography.bodySmall)
                chat.lastMessage?.let { last ->
                    Text(
                        if (last.mine) stringResource(R.string.history_you_prefix, last.body) else last.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = TesadufColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.history_empty_sub), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
