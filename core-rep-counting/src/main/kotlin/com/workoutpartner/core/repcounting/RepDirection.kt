package com.workoutpartner.core.repcounting

/**
 * Which way an Exercise's tracked angle moves as the user goes from resting
 * into the engaged (bottom/peak) phase of a Rep. Squat/Push-up/Sit-up/Lunge
 * all bend a joint, shrinking the angle (DECREASING). A Jumping Jack instead
 * spreads the arms outward, growing the angle (INCREASING).
 *
 * Every direction-dependent comparison [RepCounter] (and its tests) need
 * lives here once, as polymorphism, rather than as a `when` repeated at each
 * call site.
 */
enum class RepDirection {
    DECREASING {
        override fun hasPassedTowardEngaged(value: Float, threshold: Float) = value <= threshold
        override fun hasPassedTowardResting(value: Float, threshold: Float) = value >= threshold
        override fun furtherFromRest(a: Float, b: Float) = minOf(a, b)
        override fun towardEngaged(value: Float, delta: Float) = value - delta
        override fun towardResting(value: Float, delta: Float) = value + delta
    },
    INCREASING {
        override fun hasPassedTowardEngaged(value: Float, threshold: Float) = value >= threshold
        override fun hasPassedTowardResting(value: Float, threshold: Float) = value <= threshold
        override fun furtherFromRest(a: Float, b: Float) = maxOf(a, b)
        override fun towardEngaged(value: Float, delta: Float) = value + delta
        override fun towardResting(value: Float, delta: Float) = value - delta
    },
    ;

    /** Has [value] moved far enough from rest, past [threshold], to count as engaged? */
    abstract fun hasPassedTowardEngaged(value: Float, threshold: Float): Boolean

    /** Has [value] moved back past [threshold], far enough to count as resting again? */
    abstract fun hasPassedTowardResting(value: Float, threshold: Float): Boolean

    /** Whichever of [a]/[b] is further into the engaged phase. */
    abstract fun furtherFromRest(a: Float, b: Float): Float

    /** [value] shifted by [delta] further toward the engaged phase. */
    abstract fun towardEngaged(value: Float, delta: Float): Float

    /** [value] shifted by [delta] further toward resting. */
    abstract fun towardResting(value: Float, delta: Float): Float
}
