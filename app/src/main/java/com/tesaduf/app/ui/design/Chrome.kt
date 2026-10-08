package com.tesaduf.app.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tesaduf.app.R
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatCountdown
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Top bar: optional back, centred title, trailing actions. No Material AppBar. */
@Composable
fun TesadufTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    leading: @Composable (RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TesadufIconButton(TIcons.ArrowLeft, stringResource(R.string.back), onBack)
            }
            leading?.invoke(this)
        }
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 240.dp).semantics { heading() },
            )
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

data class BottomItem(val route: String, val label: String, val icon: ImageVector)

/** Floating dark-glass bottom navigation. Active item: cyan icon + soft glow. */
@Composable
fun TesadufBottomBar(
    items: List<BottomItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(TesadufColors.Card.copy(alpha = 0.92f))
            .border(1.dp, TesadufColors.Stroke, RoundedCornerShape(26.dp))
            .selectableGroup()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val emphasis by animateFloatAsState(if (selected) 1f else 0f, label = "navItem")
            val bounce by animateFloatAsState(
                if (selected) 1.14f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "navBounce",
            )
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .selectable(selected, onClick = { onSelect(item.route) }, role = Role.Tab)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(28.dp).graphicsLayer { scaleX = bounce; scaleY = bounce }.drawBehind {
                        softGlow(TesadufColors.Cyan, center, size.minDimension, 0.35f * emphasis)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon, contentDescription = null,
                        tint = if (selected) TesadufColors.Cyan else TesadufColors.TextMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) TesadufColors.TextPrimary else TesadufColors.TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Centred dialog with optional illustration; actions stacked full-width. */
@Composable
fun TesadufDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(TesadufColors.Card)
                .border(1.dp, TesadufColors.Stroke, RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (illustration != null) {
                illustration()
                Spacer(Modifier.height(16.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            actions()
        }
    }
}

/**
 * Bottom sheet overlay drawn by the screen itself (place last inside the screen's root
 * Box): scrim + sliding dark-glass panel. Back press and scrim tap dismiss it.
 */
@Composable
fun BoxScope.TesadufSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.sheet)
                .background(TesadufColors.Card)
                .border(1.dp, TesadufColors.Stroke, Shapes.sheet)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(Shapes.pill)
                    .background(TesadufColors.Stroke),
            )
            if (title != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                    TesadufIconButton(TIcons.Close, stringResource(R.string.close), onDismiss, tint = TesadufColors.TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Row inside sheets / settings: icon, label, optional trailing chevron. */
@Composable
fun TesadufListRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TesadufColors.TextPrimary,
    iconTint: Color = TesadufColors.Cyan,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Shapes.field)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.weight(1f))
        trailing?.invoke()
        if (chevron) {
            Icon(TIcons.ChevronRight, contentDescription = null, tint = TesadufColors.TextMuted)
        }
    }
}

/** Segmented filter (Tümü / Destiny / Tamamlanan). */
@Composable
fun <T> TesadufSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.pill)
            .background(TesadufColors.Glass)
            .border(1.dp, TesadufColors.StrokeSoft, Shapes.pill)
            .selectableGroup()
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(Shapes.pill)
                    .then(if (isSelected) Modifier.background(TesadufColors.Cyan.copy(alpha = 0.14f)).border(1.dp, TesadufColors.Cyan.copy(alpha = 0.5f), Shapes.pill) else Modifier)
                    .selectable(isSelected, onClick = { onSelect(value) }, role = Role.Tab),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (isSelected) TesadufColors.TextPrimary else TesadufColors.TextSecondary)
            }
        }
    }
}

/** Statistics card; numbers count up from 0 the first time they appear. */
@Composable
fun TesadufStatsCard(stats: List<Pair<Int, String>>, modifier: Modifier = Modifier) {
    TesadufGlassCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            stats.forEachIndexed { index, (value, label) ->
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shown = remember { Animatable(0f) }
                    LaunchedEffect(value) { shown.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
                    Text(shown.value.roundToInt().toString(), style = MaterialTheme.typography.headlineSmall)
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
                if (index < stats.lastIndex) {
                    Box(Modifier.width(1.dp).height(32.dp).background(TesadufColors.StrokeSoft))
                }
            }
        }
    }
}

/** Read-only field (anonymous id). */
@Composable
fun TesadufReadOnlyField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TesadufColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(Shapes.field)
                .background(TesadufColors.Glass)
                .border(1.dp, TesadufColors.Stroke, Shapes.field)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = IdTextStyle.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
            Icon(TIcons.Lock, contentDescription = stringResource(R.string.read_only), tint = TesadufColors.TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Countdown pill bound to the *server* expiry. Calm cyan/purple outline normally, pink
 * and brighter under 3 minutes, a soft pulse only in the last 60 seconds.
 */
@Composable
fun TesadufTimer(expiresAtMs: Long, serverNow: () -> Long, modifier: Modifier = Modifier) {
    val remaining by produceState(expiresAtMs - serverNow(), expiresAtMs) {
        while (true) {
            value = expiresAtMs - serverNow()
            delay(1_000L - (serverNow() % 1_000L).coerceIn(0, 999))
        }
    }
    val urgent = remaining <= 3 * 60_000L
    val critical = remaining <= 60_000L
    val finalSeconds = remaining in 0..10_000L
    // Heartbeat: a quick double "thump" every second in the last 10 s.
    val beat: State<Float> = if (finalSeconds) {
        rememberInfiniteTransition(label = "beat").animateFloat(
            1f, 1f,
            infiniteRepeatable(keyframes {
                durationMillis = 1_000
                1f at 0
                1.12f at 120
                1f at 260
                1.07f at 380
                1f at 560
            }),
            label = "b",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val pulse: State<Float> = if (critical) {
        rememberInfiniteTransition(label = "timerPulse").animateFloat(
            0.6f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "p",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val border = if (urgent) Brush.horizontalGradient(listOf(TesadufColors.Pink, TesadufColors.Purple))
    else Brush.horizontalGradient(listOf(TesadufColors.Cyan, TesadufColors.Purple))
    val text = formatCountdown(remaining)
    val description = stringResource(R.string.timer_remaining, text)
    Box(
        modifier
            .semantics { contentDescription = description }
            .graphicsLayer {
                alpha = pulse.value
                scaleX = beat.value
                scaleY = beat.value
            }
            .drawBehind {
                if (urgent) softGlow(TesadufColors.Pink, center, size.maxDimension * (if (finalSeconds) 1f else 0.7f), if (finalSeconds) 0.32f else 0.18f)
            }
            .clip(Shapes.pill)
            .background(TesadufColors.Night.copy(alpha = 0.6f))
            .border(1.5.dp, border, Shapes.pill)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, style = IdTextStyle.copy(fontSize = 16.sp, color = if (urgent) TesadufColors.Pink else TesadufColors.TextPrimary))
    }
}

/**
 * Last-message preview that makes the author obvious: "Sen: …" in cyan for the user,
 * "#PARTNER: …" in pink for the other person.
 */
@Composable
fun TesadufLastMessage(
    body: String,
    mine: Boolean,
    partnerLabel: String,
    modifier: Modifier = Modifier,
    color: Color = TesadufColors.TextSecondary,
) {
    val you = stringResource(R.string.preview_you)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = if (mine) TesadufColors.Cyan else TesadufColors.Pink, fontWeight = FontWeight.SemiBold)) {
            append(if (mine) you else partnerLabel)
            append(": ")
        }
        append(body)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(color = color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Small status badge (Kader / Tamamlandı / Aktif) with an optional leading icon. */
@Composable
fun TesadufBadge(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(Shapes.chip)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.45f), Shapes.chip)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
