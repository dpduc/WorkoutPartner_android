package com.workoutpartner.data

import android.content.Context
import androidx.room.Room

private const val DATABASE_NAME = "workout_partner.db"

/**
 * Builds the real, on-disk [WorkoutPartnerDatabase] — the "injectable
 * database instance" ticket 05 deferred to this ticket. Callers (the `app`
 * module's DI wiring) own the instance's lifetime; this function is just
 * the one place [MIGRATION_1_2]/[MIGRATION_2_3]/[MIGRATION_3_4]/
 * [MIGRATION_4_5] and the file name are wired together.
 *
 * Tests should not call this — build an in-memory instance directly via
 * `Room.inMemoryDatabaseBuilder(context, WorkoutPartnerDatabase::class.java)`
 * instead (see the `data` module's test sources).
 */
fun createDatabase(context: Context): WorkoutPartnerDatabase =
    Room.databaseBuilder(context.applicationContext, WorkoutPartnerDatabase::class.java, DATABASE_NAME)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()
