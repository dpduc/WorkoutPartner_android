package com.workoutpartner.core.posetracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CameraRetryPolicyTest {

    @Test
    fun `each recoverable error gets the backoff for its attempt number`() {
        val policy = CameraRetryPolicy(maxAttempts = 3, backoffMs = { attempt -> attempt * 100L })

        assertEquals(100L, policy.onRecoverableError())
        assertEquals(200L, policy.onRecoverableError())
        assertEquals(300L, policy.onRecoverableError())
    }

    @Test
    fun `gives up once maxAttempts recoverable errors have happened in a row`() {
        val policy = CameraRetryPolicy(maxAttempts = 2, backoffMs = { 0L })

        policy.onRecoverableError()
        policy.onRecoverableError()
        val exhausted = policy.onRecoverableError()

        assertNull(exhausted)
    }

    @Test
    fun `reset gives a fresh retry budget, e_g_ after a successful reconnect`() {
        val policy = CameraRetryPolicy(maxAttempts = 1, backoffMs = { 0L })

        policy.onRecoverableError()
        assertNull(policy.onRecoverableError())

        policy.reset()

        assertNotNull(policy.onRecoverableError())
    }

    @Test
    fun `the default backoff grows exponentially- 1s, 2s, 4s`() {
        val policy = CameraRetryPolicy(maxAttempts = 3)

        assertEquals(1_000L, policy.onRecoverableError())
        assertEquals(2_000L, policy.onRecoverableError())
        assertEquals(4_000L, policy.onRecoverableError())
    }

    @Test
    fun `a single attempt budget still allows exactly one retry before giving up`() {
        val policy = CameraRetryPolicy(maxAttempts = 1, backoffMs = { 50L })

        assertEquals(50L, policy.onRecoverableError())
        assertNull(policy.onRecoverableError())
    }
}
