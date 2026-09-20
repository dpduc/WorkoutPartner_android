package com.workoutpartner.app.beforeyoustart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.app.session.ResourceAnnouncerPhrases
import com.workoutpartner.app.session.SessionAnnouncer
import com.workoutpartner.app.session.toRoutineSteps
import com.workoutpartner.app.speech.PromptSpeaker
import com.workoutpartner.app.ui.components.CameraPermissionGate
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.data.RoutineWithSteps
import kotlinx.coroutines.delay

/**
 * Owns the [BeforeYouStartEngine] for one chosen Routine and renders
 * whichever phase it's on (`workout-partner-v3` ticket 03's phase skeleton,
 * ticket 10's Form Guides, ticket 12's Position Check, ticket 13's
 * Countdown). [onReadyForSession] fires once the countdown reaches
 * [BeforeYouStartPhase.Ready], straight into the first Set with no tap. It
 * carries the Athlete's chosen Jumping Jack/Step Jack Variant (ticket 11)
 * along for the ride, so [onReadyForSession] can hand it to the Session
 * that's about to start.
 *
 * Owns the [PromptSpeaker] for the whole flow: the Position Check's cues and
 * the countdown's first-Set line ("Squat. 12 reps.") share it, and it must
 * outlive the Position Check phase that would otherwise cut that line off.
 *
 * "Review form" is deliberately *not* routed through the engine: the
 * ticket asks for it to reopen every guide "anytime," regardless of the
 * Athlete's progress toward starting the Session, so it's a self-contained
 * detour back to the Overview rather than a phase transition.
 *
 * [jumpingJackVariant] is hoisted to this screen, not [WorkoutOverviewScreen]
 * itself, because the Athlete's choice needs to survive a detour into
 * [FormGuidesScreen] — a different branch of the `when` below, which would
 * discard `remember`ed state scoped to [WorkoutOverviewScreen] alone.
 *
 * [phase] mirrors the engine's own (plain, non-Compose-observable) phase:
 * every engine call that can change it is followed by a re-read here, the
 * same "publish after each engine call" pattern `SessionViewModel` uses.
 */
@Composable
fun BeforeYouStartScreen(
    routine: RoutineWithSteps,
    difficultyTier: DifficultyTier,
    /** Whether the Athlete's BMI is ≥ 30 (`workout-partner-v3` ticket 11) — [WorkoutOverviewScreen]'s Jumping Jack/Step Jack toggle defaults to Step Jack when true. See [com.workoutpartner.app.routines.RoutineDifficulty.isObese]. */
    defaultToStepJack: Boolean,
    formGuidePrefs: FormGuidePrefs,
    /** Creates the camera-backed tracker the Position Check (ticket 12) reads frames from; stopped again when that phase ends, before the Session creates its own. */
    poseTrackerFactory: () -> PoseTracker,
    onReadyForSession: (jumpingJackVariant: ExerciseVariant?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var jumpingJackVariant by remember(routine) {
        mutableStateOf(if (routine.hasJumpingJack && defaultToStepJack) ExerciseVariant.STEP_JACK else null)
    }
    val trackedExercises = remember(routine, jumpingJackVariant) { routine.trackedExercises(jumpingJackVariant) }
    var reviewingAllGuides by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf<BeforeYouStartEngine?>(null) }
    var phase by remember { mutableStateOf<BeforeYouStartPhase>(BeforeYouStartPhase.Overview) }
    val context = LocalContext.current
    val speaker = remember { PromptSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }

    val currentPhase = phase

    when {
        reviewingAllGuides -> FormGuidesScreen(
            guides = trackedExercises,
            formGuidePrefs = formGuidePrefs,
            onDone = { reviewingAllGuides = false },
            modifier = modifier,
        )

        currentPhase is BeforeYouStartPhase.FormGuides -> FormGuidesScreen(
            guides = currentPhase.guides,
            formGuidePrefs = formGuidePrefs,
            onDone = {
                engine?.let {
                    it.advance() // FormGuides -> PositionCheck
                    phase = it.phase
                }
            },
            modifier = modifier,
        )

        currentPhase is BeforeYouStartPhase.PositionCheck -> {
            val activeEngine = engine
            if (activeEngine != null) {
                CameraPermissionGate {
                    PositionCheckScreen(
                        engine = activeEngine,
                        poseTracker = remember { poseTrackerFactory() },
                        speaker = speaker,
                        onPhaseChanged = { phase = activeEngine.phase },
                        modifier = modifier,
                    )
                }
            }
        }

        currentPhase is BeforeYouStartPhase.Countdown -> {
            LaunchedEffect(Unit) {
                // The first Set's line is spoken as the countdown starts; later Sets' come from SessionAnnouncer's transitions.
                val steps = routine.toRoutineSteps(difficultyTier, jumpingJackVariant)
                val line = SessionAnnouncer(steps, ResourceAnnouncerPhrases(context)).setStart(steps.first())
                speaker.speak(line)
            }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    engine?.let {
                        it.onTick()
                        phase = it.phase
                    }
                }
            }
            CountdownScreen(secondsRemaining = currentPhase.secondsRemaining, modifier = modifier)
        }

        currentPhase == BeforeYouStartPhase.Ready -> LaunchedEffect(Unit) { onReadyForSession(jumpingJackVariant) }

        else -> WorkoutOverviewScreen(
            routine = routine,
            difficultyTier = difficultyTier,
            jumpingJackVariant = jumpingJackVariant,
            onJumpingJackVariantChange = { jumpingJackVariant = it },
            onReviewForm = { reviewingAllGuides = true },
            onStart = {
                val unseen = trackedExercises.filterNot(formGuidePrefs::hasSeen)
                val newEngine = BeforeYouStartEngine(unseen)
                newEngine.advance() // Overview -> FormGuides, or straight to PositionCheck if nothing's unseen
                engine = newEngine
                phase = newEngine.phase
            },
            modifier = modifier,
        )
    }
}
