package com.workoutpartner.app.routines

import com.workoutpartner.data.ActivityLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineDifficultyTest {

    @Test
    fun `no stats at all falls back to STANDARD`() {
        assertEquals(DifficultyTier.STANDARD, RoutineDifficulty.compute(null))
    }

    @Test
    fun `stats missing a field falls back to STANDARD`() {
        val stats = BodyStats(age = 25, heightCm = 170, weightKg = null)
        assertEquals(DifficultyTier.STANDARD, RoutineDifficulty.compute(stats))
    }

    @Test
    fun `younger, normal BMI is CHALLENGING`() {
        val stats = BodyStats(age = 25, heightCm = 170, weightKg = 65.0)
        assertEquals(DifficultyTier.CHALLENGING, RoutineDifficulty.compute(stats))
    }

    @Test
    fun `older, obese BMI is EASY`() {
        val stats = BodyStats(age = 60, heightCm = 170, weightKg = 90.0)
        assertEquals(DifficultyTier.EASY, RoutineDifficulty.compute(stats))
    }

    @Test
    fun `middle-aged, overweight BMI is STANDARD`() {
        val stats = BodyStats(age = 40, heightCm = 170, weightKg = 80.0)
        assertEquals(DifficultyTier.STANDARD, RoutineDifficulty.compute(stats))
    }

    @Test
    fun `Sedentary subtracts one, tipping a borderline STANDARD score down to EASY`() {
        val neutral = BodyStats(age = 40, heightCm = 170, weightKg = 80.0)
        check(RoutineDifficulty.compute(neutral) == DifficultyTier.STANDARD)

        val stats = neutral.copy(activityLevel = ActivityLevel.SEDENTARY)

        assertEquals(DifficultyTier.EASY, RoutineDifficulty.compute(stats))
    }

    @Test
    fun `Very Active adds one, tipping a borderline STANDARD score up to CHALLENGING`() {
        val nearlyChallenging = BodyStats(age = 25, heightCm = 170, weightKg = 80.0)
        check(RoutineDifficulty.compute(nearlyChallenging) == DifficultyTier.STANDARD)

        val stats = nearlyChallenging.copy(activityLevel = ActivityLevel.VERY_ACTIVE)

        assertEquals(DifficultyTier.CHALLENGING, RoutineDifficulty.compute(stats))
    }

    @Test
    fun `Lightly Active scores zero, same as no Activity Level at all`() {
        val withoutActivity = BodyStats(age = 40, heightCm = 170, weightKg = 80.0)
        val lightlyActive = withoutActivity.copy(activityLevel = ActivityLevel.LIGHTLY_ACTIVE)

        assertEquals(RoutineDifficulty.compute(withoutActivity), RoutineDifficulty.compute(lightlyActive))
        assertEquals(DifficultyTier.STANDARD, RoutineDifficulty.compute(lightlyActive))
    }

    @Test
    fun `Active scores zero, same as no Activity Level at all`() {
        val withoutActivity = BodyStats(age = 40, heightCm = 170, weightKg = 80.0)
        val active = withoutActivity.copy(activityLevel = ActivityLevel.ACTIVE)

        assertEquals(RoutineDifficulty.compute(withoutActivity), RoutineDifficulty.compute(active))
        assertEquals(DifficultyTier.STANDARD, RoutineDifficulty.compute(active))
    }

    @Test
    fun `a missing Activity Level still scores zero, same as before ticket 07`() {
        // No `activityLevel` at all — the pre-ticket-07 shape of BodyStats —
        // reproduces the exact CHALLENGING/EASY/STANDARD results the other
        // tests above already assert for these same three fixtures.
        assertEquals(DifficultyTier.CHALLENGING, RoutineDifficulty.compute(BodyStats(age = 25, heightCm = 170, weightKg = 65.0)))
        assertEquals(DifficultyTier.EASY, RoutineDifficulty.compute(BodyStats(age = 60, heightCm = 170, weightKg = 90.0)))
        assertEquals(DifficultyTier.STANDARD, RoutineDifficulty.compute(BodyStats(age = 40, heightCm = 170, weightKg = 80.0)))
    }

    @Test
    fun `adjustedTargetReps scales up for CHALLENGING and down for EASY`() {
        assertEquals(12, RoutineDifficulty.adjustedTargetReps(10, DifficultyTier.CHALLENGING))
        assertEquals(8, RoutineDifficulty.adjustedTargetReps(10, DifficultyTier.EASY))
        assertEquals(10, RoutineDifficulty.adjustedTargetReps(10, DifficultyTier.STANDARD))
    }

    @Test
    fun `adjustedTargetReps never drops to zero`() {
        assertEquals(1, RoutineDifficulty.adjustedTargetReps(1, DifficultyTier.EASY))
    }

    @Test
    fun `adjustedRestIntervalSeconds is the inverse of the rep multiplier`() {
        assertEquals(25, RoutineDifficulty.adjustedRestIntervalSeconds(30, DifficultyTier.CHALLENGING))
        assertEquals(38, RoutineDifficulty.adjustedRestIntervalSeconds(30, DifficultyTier.EASY))
        assertEquals(30, RoutineDifficulty.adjustedRestIntervalSeconds(30, DifficultyTier.STANDARD))
    }

    @Test
    fun `a zero rest interval stays zero regardless of tier`() {
        assertEquals(0, RoutineDifficulty.adjustedRestIntervalSeconds(0, DifficultyTier.CHALLENGING))
        assertEquals(0, RoutineDifficulty.adjustedRestIntervalSeconds(0, DifficultyTier.EASY))
    }
}
