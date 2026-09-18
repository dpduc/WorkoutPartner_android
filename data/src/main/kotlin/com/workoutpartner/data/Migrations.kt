package com.workoutpartner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.workoutpartner.core.streaks.StreakCalculator

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

/**
 * v4 -> v5 (`workout-partner-v3` ticket 02), a pure schema foundation for the
 * whole ticket set — no app behavior changes ride along with it:
 *
 * - [ActivityLevel] gains a fourth tier and drops its old names; `accounts`
 *   and `guest_profile` rows are remapped LOW->SEDENTARY,
 *   MEDIUM->LIGHTLY_ACTIVE, HIGH->ACTIVE (the conservative direction per
 *   ticket 02's own text: an ambiguous old answer should never make a
 *   Routine unexpectedly harder, so MEDIUM lands on the tier below center
 *   rather than the one at it).
 * - `tracked_profiles.accountId` becomes nullable — a Guest can own a
 *   Roster too (ticket 06's job to actually let them; this just makes room).
 * - `guest_profile` gains [GuestProfileEntity]'s new Weekly
 *   Target/Streak/Shields/notification columns, and its body-stats columns
 *   become nullable — see that entity's doc comment. SQLite can't ALTER a
 *   column's nullability in place, so both `tracked_profiles` and
 *   `guest_profile` are recreated wholesale rather than `ALTER TABLE ADD
 *   COLUMN` (which, on its own, is fine for the purely-additive columns
 *   further down — [MIGRATION_3_4] already shows `ADD COLUMN ... NOT NULL
 *   DEFAULT ...` works; it's only the nullability change on *existing*
 *   columns that forces a rebuild).
 *
 *   `tracked_profiles`'s rebuild needs real care: `tallies` carries
 *   `ON DELETE CASCADE` on `trackedProfileId -> tracked_profiles`, and
 *   SQLite's `DROP TABLE` performs an *implicit cascading delete* of every
 *   referencing row first when foreign key enforcement is on (Room enables
 *   it by default — confirmed empirically against this Room version, see
 *   this ticket's own comments). Naively dropping a table still named
 *   `tracked_profiles` while `tallies` rows point at it silently wipes
 *   every Tally. Renaming the old table out of the way first doesn't help
 *   either: SQLite auto-rewrites *other* tables' `REFERENCES` clauses to
 *   follow a renamed table when FK enforcement is on, so `tallies` would
 *   just end up pointing at whatever the old table gets renamed to, and
 *   *that* drop cascades instead (verified empirically — this exact
 *   sequence was tried and lost the Tally). The only sequence that survives
 *   FK enforcement in both directions: strip `tallies`' FK constraint first
 *   (recreate it with no `REFERENCES` clause at all, so nothing points at
 *   `tracked_profiles` anymore), rebuild `tracked_profiles` freely, then
 *   recreate `tallies` a second time to restore its FK against the final
 *   `tracked_profiles` (and pick up its own new `exerciseVariant` column in
 *   the same pass, rather than a third rebuild). `PRAGMA foreign_keys=OFF`
 *   is not a usable alternative here — Android's `SQLiteOpenHelper` already
 *   has a transaction open by the time a `Migration` runs, and per SQLite's
 *   own docs that pragma is a no-op while a transaction is active.
 *   `guest_profile` has no incoming FK, so its own recreate doesn't need
 *   any of this.
 * - [SetEntity.exerciseVariant]: new nullable column, purely additive —
 *   `sets` has no incoming FK, so a plain `ALTER TABLE ADD COLUMN` is fine.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE `accounts` SET `activityLevel` = 'SEDENTARY' WHERE `activityLevel` = 'LOW'")
        db.execSQL("UPDATE `accounts` SET `activityLevel` = 'LIGHTLY_ACTIVE' WHERE `activityLevel` = 'MEDIUM'")
        db.execSQL("UPDATE `accounts` SET `activityLevel` = 'ACTIVE' WHERE `activityLevel` = 'HIGH'")

        // Step 1: strip `tallies`' FK to `tracked_profiles` so nothing
        // references that table while it's rebuilt below. See this
        // migration's own doc comment for why this can't be skipped.
        db.execSQL(
            """
            CREATE TABLE `tallies_stripped` (
                `id` TEXT NOT NULL,
                `trackedProfileId` TEXT NOT NULL,
                `exercise` TEXT NOT NULL,
                `repsAchieved` INTEGER NOT NULL,
                `target` INTEGER,
                `timestamp` INTEGER NOT NULL,
                `formScore` INTEGER,
                `durationSeconds` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO `tallies_stripped` (`id`, `trackedProfileId`, `exercise`, `repsAchieved`, `target`, `timestamp`, `formScore`, `durationSeconds`) " +
                "SELECT `id`, `trackedProfileId`, `exercise`, `repsAchieved`, `target`, `timestamp`, `formScore`, `durationSeconds` FROM `tallies`",
        )
        db.execSQL("DROP TABLE `tallies`")
        db.execSQL("ALTER TABLE `tallies_stripped` RENAME TO `tallies`")

        // Step 2: rebuild `tracked_profiles` — safe now, nothing references it.
        db.execSQL("ALTER TABLE `tracked_profiles` RENAME TO `tracked_profiles_old`")
        db.execSQL(
            """
            CREATE TABLE `tracked_profiles` (
                `id` TEXT NOT NULL,
                `accountId` TEXT,
                `displayName` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO `tracked_profiles` (`id`, `accountId`, `displayName`) " +
                "SELECT `id`, `accountId`, `displayName` FROM `tracked_profiles_old`",
        )
        db.execSQL("DROP TABLE `tracked_profiles_old`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracked_profiles_accountId` ON `tracked_profiles` (`accountId`)")

        // Step 3: restore `tallies`' FK against the final `tracked_profiles`,
        // picking up its own new `exerciseVariant` column in the same pass.
        db.execSQL(
            """
            CREATE TABLE `tallies_restored` (
                `id` TEXT NOT NULL,
                `trackedProfileId` TEXT NOT NULL,
                `exercise` TEXT NOT NULL,
                `repsAchieved` INTEGER NOT NULL,
                `target` INTEGER,
                `timestamp` INTEGER NOT NULL,
                `formScore` INTEGER,
                `durationSeconds` INTEGER,
                `exerciseVariant` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`trackedProfileId`) REFERENCES `tracked_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "INSERT INTO `tallies_restored` (`id`, `trackedProfileId`, `exercise`, `repsAchieved`, `target`, `timestamp`, `formScore`, `durationSeconds`) " +
                "SELECT `id`, `trackedProfileId`, `exercise`, `repsAchieved`, `target`, `timestamp`, `formScore`, `durationSeconds` FROM `tallies`",
        )
        db.execSQL("DROP TABLE `tallies`")
        db.execSQL("ALTER TABLE `tallies_restored` RENAME TO `tallies`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tallies_trackedProfileId` ON `tallies` (`trackedProfileId`)")

        db.execSQL(
            """
            CREATE TABLE `guest_profile_new` (
                `id` INTEGER NOT NULL,
                `name` TEXT,
                `age` INTEGER,
                `heightCm` INTEGER,
                `weightKg` REAL,
                `activityLevel` TEXT,
                `weeklyTarget` INTEGER NOT NULL,
                `currentStreak` INTEGER NOT NULL,
                `bankedShields` INTEGER NOT NULL,
                `notificationsEnabled` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `guest_profile_new`
                (`id`, `name`, `age`, `heightCm`, `weightKg`, `activityLevel`,
                 `weeklyTarget`, `currentStreak`, `bankedShields`, `notificationsEnabled`)
            SELECT
                `id`, `name`, `age`, `heightCm`, `weightKg`,
                CASE `activityLevel`
                    WHEN 'LOW' THEN 'SEDENTARY'
                    WHEN 'MEDIUM' THEN 'LIGHTLY_ACTIVE'
                    WHEN 'HIGH' THEN 'ACTIVE'
                    ELSE `activityLevel`
                END,
                ${StreakCalculator.DEFAULT_WEEKLY_TARGET}, 0, 0, 1
            FROM `guest_profile`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `guest_profile`")
        db.execSQL("ALTER TABLE `guest_profile_new` RENAME TO `guest_profile`")

        // `tallies` already picked up `exerciseVariant` in its Step 3 rebuild
        // above; `sets` has no incoming FK, so a plain ADD COLUMN suffices.
        db.execSQL("ALTER TABLE `sets` ADD COLUMN `exerciseVariant` TEXT")
    }
}
