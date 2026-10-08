package com.tesaduf.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small local, non-sensitive preferences. */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tesaduf_prefs", Context.MODE_PRIVATE)

    private val _haptics = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    private val _reminders = MutableStateFlow(prefs.getBoolean(KEY_REMINDERS, true))
    val reminders: StateFlow<Boolean> = _reminders.asStateFlow()

    private val _sounds = MutableStateFlow(prefs.getBoolean(KEY_SOUNDS, true))
    val sounds: StateFlow<Boolean> = _sounds.asStateFlow()

    private val _shake = MutableStateFlow(prefs.getBoolean(KEY_SHAKE, true))
    val shake: StateFlow<Boolean> = _shake.asStateFlow()

    fun setSounds(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SOUNDS, enabled) }
        _sounds.value = enabled
    }

    fun setShake(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHAKE, enabled) }
        _shake.value = enabled
    }

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING, value) }

    /** Whether we already showed the system notification permission prompt once. */
    var notificationPromptShown: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PROMPT, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIF_PROMPT, value) }

    val lastOpenedAt: Long get() = prefs.getLong(KEY_LAST_OPENED, 0L)
    val lastReminderAt: Long get() = prefs.getLong(KEY_LAST_REMINDER, 0L)

    fun markOpened(now: Long = System.currentTimeMillis()) = prefs.edit { putLong(KEY_LAST_OPENED, now) }
    fun markReminded(now: Long = System.currentTimeMillis()) = prefs.edit { putLong(KEY_LAST_REMINDER, now) }

    fun setHaptics(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HAPTICS, enabled) }
        _haptics.value = enabled
    }

    fun setReminders(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REMINDERS, enabled) }
        _reminders.value = enabled
    }

    private companion object {
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_HAPTICS = "haptics"
        const val KEY_REMINDERS = "reminders"
        const val KEY_SOUNDS = "sounds"
        const val KEY_SHAKE = "shake"
        const val KEY_NOTIF_PROMPT = "notif_prompt_shown"
        const val KEY_LAST_OPENED = "last_opened_at"
        const val KEY_LAST_REMINDER = "last_reminder_at"
    }
}
