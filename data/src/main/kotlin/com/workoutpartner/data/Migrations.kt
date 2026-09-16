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

/**
 * v2 -> v3 (`workout-partner-v2` tickets 01 and 03): adds the Account's own
 * body-stats columns (name/age/heightCm/weightKg/activityLevel — all
 * nullable, so existing Account rows are simply "hasn't answered yet" per
 * [AccountEntity]'s doc comment), the [GuestProfileEntity] table for
 * pre-signup capture, and [TallyEntity]'s formScore/durationSeconds columns
 * (also nullable, same reasoning). Purely additive — no existing column is
 * changed or removed, so there's no data to transform.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `name` TEXT")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `age` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `heightCm` INTEGER")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `weightKg` REAL")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `activityLevel` TEXT")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `guest_profile` (
                `id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `age` INTEGER NOT NULL,
                `heightCm` INTEGER NOT NULL,
                `weightKg` REAL NOT NULL,
                `activityLevel` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        db.execSQL("ALTER TABLE `tallies` ADD COLUMN `formScore` INTEGER")
        db.execSQL("ALTER TABLE `tallies` ADD COLUMN `durationSeconds` INTEGER")
    }
}

/**
 * v3 -> v4 (`workout-partner-v2` ticket 02): adds [RoutineEntity.format] —
 * a display tag (see [RoutineFormat]'s doc comment), defaulted to
 * `STANDARD` for both existing rows and the column's own schema default, so
 * every pre-existing bundled Routine reads the same way it always has.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `routines` ADD COLUMN `format` TEXT NOT NULL DEFAULT 'STANDARD'")
    }
}
