package com.workoutpartner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Read-mostly in practice: Routines are bundled/seeded content (CONTEXT.md:
 * "not user-editable in v1"), so the insert methods exist for the app's own
 * seeding step (or a future reseed on update), not a user-facing "create a
 * Routine" feature.
 *
 * [getRoutineWithSteps]/[getAllRoutinesWithSteps] assemble [RoutineWithSteps]
 * from two plain, explicitly-ordered queries rather than a Room `@Relation`
 * — see [RoutineWithSteps]'s doc comment for why.
 */
@Dao
abstract class RoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSteps(steps: List<RoutineStepEntity>)

    @Transaction
    open suspend fun insertRoutineWithSteps(routine: RoutineEntity, steps: List<RoutineStepEntity>) {
        insertRoutine(routine)
        insertSteps(steps)
    }

    @Query("SELECT * FROM routines WHERE id = :routineId")
    protected abstract suspend fun getRoutine(routineId: String): RoutineEntity?

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY orderIndex ASC")
    protected abstract suspend fun getSteps(routineId: String): List<RoutineStepEntity>

    @Transaction
    open suspend fun getRoutineWithSteps(routineId: String): RoutineWithSteps? {
        val routine = getRoutine(routineId) ?: return null
        return RoutineWithSteps(routine, getSteps(routineId))
    }

    @Query("SELECT * FROM routines")
    protected abstract suspend fun getAllRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_steps ORDER BY routineId ASC, orderIndex ASC")
    protected abstract suspend fun getAllSteps(): List<RoutineStepEntity>

    @Transaction
    open suspend fun getAllRoutinesWithSteps(): List<RoutineWithSteps> {
        val stepsByRoutine = getAllSteps().groupBy { it.routineId }
        return getAllRoutines().map { routine -> RoutineWithSteps(routine, stepsByRoutine[routine.id].orEmpty()) }
    }
}
