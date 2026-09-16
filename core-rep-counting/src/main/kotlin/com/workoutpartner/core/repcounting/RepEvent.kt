package com.workoutpartner.core.repcounting

/**
 * One completed Rep. [passedFormThreshold] is whether it reached the
 * Exercise's range-of-motion threshold — averaged across a run's Reps into
 * a Form Score, for both a Session's Sets and, since `workout-partner-v2`
 * ticket 03, Quick Count's Tallies too. Quick Count still never *gates* on
 * this field (every Rep counts toward the rep total regardless of form) —
 * only the reported average changed.
 */
data class RepEvent(val exercise: Exercise, val passedFormThreshold: Boolean)
