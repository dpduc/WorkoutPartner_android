package com.workoutpartner.app.progress

import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.SetEntity
import java.time.LocalDate
import java.time.ZoneId

/** An Exercise's Personal Best (CONTEXT.md): "highest recorded rep count or Form Score... across all their Sets." The two highs are tracked independently — the Set with the most reps need not be the Set with the best form. */
data class PersonalBest(val bestReps: Int, val bestFormScore: Int)

/** One point on an Exercise's Form Score trend line (spec.md story 25), in chronological order. */
data class FormScorePoint(val date: LocalDate, val formScore: Int)

/**
 * Pure aggregations over an Account's Set history for the Progress/Streaks
 * UI (ticket 10) — Active Days for the calendar heatmap, Personal Bests,
 * and the Form Score trend per Exercise. Plain Kotlin over a `List<SetEntity>`
 * the caller already fetched ([com.workoutpartner.data.SetRepository.getSetsForAccount]);
 * no Room/Android dependency, so these are unit-testable without Robolectric
 * — the same "pure logic, impure fetch" split the rest of this app uses.
 *
 * [zone] resolves each Set's [java.time.Instant] timestamp to the calendar
 * day it falls on — injected by the caller rather than read from the system
 * default, matching this project's "inject the clock/zone" convention
 * (core-streaks, `AccountRepository.recomputeStreak`).
 */
object ProgressStats {
    /** Every distinct calendar day with at least one Set — the same "Active Day" CONTEXT.md and core-streaks use, computed the same way `AccountRepository.recomputeStreak` does. */
    fun activeDays(sets: List<SetEntity>, zone: ZoneId): Set<LocalDate> =
        sets.map { it.timestamp.atZone(zone).toLocalDate() }.toSet()

    /** One [PersonalBest] per Exercise that has at least one Set; an Exercise never attempted has no entry. */
    fun personalBests(sets: List<SetEntity>): Map<Exercise, PersonalBest> =
        sets.groupBy { it.exercise }.mapValues { (_, exerciseSets) ->
            PersonalBest(
                bestReps = exerciseSets.maxOf { it.actualReps },
                bestFormScore = exerciseSets.maxOf { it.formScore },
            )
        }

    /** [exercise]'s Sets in chronological order, one point per Set (not per day — multiple Sets of the same Exercise on the same day each plot separately, showing genuine session-to-session change). */
    fun formScoreTrend(sets: List<SetEntity>, exercise: Exercise, zone: ZoneId): List<FormScorePoint> =
        sets.filter { it.exercise == exercise }
            .sortedBy { it.timestamp }
            .map { FormScorePoint(it.timestamp.atZone(zone).toLocalDate(), it.formScore) }
}
