package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.core.repcounting.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class BeforeYouStartEngineTest {

    @Test
    fun `starts at Overview`() {
        val engine = BeforeYouStartEngine(unseenGuides = emptyList())

        assertEquals(BeforeYouStartPhase.Overview, engine.phase)
    }

    @Test
    fun `advance from Overview skips straight to PositionCheck when there are no unseen guides`() {
        val engine = BeforeYouStartEngine(unseenGuides = emptyList())

        engine.advance()

        assertEquals(BeforeYouStartPhase.PositionCheck, engine.phase)
    }

    @Test
    fun `advance from Overview goes to FormGuides carrying the unseen guides when there are any`() {
        val unseen = listOf(TrackedExercise(Exercise.SQUAT), TrackedExercise(Exercise.PUSH_UP))
        val engine = BeforeYouStartEngine(unseenGuides = unseen)

        engine.advance()

        assertEquals(BeforeYouStartPhase.FormGuides(unseen), engine.phase)
    }

    @Test
    fun `advance walks the fixed phase sequence once Form Guides is shown`() {
        val unseen = listOf(TrackedExercise(Exercise.SQUAT))
        val engine = BeforeYouStartEngine(unseenGuides = unseen)

        engine.advance()
        assertEquals(BeforeYouStartPhase.FormGuides(unseen), engine.phase)

        engine.advance()
        assertEquals(BeforeYouStartPhase.PositionCheck, engine.phase)

        engine.advance()
        assertEquals(BeforeYouStartPhase.Countdown, engine.phase)

        engine.advance()
        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    @Test
    fun `advance is a no-op once Ready`() {
        val engine = BeforeYouStartEngine(unseenGuides = emptyList())
        repeat(4) { engine.advance() }

        engine.advance()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    @Test
    fun `skipToReady reaches Ready from Overview in one call`() {
        val engine = BeforeYouStartEngine(unseenGuides = listOf(TrackedExercise(Exercise.SQUAT)))

        engine.skipToReady()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    @Test
    fun `skipToReady from a mid-sequence phase only advances the remainder`() {
        val engine = BeforeYouStartEngine(unseenGuides = listOf(TrackedExercise(Exercise.SQUAT)))
        engine.advance() // Overview -> FormGuides

        engine.skipToReady()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }
}
