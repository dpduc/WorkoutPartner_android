package com.workoutpartner.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
}
