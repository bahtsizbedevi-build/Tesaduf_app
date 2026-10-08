package com.tesaduf.app.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesaduf.app.R
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors
import kotlin.math.PI
import kotlin.math.sin

/** Current window width / height in dp (multi-window aware). */
@Composable
fun windowWidthDp(): Float = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }

@Composable
fun windowHeightDp(): Float = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp().value }

/** Draws a soft radial glow; [alpha] lets callers animate intensity without recomposition. */
fun DrawScope.softGlow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0f || radius <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius),
        radius = radius,
        center = center,
    )
}

/**
 * App background: night gradient with two faint ambient glows. With [particles] a few
 * slow-drifting specks are added (splash/onboarding only).
 */
@Composable
fun TesadufBackground(
    modifier: Modifier = Modifier,
    particles: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val drift: State<Float> = if (particles) {
        rememberInfiniteTransition(label = "drift").animateFloat(
            0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "t",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TesadufColors.Night, TesadufColors.NightRaised, TesadufColors.Night)))
            .drawBehind {
                val r = size.maxDimension * 0.5f
                softGlow(TesadufColors.Cyan, Offset(0f, size.height * 0.08f), r, 0.07f)
                softGlow(TesadufColors.Purple, Offset(size.width, size.height * 0.92f), r, 0.09f)
                if (particles) {
                    val t = drift.value
                    for (i in 0 until PARTICLE_COUNT) {
                        val seed = (i * 73 % 100) / 100f
                        val x = size.width * ((seed * 1.7f + i * 0.13f) % 1f)
                        val y = size.height * ((seed + t * (0.15f + 0.1f * (i % 3))) % 1f)
                        val twinkle = 0.25f + 0.35f * (0.5f + 0.5f * sin(2 * PI * (t * 3 + seed)).toFloat())
                        drawCircle(
                            color = (if (i % 3 == 0) TesadufColors.Pink else TesadufColors.Cyan).copy(alpha = twinkle),
                            radius = (1f + i % 3) * density * 0.8f,
                            center = Offset(x, size.height - y),
                        )
                    }
                }
            },
        content = content,
    )
}

private const val PARTICLE_COUNT = 16

/**
 * The real TESADÜF logo, untouched: square box + ContentScale.Fit (no crop, no stretch,
 * no tint). Only a soft cyan / purple / pink glow lives *behind* it.
 */
@Composable
fun TesadufLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    pulse: Boolean = false,
    glow: Float = 1f,
    glowAlpha: () -> Float = { 1f },
) {
    val breath: State<Float> = if (pulse) {
        rememberInfiniteTransition(label = "logoPulse").animateFloat(
            0.7f, 1f, infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val a = (breath.value * glow * glowAlpha()).coerceIn(0f, 1f)
                val r = this.size.minDimension * 0.62f
                softGlow(TesadufColors.Cyan, center + Offset(-r * 0.2f, 0f), r, 0.22f * a)
                softGlow(TesadufColors.Pink, center + Offset(r * 0.2f, r * 0.06f), r, 0.18f * a)
                softGlow(TesadufColors.Purple, center, r * 1.1f, 0.18f * a)
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.tesaduf_logo),
            contentDescription = stringResource(R.string.logo_content_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    }
}

/** "TESADÜF" wordmark: wide tracking, quiet gradient. */
@Composable
fun TesadufWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 30.sp, letterSpacing: TextUnit = 8.sp) {
    Text(
        text = stringResource(R.string.brand_wordmark),
        style = MaterialTheme.typography.displaySmall.merge(
            TextStyle(
                fontSize = fontSize,
                letterSpacing = letterSpacing,
                brush = Brush.horizontalGradient(listOf(Color.White, Color(0xFFD9E6FF), Color(0xFFF1D9FF))),
            ),
        ),
        modifier = modifier,
    )
}

/**
 * Glass card: translucent fill + hairline stroke. Optional [onClick] adds press feedback
 * (scale 0.98 + a slightly brighter stroke).
 */
@Composable
fun TesadufGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.card,
    fill: Color = TesadufColors.Glass,
    stroke: Color = TesadufColors.Stroke,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "cardScale")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .border(1.dp, if (pressed) TesadufColors.Cyan.copy(alpha = 0.35f) else stroke, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(color = TesadufColors.Cyan),
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}
