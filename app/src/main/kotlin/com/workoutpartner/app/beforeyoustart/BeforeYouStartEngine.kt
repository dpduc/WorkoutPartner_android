package com.workoutpartner.app.beforeyoustart

/**
 * The Before You Start flow's phases (CONTEXT.md): Workout Overview -> Form
 * Guides -> Position Check -> Countdown -> Ready, at which point the caller
 * starts the Session (or Quick Count run). `workout-partner-v3` ticket 03
 * only builds the Overview phase's real UI; the other four are modeled here
 * as plain markers with no payload yet — tickets 10/12/13 give Form Guides,
 * Position Check, and Countdown their real associated state (which guides
 * are unseen, live pose-check status, seconds remaining) without needing to
 * touch this sequence itself.
 */
sealed interface BeforeYouStartPhase {
    data object Overview : BeforeYouStartPhase
    data object FormGuides : BeforeYouStartPhase
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
 * Not yet wired into any screen: [WorkoutOverviewScreen] proceeds straight
 * from "Start" to the Session without touching this class, since Form
 * Guides/Position Check/Countdown have nothing to show yet and a screen
 * that reads [phase] only to immediately discard it would just be
 * ceremony. This ticket's job is proving out the phase sequence itself
 * (see [BeforeYouStartEngineTest]) — tickets 10/12/13 are what give
 * [advance]'s unconditional per-phase transitions real gating conditions
 * and a screen to actually drive them.
 */
class BeforeYouStartEngine {
    var phase: BeforeYouStartPhase = BeforeYouStartPhase.Overview
        private set

    /** Moves from the current phase to the next one in the fixed sequence. No-op once [BeforeYouStartPhase.Ready]. */
    fun advance() {
        phase = when (phase) {
            BeforeYouStartPhase.Overview -> BeforeYouStartPhase.FormGuides
            BeforeYouStartPhase.FormGuides -> BeforeYouStartPhase.PositionCheck
            BeforeYouStartPhase.PositionCheck -> BeforeYouStartPhase.Countdown
            BeforeYouStartPhase.Countdown -> BeforeYouStartPhase.Ready
            BeforeYouStartPhase.Ready -> BeforeYouStartPhase.Ready
        }
    }

    /**
     * Advances through every remaining phase in one call. A placeholder for
     * tickets 10/12/13 to replace with the Athlete actually completing each
     * phase (viewing Form Guides, passing the Position Check, watching the
     * countdown) instead of skipping straight through.
     */
    fun skipToReady() {
        while (phase != BeforeYouStartPhase.Ready) advance()
    }
}
