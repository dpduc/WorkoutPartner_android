package com.workoutpartner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (ticket 06): adds [PendingSyncEntity]'s `pending_sync` table, the
 * offline-write outbox for Sets and Tallies. No existing table changes, so
 * this is purely additive — no data to preserve/transform.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_sync` (
                `queueId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `entityKind` TEXT NOT NULL,
                `entityId` TEXT NOT NULL,
                `enqueuedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
