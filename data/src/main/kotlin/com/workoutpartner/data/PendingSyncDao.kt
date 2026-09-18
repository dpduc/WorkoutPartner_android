package com.workoutpartner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingSyncDao {
    @Insert
    suspend fun insert(item: PendingSyncEntity)

    @Delete
    suspend fun delete(item: PendingSyncEntity)

    /** FIFO order — oldest enqueued first, so sync-on-reconnect replays writes in the order they happened. */
    @Query("SELECT * FROM pending_sync ORDER BY enqueuedAt ASC, queueId ASC")
    suspend fun getAllOrdered(): List<PendingSyncEntity>

    @Query("SELECT COUNT(*) FROM pending_sync")
    suspend fun count(): Int

    /**
     * Removes any queued outbox entries for the given Set/Tally ids —
     * `pending_sync` has no FK to what it queues, so a cascade-delete of
     * the Set/Tally rows themselves (`workout-partner-v3` ticket 05's
     * Discard) leaves these orphaned unless swept up explicitly.
     */
    @Query("DELETE FROM pending_sync WHERE entityId IN (:entityIds)")
    suspend fun deleteByEntityIds(entityIds: List<String>)
}
