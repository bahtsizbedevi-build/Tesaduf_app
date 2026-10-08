package com.tesaduf.app.ui.chat

import com.tesaduf.app.ui.design.TIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.tesaduf.app.ui.design.BlockIllustration
import com.tesaduf.app.ui.design.ButtonTone
import com.tesaduf.app.ui.design.FloatingHearts
import com.tesaduf.app.ui.design.HeartsIllustration
import com.tesaduf.app.ui.design.HourglassIllustration
import com.tesaduf.app.ui.design.TesadufAvatar
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufBadge
import com.tesaduf.app.ui.design.TesadufButton
import com.tesaduf.app.ui.design.TesadufDialog
import com.tesaduf.app.ui.design.TesadufGlassCard
import com.tesaduf.app.ui.design.TesadufIconButton
import com.tesaduf.app.ui.design.TesadufInlineMessage
import com.tesaduf.app.ui.design.TesadufIconBadge
import com.tesaduf.app.ui.design.TesadufLoadingDots
import com.tesaduf.app.ui.design.TesadufSecondaryButton
import com.tesaduf.app.ui.design.TesadufSheet
import com.tesaduf.app.ui.design.TesadufSnackbarHost
import com.tesaduf.app.ui.design.TesadufTextButton
import com.tesaduf.app.ui.design.TesadufTimer
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatClock
import com.tesaduf.app.util.formatCountdown
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState

private enum class ChatOverlay { MENU, REPORT, CONFIRM_END, CONFIRM_BLOCK }

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    networkMonitor: NetworkMonitor,
    myAvatar: String?,
    serverNow: () -> Long,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onNewTesaduf: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val snackbar = remember { SnackbarHostState() }

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
            viewModel.consumeNotice()
            snackbar.showSnackbar(text)
        }
    }

    var overlay by rememberSaveable { mutableStateOf<ChatOverlay?>(null) }
    // Sheets and dialogs must never open underneath the keyboard.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(overlay) {
        if (overlay != null) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    // Where both avatars look while the user is typing (null = idle, random life).
    var typingGaze by remember { mutableStateOf<Offset?>(null) }
    val match = state.match

    TesadufBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ChatTopBar(
                match = match,
                online = online,
                messageCount = state.messages.size,
                typingGaze = typingGaze,
                serverNow = serverNow,
                onBack = onBack,
                onMenu = { overlay = ChatOverlay.MENU },
                menuEnabled = match?.isOpen == true && !state.actionInFlight,
            )

            val bannerError: AppError? = when {
                !online -> AppError.NoInternet
                else -> state.error?.takeIf { match?.status != MatchStatus.ENDED }
            }
            AnimatedVisibility(bannerError != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                bannerError?.let {
                    TesadufInlineMessage(
                        it,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onRetry = if (online) viewModel::dismissError else null,
                    )
                }
            }
            AnimatedVisibility(match?.status == MatchStatus.DESTINY) {
                TesadufGlassCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    fill = TesadufColors.Pink.copy(alpha = 0.08f),
                    stroke = TesadufColors.Pink.copy(alpha = 0.3f),
                ) {
                    Text(
                        stringResource(R.string.chat_destiny_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TesadufColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                    )
                }
            }

            when {
                state.fatalError != null -> {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(state.fatalError!!.messageRes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                    EndedPanel(null, onNewTesaduf, onHome)
                }
                match?.status == MatchStatus.DECIDING -> SessionEndContent(
                    match = match,
                    serverNow = serverNow,
                    inFlight = state.decisionInFlight,
                    onDecide = viewModel::decide,
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.loading && state.messages.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { TesadufLoadingDots() }
                            state.messages.isEmpty() && match?.status == MatchStatus.ACTIVE -> EmptyChat()
                            else -> MessageList(state.messages, onRetry = viewModel::retry)
                        }
                    }
                    when (match?.status) {
                        MatchStatus.ENDED -> EndedPanel(match, onNewTesaduf, onHome)
                        null, MatchStatus.UNKNOWN -> Unit
                        else -> Composer(
                            myAvatar = myAvatar,
                            typingGaze = typingGaze,
                            onTypingGaze = { typingGaze = it },
                            onSend = viewModel::send,
                        )
                    }
                }
            }
        }

        TesadufSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 80.dp))

        // Overlays: sheets, dialogs, full-screen moments.
        TesadufSheet(visible = overlay == ChatOverlay.MENU, onDismiss = { overlay = null }) {
            MenuAction(TIcons.UserBlock, stringResource(R.string.menu_block), stringResource(R.string.menu_block_sub), TesadufColors.Danger) {
                overlay = ChatOverlay.CONFIRM_BLOCK
            }
            MenuAction(TIcons.ShieldAlert, stringResource(R.string.menu_report), stringResource(R.string.menu_report_sub), TesadufColors.Warning) {
                overlay = ChatOverlay.REPORT
            }
            MenuAction(TIcons.DoorOpen, stringResource(R.string.menu_end), stringResource(R.string.menu_end_sub), TesadufColors.Pink) {
                overlay = ChatOverlay.CONFIRM_END
            }
            Spacer(Modifier.height(12.dp))
            TesadufSecondaryButton(stringResource(R.string.menu_cancel), { overlay = null }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        ReportSheet(
            visible = overlay == ChatOverlay.REPORT,
            onDismiss = { overlay = null },
            onReport = { overlay = null; viewModel.report(it) },
        )
        if (overlay == ChatOverlay.CONFIRM_END) {
            TesadufDialog(
                title = stringResource(R.string.confirm_end_title),
                body = stringResource(R.string.confirm_end_body),
                onDismiss = { overlay = null },
            ) {
                TesadufButton(stringResource(R.string.confirm_yes_end), { overlay = null; viewModel.endMatch() }, Modifier.fillMaxWidth(), tone = ButtonTone.Danger)
                Spacer(Modifier.height(10.dp))
                TesadufSecondaryButton(stringResource(R.string.cancel), { overlay = null }, Modifier.fillMaxWidth())
            }
        }
        if (overlay == ChatOverlay.CONFIRM_BLOCK) {
            TesadufDialog(
                title = stringResource(R.string.confirm_block_title),
                body = stringResource(R.string.confirm_block_body),
                onDismiss = { overlay = null },
                illustration = { BlockIllustration(Modifier.size(72.dp)) },
            ) {
                TesadufButton(stringResource(R.string.confirm_yes_block), { overlay = null; viewModel.block() }, Modifier.fillMaxWidth(), tone = ButtonTone.Danger)
                Spacer(Modifier.height(10.dp))
                TesadufSecondaryButton(stringResource(R.string.cancel), { overlay = null }, Modifier.fillMaxWidth())
            }
        }

        AnimatedVisibility(state.showBlockedConfirmation, enter = fadeIn(), exit = fadeOut()) {
            FullScreenMoment(
                illustration = { BlockIllustration(Modifier.size(140.dp)) },
                title = stringResource(R.string.blocked_title),
                body = stringResource(R.string.blocked_body),
            ) {
                TesadufButton(stringResource(R.string.ok), onHome, Modifier.fillMaxWidth(), tone = ButtonTone.Heart)
            }
        }
        AnimatedVisibility(state.celebrateDestiny, enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.96f), exit = fadeOut()) {
            FullScreenMoment(
                illustration = { HeartsIllustration(Modifier.size(180.dp)) },
                title = stringResource(R.string.destiny_title),
                body = stringResource(R.string.destiny_body),
                titleColor = TesadufColors.Pink,
                hearts = true,
            ) {
                TesadufButton(stringResource(R.string.destiny_continue), viewModel::dismissDestiny, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                TesadufSecondaryButton(stringResource(R.string.destiny_later), { viewModel.dismissDestiny(); onHome() }, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Sheet action: tinted icon badge, title and a one-line explanation. */
@Composable
private fun MenuAction(icon: ImageVector, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(Shapes.field)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TesadufIconBadge(icon, size = 44.dp, tint = accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TesadufColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Icon(TIcons.ChevronRight, contentDescription = null, tint = TesadufColors.TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ChatTopBar(
    match: Match?,
    online: Boolean,
    messageCount: Int,
    typingGaze: Offset?,
    serverNow: () -> Long,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    menuEnabled: Boolean,
) {
    // The partner's avatar glances at the conversation whenever a new message lands.
    var glance by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(messageCount) {
        if (messageCount == 0) return@LaunchedEffect
        glance = Offset(0.6f, 0.8f)
        delay(1_400)
        glance = null
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TesadufIconButton(TIcons.ArrowLeft, stringResource(R.string.back), onBack)
        if (match != null) {
            // Watches the user's text from above while they type; otherwise glances / idles.
            TesadufAvatar(match.partner.avatar, 46.dp, gaze = typingGaze?.let { Offset(it.x, 1f) } ?: glance)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(match.partner.displayId, style = IdTextStyle.copy(fontSize = 16.sp), maxLines = 1)
                val (color, label) = when {
                    !online -> TesadufColors.Warning to stringResource(R.string.chat_reconnecting)
                    match.partner.online -> TesadufColors.Success to stringResource(R.string.chat_partner_online)
                    else -> TesadufColors.TextMuted to stringResource(R.string.chat_partner_away)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            when (match.status) {
                MatchStatus.ACTIVE -> TesadufTimer(parseInstantMillis(match.expiresAt) ?: 0L, serverNow)
                MatchStatus.DESTINY -> TesadufBadge(stringResource(R.string.chat_timer_destiny), TesadufColors.Pink, icon = TIcons.Heart)
                else -> Unit
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        TesadufIconButton(TIcons.More, stringResource(R.string.chat_menu), onMenu, enabled = menuEnabled)
    }
}

@Composable
private fun MessageList(messages: List<UiMessage>, onRetry: (String) -> Unit) {
    val listState = rememberLazyListState()
    val reversed = remember(messages) { messages.asReversed() }
    // Each message animates in once, the first time it is shown.
    val seen = remember { mutableSetOf<String>() }
    // Newest is index 0 with reverseLayout; stay pinned to the bottom when already there.
    LaunchedEffect(messages.size) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reversed, key = { it.key }) { message ->
            TesadufMessageBubble(
                message,
                onRetry,
                Modifier.animateItem(fadeInSpec = null, placementSpec = tween(220), fadeOutSpec = null),
                animateIn = remember(message.key) { seen.add(message.key) },
            )
        }
    }
}

/** Chat bubble: mine = cyan→blue accent, theirs = dark glass. Timestamp kept small. */
@Composable
fun TesadufMessageBubble(
    message: UiMessage,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
    animateIn: Boolean = false,
) {
    val mine = message.mine
    val entrance = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateIn) entrance.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = entrance.value
                alpha = p.coerceIn(0f, 1f)
                translationX = (1f - p) * (if (mine) 28.dp.toPx() else -28.dp.toPx())
                scaleX = 0.94f + 0.06f * p
                scaleY = scaleX
                transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
            },
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .then(
                    if (mine) Modifier.background(TesadufColors.MineBubble)
                    else Modifier.background(TesadufColors.Card).border(1.dp, TesadufColors.StrokeSoft, shape),
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
                .then(
                    if (message.state == SendState.FAILED) {
                        Modifier.heightIn(min = 32.dp).clickable(role = Role.Button) { onRetry(message.key) }
                    } else {
                        Modifier
                    },
                ),
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
        Text(stringResource(R.string.chat_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.chat_empty_sub), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

private const val TYPING_IDLE_MS = 2_000L

/**
 * Composer: the user's own living avatar watches the text being typed, a rounded glass
 * field and the send button.
 */
@Composable
private fun Composer(
    myAvatar: String?,
    typingGaze: Offset?,
    onTypingGaze: (Offset?) -> Unit,
    onSend: (String) -> Boolean,
) {
    var text by rememberSaveable { mutableStateOf("") }
    // Follow the text only while actually typing; 2 s after the last keystroke the
    // avatars go back to their own random life (blinking, glancing around).
    LaunchedEffect(text) {
        if (text.isEmpty()) {
            onTypingGaze(null)
            return@LaunchedEffect
        }
        val progress = (text.length % 32) / 32f
        onTypingGaze(Offset(-0.3f + 1.3f * progress, 0.45f))
        delay(TYPING_IDLE_MS)
        onTypingGaze(null)
    }
    var focused by remember { mutableStateOf(false) }
    val canSend = text.isNotBlank()

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (myAvatar != null) {
                TesadufAvatar(myAvatar, 46.dp, gaze = typingGaze, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.width(6.dp))
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(TesadufColors.GlassStrong)
                    .border(1.dp, if (focused) TesadufColors.Cyan.copy(alpha = 0.45f) else TesadufColors.Stroke, RoundedCornerShape(26.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= ChatViewModel.MAX_MESSAGE_LENGTH) text = it },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    cursorBrush = SolidColor(TesadufColors.Cyan),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(stringResource(R.string.chat_input_hint), style = MaterialTheme.typography.bodyLarge, color = TesadufColors.TextMuted)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (canSend) TesadufColors.CoolAccent else SolidColor(TesadufColors.Glass))
                    .clickable(enabled = canSend, role = Role.Button) {
                        if (onSend(text)) {
                            text = ""
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    TIcons.Send, stringResource(R.string.chat_send),
                    tint = if (canSend) Color.White else TesadufColors.TextMuted,
                )
            }
        }
    }
}

/** 00:00 — hourglass, question, DEVAM ET / BİTİR with the decision countdown. */
@Composable
private fun SessionEndContent(
    match: Match,
    serverNow: () -> Long,
    inFlight: Boolean,
    onDecide: (Decision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deadline = remember(match.decisionDeadline) { parseInstantMillis(match.decisionDeadline) ?: 0L }
    val remaining by produceState(deadline - serverNow(), deadline) {
        while (true) {
            value = deadline - serverNow()
            delay(1_000)
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HourglassIllustration(Modifier.size(132.dp), animate = true)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.decision_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.decision_body), style = MaterialTheme.typography.bodyLarge.copy(color = TesadufColors.TextSecondary), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.decision_time_left, formatCountdown(remaining)), style = MaterialTheme.typography.labelMedium, color = TesadufColors.Cyan)
        Spacer(Modifier.height(28.dp))
        if (match.myDecision == null) {
            TesadufButton(
                text = stringResource(R.string.decision_continue),
                onClick = { onDecide(Decision.CONTINUE) },
                tone = ButtonTone.Heart,
                icon = TIcons.Heart,
                loading = inFlight,
                minHeight = 60.dp,
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            )
            Text(stringResource(R.string.decision_hint_continue), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            TesadufSecondaryButton(
                text = stringResource(R.string.decision_end),
                onClick = { onDecide(Decision.END) },
                icon = TIcons.Close,
                enabled = !inFlight,
                outline = TesadufColors.TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            )
            Text(stringResource(R.string.decision_hint_end), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        } else {
            TesadufLoadingDots(count = 4)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.decision_waiting), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (match.partnerDecided) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.decision_partner_decided), style = MaterialTheme.typography.bodySmall, color = TesadufColors.Pink)
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
    TesadufGlassCard(
        Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(28.dp),
        fill = TesadufColors.Card,
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.ended_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(reason), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            TesadufButton(stringResource(R.string.ended_new), onNewTesaduf, Modifier.fillMaxWidth(), tone = ButtonTone.Signature)
            Spacer(Modifier.height(10.dp))
            TesadufSecondaryButton(stringResource(R.string.ended_home), onHome, Modifier.fillMaxWidth())
        }
    }
}

/** Full-screen moment (Destiny, user blocked): illustration, title, body, actions. */
@Composable
private fun FullScreenMoment(
    illustration: @Composable () -> Unit,
    title: String,
    body: String,
    titleColor: Color = TesadufColors.TextPrimary,
    hearts: Boolean = false,
    actions: @Composable () -> Unit,
) {
    TesadufBackground(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (hearts) FloatingHearts(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            illustration()
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = titleColor, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge.copy(color = TesadufColors.TextSecondary), textAlign = TextAlign.Center)
            Spacer(Modifier.height(36.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) { actions() }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ReportSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onReport: (ReportReason) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    val options: List<Triple<ReportReason, Int, ImageVector>> = listOf(
        Triple(ReportReason.SPAM, R.string.report_spam, TIcons.Megaphone),
        Triple(ReportReason.INSULT, R.string.report_insult, TIcons.Frown),
        Triple(ReportReason.HARASSMENT, R.string.report_harassment, TIcons.Warning),
        Triple(ReportReason.INAPPROPRIATE, R.string.report_inappropriate, TIcons.Incognito),
        Triple(ReportReason.OTHER, R.string.report_other, TIcons.Ellipsis),
    )
    TesadufSheet(visible = visible, onDismiss = onDismiss, title = stringResource(R.string.report_title)) {
        Text(stringResource(R.string.report_body), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        options.forEach { (reason, label, icon) ->
            val isSelected = selected == reason
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(Shapes.field)
                    .background(if (isSelected) TesadufColors.Cyan.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable(role = Role.RadioButton) { selected = reason }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = if (isSelected) TesadufColors.Cyan else TesadufColors.TextSecondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (isSelected) TesadufColors.Cyan else TesadufColors.TextMuted, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) TesadufColors.Cyan else Color.Transparent),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        TesadufButton(
            stringResource(R.string.report_send),
            onClick = { selected?.let(onReport) },
            enabled = selected != null,
            tone = ButtonTone.Heart,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        TesadufTextButton(stringResource(R.string.cancel), onDismiss, Modifier.align(Alignment.CenterHorizontally))
    }
}
