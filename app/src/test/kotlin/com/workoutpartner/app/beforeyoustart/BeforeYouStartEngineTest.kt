package com.workoutpartner.app.beforeyoustart

import org.junit.Assert.assertEquals
import org.junit.Test

class BeforeYouStartEngineTest {

    @Test
    fun `starts at Overview`() {
        val engine = BeforeYouStartEngine()

        assertEquals(BeforeYouStartPhase.Overview, engine.phase)
    }

    @Test
    fun `advance walks the fixed phase sequence`() {
        val engine = BeforeYouStartEngine()

        engine.advance()
        assertEquals(BeforeYouStartPhase.FormGuides, engine.phase)

        engine.advance()
        assertEquals(BeforeYouStartPhase.PositionCheck, engine.phase)

        engine.advance()
        assertEquals(BeforeYouStartPhase.Countdown, engine.phase)

        engine.advance()
        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    @Test
    fun `advance is a no-op once Ready`() {
        val engine = BeforeYouStartEngine()
        repeat(4) { engine.advance() }

        engine.advance()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    @Test
    fun `skipToReady reaches Ready from Overview in one call`() {
        val engine = BeforeYouStartEngine()

        engine.skipToReady()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    @Test
    fun `skipToReady from a mid-sequence phase only advances the remainder`() {
        val engine = BeforeYouStartEngine()
        engine.advance() // Overview -> FormGuides

        engine.skipToReady()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }
}
