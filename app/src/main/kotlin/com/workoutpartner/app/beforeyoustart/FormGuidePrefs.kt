package com.workoutpartner.app.beforeyoustart

import android.content.Context
import com.workoutpartner.app.progress.TrackedExercise

/**
 * Per-[TrackedExercise] "has this Athlete already seen this Form Guide"
 * state (`workout-partner-v3` ticket 10) — plain `SharedPreferences`, the
 * same device-local UI-flow-flag pattern as
 * [com.workoutpartner.app.onboarding.OnboardingPrefs]: never keyed by
 * accountId, so it survives a Guest's sign-up or merge (ticket 05/09)
 * unchanged, since it was never tied to ownership in the first place.
 */
class FormGuidePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasSeen(tracked: TrackedExercise): Boolean = prefs.getBoolean(keyFor(tracked), false)

    fun markSeen(tracked: TrackedExercise) {
        prefs.edit().putBoolean(keyFor(tracked), true).apply()
    }

    private fun keyFor(tracked: TrackedExercise) = "${tracked.exercise.name}:${tracked.variant?.name ?: "-"}"

    private companion object {
        const val PREFS_NAME = "form_guides"
    }
}
