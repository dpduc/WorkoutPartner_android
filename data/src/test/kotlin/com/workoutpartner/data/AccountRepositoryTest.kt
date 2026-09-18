package com.workoutpartner.data

import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.streaks.StreakCalculator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountRepositoryTest {

    private val db = newInMemoryDatabase()
    private val accountRepository = AccountRepository(db)
    private val setRepository = SetRepository(db, accountRepository)

    @Test
    fun `recomputeStreak reflects the Account's own Set history`() = runTest {
        val routineId = seedRoutine()
        val account = accountRepository.createAccount("account-1", weeklyTarget = 3)
        val session = setRepository.startSession(account.id, routineId, Instant.parse("2024-01-01T10:00:00Z"))

        // Mon/Wed/Fri: meets the Weekly Target without tripping the
        // gap-safeguard (recordSet already recomputes after each write, so
        // this also exercises recomputeStreak being called repeatedly and
        // landing on the same correct answer each time).
        recordOn(session.id, "2024-01-01T10:00:00Z")
        recordOn(session.id, "2024-01-03T10:00:00Z")
        recordOn(session.id, "2024-01-05T10:00:00Z")

        val updated = accountRepository.getAccount(account.id)!!
        assertEquals(1, updated.currentStreak)
        assertEquals(1, updated.bankedShields)
    }

    @Test
    fun `claimGuestData re-owns unowned Sessions and the reclaimed history counts toward the new Account's Streak`() = runTest {
        val routineId = seedRoutine()

        // A Guest's Sessions/Sets, created before any Account exists.
        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(guestSession.id, "2024-01-01T10:00:00Z")
        recordOn(guestSession.id, "2024-01-03T10:00:00Z")
        recordOn(guestSession.id, "2024-01-05T10:00:00Z")

        // Signs up: an Account now exists, but hasn't claimed the Guest data yet.
        val account = accountRepository.createAccount("account-1")
        assertEquals(0, account.currentStreak)

        accountRepository.claimGuestData(account.id, today = LocalDate.of(2024, 1, 5), zone = ZoneOffset.UTC)

        assertEquals(account.id, db.sessionDao().getById(guestSession.id)!!.accountId)
        val migrated = accountRepository.getAccount(account.id)!!
        assertEquals(1, migrated.currentStreak)
        assertEquals(1, migrated.bankedShields)
    }

    @Test
    fun `claimGuestData also copies a saved GuestProfile onto the new Account and clears it`() = runTest {
        accountRepository.saveGuestProfile(
            name = "Alex", age = 29, heightCm = 175, weightKg = 70.0, activityLevel = ActivityLevel.LIGHTLY_ACTIVE,
        )
        val account = accountRepository.createAccount("account-1")

        accountRepository.claimGuestData(account.id, today = LocalDate.of(2024, 1, 5), zone = ZoneOffset.UTC)

        val migrated = accountRepository.getAccount(account.id)!!
        assertEquals("Alex", migrated.name)
        assertEquals(29, migrated.age)
        assertEquals(175, migrated.heightCm)
        assertEquals(70.0, migrated.weightKg)
        assertEquals(ActivityLevel.LIGHTLY_ACTIVE, migrated.activityLevel)
        assertNull(accountRepository.getGuestProfile())
    }

    @Test
    fun `claimGuestData with no saved GuestProfile leaves the new Account's profile fields null`() = runTest {
        val account = accountRepository.createAccount("account-1")

        accountRepository.claimGuestData(account.id, today = LocalDate.of(2024, 1, 5), zone = ZoneOffset.UTC)

        val migrated = accountRepository.getAccount(account.id)!!
        assertNull(migrated.name)
        assertNull(migrated.activityLevel)
    }

    @Test
    fun `recordSet also recomputes a Guest's cached Streak, not just an Account's`() = runTest {
        val routineId = seedRoutine()
        val session = setRepository.startSession(accountId = null, routineId, Instant.parse("2024-01-01T10:00:00Z"))

        recordOn(session.id, "2024-01-01T10:00:00Z")
        recordOn(session.id, "2024-01-03T10:00:00Z")
        recordOn(session.id, "2024-01-05T10:00:00Z")

        val guest = accountRepository.getGuestProfile()!!
        assertEquals(1, guest.currentStreak)
        assertEquals(1, guest.bankedShields)
    }

    @Test
    fun `updateWeeklyTarget for a Guest (null accountId) updates the GuestProfile row, not any Account`() = runTest {
        val account = accountRepository.createAccount("account-1")

        accountRepository.updateWeeklyTarget(accountId = null, weeklyTarget = 5)

        assertEquals(5, accountRepository.getGuestProfile()!!.weeklyTarget)
        assertEquals(StreakCalculator.DEFAULT_WEEKLY_TARGET, accountRepository.getAccount(account.id)!!.weeklyTarget)
    }

    @Test
    fun `updateNotificationsEnabled for a Guest updates the GuestProfile row`() = runTest {
        accountRepository.updateNotificationsEnabled(accountId = null, enabled = false)

        assertEquals(false, accountRepository.getGuestProfile()!!.notificationsEnabled)
    }

    @Test
    fun `saveGuestProfile does not stomp Streak state already accumulated on the Guest row`() = runTest {
        accountRepository.updateWeeklyTarget(accountId = null, weeklyTarget = 5)
        val routineId = seedRoutine()
        val session = setRepository.startSession(accountId = null, routineId, Instant.parse("2024-01-01T10:00:00Z"))
        recordOn(session.id, "2024-01-01T10:00:00Z")
        val streakBeforeProfileSave = accountRepository.getGuestProfile()!!.currentStreak

        accountRepository.saveGuestProfile(
            name = "Alex", age = 29, heightCm = 175, weightKg = 70.0, activityLevel = ActivityLevel.LIGHTLY_ACTIVE,
        )

        val guest = accountRepository.getGuestProfile()!!
        assertEquals("Alex", guest.name)
        assertEquals(5, guest.weeklyTarget)
        assertEquals(streakBeforeProfileSave, guest.currentStreak)
    }

    @Test
    fun `updateProfile persists all five fields`() = runTest {
        val account = accountRepository.createAccount("account-1")

        accountRepository.updateProfile(
            account.id, name = "Sam", age = 41, heightCm = 180, weightKg = 82.5, activityLevel = ActivityLevel.ACTIVE,
        )

        val updated = accountRepository.getAccount(account.id)!!
        assertEquals("Sam", updated.name)
        assertEquals(41, updated.age)
        assertEquals(180, updated.heightCm)
        assertEquals(82.5, updated.weightKg)
        assertEquals(ActivityLevel.ACTIVE, updated.activityLevel)
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
