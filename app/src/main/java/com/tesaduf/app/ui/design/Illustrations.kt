package com.tesaduf.app.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tesaduf.app.ui.theme.TesadufColors

/** Wraps content with a soft neon glow behind it. */
@Composable
fun TesadufGlow(
    modifier: Modifier = Modifier,
    color: Color = TesadufColors.Cyan,
    secondary: Color = TesadufColors.Purple,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.drawBehind {
            val r = size.minDimension * 0.75f
            softGlow(color, center + Offset(-r * 0.15f, 0f), r, 0.20f * intensity)
            softGlow(secondary, center + Offset(r * 0.15f, r * 0.05f), r, 0.16f * intensity)
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Icon inside a glowing glass circle (onboarding features, settings, dialogs). */
@Composable
fun TesadufIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = TesadufColors.Cyan,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

private fun DrawScope.neonStroke(path: Path, brush: Brush, width: Float) {
    // Glow pass + crisp pass.
    drawPath(path, brush, alpha = 0.25f, style = Stroke(width * 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, brush, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun heartPath(size: Size): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(w / 2, h * 0.92f)
    cubicTo(w * -0.05f, h * 0.55f, w * 0.05f, h * 0.02f, w / 2, h * 0.27f)
    cubicTo(w * 0.95f, h * 0.02f, w * 1.05f, h * 0.55f, w / 2, h * 0.92f)
    close()
}

/** Two overlapping neon hearts with a gentle heartbeat glow (Destiny). */
@Composable
fun HeartsIllustration(modifier: Modifier = Modifier, animate: Boolean = true) {
    val beat: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "beat").animateFloat(
            0.75f, 1f, infiniteRepeatable(tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Box(
        modifier.drawBehind {
            val b = beat.value
            val r = size.minDimension * 0.55f
            softGlow(TesadufColors.Pink, center, r, 0.30f * b)
            softGlow(TesadufColors.Cyan, center + Offset(-r * 0.3f, 0f), r * 0.8f, 0.16f * b)
            val heart = Size(size.width * 0.62f, size.height * 0.62f)
            val stroke = 4.dp.toPx()
            translate(left = size.width * 0.08f, top = size.height * 0.2f) {
                neonStroke(heartPath(heart), Brush.linearGradient(listOf(TesadufColors.Cyan, TesadufColors.Blue)), stroke)
            }
            translate(left = size.width * 0.30f, top = size.height * 0.16f) {
                neonStroke(heartPath(heart), Brush.linearGradient(listOf(TesadufColors.Purple, TesadufColors.Pink)), stroke)
            }
        },
    )
}

/** Neon hourglass (session end). With [animate] it flips over every few seconds. */
@Composable
fun HourglassIllustration(modifier: Modifier = Modifier, animate: Boolean = false) {
    val flip: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "flip").animateFloat(
            0f, 180f,
            infiniteRepeatable(keyframes {
                durationMillis = 3_400
                0f at 0
                0f at 2_600 using FastOutSlowInEasing
                180f at 3_400
            }),
            label = "r",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Box(
        modifier.graphicsLayer { rotationZ = flip.value }.drawBehind {
            val w = size.width
            val h = size.height
            softGlow(TesadufColors.Cyan, center, size.minDimension * 0.6f, 0.22f)
            val brush = Brush.verticalGradient(listOf(TesadufColors.Cyan, TesadufColors.Blue, TesadufColors.Purple))
            val stroke = 4.dp.toPx()
            val frame = Path().apply {
                moveTo(w * 0.22f, h * 0.12f); lineTo(w * 0.78f, h * 0.12f)
                moveTo(w * 0.22f, h * 0.88f); lineTo(w * 0.78f, h * 0.88f)
                moveTo(w * 0.28f, h * 0.12f)
                cubicTo(w * 0.28f, h * 0.38f, w * 0.46f, h * 0.44f, w * 0.48f, h * 0.5f)
                cubicTo(w * 0.46f, h * 0.56f, w * 0.28f, h * 0.62f, w * 0.28f, h * 0.88f)
                moveTo(w * 0.72f, h * 0.12f)
                cubicTo(w * 0.72f, h * 0.38f, w * 0.54f, h * 0.44f, w * 0.52f, h * 0.5f)
                cubicTo(w * 0.54f, h * 0.56f, w * 0.72f, h * 0.62f, w * 0.72f, h * 0.88f)
            }
            neonStroke(frame, brush, stroke)
            // Sand.
            val sand = Path().apply {
                moveTo(w * 0.36f, h * 0.84f)
                quadraticTo(w * 0.5f, h * 0.64f, w * 0.64f, h * 0.84f)
                close()
            }
            drawPath(sand, Brush.verticalGradient(listOf(TesadufColors.Cyan, TesadufColors.Blue)), alpha = 0.85f)
            drawLine(TesadufColors.Cyan, Offset(w * 0.5f, h * 0.52f), Offset(w * 0.5f, h * 0.7f), strokeWidth = stroke * 0.5f, cap = StrokeCap.Round)
        },
    )
}

/**
 * Neon hearts drifting upwards behind a celebration (Kader). One infinite transition,
 * everything computed in the draw phase.
 */
@Composable
fun FloatingHearts(modifier: Modifier = Modifier, count: Int = 16) {
    val t by rememberInfiniteTransition(label = "hearts").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "t",
    )
    Box(
        modifier.drawBehind {
            val colors = listOf(TesadufColors.Pink, TesadufColors.Purple, TesadufColors.Cyan)
            for (i in 0 until count) {
                val seed = (i * 37 % 100) / 100f
                val speed = 0.6f + (i % 4) * 0.15f
                val p = (t * speed + seed) % 1f
                val sway = sin((p * 6f + i).toDouble()).toFloat() * 14.dp.toPx()
                val x = size.width * ((seed * 1.9f + i * 0.07f) % 1f) + sway
                val y = size.height * (1.05f - p * 1.1f)
                val s = (10f + (i % 3) * 6f).dp.toPx()
                val alpha = (if (p < 0.15f) p / 0.15f else 1f) * (1f - p) * 0.55f
                translate(left = x - s / 2, top = y - s / 2) {
                    drawPath(heartPath(Size(s, s)), colors[i % 3].copy(alpha = alpha))
                }
            }
        },
    )
}

/** Red "blocked" sign. */
@Composable
fun BlockIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val r = size.minDimension * 0.36f
            softGlow(TesadufColors.Danger, center, r * 1.8f, 0.28f)
            val stroke = 6.dp.toPx()
            drawCircle(TesadufColors.Danger.copy(alpha = 0.12f), r)
            drawCircle(TesadufColors.Danger, r, style = Stroke(stroke))
            val d = r * 0.7f
            drawLine(TesadufColors.Danger, center + Offset(-d, -d), center + Offset(d, d), stroke, StrokeCap.Round)
        },
    )
}

/** Four-point neon sparkle used by empty states. */
@Composable
fun SparkIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            val r = size.minDimension * 0.42f
            softGlow(TesadufColors.Purple, center, r * 1.5f, 0.25f)
            val star = Path().apply {
                moveTo(center.x, center.y - r)
                quadraticTo(center.x, center.y, center.x + r, center.y)
                quadraticTo(center.x, center.y, center.x, center.y + r)
                quadraticTo(center.x, center.y, center.x - r, center.y)
                quadraticTo(center.x, center.y, center.x, center.y - r)
                close()
            }
            neonStroke(star, Brush.linearGradient(listOf(TesadufColors.Cyan, TesadufColors.Purple, TesadufColors.Pink)), 3.dp.toPx())
            drawCircle(TesadufColors.Cyan, 3.dp.toPx(), center + Offset(r * 0.9f, -r * 0.8f))
            drawCircle(TesadufColors.Pink, 2.5.dp.toPx(), center + Offset(-r * 0.85f, r * 0.75f))
        },
    )
}
