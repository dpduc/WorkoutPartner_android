package com.workoutpartner.data

import com.workoutpartner.core.repcounting.Exercise
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetRepositoryTest {

    private val db = newInMemoryDatabase()
    private val accountRepository = AccountRepository(db)
    private val setRepository = SetRepository(db, accountRepository)

    @Test
    fun `recordSet writes the Set to Room and enqueues it for sync`() = runTest {
        val routineId = seedRoutine()
        val session = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))

        val set = setRepository.recordSet(
            sessionId = session.id, exercise = Exercise.SQUAT, targetReps = 10, actualReps = 8, formScore = 80,
            goodSet = false, timestamp = Instant.parse("2024-01-01T10:05:00Z"), today = LocalDate.of(2024, 1, 1),
        )

        assertEquals(set, db.setDao().getById(set.id))
        val queued = db.pendingSyncDao().getAllOrdered()
        assertEquals(1, queued.size)
        assertEquals(SyncEntityKind.SET, queued.single().entityKind)
        assertEquals(set.id, queued.single().entityId)
    }

    @Test
    fun `recording a Set for a signed-in Account's Session refreshes that Account's Streak`() = runTest {
        val routineId = seedRoutine()
        val account = accountRepository.createAccount("account-1", weeklyTarget = 3)
        val session = setRepository.startSession(accountId = account.id, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))

        // Mon/Wed/Fri of the same week: meets a Weekly Target of 3 without
        // ever tripping the gap-safeguard (core-streaks' own fixture spacing
        // convention).
        recordSetOn(session.id, "2024-01-01T10:00:00Z") // Monday
        recordSetOn(session.id, "2024-01-03T10:00:00Z") // Wednesday
        recordSetOn(session.id, "2024-01-05T10:00:00Z") // Friday

        val updated = accountRepository.getAccount(account.id)!!
        assertEquals(1, updated.currentStreak)
        assertEquals(1, updated.bankedShields)
    }

    @Test
    fun `recording a Set for a Guest's Session (null accountId) touches no Account`() = runTest {
        val routineId = seedRoutine()
        val session = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))

        setRepository.recordSet(
            sessionId = session.id, exercise = Exercise.JUMPING_JACK, targetReps = 15, actualReps = 15, formScore = 100,
            goodSet = true, timestamp = Instant.parse("2024-01-01T10:05:00Z"), today = LocalDate.of(2024, 1, 1),
        )

        assertNull(db.sessionDao().getById(session.id)!!.accountId)
        assertNotNull(db.setDao().getForSession(session.id))
    }

    private suspend fun recordSetOn(sessionId: String, isoTimestamp: String) {
        // Fixed at UTC deliberately, not the test machine's default zone —
        // these timestamps sit at 10:00 UTC, close enough to a day
        // boundary in some zones to otherwise shift which calendar day the
        // Set (and its "today") land on, making the test's Streak
        // assertions flaky depending on where it runs.
        val zone = java.time.ZoneOffset.UTC
        val timestamp = Instant.parse(isoTimestamp)
        setRepository.recordSet(
            sessionId = sessionId, exercise = Exercise.SQUAT, targetReps = 10, actualReps = 10, formScore = 100,
            goodSet = true, timestamp = timestamp, today = timestamp.atZone(zone).toLocalDate(), zone = zone,
        )
    }

    private suspend fun seedRoutine(): String {
        val routine = RoutineEntity(id = "test-routine", name = "Test Routine")
        db.routineDao().insertRoutine(routine)
        return routine.id
    }
}
