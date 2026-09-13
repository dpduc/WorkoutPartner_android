package com.workoutpartner.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/**
 * An in-memory [WorkoutPartnerDatabase] for tests — Robolectric is what
 * lets this run as a plain JVM unit test rather than needing a device
 * (see the `data` module's build.gradle.kts comment).
 */
fun newInMemoryDatabase(): WorkoutPartnerDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutPartnerDatabase::class.java)
        .allowMainThreadQueries()
        .build()
