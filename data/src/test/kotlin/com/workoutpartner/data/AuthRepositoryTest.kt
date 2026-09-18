package com.workoutpartner.data

import com.workoutpartner.core.repcounting.Exercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * `AuthRepository`'s own contract (`workout-partner-v3` ticket 05): the
 * single seam for every Guest-data outcome on sign-up and sign-in.
 * [GuestAccountMigrationTest] covers the wider, `SyncEngine`-integrated
 * scenario; these tests exercise `AuthRepository`/`AccountRepository`
 * directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryTest {

    private val db = newInMemoryDatabase()
    private val accountRepository = AccountRepository(db)
    private val setRepository = SetRepository(db, accountRepository)
    private val rosterRepository = RosterRepository(db)
    private val tallyRepository = TallyRepository(db)
    private val authGateway = FakeAuthGateway()
    private val clock = Clock.fixed(Instant.parse("2024-01-05T12:00:00Z"), ZoneOffset.UTC)
    private val authRepository = AuthRepository(authGateway, accountRepository, clock)

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
    fun `signing up claims a Guest Session directly, without an external migration hook`() = runTest {
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")

        val accountId = authRepository.signUp("new@example.com", "hunter2")

        assertEquals(accountId, db.sessionDao().getById(guestSession.id)!!.accountId)
        assertFalse(accountRepository.hasUnclaimedGuestData())
    }

    @Test
    fun `signing up claims a Quick-Count-only Guest's Roster and Tallies, with no Session ever started`() = runTest {
        val profile = rosterRepository.createTrackedProfile(accountId = null, displayName = "Alex")
        val tally = tallyRepository.recordTally(
            trackedProfileId = profile.id, exercise = Exercise.SQUAT, repsAchieved = 15, target = null,
            timestamp = Instant.parse("2024-01-01T10:00:00Z"),
        )
        assertTrue(accountRepository.hasUnclaimedGuestData())

        val accountId = authRepository.signUp("new@example.com", "hunter2")

        assertEquals(accountId, db.trackedProfileDao().getById(profile.id)!!.accountId)
        assertNotNull(db.tallyDao().getById(tally.id))
        assertFalse(accountRepository.hasUnclaimedGuestData())
    }

    @Test
    fun `signing up with a saved GuestProfile claims its body-stats and Weekly Target onto the new Account`() = runTest {
        accountRepository.saveGuestProfile(
            name = "Alex", age = 29, heightCm = 175, weightKg = 70.0, activityLevel = ActivityLevel.LIGHTLY_ACTIVE,
        )
        accountRepository.updateWeeklyTarget(accountId = null, weeklyTarget = 5)

        val accountId = authRepository.signUp("new@example.com", "hunter2")

        val account = accountRepository.getAccount(accountId)!!
        assertEquals("Alex", account.name)
        assertEquals(ActivityLevel.LIGHTLY_ACTIVE, account.activityLevel)
        assertEquals(5, account.weeklyTarget)
        assertNull(accountRepository.getGuestProfile())
    }

    @Test
    fun `signing up with no Guest data performs no claim`() = runTest {
        val accountId = authRepository.signUp("new@example.com", "hunter2")

        val account = accountRepository.getAccount(accountId)!!
        assertEquals(0, account.currentStreak)
        assertEquals(0, account.bankedShields)
    }

    @Test
    fun `signing in with no unclaimed Guest data returns SignedIn`() = runTest {
        val existingAccountId = "already-registered-account"
        authGateway.registerExistingAccount(existingAccountId, "returning@example.com")

        val result = authRepository.signIn("returning@example.com", "hunter2")

        assertEquals(SignInResult.SignedIn(existingAccountId), result)
    }

    @Test
    fun `signing in with unclaimed Guest data returns GuestDataPending with correct counts, and claims nothing`() = runTest {
        val existingAccountId = "already-registered-account"
        authGateway.registerExistingAccount(existingAccountId, "returning@example.com")
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")
        val profile = rosterRepository.createTrackedProfile(accountId = null, displayName = "Alex")
        tallyRepository.recordTally(
            trackedProfileId = profile.id, exercise = Exercise.SQUAT, repsAchieved = 15, target = null,
            timestamp = Instant.parse("2024-01-01T10:00:00Z"),
        )

        val result = authRepository.signIn("returning@example.com", "hunter2")

        assertEquals(SignInResult.GuestDataPending(existingAccountId, GuestDataSummary(sessionCount = 1, trackedProfileCount = 1, tallyCount = 1)), result)
        assertNull("sign-in must never claim on its own", db.sessionDao().getById(guestSession.id)!!.accountId)
    }

    @Test
    fun `signing in with Google returns the same pending-or-not result as email sign-in, and never auto-claims`() = runTest {
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")

        val result = authRepository.signInWithGoogle("some-id-token")

        assertTrue(result is SignInResult.GuestDataPending)
        assertNull("Google sign-in must never auto-claim (the ticket 01 bug this ticket's own contract replaces)", db.sessionDao().getById(guestSession.id)!!.accountId)
    }

    @Test
    fun `resolving with Merge keeps the Account's Weekly Target and body-stats over the Guest's`() = runTest {
        val accountId = authRepository.signUp("existing@example.com", "hunter2")
        accountRepository.updateProfile(accountId, name = "Sam", age = 41, heightCm = 180, weightKg = 82.5, activityLevel = ActivityLevel.ACTIVE)
        accountRepository.updateWeeklyTarget(accountId, weeklyTarget = 6)
        authRepository.signOut()

        accountRepository.saveGuestProfile(name = "Alex", age = 29, heightCm = 175, weightKg = 70.0, activityLevel = ActivityLevel.LIGHTLY_ACTIVE)
        accountRepository.updateWeeklyTarget(accountId = null, weeklyTarget = 3)

        authRepository.resolvePendingGuestData(accountId, GuestDataResolution.MERGE)

        val merged = accountRepository.getAccount(accountId)!!
        assertEquals("Sam", merged.name) // Account's own body-stats win
        assertEquals(6, merged.weeklyTarget) // Account's own Weekly Target wins, never the Guest's
        assertNull(accountRepository.getGuestProfile())
    }

    @Test
    fun `resolving with Merge claims the Guest's Sessions and recomputes Streak across the merged history`() = runTest {
        val accountId = authRepository.signUp("existing@example.com", "hunter2")
        authRepository.signOut()

        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")
        recordOn(guestSession.id, "2024-01-03T10:00:00Z")
        recordOn(guestSession.id, "2024-01-05T10:00:00Z")

        authRepository.resolvePendingGuestData(accountId, GuestDataResolution.MERGE)

        assertEquals(accountId, db.sessionDao().getById(guestSession.id)!!.accountId)
        val merged = accountRepository.getAccount(accountId)!!
        // Default Weekly Target (3): Jan 1/3/5 2024 (Mon/Wed/Fri, same week) meet it by "today" (clock's Jan 5).
        assertEquals(1, merged.currentStreak)
        assertEquals(1, merged.bankedShields)
    }

    @Test
    fun `resolving with Merge fills body-stats the Account doesn't already have`() = runTest {
        val accountId = authRepository.signUp("existing@example.com", "hunter2")
        authRepository.signOut()
        accountRepository.saveGuestProfile(name = "Alex", age = 29, heightCm = 175, weightKg = 70.0, activityLevel = ActivityLevel.LIGHTLY_ACTIVE)

        authRepository.resolvePendingGuestData(accountId, GuestDataResolution.MERGE)

        val merged = accountRepository.getAccount(accountId)!!
        assertEquals("Alex", merged.name)
        assertEquals(ActivityLevel.LIGHTLY_ACTIVE, merged.activityLevel)
    }

    @Test
    fun `resolving with Discard removes every Guest row and their queued syncs`() = runTest {
        val accountId = authRepository.signUp("existing@example.com", "hunter2")
        authRepository.signOut()

        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        val set = recordOn(guestSession.id, "2024-01-01T10:00:00Z")
        val profile = rosterRepository.createTrackedProfile(accountId = null, displayName = "Alex")
        val tally = tallyRepository.recordTally(
            trackedProfileId = profile.id, exercise = Exercise.SQUAT, repsAchieved = 15, target = null,
            timestamp = Instant.parse("2024-01-01T10:00:00Z"),
        )
        accountRepository.saveGuestProfile(name = "Alex", age = 29, heightCm = 175, weightKg = 70.0, activityLevel = ActivityLevel.LIGHTLY_ACTIVE)
        assertEquals(2, db.pendingSyncDao().count()) // the Set and the Tally, both enqueued at write time

        authRepository.resolvePendingGuestData(accountId, GuestDataResolution.DISCARD)

        assertNull(db.sessionDao().getById(guestSession.id))
        assertNull(db.setDao().getById(set.id))
        assertNull(db.trackedProfileDao().getById(profile.id))
        assertNull(db.tallyDao().getById(tally.id))
        assertNull(accountRepository.getGuestProfile())
        assertEquals(0, db.pendingSyncDao().count())
    }

    @Test
    fun `backing out of the prompt (Cancel) signs out and leaves Guest data untouched`() = runTest {
        authRepository.signUp("existing@example.com", "hunter2")
        authRepository.signOut()
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")

        // Cancel is just signOut — resolvePendingGuestData is never called.
        authRepository.signOut()

        assertEquals(AuthState.Guest, authRepository.authState.first())
        assertNull(db.sessionDao().getById(guestSession.id)!!.accountId)
        assertTrue(accountRepository.hasUnclaimedGuestData())
    }

    @Test
    fun `a failing claim leaves Guest data unclaimed and intact`() = runTest {
        val routineId = seedRoutine()
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")

        try {
            // No Account row exists for this id — claimUnowned's FK to
            // accounts(id) fails, rolling back the whole transaction.
            accountRepository.claimGuestData("nonexistent-account", LocalDate.of(2024, 1, 5), ZoneOffset.UTC)
            fail("expected the FK-constrained claim to throw")
        } catch (e: Exception) {
            // expected
        }

        assertNull("no partial re-ownership after a failed claim", db.sessionDao().getById(guestSession.id)!!.accountId)
        assertTrue(accountRepository.hasUnclaimedGuestData())
    }

    @Test
    fun `signing out returns to Guest`() = runTest {
        authRepository.signUp("new@example.com", "hunter2")

        authRepository.signOut()

        assertEquals(AuthState.Guest, authRepository.authState.first())
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
