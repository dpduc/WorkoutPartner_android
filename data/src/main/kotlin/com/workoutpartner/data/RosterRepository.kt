package com.workoutpartner.data

/**
 * `RosterRepository` (spec.md's Seam 3 module list): an Account's Tracked
 * Profiles. Local Room CRUD only — like [AccountEntity], [TrackedProfileEntity]
 * isn't in this ticket's "Sets and Tallies" sync-queue scope (see
 * [SyncEngine]'s doc comment), so Roster Firestore mirroring isn't built
 * here either. Takes [WorkoutPartnerDatabase] rather than a bare DAO for the
 * same reason [SetRepository]/[TallyRepository]/[AccountRepository] do —
 * one consistent construction shape across this ticket's repositories, even
 * though this one never needs `withTransaction` itself.
 */
class RosterRepository(
    database: WorkoutPartnerDatabase,
    private val trackedProfileDao: TrackedProfileDao = database.trackedProfileDao(),
    private val idGenerator: () -> String = ::newEntityId,
) {
    suspend fun createTrackedProfile(accountId: String, displayName: String): TrackedProfileEntity {
        val profile = TrackedProfileEntity(id = idGenerator(), accountId = accountId, displayName = displayName)
        trackedProfileDao.insert(profile)
        return profile
    }

    suspend fun deleteTrackedProfile(profile: TrackedProfileEntity) = trackedProfileDao.delete(profile)

    suspend fun getRoster(accountId: String): List<TrackedProfileEntity> = trackedProfileDao.getForAccount(accountId)
}
