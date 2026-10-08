package com.tesaduf.app.ui.settings

import com.tesaduf.app.ui.design.TIcons
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tesaduf.app.BuildConfig
import com.tesaduf.app.R
import com.tesaduf.app.notifications.Reminders
import com.tesaduf.app.repository.SessionState
import com.tesaduf.app.repository.TesadufRepository
import com.tesaduf.app.ui.design.Badge
import com.tesaduf.app.ui.design.BadgeShowcase
import com.tesaduf.app.ui.design.ButtonTone
import com.tesaduf.app.ui.design.TesadufAvatar
import com.tesaduf.app.ui.design.TesadufBackground
import com.tesaduf.app.ui.design.TesadufButton
import com.tesaduf.app.ui.design.TesadufDialog
import com.tesaduf.app.ui.design.TesadufGlassCard
import com.tesaduf.app.ui.design.TesadufListRow
import com.tesaduf.app.ui.design.TesadufLogo
import com.tesaduf.app.ui.design.TesadufSecondaryButton
import com.tesaduf.app.ui.design.TesadufStatsCard
import com.tesaduf.app.ui.design.TesadufTopBar
import com.tesaduf.app.ui.home.BottomBarSpace
import com.tesaduf.app.ui.theme.IdTextStyle
import com.tesaduf.app.ui.theme.Shapes
import com.tesaduf.app.ui.theme.TesadufColors
import kotlinx.coroutines.launch

private enum class SettingsDialog { NOTIFICATIONS, SOUND, ABOUT, SIGN_OUT }

@Composable
fun SettingsScreen(
    repository: TesadufRepository,
    onChangeAvatar: () -> Unit,
    onBlocked: () -> Unit,
    onModeration: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val session by repository.session.collectAsStateWithLifecycle()
    val haptics by repository.preferences.haptics.collectAsStateWithLifecycle()
    val sounds by repository.preferences.sounds.collectAsStateWithLifecycle()
    val shake by repository.preferences.shake.collectAsStateWithLifecycle()
    val reminders by repository.preferences.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
    }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ready = session as? SessionState.Ready

    TesadufBackground {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = BottomBarSpace),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TesadufTopBar(title = stringResource(R.string.settings_title))
            Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (ready != null) {
                    TesadufAvatar(ready.profile.avatar, 104.dp, interactive = true)
                    Spacer(Modifier.height(12.dp))
                    Text(ready.profile.displayId, style = IdTextStyle.copy(fontSize = 22.sp))
                    Text(stringResource(R.string.anonymous_user), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(18.dp))
                    TesadufStatsCard(
                        listOf(
                            ready.stats.tesadufCount to stringResource(R.string.stat_tesaduf),
                            ready.stats.destinyCount to stringResource(R.string.stat_destiny),
                            ready.stats.bestStreak to stringResource(R.string.stat_best_streak),
                        ),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        stringResource(R.string.badges_title, ready.stats.badges.size, Badge.entries.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    BadgeShowcase(ready.stats.badges, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(18.dp))
                }
                TesadufGlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {
                        TesadufListRow(TIcons.Palette, stringResource(R.string.settings_change_avatar), onChangeAvatar)
                        TesadufListRow(TIcons.Block, stringResource(R.string.settings_blocked), onBlocked)
                        TesadufListRow(TIcons.Bell, stringResource(R.string.settings_notifications), { dialog = SettingsDialog.NOTIFICATIONS })
                        TesadufListRow(TIcons.Volume, stringResource(R.string.settings_sound), { dialog = SettingsDialog.SOUND })
                        if (ready?.isAdmin == true) {
                            TesadufListRow(TIcons.ShieldAlert, stringResource(R.string.mod_title), onModeration, iconTint = TesadufColors.Warning)
                        }
                        TesadufListRow(TIcons.Info, stringResource(R.string.settings_about), { dialog = SettingsDialog.ABOUT })
                    }
                }
                Spacer(Modifier.height(12.dp))
                TesadufGlassCard(
                    Modifier.fillMaxWidth(),
                    fill = TesadufColors.Danger.copy(alpha = 0.06f),
                    stroke = TesadufColors.Danger.copy(alpha = 0.25f),
                ) {
                    TesadufListRow(
                        TIcons.LogOut, stringResource(R.string.settings_sign_out), { dialog = SettingsDialog.SIGN_OUT },
                        tint = TesadufColors.Danger, iconTint = TesadufColors.Danger, chevron = false,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.NOTIFICATIONS -> TesadufDialog(
            title = stringResource(R.string.notifications_title),
            body = stringResource(R.string.notifications_body),
            onDismiss = { dialog = null },
        ) {
            ToggleRow(
                label = stringResource(R.string.notifications_reminders),
                description = stringResource(R.string.notifications_reminders_body),
                checked = reminders && Reminders.canPost(context),
                onToggle = { enable ->
                    repository.preferences.setReminders(enable)
                    if (enable && !Reminders.canPost(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            if (permissionDenied) {
                Text(
                    stringResource(R.string.notifications_permission_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = TesadufColors.Warning,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            TesadufSecondaryButton(stringResource(R.string.ok), { dialog = null }, Modifier.fillMaxWidth())
        }
        SettingsDialog.SOUND -> TesadufDialog(
            title = stringResource(R.string.settings_sound),
            body = stringResource(R.string.sound_body),
            onDismiss = { dialog = null },
        ) {
            ToggleRow(stringResource(R.string.sound_sounds), stringResource(R.string.sound_sounds_body), sounds, repository.preferences::setSounds)
            ToggleRow(
                stringResource(R.string.notifications_haptics), stringResource(R.string.notifications_haptics_body),
                haptics, repository.preferences::setHaptics,
            )
            ToggleRow(stringResource(R.string.sound_shake), stringResource(R.string.sound_shake_body), shake, repository.preferences::setShake)
            Spacer(Modifier.height(16.dp))
            TesadufSecondaryButton(stringResource(R.string.ok), { dialog = null }, Modifier.fillMaxWidth())
        }
        SettingsDialog.ABOUT -> TesadufDialog(
            title = stringResource(R.string.about_title),
            body = stringResource(R.string.about_body, BuildConfig.VERSION_NAME),
            onDismiss = { dialog = null },
            illustration = { TesadufLogo(size = 96.dp, glow = 0.6f) },
        ) {
            TesadufSecondaryButton(stringResource(R.string.ok), { dialog = null }, Modifier.fillMaxWidth())
        }
        SettingsDialog.SIGN_OUT -> TesadufDialog(
            title = stringResource(R.string.sign_out_title),
            body = stringResource(R.string.sign_out_body),
            onDismiss = { if (!signingOut) dialog = null },
        ) {
            TesadufButton(
                stringResource(R.string.settings_sign_out),
                onClick = {
                    signingOut = true
                    scope.launch {
                        repository.signOut()
                        signingOut = false
                        dialog = null
                        onSignedOut()
                    }
                },
                loading = signingOut,
                tone = ButtonTone.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            TesadufSecondaryButton(stringResource(R.string.cancel), { dialog = null }, Modifier.fillMaxWidth(), enabled = !signingOut)
        }
        null -> Unit
    }
}

/** Custom switch row (no Material Switch). */
@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val state = stringResource(if (checked) R.string.toggle_on else R.string.toggle_off)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Shapes.field)
            .clickable(role = Role.Switch) { onToggle(!checked) }
            .semantics { stateDescription = state }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(Shapes.pill)
                .background(if (checked) TesadufColors.CoolAccent else androidx.compose.ui.graphics.SolidColor(TesadufColors.GlassStrong))
                .border(1.dp, TesadufColors.Stroke, Shapes.pill)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(22.dp).clip(Shapes.pill).background(androidx.compose.ui.graphics.Color.White))
        }
    }
}
