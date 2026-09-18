package com.workoutpartner.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAuthGatewayTest {

    private fun newGateway() = LocalAuthGateway(ApplicationProvider.getApplicationContext())

    private suspend inline fun <reified T : Throwable> assertFailsWith(crossinline block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName} but nothing was thrown")
        } catch (e: Throwable) {
            if (e !is T) throw e
        }
    }

    @Test
    fun `starts signed out`() = runTest {
        assertNull(newGateway().currentUserId.first())
    }

    @Test
    fun `sign up persists credentials and emits the new user id`() = runTest {
        val gateway = newGateway()

        val userId = gateway.signUpWithEmail("new@example.com", "hunter2")

        assertEquals(userId, gateway.currentUserId.first())
    }

    @Test
    fun `sign up rejects an already-registered email`() = runTest {
        val gateway = newGateway()
        gateway.signUpWithEmail("dup@example.com", "hunter2")

        assertFailsWith<IllegalStateException> {
            gateway.signUpWithEmail("dup@example.com", "different1")
        }
    }

    @Test
    fun `sign up rejects a too-short password`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            newGateway().signUpWithEmail("short@example.com", "abc")
        }
    }

    @Test
    fun `sign up rejects an invalid email`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            newGateway().signUpWithEmail("not-an-email", "hunter2")
        }
    }

    @Test
    fun `sign in with correct credentials succeeds and emits the existing user id`() = runTest {
        val gateway = newGateway()
        val userId = gateway.signUpWithEmail("returning@example.com", "hunter2")
        gateway.signOut()

        val signedInId = gateway.signInWithEmail("returning@example.com", "hunter2")

        assertEquals(userId, signedInId)
        assertEquals(userId, gateway.currentUserId.first())
    }

    @Test
    fun `sign in with a wrong password fails`() = runTest {
        val gateway = newGateway()
        gateway.signUpWithEmail("wrongpass@example.com", "hunter2")

        assertFailsWith<IllegalStateException> {
            gateway.signInWithEmail("wrongpass@example.com", "not-it")
        }
    }

    @Test
    fun `sign in with an unknown email fails`() = runTest {
        assertFailsWith<IllegalStateException> {
            newGateway().signInWithEmail("nobody@example.com", "hunter2")
        }
    }

    @Test
    fun `sign out resets currentUserId to null`() = runTest {
        val gateway = newGateway()
        gateway.signUpWithEmail("new@example.com", "hunter2")

        gateway.signOut()

        assertNull(gateway.currentUserId.first())
    }

    @Test
    fun `Google sign-in is unavailable in local mode, with a friendly message`() = runTest {
        try {
            newGateway().signInWithGoogle("some-id-token")
            fail("Expected GoogleSignInUnavailableException but nothing was thrown")
        } catch (e: GoogleSignInUnavailableException) {
            assertEquals("Google sign-in isn't available in local mode — sign in with email instead.", e.message)
        }
    }
}
