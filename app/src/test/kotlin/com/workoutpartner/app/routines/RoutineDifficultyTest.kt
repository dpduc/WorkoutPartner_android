package com.workoutpartner.app.routines

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
