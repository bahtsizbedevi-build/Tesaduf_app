package com.tesaduf.app.ui.components

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tesaduf.app.R
import com.tesaduf.app.network.AppError
import com.tesaduf.app.ui.theme.TesadufColors

/** Current window width / height in dp (multi-window aware). */
@Composable
fun windowWidthDp(): Float = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }

@Composable
fun windowHeightDp(): Float = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp().value }

/** Night gradient with two static, soft neon glows. Drawn once; no animation cost. */
@Composable
fun TesadufBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TesadufColors.Night, TesadufColors.NightRaised, TesadufColors.Night),
                ),
            )
            .drawBehind {
                val r = size.maxDimension * 0.55f
                drawCircle(
                    Brush.radialGradient(
                        listOf(TesadufColors.Cyan.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(0f, size.height * 0.12f), radius = r,
                    ),
                    radius = r, center = Offset(0f, size.height * 0.12f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(TesadufColors.Pink.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width, size.height * 0.88f), radius = r,
                    ),
                    radius = r, center = Offset(size.width, size.height * 0.88f),
                )
            },
        content = content,
    )
}

/**
 * The real TESADÜF logo, drawn untouched (ContentScale.Fit on a square box: no crop,
 * no stretch, no filter). Effects live only *behind* it: a soft cyan → purple → pink
 * glow that can breathe slowly.
 */
@Composable
fun GlowingLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    pulse: Boolean = true,
    glowStrength: Float = 1f,
    glowAlpha: () -> Float = { 1f },
) {
    val transition = rememberInfiniteTransition(label = "logoGlow")
    val breath by if (pulse) {
        transition.animateFloat(
            initialValue = 0.75f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                // Reading the animated value here only re-draws; no recomposition.
                val strength = (breath * glowStrength * glowAlpha()).coerceIn(0f, 1f)
                val radius = this.size.minDimension * 0.62f
                val c = center
                drawCircle(
                    Brush.radialGradient(
                        listOf(TesadufColors.Cyan.copy(alpha = 0.28f * strength), Color.Transparent),
                        center = c + Offset(-radius * 0.18f, -radius * 0.06f), radius = radius,
                    ),
                    radius = radius, center = c + Offset(-radius * 0.18f, -radius * 0.06f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(TesadufColors.Pink.copy(alpha = 0.24f * strength), Color.Transparent),
                        center = c + Offset(radius * 0.2f, radius * 0.08f), radius = radius,
                    ),
                    radius = radius, center = c + Offset(radius * 0.2f, radius * 0.08f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(TesadufColors.Purple.copy(alpha = 0.22f * strength), Color.Transparent),
                        center = c, radius = radius * 1.1f,
                    ),
                    radius = radius * 1.1f, center = c,
                )
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

/** Frosted-glass card: translucent fill + hairline gradient stroke. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = TesadufColors.Glass,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(
                1.dp,
                Brush.verticalGradient(listOf(TesadufColors.GlassStroke, Color.White.copy(alpha = 0.04f))),
                shape,
            ),
        content = content,
    )
}

/** Primary CTA: neon gradient pill with a soft outer glow and a glassy top sheen. */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = TesadufColors.PrimaryGradient,
    glowColor: Color = TesadufColors.Cyan,
    enabled: Boolean = true,
    loading: Boolean = false,
    animateGlow: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pressScale")
    val transition = rememberInfiniteTransition(label = "ctaGlow")
    val glowPulse by if (animateGlow && enabled) {
        transition.animateFloat(
            0.55f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glowPulse",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.7f) }
    }
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier = modifier
            .heightIn(min = 60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .drawBehind {
                // Cheap fake blur: a few expanding translucent round rects.
                val strength = if (enabled) glowPulse else 0f
                for (i in 1..4) {
                    val spread = i * 5.dp.toPx()
                    drawRoundRect(
                        color = glowColor.copy(alpha = 0.07f * strength),
                        topLeft = Offset(-spread, -spread + 4.dp.toPx()),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(size.height / 2 + spread),
                    )
                }
            }
            .clip(shape)
            .background(brush)
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Transparent)),
            )
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .selectable(
                selected = false,
                enabled = enabled && !loading,
                role = Role.Button,
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

/** Secondary action: glass pill. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = TesadufColors.TextPrimary,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(TesadufColors.Glass)
            .border(1.dp, TesadufColors.GlassStroke, shape)
            .selectable(
                selected = false, enabled = enabled, role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(), onClick = onClick,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, textAlign = TextAlign.Center)
    }
}

private val AvatarPalettes = listOf(
    TesadufColors.Cyan to TesadufColors.Blue,
    TesadufColors.Blue to TesadufColors.Purple,
    TesadufColors.Purple to TesadufColors.Pink,
    TesadufColors.Pink to Color(0xFFFF8A3D),
    Color(0xFF3DF5A8) to TesadufColors.Cyan,
    Color(0xFF6E8BFF) to Color(0xFFB45CFF),
    Color(0xFFFF5CA8) to TesadufColors.Purple,
    Color(0xFF22E6FF) to Color(0xFFFF3DBB),
)

/**
 * Anonymous avatar: a glossy chat-bubble orb with two eyes, echoing the logo's
 * characters. Fully vector, so it is crisp at any size and costs no bitmap memory.
 */
@Composable
fun AnonAvatar(avatarKey: String, size: Dp, modifier: Modifier = Modifier, glow: Boolean = true) {
    val index = avatarKey.substringAfter("orb_").toIntOrNull()?.minus(1)?.coerceIn(0, 7) ?: 0
    val (from, to) = AvatarPalettes[index]
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2
                if (glow) {
                    drawCircle(
                        Brush.radialGradient(listOf(from.copy(alpha = 0.35f), Color.Transparent), center, r * 1.5f),
                        radius = r * 1.5f,
                    )
                }
                drawCircle(Brush.linearGradient(listOf(from, to), Offset.Zero, Offset(this.size.width, this.size.height)), radius = r * 0.92f)
                // Gloss highlight.
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(center.x - r * 0.35f, center.y - r * 0.45f), radius = r * 0.55f,
                    ),
                    radius = r * 0.55f, center = Offset(center.x - r * 0.35f, center.y - r * 0.45f),
                )
                // Eyes.
                val eye = Size(r * 0.2f, r * 0.34f)
                val eyeY = center.y - r * 0.12f
                drawOval(Color.White.copy(alpha = 0.95f), Offset(center.x - r * 0.34f, eyeY), eye)
                drawOval(Color.White.copy(alpha = 0.95f), Offset(center.x + r * 0.14f, eyeY), eye)
            },
    )
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .drawBehind {
                drawCircle(color.copy(alpha = 0.3f), radius = size.minDimension)
                drawCircle(color)
            },
    )
}

/** Friendly inline error with optional retry. Never shows exception text. */
@Composable
fun ErrorBanner(error: AppError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), fill = TesadufColors.Danger.copy(alpha = 0.10f)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(TesadufColors.Danger)
            Text(
                stringResource(error.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = TesadufColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                Text(
                    stringResource(R.string.retry),
                    style = MaterialTheme.typography.labelMedium,
                    color = TesadufColors.Cyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(false, onClick = onRetry, role = Role.Button)
                        .padding(8.dp),
                )
            }
        }
    }
}

/** Small radio row used in the report dialog. */
@Composable
fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TesadufColors.Cyan.copy(alpha = 0.10f) else Color.Transparent)
            .selectable(selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) TesadufColors.Cyan else TesadufColors.TextMuted, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (selected) TesadufColors.Cyan else Color.Transparent),
        )
        Box(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A rotating conic-gradient ring (used while loading). Rotation happens in the draw layer. */
@Composable
fun NeonSpinner(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "angle",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { rotationZ = angle }
            .drawBehind {
                val stroke = 3.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, TesadufColors.Cyan, TesadufColors.Purple, TesadufColors.Pink)),
                    startAngle = 0f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(stroke, stroke),
                    size = Size(this.size.width - stroke * 2, this.size.height - stroke * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            },
    )
}
