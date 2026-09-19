package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.app.progress.TrackedExercise

/**
 * The Before You Start flow's phases (CONTEXT.md): Workout Overview -> Form
 * Guides -> Position Check -> Countdown -> Ready, at which point the caller
 * starts the Session (or Quick Count run). `workout-partner-v3` ticket 10
 * gives [FormGuides] its real payload — the unseen [TrackedExercise]s to
 * show, per [FormGuidePrefs] — since the phase is skipped entirely rather
 * than shown empty; Position Check and Countdown are still modeled as plain
 * markers with no payload yet, for tickets 12/13 to fill in the same way.
 */
sealed interface BeforeYouStartPhase {
    data object Overview : BeforeYouStartPhase
    data class FormGuides(val guides: List<TrackedExercise>) : BeforeYouStartPhase
    data object PositionCheck : BeforeYouStartPhase
    data object Countdown : BeforeYouStartPhase
    data object Ready : BeforeYouStartPhase
}

/**
 * Drives the Before You Start flow (CONTEXT.md), the same pure-Kotlin
 * "engine behind the UI" pattern as [com.workoutpartner.app.session.SessionEngine] —
 * no Android, camera, or clock dependency, so it's testable with manual
 * ticks/calls.
 *
 * [unseenGuides] — the Routine's [TrackedExercise]s the Athlete hasn't seen
 * a Form Guide for yet (`workout-partner-v3` ticket 10) — is what
 * [advance] uses to decide whether [BeforeYouStartPhase.FormGuides] is
 * shown at all: an Athlete who's already seen every guide in this Routine
 * skips it entirely and lands straight on [BeforeYouStartPhase.PositionCheck],
 * per this ticket's own checklist. Position Check and Countdown still
 * advance unconditionally (tickets 12/13 are what give them real gating
 * conditions) — driven by [BeforeYouStartScreen], which owns the seen-state
 * lookup this engine only receives as data.
 */
class BeforeYouStartEngine(private val unseenGuides: List<TrackedExercise>) {
    var phase: BeforeYouStartPhase = BeforeYouStartPhase.Overview
        private set

    /** Moves from the current phase to the next one in the fixed sequence. No-op once [BeforeYouStartPhase.Ready]. */
    fun advance() {
        phase = when (phase) {
            BeforeYouStartPhase.Overview ->
                if (unseenGuides.isEmpty()) BeforeYouStartPhase.PositionCheck else BeforeYouStartPhase.FormGuides(unseenGuides)
            is BeforeYouStartPhase.FormGuides -> BeforeYouStartPhase.PositionCheck
            BeforeYouStartPhase.PositionCheck -> BeforeYouStartPhase.Countdown
            BeforeYouStartPhase.Countdown -> BeforeYouStartPhase.Ready
            BeforeYouStartPhase.Ready -> BeforeYouStartPhase.Ready
        }
    }

    /**
     * Advances through every remaining phase in one call. A placeholder for
     * tickets 12/13 to replace with the Athlete actually completing each
     * remaining phase (passing the Position Check, watching the countdown)
     * instead of skipping straight through.
     */
    fun skipToReady() {
        while (phase != BeforeYouStartPhase.Ready) advance()
    }
}
