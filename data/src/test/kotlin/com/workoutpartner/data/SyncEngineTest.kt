package com.workoutpartner.data

import com.workoutpartner.core.repcounting.Exercise
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * Ticket 06's Testing section: "offline write → queue → sync-on-reconnect,"
 * the additive two-device reconciliation ticket 06 documents as falling out
 * of client-generated ids, and ticket 14's "no Firestore presence for a
 * Guest until migration" (a Set only syncs once its owning Session has a
 * real `accountId`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {

    @Test
    fun `queued Sets and Tallies sync once online, oldest first`() = runTest {
        val db = newInMemoryDatabase()
        val remote = FakeRemoteSyncGateway()
        val routineId = seedRoutine(db)
        val accountId = seedAccount(db)
        val setRepository = SetRepository(db, AccountRepository(db))
        val tallyRepository = TallyRepository(db)
        val trackedProfileId = seedTrackedProfile(db, accountId)

        val session = setRepository.startSession(accountId = accountId, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        val set = setRepository.recordSet(
            sessionId = session.id, exercise = Exercise.SQUAT, targetReps = 10, actualReps = 10, formScore = 100,
            goodSet = true, timestamp = Instant.parse("2024-01-01T10:05:00Z"), today = LocalDate.of(2024, 1, 1),
        )
        val tally = tallyRepository.recordTally(
            trackedProfileId = trackedProfileId, exercise = Exercise.PUSH_UP, repsAchieved = 5, target = null,
            timestamp = Instant.parse("2024-01-01T10:06:00Z"),
        )

        val result = syncEngineFor(db, remote).syncPendingChanges()

        assertEquals(SyncResult(succeeded = 2, remaining = 0), result)
        assertEquals(listOf(set.id), remote.pushedSets.map { it.id })
        assertEquals(listOf(tally.id), remote.pushedTallies.map { it.id })
        assertEquals(accountId, remote.pushedSetAccountIds[set.id])
        assertEquals(accountId, remote.pushedTallyAccountIds[tally.id])
        assertEquals(0, db.pendingSyncDao().count())
    }

    @Test
    fun `a queued item stays queued while offline, and syncs once back online`() = runTest {
        val db = newInMemoryDatabase()
        val remote = FakeRemoteSyncGateway().apply { online = false }
        val routineId = seedRoutine(db)
        val accountId = seedAccount(db)
        val setRepository = SetRepository(db, AccountRepository(db))

        val session = setRepository.startSession(accountId = accountId, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        setRepository.recordSet(
            sessionId = session.id, exercise = Exercise.LUNGE, targetReps = 8, actualReps = 8, formScore = 90,
            goodSet = true, timestamp = Instant.parse("2024-01-01T10:05:00Z"), today = LocalDate.of(2024, 1, 1),
        )
        val engine = syncEngineFor(db, remote)

        val whileOffline = engine.syncPendingChanges()
        assertEquals(SyncResult(succeeded = 0, remaining = 1), whileOffline)
        assertTrue(remote.pushedSets.isEmpty())

        remote.online = true
        val onceOnline = engine.syncPendingChanges()
        assertEquals(SyncResult(succeeded = 1, remaining = 0), onceOnline)
        assertEquals(1, remote.pushedSets.size)
    }

    @Test
    fun `a Guest's Set stays queued — not pushed — until its Session is claimed by an Account`() = runTest {
        val db = newInMemoryDatabase()
        val remote = FakeRemoteSyncGateway()
        val routineId = seedRoutine(db)
        val accountRepository = AccountRepository(db)
        val setRepository = SetRepository(db, accountRepository)

        val guestSession = setRepository.startSession(accountId = null, routineId = routineId, timestamp = Instant.parse("2024-01-01T10:00:00Z"))
        val set = setRepository.recordSet(
            sessionId = guestSession.id, exercise = Exercise.SQUAT, targetReps = 10, actualReps = 10, formScore = 100,
            goodSet = true, timestamp = Instant.parse("2024-01-01T10:05:00Z"), today = LocalDate.of(2024, 1, 1),
        )
        val engine = syncEngineFor(db, remote)

        // Still a Guest: not synced, but not treated as a failure either —
        // the rest of the queue (there is none here, but in general) would
        // still be attempted.
        val whileGuest = engine.syncPendingChanges()
        assertEquals(SyncResult(succeeded = 0, remaining = 1), whileGuest)
        assertTrue(remote.pushedSets.isEmpty())

        val accountId = accountRepository.createAccount("account-1").id
        accountRepository.claimGuestData(accountId, today = LocalDate.of(2024, 1, 1))

        // The same queued entry now resolves a real owner and pushes.
        val afterMigration = engine.syncPendingChanges()
        assertEquals(SyncResult(succeeded = 1, remaining = 0), afterMigration)
        assertEquals(listOf(set.id), remote.pushedSets.map { it.id })
        assertEquals(accountId, remote.pushedSetAccountIds[set.id])
    }

    @Test
    fun `two devices' offline-created Sets reconcile additively, not by overwrite`() = runTest {
        val sharedRemote = FakeRemoteSyncGateway()

        val deviceA = newInMemoryDatabase()
        val deviceB = newInMemoryDatabase()
        val routineIdA = seedRoutine(deviceA)
        val routineIdB = seedRoutine(deviceB)
        val accountIdA = seedAccount(deviceA, id = "account-a")
        val accountIdB = seedAccount(deviceB, id = "account-b")

        val repoA = SetRepository(deviceA, AccountRepository(deviceA))
        val repoB = SetRepository(deviceB, AccountRepository(deviceB))

        val sessionA = repoA.startSession(accountId = accountIdA, routineId = routineIdA, timestamp = Instant.parse("2024-01-01T09:00:00Z"))
        val sessionB = repoB.startSession(accountId = accountIdB, routineId = routineIdB, timestamp = Instant.parse("2024-01-01T09:00:00Z"))

        val setA = repoA.recordSet(
            sessionId = sessionA.id, exercise = Exercise.SIT_UP, targetReps = 20, actualReps = 20, formScore = 100,
            goodSet = true, timestamp = Instant.parse("2024-01-01T09:05:00Z"), today = LocalDate.of(2024, 1, 1),
        )
        val setB = repoB.recordSet(
            sessionId = sessionB.id, exercise = Exercise.SIT_UP, targetReps = 20, actualReps = 20, formScore = 100,
            goodSet = true, timestamp = Instant.parse("2024-01-01T09:05:00Z"), today = LocalDate.of(2024, 1, 1),
        )

        // Neither device has seen the other's Set — this is the "logged
        // offline on two devices before either synced" scenario (spec.md
        // user story 43).
        assertTrue(setA.id != setB.id)

        syncEngineFor(deviceA, sharedRemote).syncPendingChanges()
        syncEngineFor(deviceB, sharedRemote).syncPendingChanges()

        // Both arrive as distinct documents — nothing was silently lost or
        // clobbered by the second device's sync.
        assertEquals(setOf(setA.id, setB.id), sharedRemote.pushedSets.map { it.id }.toSet())
        assertEquals(2, sharedRemote.pushedSets.size)
    }

    private fun syncEngineFor(db: WorkoutPartnerDatabase, remote: FakeRemoteSyncGateway) =
        SyncEngine(db.pendingSyncDao(), db.setDao(), db.tallyDao(), db.sessionDao(), db.trackedProfileDao(), remote)

    private suspend fun seedRoutine(db: WorkoutPartnerDatabase): String {
        val routine = RoutineEntity(id = "test-routine", name = "Test Routine")
        db.routineDao().insertRoutine(routine)
        return routine.id
    }

    private suspend fun seedAccount(db: WorkoutPartnerDatabase, id: String = "account-1"): String {
        db.accountDao().insert(AccountEntity(id = id))
        return id
    }

    private suspend fun seedTrackedProfile(db: WorkoutPartnerDatabase, accountId: String): String {
        val profile = TrackedProfileEntity(id = "profile-1", accountId = accountId, displayName = "Alex")
        db.trackedProfileDao().insert(profile)
        return profile.id
    }
}
