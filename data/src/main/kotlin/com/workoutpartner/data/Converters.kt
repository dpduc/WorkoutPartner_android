package com.workoutpartner.data

import androidx.room.TypeConverter
import com.workoutpartner.core.repcounting.Exercise
import java.time.Instant

/**
 * Room TypeConverters for the two non-primitive types this schema's columns
 * use: [Instant] timestamps (stored as epoch millis) and core-rep-counting's
 * [Exercise] enum (stored by name) — [Exercise] is reused directly rather
 * than duplicated into a parallel `data`-module enum, the same shared-
 * vocabulary pattern ticket 03 used for Landmark/PoseLandmarkFrame.
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
}
