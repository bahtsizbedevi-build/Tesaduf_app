package com.tesaduf.app.ui.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesaduf.app.R
import com.tesaduf.app.model.ChatSummary
import com.tesaduf.app.model.MatchStatus
import com.tesaduf.app.ui.design.HeartsIllustration
import com.tesaduf.app.ui.design.TIcons
import com.tesaduf.app.ui.design.TesadufAvatar
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufBadge
import com.tesaduf.app.ui.design.TesadufLastMessage
import com.tesaduf.app.ui.design.TesadufTimer
import com.tesaduf.app.network.parseInstantMillis
import com.tesaduf.app.ui.design.TesadufEmptyState
import com.tesaduf.app.ui.design.TesadufGlassCard
import com.tesaduf.app.ui.design.TesadufInlineMessage
import com.tesaduf.app.ui.design.TesadufSkeletonRow
import com.tesaduf.app.ui.design.TesadufTopBar
import com.tesaduf.app.ui.history.HistoryViewModel
import com.tesaduf.app.ui.home.BottomBarSpace
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatClock

/**
 * "Sohbetler" tab: conversations that are still open — the live tesadüf (if any) and
 * every Kader chat that continues without a time limit.
 */
@Composable
fun ChatsScreen(viewModel: HistoryViewModel, serverNow: () -> Long, onOpen: (String) -> Unit, onStart: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    // Light refresh while open (new last messages, finished timers); paused off-screen.
    LifecycleStartEffect(viewModel) {
        val job = scope.launch {
            while (true) {
                delay(LIST_REFRESH_MS)
                viewModel.refresh(silent = true)
            }
        }
        onStopOrDispose { job.cancel() }
    }
    val open = state.chats.filter { it.canOpen }

    TesadufBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TesadufTopBar(title = stringResource(R.string.chats_title))
            state.error?.let { TesadufInlineMessage(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), onRetry = viewModel::refresh) }
            when {
                state.loading && state.chats.isEmpty() -> Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { repeat(3) { TesadufSkeletonRow() } }
                open.isEmpty() && state.error == null -> TesadufEmptyState(
                    title = stringResource(R.string.chats_empty_title),
                    body = stringResource(R.string.chats_empty_sub),
                    illustration = { HeartsIllustration(Modifier.size(130.dp)) },
                    actionLabel = stringResource(R.string.home_start),
                    onAction = onStart,
                    modifier = Modifier.fillMaxSize().padding(bottom = BottomBarSpace),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = BottomBarSpace),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(open, key = { it.id }) { chat -> ChatRow(chat, serverNow, onOpen, Modifier.animateItem()) }
                }
            }
        }
    }
}

private const val LIST_REFRESH_MS = 15_000L

@Composable
private fun ChatRow(chat: ChatSummary, serverNow: () -> Long, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val (label, color) = when (chat.status) {
        MatchStatus.DESTINY -> stringResource(R.string.badge_destiny) to TesadufColors.Pink
        MatchStatus.DECIDING -> stringResource(R.string.badge_deciding) to TesadufColors.Warning
        else -> stringResource(R.string.badge_active) to TesadufColors.Success
    }
    TesadufGlassCard(
        modifier.fillMaxWidth(),
        onClick = { onOpen(chat.id) },
        fill = if (chat.status == MatchStatus.DESTINY) TesadufColors.Pink.copy(alpha = 0.06f) else TesadufColors.Glass,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TesadufAvatar(chat.partner.avatar, 52.dp, alive = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.partner.displayId, style = IdTextStyle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                    chat.lastMessage?.let {
                        Text(formatClock(it.createdAt), style = MaterialTheme.typography.labelSmall, color = TesadufColors.TextMuted)
                    }
                }
                val last = chat.lastMessage
                if (last != null) {
                    TesadufLastMessage(last.body, last.mine, chat.partner.displayId)
                } else {
                    Text(stringResource(R.string.chats_no_messages), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            val expiresAt = parseInstantMillis(chat.expiresAt)
            if (chat.status == MatchStatus.ACTIVE && expiresAt != null) {
                // Live countdown straight from the server expiry.
                TesadufTimer(expiresAt, serverNow)
            } else {
                TesadufBadge(label, color, icon = if (chat.status == MatchStatus.DESTINY) TIcons.Heart else null)
            }
        }
    }
}
