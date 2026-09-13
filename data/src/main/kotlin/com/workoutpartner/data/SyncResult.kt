package com.workoutpartner.data

/** The outcome of one [SyncEngine.syncPendingChanges] attempt. */
data class SyncResult(
    val succeeded: Int,
    val remaining: Int,
)
