package com.workoutpartner.data

/**
 * Fake [RemoteSyncGateway] for tests — spec.md's Testing Decisions call for
 * "tests against an in-memory Room database and a fake remote." [online]
 * toggles whether a push succeeds, simulating connectivity; [pushedSets]/
 * [pushedTallies] record what actually reached the "server" so a test can
 * assert on it directly (e.g. that two devices' offline writes both arrive,
 * unmerged and uncollided).
 */
class FakeRemoteSyncGateway : RemoteSyncGateway {
    var online: Boolean = true
    val pushedSets = mutableListOf<SetEntity>()
    val pushedTallies = mutableListOf<TallyEntity>()

    override suspend fun pushSet(set: SetEntity): Boolean {
        if (!online) return false
        pushedSets.add(set)
        return true
    }

    override suspend fun pushTally(tally: TallyEntity): Boolean {
        if (!online) return false
        pushedTallies.add(tally)
        return true
    }
}
