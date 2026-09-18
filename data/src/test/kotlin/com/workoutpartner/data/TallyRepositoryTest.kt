package com.workoutpartner.data

import com.workoutpartner.core.repcounting.Exercise
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TallyRepositoryTest {

    private val db = newInMemoryDatabase()
    private val tallyRepository = TallyRepository(db)

    @Test
    fun `recordTally writes the Tally to Room and enqueues it for sync`() = runTest {
        val profileId = seedTrackedProfile()

        val tally = tallyRepository.recordTally(
            trackedProfileId = profileId, exercise = Exercise.SIT_UP, repsAchieved = 12, target = 15,
            timestamp = Instant.parse("2024-01-01T10:00:00Z"),
        )

        assertEquals(tally, db.tallyDao().getById(tally.id))
        val queued = db.pendingSyncDao().getAllOrdered()
        assertEquals(1, queued.size)
        assertEquals(SyncEntityKind.TALLY, queued.single().entityKind)
        assertEquals(tally.id, queued.single().entityId)
    }

    @Test
    fun `recordTally persists formScore and durationSeconds when supplied`() = runTest {
        val profileId = seedTrackedProfile()

        val tally = tallyRepository.recordTally(
            trackedProfileId = profileId, exercise = Exercise.SQUAT, repsAchieved = 10, target = null,
            timestamp = Instant.parse("2024-01-01T10:00:00Z"), formScore = 75, durationSeconds = 42,
        )

        val stored = db.tallyDao().getById(tally.id)!!
        assertEquals(75, stored.formScore)
        assertEquals(42, stored.durationSeconds)
    }

    @Test
    fun `recordTally defaults formScore and durationSeconds to null when not supplied`() = runTest {
        val profileId = seedTrackedProfile()

        val tally = tallyRepository.recordTally(
            trackedProfileId = profileId, exercise = Exercise.SQUAT, repsAchieved = 10, target = null,
            timestamp = Instant.parse("2024-01-01T10:00:00Z"),
        )

        val stored = db.tallyDao().getById(tally.id)!!
        assertNull(stored.formScore)
        assertNull(stored.durationSeconds)
    }

    @Test
    fun `a Tally with no target records target as null, not zero`() = runTest {
        val profileId = seedTrackedProfile()

        val tally = tallyRepository.recordTally(
            trackedProfileId = profileId, exercise = Exercise.LUNGE, repsAchieved = 7, target = null,
            timestamp = Instant.parse("2024-01-01T10:00:00Z"),
        )

        assertNull(db.tallyDao().getById(tally.id)!!.target)
    }

    @Test
    fun `Tallies for a Tracked Profile come back most recent first`() = runTest {
        val profileId = seedTrackedProfile()
        val older = tallyRepository.recordTally(profileId, Exercise.SQUAT, 10, null, Instant.parse("2024-01-01T10:00:00Z"))
        val newer = tallyRepository.recordTally(profileId, Exercise.SQUAT, 12, null, Instant.parse("2024-01-02T10:00:00Z"))

        val history = tallyRepository.getTalliesForTrackedProfile(profileId)

        assertEquals(listOf(newer.id, older.id), history.map { it.id })
    }

    @Test
    fun `recordTally works for a Tracked Profile belonging to a Guest (no Account)`() = runTest {
        val profileId = seedTrackedProfile(accountId = null)

        val tally = tallyRepository.recordTally(
            trackedProfileId = profileId, exercise = Exercise.SQUAT, repsAchieved = 8, target = 10,
            timestamp = Instant.parse("2024-01-01T10:00:00Z"),
        )

        assertEquals(tally, db.tallyDao().getById(tally.id))
        assertEquals(listOf(tally.id), tallyRepository.getTalliesForTrackedProfile(profileId).map { it.id })
    }

    private suspend fun seedTrackedProfile(accountId: String? = "account-1"): String {
        if (accountId != null) {
            db.accountDao().insert(AccountEntity(id = accountId))
        }
        val profile = TrackedProfileEntity(id = "profile-1", accountId = accountId, displayName = "Alex")
        db.trackedProfileDao().insert(profile)
        return profile.id
    }
}
