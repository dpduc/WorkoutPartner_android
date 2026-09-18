package com.workoutpartner.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.workoutpartner.core.streaks.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises [MIGRATION_2_3]'s raw SQL directly against a hand-built v2-shaped
 * `accounts`/`tallies` schema (mirroring `data/schemas/.../2.json`), via a
 * plain [SupportSQLiteOpenHelper] rather than Room's `MigrationTestHelper` —
 * this Room/Robolectric combination has a known driver incompatibility with
 * `MigrationTestHelper` (`FrameworkSQLiteOpenHelperFactory`-backed opens
 * fail with "driver is configured to open a database named X but path Y was
 * requested"). Going straight to the framework SQLite layer sidesteps it
 * while still proving the migration's actual SQL is correct.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @Test
    fun `migrating 2 to 3 adds account profile columns, the guest_profile table, and tally columns`() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name("migration-test.db")
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `accounts` (`id` TEXT NOT NULL, `weeklyTarget` INTEGER NOT NULL, " +
                                "`bankedShields` INTEGER NOT NULL, `currentStreak` INTEGER NOT NULL, " +
                                "`notificationsEnabled` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE TABLE `tallies` (`id` TEXT NOT NULL, `trackedProfileId` TEXT NOT NULL, " +
                                "`exercise` TEXT NOT NULL, `repsAchieved` INTEGER NOT NULL, `target` INTEGER, " +
                                "`timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        db.execSQL(
            "INSERT INTO accounts (id, weeklyTarget, bankedShields, currentStreak, notificationsEnabled) " +
                "VALUES ('acct-1', 3, 0, 0, 1)",
        )

        MIGRATION_2_3.migrate(db)

        db.query("SELECT name, age, heightCm, weightKg, activityLevel FROM accounts WHERE id = 'acct-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            for (columnIndex in 0..4) assertTrue(cursor.isNull(columnIndex))
        }

        db.execSQL(
            "INSERT INTO guest_profile (id, name, age, heightCm, weightKg, activityLevel) " +
                "VALUES (0, 'Guest Name', 30, 170, 65.5, 'MEDIUM')",
        )
        db.query("SELECT name, age FROM guest_profile WHERE id = 0").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Guest Name", cursor.getString(0))
            assertEquals(30, cursor.getInt(1))
        }

        val tallyColumns = mutableListOf<String>()
        db.query("PRAGMA table_info(tallies)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) tallyColumns.add(cursor.getString(nameIndex))
        }
        assertTrue(tallyColumns.contains("formScore"))
        assertTrue(tallyColumns.contains("durationSeconds"))

        db.close()
    }

    @Test
    fun `migrating 3 to 4 adds routines format column, defaulted to STANDARD`() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name("migration-test-3-4.db")
            .callback(
                object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE `routines` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        db.execSQL("INSERT INTO routines (id, name) VALUES ('r1', 'Existing Routine')")

        MIGRATION_3_4.migrate(db)

        db.query("SELECT format FROM routines WHERE id = 'r1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("STANDARD", cursor.getString(0))
        }

        db.close()
    }

    private fun openV4Database(name: String): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `accounts` (`id` TEXT NOT NULL, `weeklyTarget` INTEGER NOT NULL, " +
                                "`bankedShields` INTEGER NOT NULL, `currentStreak` INTEGER NOT NULL, " +
                                "`notificationsEnabled` INTEGER NOT NULL, `name` TEXT, `age` INTEGER, " +
                                "`heightCm` INTEGER, `weightKg` REAL, `activityLevel` TEXT, PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE TABLE `tracked_profiles` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                                "`displayName` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)",
                        )
                        db.execSQL("CREATE INDEX `index_tracked_profiles_accountId` ON `tracked_profiles` (`accountId`)")
                        db.execSQL(
                            "CREATE TABLE `guest_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                                "`age` INTEGER NOT NULL, `heightCm` INTEGER NOT NULL, `weightKg` REAL NOT NULL, " +
                                "`activityLevel` TEXT NOT NULL, PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE TABLE `sets` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `exercise` TEXT NOT NULL, " +
                                "`targetReps` INTEGER NOT NULL, `actualReps` INTEGER NOT NULL, `formScore` INTEGER NOT NULL, " +
                                "`goodSet` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE TABLE `tallies` (`id` TEXT NOT NULL, `trackedProfileId` TEXT NOT NULL, " +
                                "`exercise` TEXT NOT NULL, `repsAchieved` INTEGER NOT NULL, `target` INTEGER, " +
                                "`timestamp` INTEGER NOT NULL, `formScore` INTEGER, `durationSeconds` INTEGER, PRIMARY KEY(`id`), " +
                                "FOREIGN KEY(`trackedProfileId`) REFERENCES `tracked_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        // Room enables foreign key enforcement by default (confirmed
        // empirically against this project's Room version) — matching that
        // here is what makes `migrating 4 to 5 rebuilds tracked_profiles
        // without cascade-deleting tallies` below a meaningful test, not a
        // false-negative one.
        db.execSQL("PRAGMA foreign_keys = ON")
        return db
    }

    @Test
    fun `migrating 4 to 5 remaps Activity Level values in accounts and guest_profile`() {
        val db = openV4Database("migration-test-4-5-activity-level.db")
        db.execSQL(
            "INSERT INTO accounts (id, weeklyTarget, bankedShields, currentStreak, notificationsEnabled, activityLevel) " +
                "VALUES ('low-acct', 3, 0, 0, 1, 'LOW'), ('med-acct', 3, 0, 0, 1, 'MEDIUM'), " +
                "('high-acct', 3, 0, 0, 1, 'HIGH'), ('unset-acct', 3, 0, 0, 1, NULL)",
        )
        db.execSQL(
            "INSERT INTO guest_profile (id, name, age, heightCm, weightKg, activityLevel) " +
                "VALUES (0, 'Guest', 30, 170, 65.5, 'MEDIUM')",
        )

        MIGRATION_4_5.migrate(db)

        val remapped = mutableMapOf<String, String?>()
        db.query("SELECT id, activityLevel FROM accounts").use { cursor ->
            while (cursor.moveToNext()) {
                remapped[cursor.getString(0)] = if (cursor.isNull(1)) null else cursor.getString(1)
            }
        }
        assertEquals("SEDENTARY", remapped["low-acct"])
        assertEquals("LIGHTLY_ACTIVE", remapped["med-acct"])
        assertEquals("ACTIVE", remapped["high-acct"])
        assertNull(remapped["unset-acct"])

        db.query("SELECT activityLevel FROM guest_profile WHERE id = 0").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("LIGHTLY_ACTIVE", cursor.getString(0))
        }

        db.close()
    }

    @Test
    fun `migrating 4 to 5 makes tracked_profiles accountId nullable while preserving existing rows`() {
        val db = openV4Database("migration-test-4-5-tracked-profiles.db")
        db.execSQL(
            "INSERT INTO accounts (id, weeklyTarget, bankedShields, currentStreak, notificationsEnabled) " +
                "VALUES ('acct-1', 3, 0, 0, 1)",
        )
        db.execSQL("INSERT INTO tracked_profiles (id, accountId, displayName) VALUES ('tp-1', 'acct-1', 'Alex')")

        MIGRATION_4_5.migrate(db)

        db.query("SELECT accountId, displayName FROM tracked_profiles WHERE id = 'tp-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("acct-1", cursor.getString(0))
            assertEquals("Alex", cursor.getString(1))
        }

        // The column itself must now accept NULL — this insert would have
        // violated the old NOT NULL constraint.
        db.execSQL("INSERT INTO tracked_profiles (id, accountId, displayName) VALUES ('tp-2', NULL, 'Sam')")
        db.query("SELECT accountId FROM tracked_profiles WHERE id = 'tp-2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }

        db.close()
    }

    /**
     * Regression test for a real bug caught in review: `tallies` carries
     * `ON DELETE CASCADE` on `trackedProfileId -> tracked_profiles`, and
     * SQLite implicitly cascade-deletes referencing rows when the
     * *referenced* table is dropped, if foreign key enforcement is on
     * (which [openV4Database] now matches Room's own default for). An
     * earlier version of `MIGRATION_4_5` rebuilt `tracked_profiles` with a
     * plain create-new/drop-old/rename-new sequence — and, when that didn't
     * work, a rename-old-table-out-of-the-way-first variant — and both
     * silently deleted every Tally under exactly this condition (verified
     * empirically before the fix). This test exists so that regresses loud.
     */
    @Test
    fun `migrating 4 to 5 rebuilds tracked_profiles without cascade-deleting tallies`() {
        val db = openV4Database("migration-test-4-5-tallies-survive.db")
        db.execSQL(
            "INSERT INTO accounts (id, weeklyTarget, bankedShields, currentStreak, notificationsEnabled) " +
                "VALUES ('acct-1', 3, 0, 0, 1)",
        )
        db.execSQL("INSERT INTO tracked_profiles (id, accountId, displayName) VALUES ('tp-1', 'acct-1', 'Alex')")
        db.execSQL(
            "INSERT INTO tallies (id, trackedProfileId, exercise, repsAchieved, timestamp) " +
                "VALUES ('tally-1', 'tp-1', 'JUMPING_JACK', 20, 0)",
        )

        MIGRATION_4_5.migrate(db)

        db.query("SELECT trackedProfileId FROM tallies WHERE id = 'tally-1'").use { cursor ->
            assertTrue("the Tally must survive the tracked_profiles rebuild", cursor.moveToFirst())
            assertEquals("tp-1", cursor.getString(0))
        }

        // Prove the FK is genuinely restored against the *final*
        // tracked_profiles, not just dropped silently: deleting the Tracked
        // Profile now should cascade-delete its Tally, same as pre-migration.
        db.execSQL("DELETE FROM tracked_profiles WHERE id = 'tp-1'")
        db.query("SELECT COUNT(*) FROM tallies WHERE id = 'tally-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.close()
    }

    @Test
    fun `migrating 4 to 5 gives guest_profile streak columns with correct defaults and nullable body-stats`() {
        val db = openV4Database("migration-test-4-5-guest-profile.db")
        db.execSQL(
            "INSERT INTO guest_profile (id, name, age, heightCm, weightKg, activityLevel) " +
                "VALUES (0, 'Guest', 30, 170, 65.5, 'HIGH')",
        )

        MIGRATION_4_5.migrate(db)

        db.query(
            "SELECT weeklyTarget, currentStreak, bankedShields, notificationsEnabled FROM guest_profile WHERE id = 0",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(StreakCalculator.DEFAULT_WEEKLY_TARGET, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
        }

        // The columns themselves must now accept NULL body-stats — a fresh
        // singleton row with no onboarding answers yet.
        db.execSQL("DELETE FROM guest_profile")
        db.execSQL(
            "INSERT INTO guest_profile (id, name, age, heightCm, weightKg, activityLevel, weeklyTarget, " +
                "currentStreak, bankedShields, notificationsEnabled) VALUES (0, NULL, NULL, NULL, NULL, NULL, 3, 0, 0, 1)",
        )
        db.query("SELECT name FROM guest_profile WHERE id = 0").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }

        db.close()
    }

    @Test
    fun `migrating 4 to 5 adds nullable exerciseVariant columns to sets and tallies`() {
        val db = openV4Database("migration-test-4-5-exercise-variant.db")
        db.execSQL(
            "INSERT INTO accounts (id, weeklyTarget, bankedShields, currentStreak, notificationsEnabled) " +
                "VALUES ('acct-1', 3, 0, 0, 1)",
        )
        db.execSQL("INSERT INTO tracked_profiles (id, accountId, displayName) VALUES ('tp-1', 'acct-1', 'Alex')")
        db.execSQL(
            "INSERT INTO sets (id, sessionId, exercise, targetReps, actualReps, formScore, goodSet, timestamp) " +
                "VALUES ('set-1', 'session-1', 'JUMPING_JACK', 20, 20, 90, 1, 0)",
        )
        db.execSQL(
            "INSERT INTO tallies (id, trackedProfileId, exercise, repsAchieved, timestamp) " +
                "VALUES ('tally-1', 'tp-1', 'JUMPING_JACK', 20, 0)",
        )

        MIGRATION_4_5.migrate(db)

        db.query("SELECT exerciseVariant FROM sets WHERE id = 'set-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        db.query("SELECT exerciseVariant FROM tallies WHERE id = 'tally-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }

        db.execSQL("UPDATE sets SET exerciseVariant = 'STEP_JACK' WHERE id = 'set-1'")
        db.query("SELECT exerciseVariant FROM sets WHERE id = 'set-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("STEP_JACK", cursor.getString(0))
        }

        db.close()
    }
}
