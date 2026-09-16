package com.workoutpartner.data

import androidx.room.TypeConverter
import com.workoutpartner.core.repcounting.Exercise
import java.time.Instant

/**
 * Room TypeConverters for this schema's non-primitive column types:
 * [Instant] timestamps (stored as epoch millis), core-rep-counting's
 * [Exercise] enum (stored by name, reused directly rather than duplicated
 * into a parallel `data`-module enum — the same shared-vocabulary pattern
 * ticket 03 used for Landmark/PoseLandmarkFrame), this module's own
 * [SyncEntityKind] (ticket 06), also stored by name, and [ActivityLevel]
 * (`workout-partner-v2` ticket 01), likewise stored by name.
 */
object Converters {
    @TypeConverter
    fun instantToEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(epochMilli: Long?): Instant? = epochMilli?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun exerciseToName(exercise: Exercise?): String? = exercise?.name

    @TypeConverter
    fun nameToExercise(name: String?): Exercise? = name?.let(Exercise::valueOf)

    @TypeConverter
    fun syncEntityKindToName(kind: SyncEntityKind?): String? = kind?.name

    @TypeConverter
    fun nameToSyncEntityKind(name: String?): SyncEntityKind? = name?.let(SyncEntityKind::valueOf)

    @TypeConverter
    fun activityLevelToName(level: ActivityLevel?): String? = level?.name

    @TypeConverter
    fun nameToActivityLevel(name: String?): ActivityLevel? = name?.let(ActivityLevel::valueOf)

    @TypeConverter
    fun routineFormatToName(format: RoutineFormat?): String? = format?.name

    @TypeConverter
    fun nameToRoutineFormat(name: String?): RoutineFormat? = name?.let(RoutineFormat::valueOf)
}
