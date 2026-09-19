package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import java.time.Instant

/**
 * A Tally (CONTEXT.md): the record produced by one Quick Count run —
 * belongs to a [TrackedProfileEntity], not to an Account's own
 * Session/Streak history. [target] is optional (spec.md user story 36:
 * "optionally set a target count for a Quick Count run").
 *
 * [formScore]/[durationSeconds] (`workout-partner-v2` ticket 03) reverse
 * the earlier "Quick Count Tallies never have a Form Score" decision —
 * `QuickCountEngine` now records each Rep's form-threshold pass the same
 * way `SessionEngine` does, and [durationSeconds] is elapsed run time.
 * Nullable: a Tally recorded before this ticket landed genuinely has
 * neither value, and a fabricated `0` would misrepresent that as a real
 * (and suspiciously perfect/instant) result.
 *
 * Cascade-deletes with its Tracked Profile: a Tally has no existence apart
 * from the profile it was recorded against.
 *
 * [exerciseVariant] (`workout-partner-v3` ticket 02, typed as
 * [ExerciseVariant] since ticket 08): see [SetEntity]'s doc comment on the
 * field of the same name — same reasoning. Not yet written by any caller
 * (Quick Count has no Step Jack entry point — that's a later ticket, if
 * ever built), but ready for one.
 */
@Entity(
    tableName = "tallies",
    foreignKeys = [
        ForeignKey(
            entity = TrackedProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackedProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackedProfileId")],
)
data class TallyEntity(
    @PrimaryKey val id: String,
    val trackedProfileId: String,
    val exercise: Exercise,
    val repsAchieved: Int,
    val target: Int?,
    val timestamp: Instant,
    val formScore: Int? = null,
    val durationSeconds: Int? = null,
    val exerciseVariant: ExerciseVariant? = null,
)
