package com.workoutpartner.data

/**
 * An Account's (or Guest's) self-reported daily activity level (CONTEXT.md),
 * a 4-tier scale collected during onboarding and consumed by `app`'s
 * routine-difficulty tuning alongside body-mass/age. `workout-partner-v3`
 * ticket 02 replaced the original 3-tier LOW/MEDIUM/HIGH scale with these
 * four friendlier tiers; existing stored answers are remapped by
 * `MIGRATION_4_5` (LOW->SEDENTARY, MEDIUM->LIGHTLY_ACTIVE, HIGH->ACTIVE) —
 * see that migration's doc comment for why those particular pairings.
 */
enum class ActivityLevel {
    SEDENTARY,
    LIGHTLY_ACTIVE,
    ACTIVE,
    VERY_ACTIVE,
}
