package com.workoutpartner.app.speech

import android.content.Context

/**
 * Whether spoken prompts are on (`workout-partner-v3` ticket 13's Settings
 * toggle, default on) — plain `SharedPreferences`, the same device-local
 * UI-preference pattern as [com.workoutpartner.app.onboarding.OnboardingPrefs]:
 * about this device's speaker, not Account data, so it's the same for a Guest
 * and an Account holder and never synced.
 */
class SpeechPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var spokenPromptsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    private companion object {
        const val PREFS_NAME = "speech"
        const val KEY_ENABLED = "spoken_prompts_enabled"
    }
}
