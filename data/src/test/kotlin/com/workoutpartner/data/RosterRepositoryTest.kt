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

    @Test
    fun `a Guest (no Account) can create a Tracked Profile and it lands in the Guest's own Roster`() = runTest {
        val profile = rosterRepository.createTrackedProfile(accountId = null, "Alex")

        assertEquals(listOf(profile), rosterRepository.getRoster(null))
    }

    @Test
    fun `a Guest's Roster and an Account's Roster don't leak into each other`() = runTest {
        val accountId = seedAccount()
        val guestProfile = rosterRepository.createTrackedProfile(accountId = null, "Guest's Alex")
        val accountProfile = rosterRepository.createTrackedProfile(accountId, "Account's Sam")

        assertEquals(listOf(guestProfile), rosterRepository.getRoster(null))
        assertEquals(listOf(accountProfile), rosterRepository.getRoster(accountId))
    }

    @Test
    fun `deleting a Guest's Tracked Profile removes it from the Guest's Roster`() = runTest {
        val profile = rosterRepository.createTrackedProfile(accountId = null, "Alex")

        rosterRepository.deleteTrackedProfile(profile)

        assertTrue(rosterRepository.getRoster(null).isEmpty())
    }

    private suspend fun seedAccount(): String {
        val account = AccountEntity(id = "account-1")
        db.accountDao().insert(account)
        return account.id
    }
}
