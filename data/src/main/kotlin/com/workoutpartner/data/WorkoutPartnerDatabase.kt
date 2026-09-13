package com.workoutpartner.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room schema for spec.md's conceptual data model (ticket 05) plus the
 * offline-sync outbox (ticket 06's [PendingSyncEntity]). [createDatabase]
 * builds a real, on-disk instance; the repository classes (ticket 06) are
 * everything that reads or writes through it.
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
        PendingSyncEntity::class,
    ],
    version = 2,
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
    abstract fun pendingSyncDao(): PendingSyncDao
}
