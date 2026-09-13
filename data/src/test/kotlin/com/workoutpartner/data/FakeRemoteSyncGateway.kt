package com.workoutpartner.data

/**
 * Fake [RemoteSyncGateway] for tests — spec.md's Testing Decisions call for
 * "tests against an in-memory Room database and a fake remote." [online]
 * toggles whether a push succeeds, simulating connectivity; [pushedSets]/
 * [pushedTallies] record what actually reached the "server" so a test can
 * assert on it directly (e.g. that two devices' offline writes both arrive,
 * unmerged and uncollided). [pushedSetAccountIds]/[pushedTallyAccountIds]
 * record which `accountId` each push carried (ticket 14), so a test can
 * confirm ownership was resolved correctly, not just that a push happened.
 */
class FakeRemoteSyncGateway : RemoteSyncGateway {
    var online: Boolean = true
    val pushedSets = mutableListOf<SetEntity>()
    val pushedTallies = mutableListOf<TallyEntity>()
    val pushedSetAccountIds = mutableMapOf<String, String>()
    val pushedTallyAccountIds = mutableMapOf<String, String>()

    override suspend fun pushSet(set: SetEntity, accountId: String): Boolean {
        if (!online) return false
        pushedSets.add(set)
        pushedSetAccountIds[set.id] = accountId
        return true
    }

    override suspend fun pushTally(tally: TallyEntity, accountId: String): Boolean {
        if (!online) return false
        pushedTallies.add(tally)
        pushedTallyAccountIds[tally.id] = accountId
        return true
    }
}
