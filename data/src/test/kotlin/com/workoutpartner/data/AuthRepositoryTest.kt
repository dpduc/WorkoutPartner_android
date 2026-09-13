package com.workoutpartner.data

import com.workoutpartner.core.repcounting.Exercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryTest {

    private val db = newInMemoryDatabase()
    private val accountRepository = AccountRepository(db)
    private val setRepository = SetRepository(db, accountRepository)
    private val authGateway = FakeAuthGateway()
    private val authRepository = AuthRepository(authGateway, accountRepository)

    @Test
    fun `starts as Guest, with no Firebase identity involved`() = runTest {
        assertEquals(AuthState.Guest, authRepository.authState.first())
    }

    @Test
    fun `signing up creates a local Account and moves authState to SignedIn`() = runTest {
        val accountId = authRepository.signUp("new@example.com", "hunter2")

        assertEquals(AuthState.SignedIn(accountId), authRepository.authState.first())
        assertNotNull(accountRepository.getAccount(accountId))
    }

    @Test
    fun `signing up with existing Guest data triggers the migration callback with that Account's id`() = runTest {
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")

        var triggeredFor: String? = null
        val repository = AuthRepository(authGateway, accountRepository, onGuestDataToMigrate = { triggeredFor = it })

        val accountId = repository.signUp("new@example.com", "hunter2")

        assertEquals(accountId, triggeredFor)
    }

    @Test
    fun `signing up with no Guest data on this device never calls the migration callback`() = runTest {
        var callbackInvoked = false
        val repository = AuthRepository(authGateway, accountRepository, onGuestDataToMigrate = { callbackInvoked = true })

        repository.signUp("new@example.com", "hunter2")

        assertEquals(false, callbackInvoked)
    }

    @Test
    fun `the default migration callback is a no-op — this ticket triggers, it does not migrate`() = runTest {
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")

        authRepository.signUp("new@example.com", "hunter2")

        // Nothing actually re-owned the Guest Session — that's ticket 08's
        // job, via whatever real onGuestDataToMigrate it supplies.
        assertNull(db.sessionDao().getById(guestSession.id)!!.accountId)
    }

    @Test
    fun `signing in returns the existing Account's id without fabricating local state`() = runTest {
        val existingAccountId = "already-registered-account"
        authGateway.registerExistingAccount(existingAccountId, "returning@example.com")
        // No accountRepository.createAccount call here — simulates this
        // device never having seen this Account before (ticket 06's known
        // no-pull-sync gap; "synced history should become visible" stays
        // unmet, which this ticket's own text acknowledges depending on).

        val signedInId = authRepository.signIn("returning@example.com", "hunter2")

        assertEquals(existingAccountId, signedInId)
        assertNull(accountRepository.getAccount(existingAccountId))
    }

    @Test
    fun `signing out returns to Guest`() = runTest {
        authRepository.signUp("new@example.com", "hunter2")

        authRepository.signOut()

        assertEquals(AuthState.Guest, authRepository.authState.first())
    }

    private suspend fun recordOn(sessionId: String, isoTimestamp: String) {
        val zone = ZoneOffset.UTC
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
