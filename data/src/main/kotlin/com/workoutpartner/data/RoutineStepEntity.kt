package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.workoutpartner.core.repcounting.Exercise

/**
 * One (Exercise, target reps, rest interval) step in a [RoutineEntity]'s
 * ordered sequence — [orderIndex] carries that order, since SQL rows have
 * no inherent one. [RoutineDao] is responsible for reading these back
 * ordered by it.
 *
 * Cascade-deletes with its Routine: a step has no existence apart from the
 * Routine that defines it. [id] is an auto-generated surrogate key, since
 * these rows are purely local ordering data with no cross-system identity
 * of their own (unlike the UUID keys used where Firestore sync needs a
 * stable id).
 */
@Entity(
    tableName = "routine_steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: String,
    val orderIndex: Int,
    val exercise: Exercise,
    val targetReps: Int,
    val restIntervalSeconds: Int,
)
