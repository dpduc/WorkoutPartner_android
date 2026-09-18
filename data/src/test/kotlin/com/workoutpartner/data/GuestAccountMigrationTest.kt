package com.workoutpartner.data

import com.workoutpartner.core.repcounting.Exercise
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Guest -> Account migration scenario, integration-style: a real
 * [AuthRepository] wired to a real [AccountRepository] (not a test-double
 * hook — `workout-partner-v3` ticket 05 made [AuthRepository.signUp]
 * call [AccountRepository.claimGuestData] directly, so there's no separate
 * migration object to inject anymore), together with [SyncEngine] and a
 * [FakeRemoteSyncGateway]. Narrower `AuthRepository`-only contract tests
 * (pending/Merge/Discard/Cancel/a failing claim) live in [AuthRepositoryTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuestAccountMigrationTest {

    private val db = newInMemoryDatabase()
    private val accountRepository = AccountRepository(db)
    private val setRepository = SetRepository(db, accountRepository)
    private val clock = Clock.fixed(Instant.parse("2024-01-05T12:00:00Z"), ZoneOffset.UTC)
    private val authGateway = FakeAuthGateway()
    private val authRepository = AuthRepository(authGateway, accountRepository, clock)
    private val remote = FakeRemoteSyncGateway()
    private val syncEngine = SyncEngine(db.pendingSyncDao(), db.setDao(), db.tallyDao(), db.sessionDao(), db.trackedProfileDao(), remote)

    @Test
    fun `signing up as a Guest with local history migrates it, leaving no unclaimed data behind`() = runTest {
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")
        recordOn(guestSession.id, "2024-01-03T10:00:00Z")
        recordOn(guestSession.id, "2024-01-05T10:00:00Z")

        val accountId = authRepository.signUp("new@example.com", "hunter2")

        assertEquals(accountId, db.sessionDao().getById(guestSession.id)!!.accountId)
        assertFalse(accountRepository.hasUnclaimedGuestData())
        val account = accountRepository.getAccount(accountId)!!
        assertEquals(1, account.currentStreak)
        assertEquals(1, account.bankedShields)
    }

    @Test
    fun `the Guest's pre-signup Sets still sync to the remote after migration re-owns them`() = runTest {
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        val set = recordOn(guestSession.id, "2024-01-01T10:00:00Z")

        authRepository.signUp("new@example.com", "hunter2")
        val result = syncEngine.syncPendingChanges()

        assertEquals(SyncResult(succeeded = 1, remaining = 0), result)
        assertEquals(listOf(set.id), remote.pushedSets.map { it.id })
    }

    @Test
    fun `signing up with no Guest data performs no migration`() = runTest {
        val accountId = authRepository.signUp("new@example.com", "hunter2")

        val account = accountRepository.getAccount(accountId)!!
        assertEquals(0, account.currentStreak)
        assertEquals(0, account.bankedShields)
    }

    private suspend fun recordOn(sessionId: String, isoTimestamp: String): SetEntity {
        val zone = ZoneOffset.UTC
        val timestamp = Instant.parse(isoTimestamp)
        return setRepository.recordSet(
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
