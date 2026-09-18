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

    /**
     * An Athlete's Roster (CONTEXT.md). [accountId] `null` selects the
     * device's single Guest's Roster (`workout-partner-v3` ticket 06) — `IS`
     * rather than `=` so the same query works for both, since SQL's
     * `= NULL` never matches.
     */
    @Query("SELECT * FROM tracked_profiles WHERE accountId IS :accountId ORDER BY displayName ASC")
    suspend fun getForAccount(accountId: String?): List<TrackedProfileEntity>

    /** Re-points every unowned (Guest) Tracked Profile at [accountId] in one write, mirroring [SessionDao.claimUnowned] (`workout-partner-v3` ticket 05). */
    @Query("UPDATE tracked_profiles SET accountId = :accountId WHERE accountId IS NULL")
    suspend fun claimUnowned(accountId: String)

    /** Permanently removes every unowned (Guest) Tracked Profile — cascades to their Tallies (`workout-partner-v3` ticket 05's Discard). */
    @Query("DELETE FROM tracked_profiles WHERE accountId IS NULL")
    suspend fun deleteUnowned()
}
