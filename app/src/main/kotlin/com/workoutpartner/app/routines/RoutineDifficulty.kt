package com.workoutpartner.app.routines

import com.workoutpartner.data.AccountEntity
import com.workoutpartner.data.ActivityLevel
import com.workoutpartner.data.GuestProfileEntity
import kotlin.math.roundToInt

/**
 * The age/height/weight/[ActivityLevel] a difficulty tier is computed from
 * — deliberately not [AccountEntity] itself: a Guest (`workout-partner-v2`
 * ticket 01) has the same answers on a [GuestProfileEntity] instead, and
 * this tuning applies to both, per the ticket's "collect for everyone at
 * first use" decision.
 */
data class BodyStats(val age: Int?, val heightCm: Int?, val weightKg: Double?, val activityLevel: ActivityLevel? = null)

fun AccountEntity.toBodyStats() = BodyStats(age, heightCm, weightKg, activityLevel)
fun GuestProfileEntity.toBodyStats() = BodyStats(age, heightCm, weightKg, activityLevel)

enum class DifficultyTier { EASY, STANDARD, CHALLENGING }

private enum class BmiCategory { UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE }
private enum class AgeBand { YOUNGER, MIDDLE, OLDER }

/**
 * Scales a Routine's rep targets/rest intervals from an Account's (or
 * Guest's) body stats — BMI (from height/weight) and an age band, plus
 * (since `workout-partner-v3` ticket 07) the self-reported [ActivityLevel]:
 * Sedentary -1, Lightly Active/Active 0 (unchanged from the pre-ticket-07
 * behavior, where Activity Level didn't score at all), Very Active +1,
 * missing 0. Reasonable-default thresholds, the same "defensible default,
 * documented as a placeholder, not a settled clinical formula" spirit as
 * [com.workoutpartner.app.session.SessionEngine]'s own Good Set threshold
 * and core-rep-counting's angle thresholds — not medical/fitness advice,
 * and not spec-derived.
 *
 * Applied at Session-start time when [com.workoutpartner.app.session.SessionViewModel]
 * builds its engine steps, not by mutating the seeded `RoutineEntity`/
 * `RoutineStepEntity` rows — those stay "seeded content, not user-editable"
 * per `RoutineEntity`'s own doc comment.
 */
object RoutineDifficulty {
    fun compute(stats: BodyStats?): DifficultyTier {
        val age = stats?.age
        val heightCm = stats?.heightCm
        val weightKg = stats?.weightKg
        if (age == null || heightCm == null || weightKg == null || heightCm <= 0) return DifficultyTier.STANDARD

        val bmiScore = when (bmiCategory(heightCm, weightKg)) {
            BmiCategory.UNDERWEIGHT, BmiCategory.NORMAL -> 1
            BmiCategory.OVERWEIGHT -> 0
            BmiCategory.OBESE -> -1
        }
        val ageScore = when (ageBand(age)) {
            AgeBand.YOUNGER -> 1
            AgeBand.MIDDLE -> 0
            AgeBand.OLDER -> -1
        }
        val activityScore = when (stats.activityLevel) {
            ActivityLevel.SEDENTARY -> -1
            ActivityLevel.LIGHTLY_ACTIVE, ActivityLevel.ACTIVE, null -> 0
            ActivityLevel.VERY_ACTIVE -> 1
        }
        val total = bmiScore + ageScore + activityScore
        return when {
            total >= 2 -> DifficultyTier.CHALLENGING
            total <= -1 -> DifficultyTier.EASY
            else -> DifficultyTier.STANDARD
        }
    }

    /** More reps/less rest for [DifficultyTier.CHALLENGING], fewer reps/more rest for [DifficultyTier.EASY]. */
    private fun repMultiplierFor(tier: DifficultyTier): Double = when (tier) {
        DifficultyTier.EASY -> 0.8
        DifficultyTier.STANDARD -> 1.0
        DifficultyTier.CHALLENGING -> 1.2
    }

    fun adjustedTargetReps(baseTargetReps: Int, tier: DifficultyTier): Int =
        (baseTargetReps * repMultiplierFor(tier)).roundToInt().coerceAtLeast(1)

    /** Inverse of [repMultiplierFor]: a harder tier means less rest, not more. */
    fun adjustedRestIntervalSeconds(baseRestIntervalSeconds: Int, tier: DifficultyTier): Int {
        if (baseRestIntervalSeconds <= 0) return baseRestIntervalSeconds
        return (baseRestIntervalSeconds / repMultiplierFor(tier)).roundToInt().coerceAtLeast(0)
    }

    private fun bmiCategory(heightCm: Int, weightKg: Double): BmiCategory {
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        return when {
            bmi < 18.5 -> BmiCategory.UNDERWEIGHT
            bmi < 25.0 -> BmiCategory.NORMAL
            bmi < 30.0 -> BmiCategory.OVERWEIGHT
            else -> BmiCategory.OBESE
        }
    }

    private fun ageBand(age: Int): AgeBand = when {
        age < 30 -> AgeBand.YOUNGER
        age <= 50 -> AgeBand.MIDDLE
        else -> AgeBand.OLDER
    }
}
