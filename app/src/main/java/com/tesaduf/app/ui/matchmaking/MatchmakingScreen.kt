package com.tesaduf.app.ui.matchmaking

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesaduf.app.R
import com.tesaduf.app.data.NetworkMonitor
import com.tesaduf.app.network.AppError
import com.tesaduf.app.ui.cinematic.CinematicDirector
import com.tesaduf.app.ui.cinematic.CinematicMode
import com.tesaduf.app.ui.cinematic.CinematicScene
import com.tesaduf.app.ui.cinematic.rememberCinematicFx
import com.tesaduf.app.ui.design.TesadufButton
import com.tesaduf.app.ui.design.TesadufGlassCard
import com.tesaduf.app.ui.design.TesadufQuoteTicker
import com.tesaduf.app.ui.design.TesadufInlineMessage
import com.tesaduf.app.ui.design.TesadufSecondaryButton
import com.tesaduf.app.ui.design.TesadufTextButton
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.TesadufColors
import com.tesaduf.app.util.formatCountdown
import kotlinx.coroutines.delay

/**
 * Matchmaking as a story: "Bağlantılar kuruluyor → Yeni sohbetler yolda → Bir tesadüf
 * hazırlanıyor → Sana uygun biri aranıyor" loops while the server searches; when a
 * partner is found the paths cross ("Yollarınız kesişmek üzere"), "İşte o an…", then
 * the chat opens. Polling, cancel and errors work exactly as before.
 */
@Composable
fun MatchmakingScreen(
    viewModel: MatchmakingViewModel,
    networkMonitor: NetworkMonitor,
    hapticsEnabled: Boolean,
    onMatched: (String) -> Unit,
    onCancel: () -> Unit,
    onHome: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val matched = rememberUpdatedState(onMatched)
    val fx = rememberCinematicFx(hapticsEnabled)
    val director = remember { CinematicDirector(CinematicMode.Matchmaking, fx) }
    val found = state.matchedId != null

    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(found) { if (found) director.onMatched() }
    LaunchedEffect(director.finished) {
        val id = state.matchedId
        if (director.finished && id != null) matched.value(id)
    }
    LaunchedEffect(online) {
        if (online && state.error?.isConnectivity == true) viewModel.retryNow()
    }

    val cancel = {
        viewModel.cancel()
        onCancel()
    }
    BackHandler(enabled = !found, onBack = cancel)

    val elapsed by produceState(0L, state.searchStartedAtMs) {
        while (true) {
            value = System.currentTimeMillis() - state.searchStartedAtMs
            delay(1_000)
        }
    }

    // Scene on top, controls in their own area below: cards never cover the story's
    // caption / progress line; when a card appears the scene smoothly makes room.
    Box(Modifier.fillMaxSize().background(SceneBottom)) {
        Column(Modifier.fillMaxSize()) {
            CinematicScene(director, Modifier.weight(1f).fillMaxWidth())
            BottomControls(
                visible = !found,
                state = state,
                online = online,
                viewModel = viewModel,
                cancel = cancel,
                onHome = onHome,
            )
        }

        // Quiet status on top: elapsed time and how many others are searching.
        AnimatedVisibility(!found, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Column(Modifier.safeDrawingPadding().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatCountdown(elapsed), style = IdTextStyle.copy(color = TesadufColors.TextSecondary))
                if (state.othersWaiting > 0) {
                    Text(
                        pluralStringResource(R.plurals.mm_others_waiting, state.othersWaiting, state.othersWaiting),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(10.dp))
                TesadufQuoteTicker(Modifier.padding(horizontal = 32.dp), intervalMs = 6_000L)
            }
        }

    }
}


private val SceneBottom = Color(0xFF080617)

@Composable
private fun BottomControls(
    visible: Boolean,
    state: MatchmakingUiState,
    online: Boolean,
    viewModel: MatchmakingViewModel,
    cancel: () -> Unit,
    onHome: () -> Unit,
) {
    AnimatedVisibility(visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val error: AppError? = if (!online) AppError.NoInternet else state.error
            AnimatedVisibility(error != null, enter = fadeIn(), exit = fadeOut()) {
                error?.let { TesadufInlineMessage(it, Modifier.padding(bottom = 12.dp), onRetry = viewModel::retryNow) }
            }
            AnimatedVisibility(state.timedOut, enter = fadeIn(), exit = fadeOut()) {
                TesadufGlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.mm_timeout_title), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.mm_timeout_body), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        TesadufButton(stringResource(R.string.mm_retry), viewModel::restart, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        TesadufSecondaryButton(stringResource(R.string.mm_home), onHome, Modifier.fillMaxWidth())
                    }
                }
            }
            AnimatedVisibility(state.showNotFoundHint && error == null && !state.timedOut, enter = fadeIn(), exit = fadeOut()) {
                TesadufGlassCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.mm_not_found), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.mm_not_found_sub), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        TesadufTextButton(stringResource(R.string.mm_keep_searching), viewModel::keepSearching, color = TesadufColors.Cyan)
                    }
                }
            }
            if (!state.timedOut) {
                TesadufSecondaryButton(stringResource(R.string.mm_cancel), onClick = cancel, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
