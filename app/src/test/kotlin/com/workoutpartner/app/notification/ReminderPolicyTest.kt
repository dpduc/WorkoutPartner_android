package com.workoutpartner.app.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderPolicyTest {

    // A fixed Monday, so week-boundary math in tests is easy to reason about.
    private val monday = LocalDate.of(2024, 1, 1)

    @Test
    fun `does not remind when the Account has notifications turned off`() {
        val remind = ReminderPolicy.shouldRemind(
            activeDays = emptySet(), today = monday, weeklyTarget = 3, notificationsEnabled = false,
        )

        assertFalse(remind)
    }

    @Test
    fun `does not remind when today is already an Active Day`() {
        val remind = ReminderPolicy.shouldRemind(
            activeDays = setOf(monday), today = monday, weeklyTarget = 3, notificationsEnabled = true,
        )

        assertFalse(remind)
    }

    @Test
    fun `does not remind once this week's Weekly Target is already met`() {
        val wednesday = monday.plusDays(2)
        val activeDays = setOf(monday, monday.plusDays(1), wednesday) // Mon, Tue, Wed: 3 this week already

        val remind = ReminderPolicy.shouldRemind(
            activeDays = activeDays, today = wednesday.plusDays(1), weeklyTarget = 3, notificationsEnabled = true,
        )

        assertFalse(remind)
    }

    @Test
    fun `reminds when today is not yet Active and this week's target is not yet met`() {
        val activeDays = setOf(monday) // only 1 Active Day so far this week

        val remind = ReminderPolicy.shouldRemind(
            activeDays = activeDays, today = monday.plusDays(2), weeklyTarget = 3, notificationsEnabled = true,
        )

        assertTrue(remind)
    }

    @Test
    fun `an Active Day from last week does not count toward this week's target`() {
        val lastSunday = monday.minusDays(1) // the day before this Monday, i.e. last week
        val activeDays = setOf(lastSunday.minusDays(2), lastSunday.minusDays(1), lastSunday) // 3 Active Days, all last week

        val remind = ReminderPolicy.shouldRemind(
            activeDays = activeDays, today = monday, weeklyTarget = 3, notificationsEnabled = true,
        )

        assertTrue(remind)
    }
}
