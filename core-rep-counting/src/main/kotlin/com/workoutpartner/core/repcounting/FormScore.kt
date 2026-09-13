package com.workoutpartner.core.repcounting

import kotlin.math.roundToInt

/**
 * A completed Set's Form Score (CONTEXT.md): the percentage, 0-100, of its
 * Reps that passed the Exercise's range-of-motion threshold. A Set with no
 * Reps scores 0.
 */
object FormScore {
    fun compute(reps: List<RepEvent>): Int {
        if (reps.isEmpty()) return 0
        val passed = reps.count { it.passedFormThreshold }
        return ((passed * 100f) / reps.size).roundToInt()
    }
}
