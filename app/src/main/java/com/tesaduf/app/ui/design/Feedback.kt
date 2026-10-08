package com.tesaduf.app.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tesaduf.app.R
import com.tesaduf.app.network.AppError
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors

/** Three neon dots breathing in sequence — the app's loading indicator. */
@Composable
fun TesadufLoadingDots(
    modifier: Modifier = Modifier,
    color: Color? = null,
    dotSize: Dp = 8.dp,
    count: Int = 3,
) {
    val description = stringResource(R.string.loading)
    val t by rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1_200, easing = LinearEasing)), label = "t",
    )
    val palette = listOf(TesadufColors.Cyan, TesadufColors.Blue, TesadufColors.Purple, TesadufColors.Pink)
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(dotSize * 0.9f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        val phase = ((t - i * 0.18f) % 1f + 1f) % 1f
                        val wave = if (phase < 0.5f) phase * 2 else (1f - phase) * 2
                        alpha = 0.35f + 0.65f * wave
                        scaleX = 0.8f + 0.3f * wave
                        scaleY = scaleX
                    }
                    .clip(CircleShape)
                    .background(color ?: palette[i % palette.size]),
            )
        }
    }
}

/** Circular neon progress ring for full-screen loading. */
@Composable
fun TesadufProgressRing(size: Dp, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.loading)
    val angle by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "a",
    )
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = description }
            .graphicsLayer { rotationZ = angle }
            .drawBehind {
                val stroke = 3.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, TesadufColors.Cyan, TesadufColors.Purple)),
                    startAngle = 0f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(stroke, stroke),
                    size = Size(this.size.width - stroke * 2, this.size.height - stroke * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
    )
}

/** Pulsing placeholder block used to build skeleton rows. */
@Composable
fun TesadufSkeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = Shapes.chip) {
    val a by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.05f, 0.11f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a",
    )
    Box(modifier.clip(shape).drawBehind { drawRect(Color.White.copy(alpha = a)) })
}

/** Skeleton for list rows (history, blocked users). */
@Composable
fun TesadufSkeletonRow(modifier: Modifier = Modifier) {
    TesadufGlassCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TesadufSkeleton(Modifier.size(44.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TesadufSkeleton(Modifier.width(120.dp).height(14.dp))
                Spacer(Modifier.height(8.dp))
                TesadufSkeleton(Modifier.width(160.dp).height(10.dp))
            }
            TesadufSkeleton(Modifier.width(72.dp).height(24.dp))
        }
    }
}

/**
 * Empty state: small neon illustration, title, body and an optional action.
 * [illustration] receives the available size.
 */
@Composable
fun TesadufEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = { SparkIllustration(Modifier.size(120.dp)) },
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        illustration()
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            TesadufButton(actionLabel, onAction, Modifier.widthIn(min = 220.dp))
        }
    }
}

/** Inline error/status message with optional retry. Never shows exception text. */
@Composable
fun TesadufInlineMessage(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val icon: ImageVector = if (error.isConnectivity) TIcons.WifiOff else TIcons.Alert
    val accent = if (error.isConnectivity) TesadufColors.Warning else TesadufColors.Danger
    TesadufGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        fill = accent.copy(alpha = 0.08f),
        stroke = accent.copy(alpha = 0.25f),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(error.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = TesadufColors.TextPrimary,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            if (onRetry != null) {
                TesadufTextButton(stringResource(R.string.retry), onRetry, color = TesadufColors.Cyan)
            }
        }
    }
}

/** Snackbar host with the TESADÜF glass snackbar (replaces toasts). */
@Composable
fun TesadufSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> TesadufSnackbar(data) }
}

@Composable
private fun TesadufSnackbar(data: SnackbarData) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TesadufColors.CardHigh)
            .drawBehind { drawRect(Brush.horizontalGradient(listOf(TesadufColors.Cyan, TesadufColors.Purple)), size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = TesadufColors.TextPrimary,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        data.visuals.actionLabel?.let { label ->
            TesadufTextButton(label, onClick = data::performAction, color = TesadufColors.Cyan)
        }
    }
}
