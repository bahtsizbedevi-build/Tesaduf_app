package com.tesaduf.app.ui.design

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import com.tesaduf.app.R
import com.tesaduf.app.data.AppPreferences

/** Short UI sounds (original, synthesised in-house; see res/raw). */
enum class UiSound(val res: Int, val volume: Float) {
    Whoosh(R.raw.cine_whoosh, 0.5f),
    Sparkle(R.raw.cine_sparkle, 0.35f),
    Tick(R.raw.ui_tick, 0.4f),
    Chime(R.raw.cine_chime, 0.45f),
    Send(R.raw.ui_send, 0.35f),
    React(R.raw.ui_react, 0.4f),
}

/**
 * One SoundPool for the whole app (sounds are tiny and pre-decoded, so playback is
 * instant). Respects the "Sesler" preference; plays in the media stream so the
 * phone's silent mode / volume rocker apply.
 */
class SoundFx(context: Context, private val prefs: AppPreferences) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = UiSound.entries.associateWith { pool.load(context, it.res, 1) }

    fun play(sound: UiSound) {
        if (!prefs.sounds.value) return
        val id = ids[sound] ?: return
        pool.play(id, sound.volume, sound.volume, 0, 0, 1f)
    }
}

/** Null in previews/tests; screens just skip sounds then. */
val LocalSoundFx = staticCompositionLocalOf<SoundFx?> { null }
