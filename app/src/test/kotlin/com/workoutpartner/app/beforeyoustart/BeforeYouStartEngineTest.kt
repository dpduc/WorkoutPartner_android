package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.core.posetracking.RawPoseFrame
import com.workoutpartner.core.posetracking.RawPoseLandmark
import com.workoutpartner.core.repcounting.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        assertTrue(engine.phase is BeforeYouStartPhase.PositionCheck)
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
        assertTrue(engine.phase is BeforeYouStartPhase.PositionCheck)

        engine.advance()
        assertEquals(BeforeYouStartPhase.Countdown(BeforeYouStartEngine.COUNTDOWN_SECONDS), engine.phase)

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

    // --- Position Check (workout-partner-v3 ticket 12) ---

    @Test
    fun `Position Check begins with nothing detected and Start anyway unavailable`() {
        val engine = enginePastFormGuides()

        assertEquals(
            PositionCheckStatus(bodyInFrame = false, distance = DistanceStatus.UNKNOWN, secondsElapsed = 0, startAnywayAvailable = false),
            positionCheck(engine),
        )
    }

    @Test
    fun `reports a body out of frame when fewer than 28 of 33 landmarks are confident`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(frame(confidentCount = 27))

        assertFalse(positionCheck(engine).bodyInFrame)
    }

    @Test
    fun `reports a body in frame once at least 28 of 33 landmarks are confident`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(frame(confidentCount = 28))

        assertTrue(positionCheck(engine).bodyInFrame)
    }

    @Test
    fun `an empty frame with no pose detected is out of frame with unknown distance`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(RawPoseFrame(emptyList()))

        assertEquals(false, positionCheck(engine).bodyInFrame)
        assertEquals(DistanceStatus.UNKNOWN, positionCheck(engine).distance)
    }

    @Test
    fun `a skeleton under 40 percent of the frame height is too far`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f))

        assertEquals(DistanceStatus.TOO_FAR, positionCheck(engine).distance)
    }

    @Test
    fun `a skeleton over 80 percent of the frame height is too close`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(frame(topY = 0.05f, bottomY = 0.9f))

        assertEquals(DistanceStatus.TOO_CLOSE, positionCheck(engine).distance)
    }

    @Test
    fun `a skeleton between 40 and 80 percent of the frame height is a good distance`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(frame(topY = 0.2f, bottomY = 0.8f))

        assertEquals(DistanceStatus.OK, positionCheck(engine).distance)
    }

    @Test
    fun `low confidence landmarks do not stretch the measured skeleton height`() {
        val engine = enginePastFormGuides()

        // 30 confident landmarks span 0.2..0.8 (60%); the 3 unconfident ones sit far outside that.
        engine.onPoseFrame(frame(topY = 0.2f, bottomY = 0.8f, confidentCount = 30, strayY = 0.0f))

        assertEquals(DistanceStatus.OK, positionCheck(engine).distance)
    }

    @Test
    fun `all checks passing plus 2 seconds of stability auto-advances to Countdown`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(goodFrame())
        engine.onTick()
        assertTrue(engine.phase is BeforeYouStartPhase.PositionCheck)

        engine.onPoseFrame(goodFrame())
        engine.onTick()

        assertEquals(BeforeYouStartPhase.Countdown(BeforeYouStartEngine.COUNTDOWN_SECONDS), engine.phase)
    }

    @Test
    fun `movement resets the stability window`() {
        val engine = enginePastFormGuides()
        engine.onPoseFrame(goodFrame())
        engine.onTick() // 1 stable second

        engine.onPoseFrame(goodFrame(xOffset = 0.2f)) // jumps well past the displacement threshold
        engine.onTick()
        engine.onPoseFrame(goodFrame(xOffset = 0.2f))
        engine.onTick()
        assertTrue("only 1 stable second since the move", engine.phase is BeforeYouStartPhase.PositionCheck)

        engine.onPoseFrame(goodFrame(xOffset = 0.2f))
        engine.onTick()

        assertEquals(BeforeYouStartPhase.Countdown(BeforeYouStartEngine.COUNTDOWN_SECONDS), engine.phase)
    }

    @Test
    fun `a failing check keeps the phase from advancing however long it lasts`() {
        val engine = enginePastFormGuides()

        repeat(10) {
            engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f)) // too far
            engine.onTick()
        }

        assertTrue(engine.phase is BeforeYouStartPhase.PositionCheck)
    }

    @Test
    fun `stability doesn't accrue without any frames arriving`() {
        val engine = enginePastFormGuides()
        engine.onPoseFrame(goodFrame())

        repeat(5) { engine.onTick() } // camera went silent after one good frame

        assertTrue(engine.phase is BeforeYouStartPhase.PositionCheck)
    }

    @Test
    fun `Start anyway appears only after 15 seconds`() {
        val engine = enginePastFormGuides()

        repeat(14) {
            engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f))
            engine.onTick()
        }
        assertFalse(positionCheck(engine).startAnywayAvailable)

        engine.onTick()

        assertTrue(positionCheck(engine).startAnywayAvailable)
    }

    @Test
    fun `startAnyway proceeds to Countdown once available`() {
        val engine = enginePastFormGuides()
        repeat(15) { engine.onTick() }

        engine.startAnyway()

        assertEquals(BeforeYouStartPhase.Countdown(BeforeYouStartEngine.COUNTDOWN_SECONDS), engine.phase)
    }

    @Test
    fun `startAnyway is ignored before it's available`() {
        val engine = enginePastFormGuides()
        repeat(14) { engine.onTick() }

        engine.startAnyway()

        assertTrue(engine.phase is BeforeYouStartPhase.PositionCheck)
    }

    @Test
    fun `Position Check inputs are ignored outside the Position Check phase`() {
        val engine = BeforeYouStartEngine(unseenGuides = emptyList())

        engine.onPoseFrame(goodFrame())
        repeat(20) { engine.onTick() }
        engine.startAnyway()

        assertEquals(BeforeYouStartPhase.Overview, engine.phase)
    }

    @Test
    fun `speaks Body detected once the body comes into frame`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(goodFrame())

        assertEquals(PositionCue.BODY_DETECTED, engine.takeCue())
        assertNull("a cue is consumed once taken", engine.takeCue())
    }

    @Test
    fun `speaks Come a bit closer when too far and Step back a little when too close`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f)) // in frame + too far
        assertEquals(PositionCue.BODY_DETECTED, engine.takeCue())
        assertEquals(PositionCue.MOVE_CLOSER, engine.takeCue())
        assertNull(engine.takeCue())

        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f))
        engine.onTick()
        assertNull("no repeat within the first seconds of an unchanged failure", engine.takeCue())

        engine.onPoseFrame(frame(topY = 0.05f, bottomY = 0.9f)) // now too close
        assertEquals(PositionCue.STEP_BACK, engine.takeCue())

        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f)) // back to too far
        assertEquals(PositionCue.MOVE_CLOSER, engine.takeCue())
    }

    @Test
    fun `repeats a distance cue every 5 seconds while the failure persists`() {
        val engine = enginePastFormGuides()
        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f))
        engine.takeCue() // Body detected
        engine.takeCue() // Come a bit closer

        repeat(4) {
            engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f))
            engine.onTick()
        }
        assertNull(engine.takeCue())

        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f))
        engine.onTick()

        assertEquals(PositionCue.MOVE_CLOSER, engine.takeCue())
    }

    @Test
    fun `slow drift too small to notice between frames still resets the stability window`() {
        val engine = enginePastFormGuides()

        // Each step is well under the stillness threshold on its own; together they exceed it.
        (0..5).forEach { engine.onPoseFrame(goodFrame(xOffset = it * 0.005f)) }
        engine.onTick()
        engine.onPoseFrame(goodFrame(xOffset = 0.025f))
        engine.onTick()
        assertTrue("only 1 stable second since the drift", engine.phase is BeforeYouStartPhase.PositionCheck)

        engine.onPoseFrame(goodFrame(xOffset = 0.025f))
        engine.onTick()

        assertEquals(BeforeYouStartPhase.Countdown(BeforeYouStartEngine.COUNTDOWN_SECONDS), engine.phase)
    }

    @Test
    fun `a single failing frame anywhere in a second keeps that second from counting as stable`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(goodFrame())
        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f)) // too far, briefly
        engine.onPoseFrame(goodFrame())
        engine.onTick()
        engine.onPoseFrame(goodFrame())
        engine.onTick()

        assertTrue("only the second window was fully clean", engine.phase is BeforeYouStartPhase.PositionCheck)
    }

    @Test
    fun `Body detected is spoken only once even if the in-frame count flickers`() {
        val engine = enginePastFormGuides()

        engine.onPoseFrame(frame(confidentCount = 28))
        engine.onPoseFrame(frame(confidentCount = 27))
        engine.onPoseFrame(frame(confidentCount = 28))

        assertEquals(PositionCue.BODY_DETECTED, engine.takeCue())
        assertNull(engine.takeCue())
    }

    @Test
    fun `a suppressed distance cue doesn't get re-announced when the body flickers back into frame`() {
        val engine = enginePastFormGuides()
        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f)) // in frame + too far
        engine.takeCue() // Body detected
        engine.takeCue() // Come a bit closer

        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f, confidentCount = 27)) // body drops out: cue suppressed
        engine.onPoseFrame(frame(topY = 0.3f, bottomY = 0.69f)) // back in, still too far

        assertNull(engine.takeCue())
    }

    // --- Countdown (workout-partner-v3 ticket 13) ---

    @Test
    fun `Countdown starts at 10 seconds`() {
        val engine = enginePastFormGuides()
        engine.startPastPositionCheck()

        assertEquals(BeforeYouStartPhase.Countdown(10), engine.phase)
    }

    @Test
    fun `Countdown reaches Ready after 10 ticks, not before`() {
        val engine = enginePastFormGuides()
        engine.startPastPositionCheck()

        repeat(9) { engine.onTick() }
        assertEquals(BeforeYouStartPhase.Countdown(1), engine.phase)

        engine.onTick()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    @Test
    fun `ticks after Ready change nothing`() {
        val engine = enginePastFormGuides()
        engine.startPastPositionCheck()
        repeat(10) { engine.onTick() }

        engine.onTick()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    // --- Quick Count (workout-partner-v3 ticket 14) ---

    @Test
    fun `forQuickCount starts directly at Position Check, with no Overview or Form Guides first`() {
        val engine = BeforeYouStartEngine.forQuickCount()

        assertTrue(engine.phase is BeforeYouStartPhase.PositionCheck)
    }

    @Test
    fun `forQuickCount reaches Ready once the Position Check passes, with no Countdown after it`() {
        val engine = BeforeYouStartEngine.forQuickCount()

        engine.onPoseFrame(goodFrame())
        engine.onTick()
        engine.onPoseFrame(goodFrame())
        engine.onTick()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    @Test
    fun `forQuickCount's Start anyway is available after 15 seconds and also skips straight to Ready`() {
        val engine = BeforeYouStartEngine.forQuickCount()
        repeat(14) { engine.onTick() }
        assertFalse(positionCheck(engine).startAnywayAvailable)

        repeat(1) { engine.onTick() }
        engine.startAnyway()

        assertEquals(BeforeYouStartPhase.Ready, engine.phase)
    }

    /** Position Check -> Countdown the way "Start anyway" gets there: after its 15 seconds. */
    private fun BeforeYouStartEngine.startPastPositionCheck() {
        repeat(15) { onTick() }
        startAnyway()
    }

    private fun enginePastFormGuides() = BeforeYouStartEngine(unseenGuides = emptyList()).also { it.advance() }

    private fun positionCheck(engine: BeforeYouStartEngine): PositionCheckStatus =
        (engine.phase as BeforeYouStartPhase.PositionCheck).status

    private fun goodFrame(xOffset: Float = 0f) = frame(topY = 0.2f, bottomY = 0.8f, xOffset = xOffset)

    /**
     * 33 landmarks spread evenly from [topY] to [bottomY]; only the first
     * [confidentCount] are confident — the rest are low-confidence strays at
     * [strayY] (default: [topY]), which the height measurement must ignore.
     */
    private fun frame(
        topY: Float = 0.2f,
        bottomY: Float = 0.8f,
        confidentCount: Int = RawPoseFrame.LANDMARK_COUNT,
        strayY: Float = topY,
        xOffset: Float = 0f,
    ): RawPoseFrame {
        val landmarks = (0 until RawPoseFrame.LANDMARK_COUNT).map { i ->
            if (i < confidentCount) {
                val y = topY + (bottomY - topY) * i / (confidentCount - 1).coerceAtLeast(1)
                RawPoseLandmark(x = 0.5f + xOffset, y = y, z = 0f, visibility = 0.9f, presence = 0.9f)
            } else {
                RawPoseLandmark(x = 0.5f + xOffset, y = strayY, z = 0f, visibility = 0.1f, presence = 0.1f)
            }
        }
        return RawPoseFrame(landmarks)
    }
}
