package com.workoutpartner.data

import com.workoutpartner.core.repcounting.Exercise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Per spec.md's Testing Decisions: "the Guest → Account migration
 * transaction is tested against an in-memory Room database and a fake
 * remote, alongside ticket 06's sync tests" — these exercise the real
 * [GuestAccountMigration] wired into [AuthRepository] (not a test-double
 * lambda, unlike ticket 07's own `AuthRepositoryTest`), together with
 * [SyncEngine] and a [FakeRemoteSyncGateway].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuestAccountMigrationTest {

    private val db = newInMemoryDatabase()
    private val accountRepository = AccountRepository(db)
    private val setRepository = SetRepository(db, accountRepository)
    private val clock = Clock.fixed(Instant.parse("2024-01-05T12:00:00Z"), ZoneOffset.UTC)
    private val migration = GuestAccountMigration(accountRepository, clock)
    private val authGateway = FakeAuthGateway()
    private val authRepository = AuthRepository(authGateway, accountRepository, onGuestDataToMigrate = migration::invoke)
    private val remote = FakeRemoteSyncGateway()
    private val syncEngine = SyncEngine(db.pendingSyncDao(), db.setDao(), db.tallyDao(), remote)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `signUp suspends until migration actually completes — not a background job with a visible in-progress window`() = runTest {
        // A plain post-condition check after signUp returns wouldn't tell
        // "properly awaited" apart from "launched as a background job that
        // happened to finish before the assertions ran" — runTest's
        // scheduler drains both the same way. A gate that only releases on
        // command is what actually distinguishes them: if signUp returned
        // early, the assertions below would run before the gate opens.
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")

        val migrationGate = CompletableDeferred<Unit>()
        var migrationCompleted = false
        val gatedRepository = AuthRepository(
            FakeAuthGateway(),
            accountRepository,
            onGuestDataToMigrate = { accountId ->
                migrationGate.await()
                accountRepository.claimGuestData(accountId, LocalDate.of(2024, 1, 1), ZoneOffset.UTC)
                migrationCompleted = true
            },
        )

        val signUpJob = launch { gatedRepository.signUp("gated@example.com", "hunter2") }
        runCurrent() // let signUp run up to the point it's suspended on the gate, no further

        assertFalse("migration must not have completed yet", migrationCompleted)
        assertFalse("signUp must not have returned yet", signUpJob.isCompleted)
        assertNull("no partial re-ownership while migration is still pending", db.sessionDao().getById(guestSession.id)!!.accountId)

        migrationGate.complete(Unit)
        signUpJob.join()

        assertTrue(migrationCompleted)
        assertNotNull(db.sessionDao().getById(guestSession.id)!!.accountId)
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
