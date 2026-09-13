package com.workoutpartner.data

import androidx.room.withTransaction
import com.workoutpartner.core.repcounting.Exercise
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * `SetRepository` (spec.md's Seam 3 module list): owns both [SessionEntity]
 * and [SetEntity] persistence — the ticket-level module list doesn't name a
 * separate "SessionRepository," and a Session has no independent meaning
 * apart from the Sets performed within it (CONTEXT.md), so its lifecycle
 * lives here alongside them.
 *
 * [recordSet] does three things atomically: writes the [SetEntity] to Room
 * (offline-first, ADR-0002), enqueues it for [SyncEngine] to push to
 * Firestore once online, and — for a signed-in Account's Session, not a
 * Guest's — refreshes that Account's cached Streak via
 * [AccountRepository.recomputeStreak], since a new Set may have just logged
 * a new Active Day.
 *
 * That atomicity requires [accountRepository] to have been constructed from
 * this same [database] instance — [database.withTransaction] only covers
 * DAO calls Room recognizes as running against it, and `accountRepository`'s
 * own DAOs are whichever instance it was given at construction, not
 * necessarily this one. Callers wiring these repositories together (there's
 * no DI framework in this project to enforce it) must pass the same
 * `WorkoutPartnerDatabase` to both.
 */
class SetRepository(
    private val database: WorkoutPartnerDatabase,
    private val accountRepository: AccountRepository,
    private val sessionDao: SessionDao = database.sessionDao(),
    private val setDao: SetDao = database.setDao(),
    private val pendingSyncDao: PendingSyncDao = database.pendingSyncDao(),
    private val idGenerator: () -> String = ::newEntityId,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun startSession(accountId: String?, routineId: String, timestamp: Instant): SessionEntity {
        val session = SessionEntity(id = idGenerator(), accountId = accountId, routineId = routineId, timestamp = timestamp)
        sessionDao.insert(session)
        return session
    }

    suspend fun recordSet(
        sessionId: String,
        exercise: Exercise,
        targetReps: Int,
        actualReps: Int,
        formScore: Int,
        goodSet: Boolean,
        timestamp: Instant,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SetEntity {
        val set = SetEntity(
            id = idGenerator(),
            sessionId = sessionId,
            exercise = exercise,
            targetReps = targetReps,
            actualReps = actualReps,
            formScore = formScore,
            goodSet = goodSet,
            timestamp = timestamp,
        )

        database.withTransaction {
            setDao.insert(set)
            pendingSyncDao.insert(PendingSyncEntity(entityKind = SyncEntityKind.SET, entityId = set.id, enqueuedAt = clock.instant()))

            val accountId = sessionDao.getById(sessionId)?.accountId
            if (accountId != null) {
                accountRepository.recomputeStreak(accountId, today, zone)
            }
        }

        return set
    }

    suspend fun getSetsForSession(sessionId: String): List<SetEntity> = setDao.getForSession(sessionId)

    suspend fun getSetsForAccount(accountId: String): List<SetEntity> = setDao.getForAccount(accountId)
}
