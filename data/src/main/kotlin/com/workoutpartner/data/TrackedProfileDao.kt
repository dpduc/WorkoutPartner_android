package com.workoutpartner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TrackedProfileDao {
    @Insert
    suspend fun insert(profile: TrackedProfileEntity)

    @Delete
    suspend fun delete(profile: TrackedProfileEntity)

    @Query("SELECT * FROM tracked_profiles WHERE id = :profileId")
    suspend fun getById(profileId: String): TrackedProfileEntity?

    /** An Account's Roster (CONTEXT.md). */
    @Query("SELECT * FROM tracked_profiles WHERE accountId = :accountId ORDER BY displayName ASC")
    suspend fun getForAccount(accountId: String): List<TrackedProfileEntity>
}
