package com.tesaduf.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** TESADÜF palette: deep night base, neon used only for accents and glow. */
object TesadufColors {
    val Night = Color(0xFF070914)
    val NightRaised = Color(0xFF0C1028)
    val Ink = Color(0xFF111633)

    val Cyan = Color(0xFF22E6FF)
    val Blue = Color(0xFF2F7BFF)
    val Purple = Color(0xFF8B5CFF)
    val Pink = Color(0xFFFF3DBB)

    val TextPrimary = Color(0xFFF2F5FF)
    val TextSecondary = Color(0xFF9AA3C7)
    val TextMuted = Color(0xFF636C93)

    val Glass = Color(0x0FFFFFFF)
    val GlassStrong = Color(0x1AFFFFFF)
    val GlassStroke = Color(0x24FFFFFF)

    val Success = Color(0xFF3DF5A8)
    val Warning = Color(0xFFFFB347)
    val Danger = Color(0xFFFF5C7A)

    val PrimaryGradient = Brush.linearGradient(listOf(Cyan, Blue, Purple))
    val HeartGradient = Brush.linearGradient(listOf(Purple, Pink))
    val MineBubble = Brush.linearGradient(listOf(Color(0xFF1FC8F0), Color(0xFF2F6BFF)))
}

private val ColorScheme = darkColorScheme(
    primary = TesadufColors.Cyan,
    onPrimary = TesadufColors.Night,
    secondary = TesadufColors.Purple,
    tertiary = TesadufColors.Pink,
    background = TesadufColors.Night,
    onBackground = TesadufColors.TextPrimary,
    surface = TesadufColors.NightRaised,
    onSurface = TesadufColors.TextPrimary,
    surfaceVariant = TesadufColors.Ink,
    onSurfaceVariant = TesadufColors.TextSecondary,
    surfaceContainerHigh = TesadufColors.Ink,
    error = TesadufColors.Danger,
    outline = TesadufColors.GlassStroke,
)

private val Base = TextStyle(fontFamily = FontFamily.SansSerif, color = TesadufColors.TextPrimary)

private val AppTypography = Typography(
    displaySmall = Base.copy(fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp),
    headlineSmall = Base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = Base.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = Base.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = Base.copy(fontSize = 14.sp, lineHeight = 20.sp, color = TesadufColors.TextSecondary),
    bodySmall = Base.copy(fontSize = 12.sp, lineHeight = 16.sp, color = TesadufColors.TextSecondary),
    labelLarge = Base.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp),
    labelMedium = Base.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
    labelSmall = Base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

/** Monospace style for anonymous ids and timers (no layout jitter while digits change). */
val IdTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    color = TesadufColors.TextPrimary,
    letterSpacing = 2.sp,
)

@Composable
fun TesadufTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography, content = content)
}
