package com.tesaduf.app.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors

enum class ButtonTone(val fill: Brush, val glow: Color) {
    /** The one signature CTA per screen. */
    Signature(TesadufColors.Signature, TesadufColors.Blue),
    Cool(TesadufColors.CoolAccent, TesadufColors.Cyan),
    Heart(TesadufColors.Heart, TesadufColors.Pink),
    Danger(TesadufColors.DangerFill, TesadufColors.Danger),
}

/**
 * Primary TESADÜF button: gradient pill with a restrained outer glow and a glassy top
 * sheen. Press: scale 0.97 and the glow tightens.
 */
@Composable
fun TesadufButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Cool,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    minHeight: Dp = 56.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    /** A soft light streak sweeps across every few seconds (hero CTAs only). */
    shimmer: Boolean = false,
) {
    val sweep: State<Float> = if (shimmer && enabled) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            0f, 1f, infiniteRepeatable(tween(4_200, easing = LinearEasing)), label = "s",
        )
    } else {
        remember { mutableFloatStateOf(-1f) }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "btnScale")
    val glow by animateFloatAsState(
        when {
            !enabled -> 0f
            pressed -> 1f
            else -> 0.6f
        },
        label = "btnGlow",
    )
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .drawBehind {
                // Fake blur: three widening, fading round rects under the pill.
                for (i in 1..3) {
                    val spread = i * 4.dp.toPx()
                    drawRoundRect(
                        color = tone.glow.copy(alpha = 0.08f * glow),
                        topLeft = Offset(-spread, -spread + 3.dp.toPx()),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(size.height / 2 + spread),
                    )
                }
            }
            .clip(Shapes.pill)
            .background(tone.fill)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent)))
            .drawWithContent {
                drawContent()
                // Streak travels during the first ~35% of the cycle, then rests.
                val p = sweep.value / 0.35f
                if (p in 0f..1f) {
                    val w = size.width
                    val x = -w * 0.4f + w * 1.8f * p
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                            start = Offset(x, 0f),
                            end = Offset(x + w * 0.3f, size.height),
                        ),
                    )
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.22f), Shapes.pill)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            TesadufLoadingDots(color = Color.White)
        } else {
            ButtonContent(text, icon, Color.White, textStyle)
        }
    }
}

/** Secondary: dark glass pill with a hairline outline. */
@Composable
fun TesadufSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = TesadufColors.TextPrimary,
    outline: Color = TesadufColors.Stroke,
    minHeight: Dp = 52.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "secScale")
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clip(Shapes.pill)
            .background(TesadufColors.Glass)
            .border(1.dp, if (pressed) contentColor.copy(alpha = 0.5f) else outline, Shapes.pill)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(text, icon, contentColor, MaterialTheme.typography.labelLarge)
    }
}

/** Low-emphasis text action (e.g. "Geç", "Vazgeç"). Keeps a 48dp touch target. */
@Composable
fun TesadufTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TesadufColors.TextSecondary,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(Shapes.chip)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) color else TesadufColors.TextMuted)
    }
}

/** Round glass icon button, 48dp target. */
@Composable
fun TesadufIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TesadufColors.TextPrimary,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(TesadufColors.Glass).border(1.dp, TesadufColors.StrokeSoft, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) tint else TesadufColors.TextMuted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, color: Color, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = style, color = color, textAlign = TextAlign.Center)
    }
}
