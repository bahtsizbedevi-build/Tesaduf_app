package com.tesaduf.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesaduf.app.R
import com.tesaduf.app.model.Match
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.notifications.Reminders
import com.tesaduf.app.repository.SessionState
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.design.ButtonTone
import com.tesaduf.app.ui.design.TIcons
import com.tesaduf.app.ui.design.TesadufAvatar
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufButton
import com.tesaduf.app.ui.design.TesadufDialog
import com.tesaduf.app.ui.design.TesadufGlassCard
import com.tesaduf.app.ui.design.TesadufInlineMessage
import com.tesaduf.app.ui.design.TesadufLoadingDots
import com.tesaduf.app.ui.design.TesadufQuoteTicker
import com.tesaduf.app.ui.design.TesadufSecondaryButton
import com.tesaduf.app.ui.design.TesadufStatsCard
import com.tesaduf.app.ui.design.TesadufWordmark
import com.tesaduf.app.ui.design.softGlow
import com.tesaduf.app.ui.design.windowWidthDp
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Height reserved at the bottom of tab screens for the floating bottom bar. */
val BottomBarSpace = 124.dp

@Composable
fun HomeScreen(
    repository: TesadufRepository,
    onStart: () -> Unit,
    onResume: (matchId: String) -> Unit,
) {
    val session by repository.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coming back to Home refreshes stats and the "live match" card.
    LifecycleResumeEffect(Unit) {
        if (repository.session.value is SessionState.Ready) repository.refreshSession() else repository.ensureSession()
        onPauseOrDispose { }
    }

    // Ask for notification permission once (Android 13+), where reminders make sense.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = repository.preferences
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !prefs.notificationPromptShown && !Reminders.canPost(context)) {
            prefs.notificationPromptShown = true
            delay(1_200)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    TesadufBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = BottomBarSpace),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TesadufWordmark(fontSize = 14.sp, letterSpacing = 7.sp)
            AnimatedContent(
                targetState = session,
                transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(250)) },
                contentKey = { it::class },
                label = "homeState",
                modifier = Modifier.widthIn(max = 480.dp),
            ) { state ->
                when (state) {
                    is SessionState.Loading -> Column(
                        Modifier.fillMaxWidth().heightIn(min = 320.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        TesadufLoadingDots()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.profile_preparing), style = MaterialTheme.typography.bodyMedium)
                    }
                    is SessionState.Failed -> Box(Modifier.padding(top = 40.dp)) {
                        TesadufInlineMessage(state.error, onRetry = { repository.refreshSession(showLoading = true) })
                    }
                    is SessionState.Ready -> ReadyContent(state, repository, onStart, onResume)
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: SessionState.Ready,
    repository: TesadufRepository,
    onStart: () -> Unit,
    onResume: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmReplace by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var endError by remember { mutableStateOf<AppError?>(null) }
    val live: Match? = state.liveMatch
    val heroSize = (windowWidthDp() * 0.78f).coerceIn(250f, 340f).dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AvatarHalo(state.profile.avatar, heroSize)
        // Anonymous id in a quiet glass chip.
        Text(
            state.profile.displayId,
            style = IdTextStyle.copy(fontSize = 20.sp, letterSpacing = 3.sp),
            modifier = Modifier
                .clip(Shapes.pill)
                .background(TesadufColors.Glass)
                .border(1.dp, TesadufColors.StrokeSoft, Shapes.pill)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
        TesadufQuoteTicker(Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(18.dp))
        TesadufStatsCard(
            listOf(
                state.stats.tesadufCount to stringResource(R.string.stat_tesaduf),
                state.stats.destinyCount to stringResource(R.string.stat_destiny),
            ),
        )
        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(live != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            if (live != null) {
                TesadufGlassCard(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    fill = TesadufColors.Purple.copy(alpha = 0.10f),
                    stroke = TesadufColors.Purple.copy(alpha = 0.35f),
                    onClick = { onResume(live.id) },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        TesadufAvatar(live.partner.avatar, 44.dp, alive = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.home_live_title), style = MaterialTheme.typography.titleSmall)
                            Text(live.partner.displayId, style = IdTextStyle.copy(fontSize = 13.sp, color = TesadufColors.TextSecondary))
                        }
                        Text(stringResource(R.string.home_live_open), style = MaterialTheme.typography.labelMedium, color = TesadufColors.Cyan)
                    }
                }
            }
        }

        // Always available: a live tesadüf only asks for confirmation first.
        TesadufButton(
            text = stringResource(R.string.home_start),
            onClick = { if (live != null) confirmReplace = true else onStart() },
            tone = ButtonTone.Signature,
            icon = TIcons.Sparkles,
            minHeight = 68.dp,
            textStyle = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp),
            modifier = Modifier.fillMaxWidth(),
            shimmer = true,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.home_caption), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }

    if (confirmReplace && live != null) {
        TesadufDialog(
            title = stringResource(R.string.replace_title),
            body = stringResource(R.string.replace_body, live.partner.displayId),
            onDismiss = { if (!ending) confirmReplace = false },
        ) {
            endError?.let { TesadufInlineMessage(it, Modifier.padding(bottom = 12.dp)) }
            TesadufButton(
                stringResource(R.string.replace_confirm),
                onClick = {
                    ending = true
                    endError = null
                    scope.launch {
                        when (val result = repository.endMatch(live.id)) {
                            is Outcome.Success -> {
                                repository.onLiveMatchChanged(result.value)
                                confirmReplace = false
                                onStart()
                            }
                            is Outcome.Failure -> endError = result.error
                        }
                        ending = false
                    }
                },
                loading = ending,
                tone = ButtonTone.Heart,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            TesadufSecondaryButton(
                stringResource(R.string.home_live_resume_short),
                onClick = { confirmReplace = false; onResume(live.id) },
                enabled = !ending,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The user's living avatar inside slowly turning neon orbits with a few drifting
 * motes — the same visual language as the intro, kept calm for everyday use.
 */
@Composable
private fun AvatarHalo(avatar: String, size: Dp) {
    val transition = rememberInfiniteTransition(label = "halo")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "t")
    val float by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f",
    )
    Box(
        Modifier
            .size(size, size * 0.82f)
            .drawBehind {
                val c = Offset(this.size.width / 2, this.size.height * 0.5f)
                val rx = this.size.width * 0.47f
                val ry = rx * 0.34f
                softGlow(TesadufColors.Purple, c, rx * 0.9f, 0.16f)
                softGlow(TesadufColors.Cyan, c - Offset(rx * 0.3f, 0f), rx * 0.6f, 0.08f)
                val brush = Brush.sweepGradient(listOf(TesadufColors.Cyan, TesadufColors.Purple, TesadufColors.Pink, TesadufColors.Cyan), c)
                for (k in 0..1) {
                    val tilt = if (k == 0) -14f else 16f
                    rotate(tilt, c) {
                        val start = t * 360f * (if (k == 0) 1f else -1f)
                        val tl = Offset(c.x - rx, c.y - ry)
                        val sz = Size(rx * 2, ry * 2)
                        drawArc(brush, start, 300f, false, tl, sz, alpha = 0.12f, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(brush, start, 300f, false, tl, sz, alpha = 0.55f, style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                val colors = listOf(TesadufColors.Cyan, TesadufColors.Purple, TesadufColors.Pink, Color.White)
                for (i in 0 until 14) {
                    val a = (t * (1f + (i % 3) * 0.5f) + i / 14f) * 2f * PI.toFloat()
                    val f = 0.7f + (i % 5) * 0.08f
                    val p = Offset(c.x + cos(a) * rx * f, c.y + sin(a) * ry * f * 1.6f)
                    val tw = 0.35f + 0.4f * (0.5f + 0.5f * sin(a * 3f))
                    drawCircle(colors[i % 4].copy(alpha = tw), (1.2f + i % 3).dp.toPx(), p)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        TesadufAvatar(
            avatar,
            size * 0.5f,
            modifier = Modifier.graphicsLayer { translationY = float * 5.dp.toPx() },
        )
    }
}
