package com.workoutpartner.app.onboarding

import android.content.Context

/**
 * One-time onboarding gates, persisted per install: [hasSeenDisclaimer]
 * (ticket 13, spec.md story 22: "a safety disclaimer on first launch...
 * must be shown before any tracking screen is reachable"),
 * [hasChosenHowToStart] (the "Continue as Guest / Sign up / Sign in"
 * choice, story 1), and [hasCompletedProfile] (`workout-partner-v2` ticket
 * 01: name/age/height/weight/activity-level, shown once right after Guest/
 * Sign up) — each shown once, not on every launch. Plain `SharedPreferences`
 * rather than Room: this is UI-flow state, not app data (the profile
 * answers themselves live in Room, on `AccountEntity`/`GuestProfileEntity`
 * — this flag only tracks whether the screen has been shown), and doesn't
 * belong in the Room schema (ticket 05) or sync anywhere (ticket 06).
 */
class OnboardingPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hasSeenDisclaimer: Boolean
        get() = prefs.getBoolean(KEY_SEEN_DISCLAIMER, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEN_DISCLAIMER, value).apply()

    var hasChosenHowToStart: Boolean
        get() = prefs.getBoolean(KEY_CHOSEN_HOW_TO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_CHOSEN_HOW_TO_START, value).apply()

    var hasCompletedProfile: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED_PROFILE, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPLETED_PROFILE, value).apply()

    private companion object {
        const val PREFS_NAME = "onboarding"
        const val KEY_SEEN_DISCLAIMER = "seen_disclaimer"
        const val KEY_CHOSEN_HOW_TO_START = "chosen_how_to_start"
        const val KEY_COMPLETED_PROFILE = "completed_profile"
    }
}
