package com.workoutpartner.core.streaks

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The Streak Calculator (Seam 2, per spec.md): a pure function of an
 * Account's Active Day history, its Weekly Target, and an injected "today"
 * — no persistence or clock dependency lives here. Covers user stories
 * 26-31.
 *
 * [activeDays] is expected already deduplicated to one entry per calendar
 * day — per CONTEXT.md's Active Day definition ("counted once per day
 * regardless of how many Sessions happened that day"), this module doesn't
 * know about Sets/Sessions at all, only days, so it leans on `Set<LocalDate>`
 * to make same-day collapsing a type guarantee rather than logic here.
 *
 * Walks forward day by day from the account's first Active Day through
 * [today], tracking two independent rules from CONTEXT.md's Streak
 * definition and ADR-0005:
 *  - **Gap-safeguard**: any run of [GAP_SAFEGUARD_DAYS] consecutive
 *    zero-Active-Day days breaks the Streak (and wipes banked Shields)
 *    immediately, regardless of the weekly tally, mid-week or not.
 *  - **Weekly tally**: each calendar week (Monday-Sunday) that meets
 *    [weeklyTarget] extends the Streak by one and banks a Shield (capped at
 *    [shieldCap]); a week that misses it is covered by a banked Shield if
 *    one is available, otherwise the Streak (and any banked Shields) reset
 *    to zero.
 *
 * The week containing [today] counts as met the moment its Active Day
 * count reaches [weeklyTarget] — it doesn't wait for the week to finish,
 * matching spec.md's "Duolingo-style weekly Streak" framing. Short of the
 * target, it's simply pending (not yet a miss), since the week isn't over.
 *
 * Design decisions this ticket left unspecified, made here and documented
 * rather than left implicit (same spirit as ticket 02's placeholder angle
 * thresholds):
 *  - Weeks are Monday-Sunday (ISO). The spec doesn't pin a week-start day.
 *  - [DEFAULT_SHIELD_CAP] is 4 — roughly one bankable "miss" a month.
 *  - A gap-safeguard break wipes banked Shields along with the Streak
 *    count, on the reasoning that "the Streak breaks" (CONTEXT.md) means a
 *    full reset of progress, not just the displayed number — the
 *    alternative (Shields surviving a gap break) isn't ruled out by the
 *    spec but reads as inconsistent with a from-scratch restart.
 */
object StreakCalculator {
    const val DEFAULT_WEEKLY_TARGET = 3

    /** Not specified numerically in the spec ("banked Shields are capped") — picked so an Account can miss roughly one week a month without losing their Streak. */
    const val DEFAULT_SHIELD_CAP = 4

    private const val GAP_SAFEGUARD_DAYS = 3

    fun calculate(
        activeDays: Set<LocalDate>,
        today: LocalDate,
        weeklyTarget: Int = DEFAULT_WEEKLY_TARGET,
        shieldCap: Int = DEFAULT_SHIELD_CAP,
    ): StreakStatus {
        val earliestActiveDay = activeDays.minOrNull()
            ?: return StreakStatus(currentStreak = 0, bankedShields = 0, gapSafeguardFired = false)

        var streak = 0
        var shields = 0
        var gapFired = false
        var consecutiveZeroDays = 0
        var weekActiveDayCount = 0
        var weekSpoiledByGap = false

        // Starts exactly at the account's first-ever Active Day, not backed
        // up to that week's Monday — days before the account had any
        // history at all aren't a "gap" (there was nothing to be active
        // for), so they must never feed the gap-safeguard's zero-day count.
        // A first week that starts mid-week is otherwise evaluated exactly
        // like any other: met if its (fewer) available days still reach
        // weeklyTarget.
        var day = earliestActiveDay
        while (day <= today) {
            if (day in activeDays) {
                consecutiveZeroDays = 0
                weekActiveDayCount++
            } else {
                consecutiveZeroDays++
                if (consecutiveZeroDays >= GAP_SAFEGUARD_DAYS) {
                    streak = 0
                    shields = 0
                    gapFired = true
                    weekSpoiledByGap = true
                }
            }

            val isEndOfWeek = day.dayOfWeek == DayOfWeek.SUNDAY
            val isLastDayToEvaluate = day == today

            if (isEndOfWeek || isLastDayToEvaluate) {
                if (!weekSpoiledByGap) {
                    var weekWasEvaluated = true
                    when {
                        weekActiveDayCount >= weeklyTarget -> {
                            streak++
                            if (shields < shieldCap) shields++
                        }
                        isEndOfWeek -> {
                            // A genuinely completed week that missed the
                            // target (a still-in-progress current week falls
                            // through here too, but only when it's also the
                            // week's Sunday, at which point it's no longer
                            // "pending").
                            if (shields > 0) {
                                shields--
                                streak++
                            } else {
                                streak = 0
                                shields = 0
                            }
                        }
                        else -> weekWasEvaluated = false // partial current week, target not yet met — pending, not a miss.
                    }
                    if (weekWasEvaluated) gapFired = false
                }

                if (isEndOfWeek) {
                    weekActiveDayCount = 0
                    weekSpoiledByGap = false
                }
            }

            day = day.plusDays(1)
        }

        return StreakStatus(currentStreak = streak, bankedShields = shields, gapSafeguardFired = gapFired)
    }
}
