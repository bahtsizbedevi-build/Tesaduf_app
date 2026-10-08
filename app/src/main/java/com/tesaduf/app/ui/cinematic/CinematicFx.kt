package com.tesaduf.app.ui.cinematic

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import com.tesaduf.app.ui.design.LocalSoundFx
import com.tesaduf.app.ui.design.UiSound

/**
 * Sound for story beats: whoosh (Whoosh), sparkle (Sparkle), soft click (Connect),
 * chime (Chime). Defaults to the app SoundFx; the "Sesler" setting turns it off.
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
fun rememberCinematicFx(hapticsEnabled: Boolean, sound: CinematicSound? = null): (CinematicCue) -> Unit {
    val view = LocalView.current
    val fx = LocalSoundFx.current
    val sound = sound ?: remember(fx) {
        CinematicSound { cue ->
            fx?.play(
                when (cue) {
                    CinematicCue.Sparkle -> UiSound.Sparkle
                    CinematicCue.Whoosh -> UiSound.Whoosh
                    CinematicCue.Connect -> UiSound.Tick
                    CinematicCue.Chime -> UiSound.Chime
                },
            )
        }
    }
    val haptics = rememberUpdatedState(hapticsEnabled)
    return remember(view, sound) {
        { cue ->
            sound.play(cue)
            if (haptics.value) view.haptic(cue)
        }
    }
}
