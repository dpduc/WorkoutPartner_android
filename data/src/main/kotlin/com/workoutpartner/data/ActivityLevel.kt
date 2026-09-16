package com.workoutpartner.data

/**
 * An Account's self-reported daily activity level (CONTEXT.md), a 3-tier
 * scale collected during onboarding (`workout-partner-v2` ticket 01) and
 * consumed by `app`'s routine-difficulty tuning alongside body-mass/age
 * (ticket 02).
 */
enum class ActivityLevel {
    LOW,
    MEDIUM,
    HIGH,
}
