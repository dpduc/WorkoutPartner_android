package com.workoutpartner.core.streaks

import java.time.LocalDate

/** A fixed Monday every test anchors its week/weekday offsets to. */
val ANCHOR_MONDAY: LocalDate = LocalDate.of(2024, 1, 1)

/**
 * The [dayOfWeek]th day (1=Monday..7=Sunday) of the [week]th week (1-indexed)
 * after [ANCHOR_MONDAY] — lets tests read as "week 2, day 3" instead of bare
 * dates.
 */
fun day(week: Int, dayOfWeek: Int): LocalDate =
    ANCHOR_MONDAY.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek - 1).toLong())
