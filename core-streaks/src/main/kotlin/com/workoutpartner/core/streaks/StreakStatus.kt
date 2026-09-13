package com.workoutpartner.core.streaks

/**
 * The result of one [StreakCalculator.calculate] call — this ticket's
 * required output shape: current Streak count, banked Streak Shield count,
 * and whether the gap-safeguard is the reason the Streak currently reads
 * zero (as opposed to a plain Weekly-Target miss with no Shield banked).
 * [gapSafeguardFired] reflects the most recent evaluation only: once a
 * later week rebuilds the Streak, it goes back to `false` — see
 * [StreakCalculator]'s doc comment.
 */
data class StreakStatus(
    val currentStreak: Int,
    val bankedShields: Int,
    val gapSafeguardFired: Boolean,
)
