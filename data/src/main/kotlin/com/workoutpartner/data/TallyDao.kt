package com.workoutpartner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TallyDao {
    @Insert
    suspend fun insert(tally: TallyEntity)

    @Query("SELECT * FROM tallies WHERE id = :tallyId")
    suspend fun getById(tallyId: String): TallyEntity?

    /** A Tracked Profile's Tally history (spec.md user story 39: "review someone's history later"), most recent first. */
    @Query("SELECT * FROM tallies WHERE trackedProfileId = :trackedProfileId ORDER BY timestamp DESC")
    suspend fun getForTrackedProfile(trackedProfileId: String): List<TallyEntity>
}
