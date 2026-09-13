package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.workoutpartner.core.repcounting.Exercise
import java.time.Instant

/**
 * A Tally (CONTEXT.md): the record produced by one Quick Count run —
 * belongs to a [TrackedProfileEntity], not to an Account's own
 * Session/Streak history. Never carries a Form Score (CONTEXT.md: "Quick
 * Count Tallies never have a Form Score — they're raw counts"), and
 * [target] is optional (spec.md user story 36: "optionally set a target
 * count for a Quick Count run").
 *
 * Cascade-deletes with its Tracked Profile: a Tally has no existence apart
 * from the profile it was recorded against.
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
)
