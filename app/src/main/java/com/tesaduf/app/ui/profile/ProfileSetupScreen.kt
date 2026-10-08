package com.tesaduf.app.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesaduf.app.R
import com.tesaduf.app.network.AppError
import com.tesaduf.app.network.Outcome
import com.tesaduf.app.repository.SessionState
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.design.Accessory
import com.tesaduf.app.ui.design.AccessoryPicker
import com.tesaduf.app.ui.design.AvatarStyle
import com.tesaduf.app.ui.design.TesadufAvatar
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufButton
import com.tesaduf.app.ui.design.TesadufInlineMessage
import com.tesaduf.app.ui.design.TesadufLoadingDots
import com.tesaduf.app.ui.design.TesadufReadOnlyField
import com.tesaduf.app.ui.design.TesadufTopBar
import com.tesaduf.app.ui.theme.TesadufColors
import kotlinx.coroutines.launch

/**
 * First-run profile creation (and later "Avatarı Değiştir"). The anonymous id is assigned
 * by the server and read-only; only the avatar colour/shape can be chosen.
 */
@Composable
fun ProfileSetupScreen(
    repository: TesadufRepository,
    editMode: Boolean,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val session by repository.session.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var accessory by rememberSaveable { mutableStateOf(0) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<AppError?>(null) }

    LaunchedEffect(Unit) { repository.ensureSession() }
    val ready = session as? SessionState.Ready
    LaunchedEffect(ready?.profile?.avatar) {
        // Initialise the pickers once from the server profile.
        if (ready != null && accessory == 0) {
            accessory = AvatarStyle.parse(ready.profile.avatar).accessory.index
        }
    }

    TesadufBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TesadufTopBar(onBack = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (editMode) R.string.profile_edit_title else R.string.profile_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.profile_sub), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))

                AnimatedContent(
                    targetState = session,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "profileState",
                    modifier = Modifier.widthIn(max = 480.dp),
                ) { state ->
                    when (state) {
                        is SessionState.Loading -> Column(
                            Modifier.fillMaxWidth().heightIn(min = 240.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(60.dp))
                            TesadufLoadingDots()
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.profile_preparing), style = MaterialTheme.typography.bodyMedium)
                        }
                        is SessionState.Failed -> TesadufInlineMessage(state.error, onRetry = { repository.refreshSession(showLoading = true) })
                        is SessionState.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val style = AvatarStyle(AvatarStyle.parse(state.profile.avatar).color, Accessory.of(accessory))
                            TesadufAvatar(style.key, 148.dp)
                            Text(stringResource(R.string.profile_color_note), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(28.dp))
                            TesadufReadOnlyField(stringResource(R.string.profile_id_label), state.profile.displayId)
                            Spacer(Modifier.height(24.dp))
                            SectionLabel(stringResource(R.string.profile_accessory))
                            AccessoryPicker(
                                style.color, style.accessory, { accessory = it.index }, Modifier.fillMaxWidth(),
                                unlocked = state.stats.unlockedAccessories,
                                current = AvatarStyle.parse(state.profile.avatar).accessory,
                            )
                            error?.let {
                                Spacer(Modifier.height(16.dp))
                                TesadufInlineMessage(it)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                TesadufButton(
                    text = stringResource(if (editMode) R.string.profile_save else R.string.profile_continue),
                    enabled = ready != null,
                    loading = saving,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                    onClick = {
                        val current = ready ?: return@TesadufButton
                        val chosen = AvatarStyle(AvatarStyle.parse(current.profile.avatar).color, Accessory.of(accessory)).key
                        if (chosen == current.profile.avatar) {
                            onDone()
                            return@TesadufButton
                        }
                        saving = true
                        error = null
                        scope.launch {
                            when (val result = repository.updateAvatar(chosen)) {
                                is Outcome.Success -> onDone()
                                is Outcome.Failure -> error = result.error
                            }
                            saving = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TesadufColors.TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}
