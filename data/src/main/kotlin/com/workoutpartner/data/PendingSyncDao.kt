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
}
