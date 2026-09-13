package com.workoutpartner.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room schema for spec.md's conceptual data model (ticket 05). Building
 * this into a real, injectable database instance (and everything that reads
 * or writes through it) is the repository layer's job — Seam 3, ticket 06.
 * This class and its DAOs are schema only.
 */
@Database(
    entities = [
        AccountEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        SessionEntity::class,
        SetEntity::class,
        TrackedProfileEntity::class,
        TallyEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WorkoutPartnerDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun routineDao(): RoutineDao
    abstract fun sessionDao(): SessionDao
    abstract fun setDao(): SetDao
    abstract fun trackedProfileDao(): TrackedProfileDao
    abstract fun tallyDao(): TallyDao
}
