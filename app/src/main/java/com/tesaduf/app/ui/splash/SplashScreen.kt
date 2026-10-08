package com.tesaduf.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.tesaduf.app.ui.components.windowWidthDp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tesaduf.app.R
import com.tesaduf.app.ui.components.GlowingLogo
import com.tesaduf.app.ui.components.TesadufBackground
import com.tesaduf.app.ui.theme.TesadufColors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_TOTAL_MS = 1_700L

/**
 * Branded splash: background fades in, the untouched logo fades and scales up slightly
 * while its glow strengthens, then the wordmark and slogan appear. ~1.7 s total; the
 * anonymous session bootstrap runs in parallel, so the splash never adds waiting.
 */
@Composable
fun SplashScreen(onStart: () -> Unit, onFinished: () -> Unit) {
    val background = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.92f) }
    val glow = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val slogan = remember { Animatable(0f) }
    val finished = rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        onStart()
        val ease = FastOutSlowInEasing
        listOf(
            async { background.animateTo(1f, tween(300)) },
            async { delay(150); launch { logoAlpha.animateTo(1f, tween(650, easing = ease)) }; logoScale.animateTo(1f, tween(800, easing = ease)) },
            async { delay(300); glow.animateTo(1f, tween(900, easing = ease)) },
            async { delay(750); title.animateTo(1f, tween(420, easing = ease)) },
            async { delay(950); slogan.animateTo(1f, tween(420, easing = ease)) },
        ).awaitAll()
        delay((SPLASH_TOTAL_MS - 1_370L).coerceAtLeast(0))
        finished.value()
    }

    val logoSize = (windowWidthDp() * 0.58f).coerceIn(180f, 300f).dp

    TesadufBackground(Modifier.graphicsLayer { alpha = background.value }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlowingLogo(
                size = logoSize,
                pulse = false,
                glowStrength = 1f,
                modifier = Modifier.graphicsLayer {
                    alpha = logoAlpha.value
                    // Uniform scale only: the logo's proportions never change.
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                },
                glowAlpha = { glow.value },
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.brand_wordmark),
                style = MaterialTheme.typography.displaySmall.merge(
                    TextStyle(brush = Brush.linearGradient(listOf(TesadufColors.Cyan, TesadufColors.Purple, TesadufColors.Pink))),
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 12.dp.toPx()
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.slogan),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = slogan.value },
            )
        }
    }
}
