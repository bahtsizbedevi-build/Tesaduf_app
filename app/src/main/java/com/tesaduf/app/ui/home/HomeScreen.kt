package com.tesaduf.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tesaduf.app.ui.components.windowHeightDp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesaduf.app.R
import com.tesaduf.app.model.Match
import com.tesaduf.app.model.Profile
import com.tesaduf.app.repository.SessionState
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.components.AnonAvatar
import com.tesaduf.app.ui.components.ErrorBanner
import com.tesaduf.app.ui.components.GlassSurface
import com.tesaduf.app.ui.components.GlowingLogo
import com.tesaduf.app.ui.components.NeonButton
import com.tesaduf.app.ui.components.NeonSpinner
import com.tesaduf.app.ui.components.StatusDot
import com.tesaduf.app.ui.components.TesadufBackground
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.TesadufColors

@Composable
fun HomeScreen(
    repository: TesadufRepository,
    onStart: () -> Unit,
    onResume: (matchId: String) -> Unit,
    onHistory: () -> Unit,
) {
    val session by repository.session.collectAsStateWithLifecycle()

    // Coming back to Home (e.g. after a chat) refreshes the "live match" card.
    LifecycleResumeEffect(Unit) {
        if (repository.session.value is SessionState.Ready) repository.refreshSession() else repository.ensureSession()
        onPauseOrDispose { }
    }

    val logoSize = (windowHeightDp() * 0.2f).coerceIn(110f, 190f).dp

    TesadufBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.brand_wordmark),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 4.sp),
                    color = TesadufColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onHistory) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.home_history),
                        tint = TesadufColors.TextPrimary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            GlowingLogo(size = logoSize)
            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = session,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "homeState",
                modifier = Modifier.widthIn(max = 480.dp),
            ) { state ->
                when (state) {
                    is SessionState.Loading -> LoadingCard()
                    is SessionState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ErrorBanner(state.error, onRetry = { repository.refreshSession(showLoading = true) })
                    }
                    is SessionState.Ready -> ReadyContent(state.profile, state.liveMatch, onStart, onResume, onHistory)
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    GlassSurface(Modifier.fillMaxWidth().heightIn(min = 200.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NeonSpinner(40.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.home_loading), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReadyContent(
    profile: Profile,
    liveMatch: Match?,
    onStart: () -> Unit,
    onResume: (String) -> Unit,
    onHistory: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnonAvatar(profile.avatar, size = 84.dp)
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.home_identity_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = TesadufColors.TextMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(profile.displayId, style = IdTextStyle.copy(fontSize = 28.sp))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(TesadufColors.Success)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.home_ready), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        if (liveMatch != null) {
            GlassSurface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                fill = TesadufColors.Purple.copy(alpha = 0.10f),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnonAvatar(liveMatch.partner.avatar, 36.dp, glow = false)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_live_title), style = MaterialTheme.typography.titleMedium)
                        Text(liveMatch.partner.displayId, style = IdTextStyle.copy(fontSize = 13.sp, color = TesadufColors.TextSecondary))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            NeonButton(
                text = stringResource(R.string.home_live_resume),
                onClick = { onResume(liveMatch.id) },
                modifier = Modifier.fillMaxWidth(),
                animateGlow = true,
            )
        } else {
            NeonButton(
                text = stringResource(R.string.home_start),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                animateGlow = true,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.home_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onHistory) {
            Text(
                stringResource(R.string.home_history),
                style = MaterialTheme.typography.labelMedium,
                color = TesadufColors.Cyan,
            )
        }
    }
}
