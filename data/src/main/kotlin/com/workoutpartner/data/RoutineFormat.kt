package com.workoutpartner.data

/**
 * A Routine's display format tag (`workout-partner-v2` ticket 02) —
 * label/description only in this round, not an execution model: every
 * format still runs through the same rep-target + rest-interval
 * `SessionEngine` state machine a [RoutineStepEntity] sequence always has.
 * A real timer/round-based HIIT/Tabata/AMRAP engine is deferred (see
 * `.scratch/workout-partner-v2/issues/04-interval-timer-engine.md`).
 */
enum class RoutineFormat {
    STANDARD,
    HIIT,
    TABATA,
    AMRAP,
}
