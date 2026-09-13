package com.workoutpartner.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RosterRepositoryTest {

    private val db = newInMemoryDatabase()
    private val rosterRepository = RosterRepository(db)

    @Test
    fun `creating a Tracked Profile adds it to the owning Account's Roster`() = runTest {
        val accountId = seedAccount()

        val profile = rosterRepository.createTrackedProfile(accountId, "Alex")

        assertEquals(listOf(profile), rosterRepository.getRoster(accountId))
    }

    @Test
    fun `deleting a Tracked Profile removes it from the Roster`() = runTest {
        val accountId = seedAccount()
        val profile = rosterRepository.createTrackedProfile(accountId, "Alex")

        rosterRepository.deleteTrackedProfile(profile)

        assertTrue(rosterRepository.getRoster(accountId).isEmpty())
    }

    private suspend fun seedAccount(): String {
        val account = AccountEntity(id = "account-1")
        db.accountDao().insert(account)
        return account.id
    }
}
