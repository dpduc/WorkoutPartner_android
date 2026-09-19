package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import java.time.Instant

/**
 * A Set (CONTEXT.md): one continuous block of Reps of a single Exercise
 * performed within a [SessionEntity]. [formScore] is 0-100, the same range
 * `core-rep-counting.FormScore.compute` produces. [goodSet] mirrors
 * CONTEXT.md's Good Set definition (actualReps >= targetReps AND formScore
 * at or above the Exercise's threshold) — computed once by the caller when
 * the Set completes (ticket 09), not re-derived by this schema.
 *
 * A Set's calendar day (via [timestamp]) is what CONTEXT.md's Active Day is
 * built from ("a calendar day on which the Account completed at least one
 * Set"); this ticket deliberately doesn't pre-aggregate that here — folding
 * these into distinct Active Days needs a timezone/"today" policy, which is
 * ticket 06's repository-layer job, not this schema's.
 *
 * Cascade-deletes with its Session: a Set has no existence apart from the
 * Session it was performed in.
 *
 * [exerciseVariant] (`workout-partner-v3` ticket 02, typed as
 * [ExerciseVariant] since ticket 08) is null for [exercise]'s standard form,
 * or the variant the Athlete performed instead — see CONTEXT.md's Exercise
 * Variant entry.
 */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exercise: Exercise,
    val targetReps: Int,
    val actualReps: Int,
    val formScore: Int,
    val goodSet: Boolean,
    val timestamp: Instant,
    val exerciseVariant: ExerciseVariant? = null,
)
