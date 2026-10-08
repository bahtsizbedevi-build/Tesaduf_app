package com.tesaduf.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TESADÜF design tokens. Night surfaces carry the UI; neon is reserved for the CTA,
 * active states, progress, icons and key information.
 */
object TesadufColors {
    val Night = Color(0xFF070914)
    val NightRaised = Color(0xFF0A1020)
    val Card = Color(0xFF10182D)
    val CardHigh = Color(0xFF151F38)

    val Cyan = Color(0xFF00E5FF)
    val Blue = Color(0xFF3B82F6)
    val Purple = Color(0xFF7C3AED)
    val Pink = Color(0xFFF43F9E)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA7B0C5)
    val TextMuted = Color(0xFF6B7591)

    val Glass = Color(0x0FFFFFFF)
    val GlassStrong = Color(0x17FFFFFF)
    val Stroke = Color(0x1FFFFFFF)
    val StrokeSoft = Color(0x0FFFFFFF)

    val Danger = Color(0xFFEF4444)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFFFB020)

    /** The signature CTA gradient. Used once per screen at most. */
    val Signature = Brush.horizontalGradient(listOf(Cyan, Blue, Purple, Pink))
    /** Darker cyan→blue so white labels keep ≥4:1 contrast. */
    val CoolAccent = Brush.horizontalGradient(listOf(Color(0xFF0284C7), Blue, Color(0xFF6366F1)))
    val Heart = Brush.horizontalGradient(listOf(Purple, Pink))
    val MineBubble = Brush.linearGradient(listOf(Color(0xFF0891B2), Color(0xFF2563EB)))
    val DangerFill = Brush.horizontalGradient(listOf(Color(0xFFB4233A), Danger))
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Horizontal screen gutter. */
    val gutter = 20.dp
}

object Shapes {
    val pill = RoundedCornerShape(50)
    val card = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val field = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(12.dp)
}

private val Base = TextStyle(fontFamily = FontFamily.SansSerif, color = TesadufColors.TextPrimary)

/** H1 28 / H2 22 / H3 18 / body 16 / caption 13–14 / button 16 semibold. */
private val AppTypography = Typography(
    displaySmall = Base.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp),
    headlineMedium = Base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = Base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = Base.copy(fontSize = 14.sp, lineHeight = 20.sp, color = TesadufColors.TextSecondary),
    bodySmall = Base.copy(fontSize = 13.sp, lineHeight = 18.sp, color = TesadufColors.TextSecondary),
    labelLarge = Base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelMedium = Base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = Base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

/** Monospace for anonymous ids and timers (no jitter while digits change). */
val IdTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    color = TesadufColors.TextPrimary,
    letterSpacing = 1.5.sp,
)

private val ColorScheme = darkColorScheme(
    primary = TesadufColors.Cyan,
    onPrimary = TesadufColors.Night,
    secondary = TesadufColors.Purple,
    tertiary = TesadufColors.Pink,
    background = TesadufColors.Night,
    onBackground = TesadufColors.TextPrimary,
    surface = TesadufColors.Card,
    onSurface = TesadufColors.TextPrimary,
    onSurfaceVariant = TesadufColors.TextSecondary,
    error = TesadufColors.Danger,
    outline = TesadufColors.Stroke,
)

@Composable
fun TesadufTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography, content = content)
}
