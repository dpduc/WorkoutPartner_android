package com.workoutpartner.core.streaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scenarios per ticket 04's Testing section: target met, target missed with
 * a Shield available, target missed with no Shield, and the gap-safeguard
 * firing mid-week — plus a few more covering this ticket's other documented
 * rules (early credit for the in-progress week, a pending week not being
 * treated as a miss, the Shield cap, and gapSafeguardFired reflecting only
 * the most recent evaluation).
 *
 * Every fixture is built to avoid *accidentally* tripping the gap-safeguard
 * (never more than two consecutive zero-Active-Day days) unless a test is
 * specifically about it — see [day]'s Mon/Thu/Sun and Wed/Fri spacing
 * patterns below.
 */
class StreakCalculatorTest {

    @Test
    fun `no history at all is a fresh Account, not a broken one`() {
        val result = StreakCalculator.calculate(activeDays = emptySet(), today = day(1, 1))

        assertEquals(StreakStatus(currentStreak = 0, bankedShields = 0, gapSafeguardFired = false), result)
    }

    @Test
    fun `meeting the Weekly Target extends the Streak and banks a Shield, week after week`() {
        val activeDays = setOf(
            day(1, 1), day(1, 3), day(1, 5), // week 1: Mon/Wed/Fri
            day(2, 1), day(2, 3), day(2, 5), // week 2: Mon/Wed/Fri
        )

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(2, 7))

        assertEquals(StreakStatus(currentStreak = 2, bankedShields = 2, gapSafeguardFired = false), result)
    }

    @Test
    fun `an Account's very first week, starting mid-week, is judged on its own days only — not padded with phantom gap days before it existed`() {
        // The account's first-ever Active Day is a Thursday: nothing before
        // it should count as a "zero Active Day" toward the gap-safeguard,
        // since there's no history to have been active in.
        val activeDays = setOf(day(1, 4), day(1, 5), day(1, 6)) // Thu/Fri/Sat: 3 Active Days, meets a target of 3

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(1, 7))

        assertEquals(StreakStatus(currentStreak = 1, bankedShields = 1, gapSafeguardFired = false), result)
    }

    @Test
    fun `the in-progress week counts as met the moment its Active Day count reaches the target`() {
        val activeDays = setOf(day(1, 1), day(1, 3), day(1, 4)) // Mon/Wed/Thu — target hit on a Thursday

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(1, 4)) // today is that Thursday, week isn't over

        assertEquals(StreakStatus(currentStreak = 1, bankedShields = 1, gapSafeguardFired = false), result)
    }

    @Test
    fun `a Weekly Target miss with a Shield banked is covered, not broken`() {
        val activeDays = setOf(
            day(1, 1), day(1, 4), day(1, 7), // week 1: meets target (3), banks a Shield
            day(2, 3), day(2, 5), // week 2: only 2 Active Days — misses the target
        )

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(2, 7))

        assertEquals(StreakStatus(currentStreak = 2, bankedShields = 0, gapSafeguardFired = false), result)
    }

    @Test
    fun `a Weekly Target miss with no Shield banked resets the Streak`() {
        val activeDays = setOf(day(1, 3), day(1, 5)) // week 1, its first ever: only 2 Active Days

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(1, 7))

        assertEquals(StreakStatus(currentStreak = 0, bankedShields = 0, gapSafeguardFired = false), result)
    }

    @Test
    fun `a Weekly Target miss that's still in progress is pending, not a broken Streak`() {
        val activeDays = setOf(
            day(1, 1), day(1, 4), day(1, 7), // week 1: meets target, streak = 1
            day(2, 1), // week 2 so far: only 1 Active Day, well short of target — but the week isn't over
        )

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(2, 3)) // mid-week 2, no miss has actually happened yet

        assertEquals(StreakStatus(currentStreak = 1, bankedShields = 1, gapSafeguardFired = false), result)
    }

    @Test
    fun `the gap-safeguard fires mid-week, independent of the weekly tally, wiping the Streak and its Shields`() {
        val activeDays = setOf(
            day(1, 1), day(1, 4), day(1, 7), // week 1: meets target, streak = 1, banks a Shield
            day(2, 1), // week 2: one Active Day, then nothing
            // day(2,2), day(2,3), day(2,4) all missing -> 3 consecutive zero-Active-Day days by Thursday
        )

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(2, 4)) // Thursday — mid-week, not the week's Sunday

        assertEquals(StreakStatus(currentStreak = 0, bankedShields = 0, gapSafeguardFired = true), result)
    }

    @Test
    fun `gapSafeguardFired reflects only the most recent evaluation, clearing once the Streak rebuilds`() {
        val activeDays = setOf(
            day(1, 1), day(1, 4), day(1, 7), // week 1: meets target, streak = 1
            day(2, 1), // week 2: gap-safeguard fires by day(2,4) (Thursday), same as the previous test
            day(2, 5), // week 2 recovers a little, but this week is already spoiled by the gap
            // week 3: rebuilt cleanly
            day(3, 1), day(3, 4), day(3, 7),
        )

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(3, 7))

        assertEquals(StreakStatus(currentStreak = 1, bankedShields = 1, gapSafeguardFired = false), result)
    }

    @Test
    fun `banked Shields are capped`() {
        val sixSuccessfulWeeks = (1..6).flatMap { week -> listOf(day(week, 1), day(week, 4), day(week, 7)) }.toSet()

        val result = StreakCalculator.calculate(activeDays = sixSuccessfulWeeks, today = day(6, 7))

        assertEquals(6, result.currentStreak)
        assertEquals(StreakCalculator.DEFAULT_SHIELD_CAP, result.bankedShields)
        assertFalse(result.gapSafeguardFired)
    }

    @Test
    fun `a custom shieldCap of zero means every miss breaks the Streak immediately`() {
        val activeDays = setOf(
            day(1, 1), day(1, 4), day(1, 7), // week 1: meets target, but no Shield to bank
            day(2, 3), day(2, 5), // week 2: misses target
        )

        val result = StreakCalculator.calculate(activeDays = activeDays, today = day(2, 7), shieldCap = 0)

        assertEquals(0, result.currentStreak)
        assertEquals(0, result.bankedShields)
        assertTrue("a plain miss with no shield, not the gap rule", !result.gapSafeguardFired)
    }
}
