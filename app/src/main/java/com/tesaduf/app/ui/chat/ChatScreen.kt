package com.tesaduf.app.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesaduf.app.R
import com.tesaduf.app.data.NetworkMonitor
import com.tesaduf.app.model.Decision
import com.tesaduf.app.model.Match
import com.tesaduf.app.model.MatchStatus
import com.tesaduf.app.model.ReportReason
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.parseInstantMillis
import com.tesaduf.app.ui.components.AnonAvatar
import com.tesaduf.app.ui.components.ErrorBanner
import com.tesaduf.app.ui.components.GlassButton
import com.tesaduf.app.ui.components.GlassSurface
import com.tesaduf.app.ui.components.NeonButton
import com.tesaduf.app.ui.components.NeonSpinner
import com.tesaduf.app.ui.components.RadioRow
import com.tesaduf.app.ui.components.StatusDot
import com.tesaduf.app.ui.components.TesadufBackground
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatClock
import com.tesaduf.app.util.formatCountdown
import kotlinx.coroutines.delay

private const val WARNING_THRESHOLD_MS = 2 * 60_000L

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    networkMonitor: NetworkMonitor,
    serverNow: () -> Long,
    onBack: () -> Unit,
    onNewTesaduf: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val context = LocalContext.current

    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    var wasOffline by remember { mutableStateOf(false) }
    LaunchedEffect(online) {
        if (!online) wasOffline = true
        else if (wasOffline) {
            wasOffline = false
            viewModel.onConnectivityRestored()
        }
    }
    state.notice?.let { res ->
        val text = stringResource(res)
        LaunchedEffect(res) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    var dialog by rememberSaveable { mutableStateOf<ChatDialog?>(null) }
    val match = state.match

    TesadufBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ChatTopBar(
                match = match,
                online = online,
                serverNow = serverNow,
                onBack = onBack,
                onMenu = { dialog = it },
                actionsEnabled = match?.isOpen == true && !state.actionInFlight,
            )

            val bannerError: AppError? = when {
                !online -> AppError.NoInternet
                else -> state.error?.takeIf { match?.status != MatchStatus.ENDED }
            }
            AnimatedVisibility(bannerError != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                bannerError?.let {
                    ErrorBanner(
                        it,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onRetry = if (online) viewModel::dismissError else null,
                    )
                }
            }
            AnimatedVisibility(match?.status == MatchStatus.DESTINY) {
                DestinyBanner()
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.fatalError != null -> CenterMessage(stringResource(state.fatalError!!.messageRes))
                    state.loading && state.messages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { NeonSpinner(40.dp) }
                    state.messages.isEmpty() && match?.status == MatchStatus.ACTIVE -> EmptyChat()
                    else -> MessageList(state.messages, onRetry = viewModel::retry)
                }
            }

            when {
                state.fatalError != null -> EndedPanel(null, onNewTesaduf, onBack)
                match == null -> Unit
                match.status == MatchStatus.DECIDING -> DecisionPanel(
                    match = match,
                    serverNow = serverNow,
                    inFlight = state.decisionInFlight,
                    onDecide = viewModel::decide,
                )
                match.status == MatchStatus.ENDED -> EndedPanel(match, onNewTesaduf, onBack)
                else -> InputBar(onSend = viewModel::send)
            }
        }
    }

    when (dialog) {
        ChatDialog.END -> ConfirmDialog(
            title = R.string.confirm_end_title, body = R.string.confirm_end_body, confirm = R.string.confirm_yes_end,
            onConfirm = { dialog = null; viewModel.endMatch() }, onDismiss = { dialog = null },
        )
        ChatDialog.BLOCK -> ConfirmDialog(
            title = R.string.confirm_block_title, body = R.string.confirm_block_body, confirm = R.string.confirm_yes_block,
            onConfirm = { dialog = null; viewModel.block() }, onDismiss = { dialog = null },
        )
        ChatDialog.REPORT -> ReportDialog(
            onReport = { dialog = null; viewModel.report(it) }, onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

enum class ChatDialog { END, BLOCK, REPORT }

@Composable
private fun ChatTopBar(
    match: Match?,
    online: Boolean,
    serverNow: () -> Long,
    onBack: () -> Unit,
    onMenu: (ChatDialog) -> Unit,
    actionsEnabled: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_back), tint = TesadufColors.TextPrimary)
        }
        if (match != null) {
            AnonAvatar(match.partner.avatar, 40.dp, glow = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(match.partner.displayId, style = IdTextStyle.copy(fontSize = 16.sp), maxLines = 1)
                val (color, label) = when {
                    !online -> TesadufColors.Warning to stringResource(R.string.chat_reconnecting)
                    match.partner.online -> TesadufColors.Success to stringResource(R.string.chat_partner_online)
                    else -> TesadufColors.TextMuted to stringResource(R.string.chat_partner_away)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color, Modifier.size(7.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            TimerChip(match, serverNow)
        } else {
            Spacer(Modifier.weight(1f))
        }
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = actionsEnabled) {
                Icon(
                    Icons.Filled.MoreVert, stringResource(R.string.chat_menu),
                    tint = if (actionsEnabled) TesadufColors.TextPrimary else TesadufColors.TextMuted,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = TesadufColors.Ink,
            ) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_end)) }, onClick = { menuOpen = false; onMenu(ChatDialog.END) })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_block), color = TesadufColors.Danger) }, onClick = { menuOpen = false; onMenu(ChatDialog.BLOCK) })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_report), color = TesadufColors.Danger) }, onClick = { menuOpen = false; onMenu(ChatDialog.REPORT) })
            }
        }
    }
}

/** Remaining-time chip; ticks once a second using the server-synced clock. */
@Composable
private fun TimerChip(match: Match, serverNow: () -> Long) {
    val shape = RoundedCornerShape(14.dp)
    if (match.status == MatchStatus.DESTINY) {
        Box(
            Modifier.clip(shape).background(TesadufColors.HeartGradient).padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Text("❤ " + stringResource(R.string.chat_timer_destiny), style = MaterialTheme.typography.labelMedium, color = Color.White) }
        return
    }
    if (match.status != MatchStatus.ACTIVE) return
    val expiresAt = remember(match.expiresAt) { parseInstantMillis(match.expiresAt) ?: 0L }
    val remaining by produceState(expiresAt - serverNow(), expiresAt) {
        while (true) {
            value = expiresAt - serverNow()
            delay(1_000L - (serverNow() % 1_000L).coerceIn(0, 999))
        }
    }
    val warning = remaining in 0..WARNING_THRESHOLD_MS
    val pulse = if (warning) {
        val t = rememberInfiniteTransition(label = "timerPulse")
        t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val accent = if (warning) TesadufColors.Warning else TesadufColors.Cyan
    Box(
        Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .graphicsLayer { alpha = pulse.value }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(formatCountdown(remaining), style = IdTextStyle.copy(fontSize = 15.sp, color = accent, letterSpacing = 1.sp))
    }
}

@Composable
private fun DestinyBanner() {
    GlassSurface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        fill = TesadufColors.Pink.copy(alpha = 0.10f),
    ) {
        Text(
            stringResource(R.string.chat_destiny_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = TesadufColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

@Composable
private fun MessageList(messages: List<UiMessage>, onRetry: (String) -> Unit) {
    val listState = rememberLazyListState()
    val reversed = remember(messages) { messages.asReversed() }
    // Newest is index 0 with reverseLayout; keep pinned to the bottom when already there.
    LaunchedEffect(messages.size) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(reversed, key = { it.key }) { message ->
            MessageBubble(message, onRetry)
        }
    }
}

@Composable
private fun MessageBubble(message: UiMessage, onRetry: (String) -> Unit) {
    val mine = message.mine
    val shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .then(
                    if (mine) Modifier.background(TesadufColors.MineBubble)
                    else Modifier.background(TesadufColors.GlassStrong).border(1.dp, TesadufColors.GlassStroke, shape),
                )
                .graphicsLayer { alpha = if (message.state == SendState.SENDING) 0.7f else 1f }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(message.body, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        val meta = when (message.state) {
            SendState.SENDING -> stringResource(R.string.chat_sending)
            SendState.FAILED -> stringResource(R.string.chat_failed)
            SendState.SENT -> formatClock(message.createdAt)
        }
        Text(
            meta,
            style = MaterialTheme.typography.labelSmall,
            color = if (message.state == SendState.FAILED) TesadufColors.Danger else TesadufColors.TextMuted,
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .then(if (message.state == SendState.FAILED) Modifier.clickable { onRetry(message.key) } else Modifier),
        )
    }
}

@Composable
private fun EmptyChat() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.chat_empty_sub), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun InputBar(onSend: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = text.isNotBlank()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        GlassSurface(Modifier.weight(1f), shape = RoundedCornerShape(26.dp), fill = TesadufColors.GlassStrong) {
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= ChatViewModel.MAX_MESSAGE_LENGTH) text = it },
                textStyle = MaterialTheme.typography.bodyLarge,
                cursorBrush = SolidColor(TesadufColors.Cyan),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 15.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.chat_input_hint), style = MaterialTheme.typography.bodyLarge, color = TesadufColors.TextMuted)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (canSend) TesadufColors.PrimaryGradient else SolidColor(TesadufColors.Glass))
                .clickable(enabled = canSend) { if (onSend(text)) text = "" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send),
                tint = if (canSend) Color.White else TesadufColors.TextMuted,
            )
        }
    }
}

@Composable
private fun DecisionPanel(match: Match, serverNow: () -> Long, inFlight: Boolean, onDecide: (Decision) -> Unit) {
    val deadline = remember(match.decisionDeadline) { parseInstantMillis(match.decisionDeadline) ?: 0L }
    val remaining by produceState(deadline - serverNow(), deadline) {
        while (true) {
            value = deadline - serverNow()
            delay(1_000)
        }
    }
    GlassSurface(
        Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        fill = TesadufColors.Ink.copy(alpha = 0.92f),
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.decision_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.decision_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.decision_time_left, formatCountdown(remaining)),
                style = MaterialTheme.typography.labelMedium, color = TesadufColors.Cyan,
            )
            Spacer(Modifier.height(18.dp))
            if (match.myDecision == null) {
                NeonButton(
                    text = stringResource(R.string.decision_continue),
                    onClick = { onDecide(Decision.CONTINUE) },
                    brush = TesadufColors.HeartGradient,
                    glowColor = TesadufColors.Pink,
                    loading = inFlight,
                    animateGlow = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = stringResource(R.string.decision_end),
                    onClick = { onDecide(Decision.END) },
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                NeonSpinner(32.dp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.decision_waiting), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            if (match.partnerDecided) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.decision_partner_decided), style = MaterialTheme.typography.bodySmall, color = TesadufColors.Pink)
            }
        }
    }
}

@Composable
private fun EndedPanel(match: Match?, onNewTesaduf: () -> Unit, onHome: () -> Unit) {
    val reason = when (match?.endReason) {
        "expired" -> R.string.ended_expired
        "declined" -> if (match.endedByMe) R.string.ended_by_you else R.string.ended_declined
        "ended_by_user" -> if (match.endedByMe) R.string.ended_by_you else R.string.ended_by_partner
        "blocked" -> if (match.endedByMe) R.string.ended_blocked else R.string.ended_by_partner
        "reported" -> if (match.endedByMe) R.string.ended_reported else R.string.ended_by_partner
        "abandoned" -> R.string.ended_abandoned
        else -> R.string.ended_declined
    }
    GlassSurface(
        Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        fill = TesadufColors.Ink.copy(alpha = 0.92f),
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.ended_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(reason), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            NeonButton(stringResource(R.string.ended_new), onClick = onNewTesaduf, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            GlassButton(stringResource(R.string.ended_home), onClick = onHome, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConfirmDialog(title: Int, body: Int, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TesadufColors.Ink,
        title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(confirm), color = TesadufColors.Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = TesadufColors.TextSecondary) }
        },
    )
}

@Composable
private fun ReportDialog(onReport: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    val options = listOf(
        ReportReason.SPAM to R.string.report_spam,
        ReportReason.INSULT to R.string.report_insult,
        ReportReason.HARASSMENT to R.string.report_harassment,
        ReportReason.INAPPROPRIATE to R.string.report_inappropriate,
        ReportReason.OTHER to R.string.report_other,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TesadufColors.Ink,
        title = { Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(stringResource(R.string.report_body), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                options.forEach { (reason, label) ->
                    RadioRow(stringResource(label), selected == reason) { selected = reason }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onReport) }, enabled = selected != null) {
                Text(stringResource(R.string.report_send), color = if (selected != null) TesadufColors.Danger else TesadufColors.TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = TesadufColors.TextSecondary) }
        },
    )
}
