package com.workoutpartner.data

import androidx.room.withTransaction
import com.workoutpartner.core.repcounting.Exercise
import java.time.Clock
import java.time.Instant

/**
 * `TallyRepository` (spec.md's Seam 3 module list): Quick Count's Tally
 * persistence. [recordTally] writes to Room and enqueues the Tally for
 * [SyncEngine], the same offline-write-then-sync shape as
 * [SetRepository.recordSet] — but never touches an Account's Streak, since
 * Quick Count activity deliberately doesn't count toward the Account's own
 * Streak/Active Days (spec.md user story 41; CONTEXT.md's Tally
 * definition).
 *
 * [formScore]/[durationSeconds] (`workout-partner-v2` ticket 03) default to
 * `null` rather than being required: every *current* caller
 * ([com.workoutpartner.app.quickcount.QuickCountViewModel]) always supplies
 * real values now, but this repository itself doesn't need to assume that
 * forever — `null` is the honest answer for a Tally this repository can't
 * actually compute those for.
 */
class TallyRepository(
    private val database: WorkoutPartnerDatabase,
    private val tallyDao: TallyDao = database.tallyDao(),
    private val pendingSyncDao: PendingSyncDao = database.pendingSyncDao(),
    private val idGenerator: () -> String = ::newEntityId,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun recordTally(
        trackedProfileId: String,
        exercise: Exercise,
        repsAchieved: Int,
        target: Int?,
        timestamp: Instant,
        formScore: Int? = null,
        durationSeconds: Int? = null,
    ): TallyEntity {
        val tally = TallyEntity(
            id = idGenerator(),
            trackedProfileId = trackedProfileId,
            exercise = exercise,
            repsAchieved = repsAchieved,
            target = target,
            timestamp = timestamp,
            formScore = formScore,
            durationSeconds = durationSeconds,
        )

        database.withTransaction {
            tallyDao.insert(tally)
            pendingSyncDao.insert(PendingSyncEntity(entityKind = SyncEntityKind.TALLY, entityId = tally.id, enqueuedAt = clock.instant()))
        }

        return tally
    }

    suspend fun getTalliesForTrackedProfile(trackedProfileId: String): List<TallyEntity> =
        tallyDao.getForTrackedProfile(trackedProfileId)
}
