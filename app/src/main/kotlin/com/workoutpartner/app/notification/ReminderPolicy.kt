package com.workoutpartner.app.notification

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Whether the streak-aware daily reminder (ticket 12, spec.md user story
 * 33) should fire today — pure Kotlin over already-fetched data, the same
 * "logic worth testing lives in a plain object" split as
 * [com.workoutpartner.app.progress.ProgressStats]. [DailyReminderWorker] is
 * the only caller, and does the actual fetching/notifying.
 *
 * Fires only when *both* are true (this ticket's scope line, not an
 * unconditional daily ping): [notificationsEnabled] is on, today isn't
 * already an Active Day, and this calendar week (Monday-Sunday, the same
 * week core-streaks uses) hasn't yet reached [weeklyTarget] Active Days.
 *
 * This week-boundary/target-met check is a deliberate, tested parallel
 * implementation of the same semantics
 * `core-streaks.StreakCalculator.calculate` uses internally (Monday-Sunday,
 * "met the moment reached, not waiting for Sunday") — not a call into it,
 * because `StreakCalculator`'s public output (`StreakStatus`) doesn't
 * expose "has this week's target been met so far" as its own value, only
 * the already-settled `currentStreak`/`bankedShields`. If `StreakCalculator`
 * ever changes its week-boundary or target-met rules, this needs updating
 * to match — a real, accepted duplication, not an oversight.
 */
object ReminderPolicy {
    fun shouldRemind(
        activeDays: Set<LocalDate>,
        today: LocalDate,
        weeklyTarget: Int,
        notificationsEnabled: Boolean,
    ): Boolean {
        if (!notificationsEnabled) return false
        if (today in activeDays) return false

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val activeDaysThisWeek = activeDays.count { it in weekStart..today }
        return activeDaysThisWeek < weeklyTarget
    }
}
