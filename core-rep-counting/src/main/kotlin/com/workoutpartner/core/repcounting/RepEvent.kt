package com.workoutpartner.core.repcounting

/**
 * One completed Rep. [passedFormThreshold] is whether it reached the
 * Exercise's range-of-motion threshold — Quick Count (ticket 11) reuses this
 * engine but ignores this field, since Quick Count has no Form Score.
 */
data class RepEvent(val exercise: Exercise, val passedFormThreshold: Boolean)
