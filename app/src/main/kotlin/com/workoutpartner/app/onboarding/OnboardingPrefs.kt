package com.workoutpartner.app.onboarding

import android.content.Context

/**
 * Two one-time onboarding gates (ticket 13), persisted per install:
 * [hasSeenDisclaimer] (spec.md story 22: "a safety disclaimer on first
 * launch... must be shown before any tracking screen is reachable") and
 * [hasChosenHowToStart] (the "Continue as Guest / Sign up / Sign in" choice,
 * story 1) — each shown once, not on every launch. Plain `SharedPreferences`
 * rather than Room: this is UI-flow state, not app data, and doesn't belong
 * in the Room schema (ticket 05) or sync anywhere (ticket 06).
 */
class OnboardingPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hasSeenDisclaimer: Boolean
        get() = prefs.getBoolean(KEY_SEEN_DISCLAIMER, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_DISCLAIMER, value).apply()

    var hasChosenHowToStart: Boolean
        get() = prefs.getBoolean(KEY_CHOSEN_HOW_TO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_CHOSEN_HOW_TO_START, value).apply()

    private companion object {
        const val PREFS_NAME = "onboarding"
        const val KEY_SEEN_DISCLAIMER = "seen_disclaimer"
        const val KEY_CHOSEN_HOW_TO_START = "chosen_how_to_start"
    }
}
