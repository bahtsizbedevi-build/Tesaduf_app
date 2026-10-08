package com.tesaduf.app.ui.cinematic

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/**
 * Optional sound for story beats. Not wired to any audio yet (sound is off by default);
 * plug a SoundPool-backed implementation in here when assets exist:
 * whoosh (Whoosh), sparkle (Sparkle), soft click (Connect), magical chime (Chime).
 */
fun interface CinematicSound {
    fun play(cue: CinematicCue)

    companion object {
        val Silent = CinematicSound { }
    }
}

/** Light haptics at meaningful beats; respects the user's "titreşim" preference. */
private fun View.haptic(cue: CinematicCue) {
    val constant = when (cue) {
        CinematicCue.Connect -> HapticFeedbackConstants.CLOCK_TICK
        CinematicCue.Whoosh -> HapticFeedbackConstants.KEYBOARD_TAP
        CinematicCue.Chime -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        CinematicCue.Sparkle -> return
    }
    performHapticFeedback(constant)
}

/** Returns the cue handler to give a [CinematicDirector]. */
@Composable
fun rememberCinematicFx(hapticsEnabled: Boolean, sound: CinematicSound = CinematicSound.Silent): (CinematicCue) -> Unit {
    val view = LocalView.current
    val haptics = rememberUpdatedState(hapticsEnabled)
    return remember(view, sound) {
        { cue ->
            sound.play(cue)
            if (haptics.value) view.haptic(cue)
        }
    }
}
