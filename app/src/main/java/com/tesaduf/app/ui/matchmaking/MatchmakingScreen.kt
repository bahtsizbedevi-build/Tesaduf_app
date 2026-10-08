package com.tesaduf.app.ui.matchmaking

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.tesaduf.app.ui.components.windowWidthDp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesaduf.app.R
import com.tesaduf.app.data.NetworkMonitor
import com.tesaduf.app.network.AppError
import com.tesaduf.app.ui.components.ErrorBanner
import com.tesaduf.app.ui.components.GlassButton
import com.tesaduf.app.ui.components.GlassSurface
import com.tesaduf.app.ui.components.GlowingLogo
import com.tesaduf.app.ui.components.TesadufBackground
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatCountdown
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MatchmakingScreen(
    viewModel: MatchmakingViewModel,
    networkMonitor: NetworkMonitor,
    onMatched: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val matched = rememberUpdatedState(onMatched)

    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(state.matchedId) {
        state.matchedId?.let { matched.value(it) }
    }
    // Resume immediately when connectivity comes back instead of waiting for backoff.
    LaunchedEffect(online) {
        if (online && state.error?.isConnectivity == true) viewModel.retryNow()
    }

    val cancel = {
        viewModel.cancel()
        onCancel()
    }
    BackHandler(onBack = cancel)

    val elapsed by produceState(0L, state.searchStartedAtMs) {
        while (true) {
            value = System.currentTimeMillis() - state.searchStartedAtMs
            delay(1_000)
        }
    }
    val orbSize = (windowWidthDp() * 0.68f).coerceIn(220f, 320f).dp

    TesadufBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center) {
                SearchingOrb(orbSize)
                GlowingLogo(size = orbSize * 0.42f, pulse = true, glowStrength = 0.6f)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                if (state.matchedId != null) stringResource(R.string.mm_found) else stringResource(R.string.mm_searching),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.mm_searching_sub),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(formatCountdown(elapsed), style = IdTextStyle.copy(color = TesadufColors.Cyan))
            if (state.othersWaiting > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    pluralStringResource(R.plurals.mm_others_waiting, state.othersWaiting, state.othersWaiting),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val error: AppError? = if (!online) AppError.NoInternet else state.error
                AnimatedVisibility(error != null, enter = fadeIn(), exit = fadeOut()) {
                    error?.let { ErrorBanner(it, Modifier.padding(bottom = 16.dp), onRetry = viewModel::retryNow) }
                }
                AnimatedVisibility(state.showNotFoundHint && error == null, enter = fadeIn(), exit = fadeOut()) {
                    GlassSurface(Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.mm_not_found), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.mm_not_found_sub), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            GlassButton(
                                stringResource(R.string.mm_keep_searching),
                                onClick = viewModel::keepSearching,
                                contentColor = TesadufColors.Cyan,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                GlassButton(stringResource(R.string.mm_cancel), onClick = cancel, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Rotating neon ring + two soft pulse rings + a handful of orbiting particles.
 * One infinite transition drives everything and it is all read in the draw phase,
 * so the animation never triggers recomposition.
 */
@Composable
private fun SearchingOrb(size: Dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val t by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "t",
    )
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_200, easing = LinearEasing), RepeatMode.Restart), label = "pulse",
    )
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2
                // Pulse rings expanding outwards.
                for (k in 0..1) {
                    val p = (pulse + k * 0.5f) % 1f
                    drawCircle(
                        color = TesadufColors.Cyan.copy(alpha = 0.22f * (1f - p)),
                        radius = r * (0.55f + 0.45f * p),
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
                // Rotating gradient ring.
                val stroke = 4.dp.toPx()
                val ringInset = r * 0.2f
                rotate(t * 360f * 2) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, TesadufColors.Cyan, TesadufColors.Purple, TesadufColors.Pink, Color.Transparent),
                        ),
                        startAngle = 0f, sweepAngle = 320f, useCenter = false,
                        topLeft = Offset(ringInset, ringInset),
                        size = Size(this.size.width - ringInset * 2, this.size.height - ringInset * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                // Orbiting particles.
                val colors = listOf(TesadufColors.Cyan, TesadufColors.Purple, TesadufColors.Pink)
                for (i in 0 until PARTICLES) {
                    val speed = 1f + (i % 3) * 0.35f
                    val angle = 2 * PI * (t * speed + i / PARTICLES.toFloat())
                    val orbit = r * (0.72f + 0.2f * ((i * 37) % 10) / 10f)
                    val c = Offset(center.x + (orbit * cos(angle)).toFloat(), center.y + (orbit * sin(angle)).toFloat())
                    drawCircle(colors[i % 3].copy(alpha = 0.75f), radius = (1.5f + i % 3).dp.toPx(), center = c)
                }
            },
    )
}

private const val PARTICLES = 10
