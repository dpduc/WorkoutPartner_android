package com.workoutpartner.app.beforeyoustart

import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.workoutpartner.app.R
import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant

/**
 * One [TrackedExercise]'s Form Guide, as resource ids — resolved into real
 * strings by [resolve] only inside a `@Composable`, the same "hold ids,
 * resolve at the call site" split the rest of this app's resource-backed UI
 * uses. Ticket 10's checklist asks for this content "bundled... in string
 * resources" specifically (unlike most of this app's copy, which is inline
 * Compose literals), so it can be revised/localized without touching this
 * mapping or [FormGuidesScreen].
 */
data class FormGuideResources(
    @StringRes val nameRes: Int,
    @DrawableRes val imageRes: Int,
    @StringRes val startingPositionRes: Int,
    @ArrayRes val cuesRes: Int,
    @ArrayRes val mistakesRes: Int,
    @StringRes val trackerCheckRes: Int,
)

/** [FormGuideResources] resolved to real text — what [FormGuidePage] actually renders. */
data class FormGuideContent(
    val name: String,
    val startingPosition: String,
    val cues: List<String>,
    val mistakes: List<String>,
    val trackerCheck: String,
    @DrawableRes val imageRes: Int,
)

@Composable
fun FormGuideResources.resolve(): FormGuideContent = FormGuideContent(
    name = stringResource(nameRes),
    startingPosition = stringResource(startingPositionRes),
    cues = stringArrayResource(cuesRes).toList(),
    mistakes = stringArrayResource(mistakesRes).toList(),
    trackerCheck = stringResource(trackerCheckRes),
    imageRes = imageRes,
)

/**
 * The bundled Form Guide content for all six guides ticket 10 asks for —
 * the five [Exercise]s plus [ExerciseVariant.STEP_JACK] — keyed by
 * [TrackedExercise] the same way [com.workoutpartner.app.progress.ProgressStats.personalBests]
 * already keys Personal Bests, rather than a second bare
 * `Pair<Exercise, ExerciseVariant?>`.
 */
object FormGuides {
    private val byTrackedExercise: Map<TrackedExercise, FormGuideResources> = mapOf(
        TrackedExercise(Exercise.SQUAT) to FormGuideResources(
            nameRes = R.string.form_guide_squat_name,
            imageRes = R.drawable.ic_form_guide_placeholder,
            startingPositionRes = R.string.form_guide_squat_starting_position,
            cuesRes = R.array.form_guide_squat_cues,
            mistakesRes = R.array.form_guide_squat_mistakes,
            trackerCheckRes = R.string.form_guide_squat_tracker_check,
        ),
        TrackedExercise(Exercise.PUSH_UP) to FormGuideResources(
            nameRes = R.string.form_guide_push_up_name,
            imageRes = R.drawable.ic_form_guide_placeholder,
            startingPositionRes = R.string.form_guide_push_up_starting_position,
            cuesRes = R.array.form_guide_push_up_cues,
            mistakesRes = R.array.form_guide_push_up_mistakes,
            trackerCheckRes = R.string.form_guide_push_up_tracker_check,
        ),
        TrackedExercise(Exercise.SIT_UP) to FormGuideResources(
            nameRes = R.string.form_guide_sit_up_name,
            imageRes = R.drawable.ic_form_guide_placeholder,
            startingPositionRes = R.string.form_guide_sit_up_starting_position,
            cuesRes = R.array.form_guide_sit_up_cues,
            mistakesRes = R.array.form_guide_sit_up_mistakes,
            trackerCheckRes = R.string.form_guide_sit_up_tracker_check,
        ),
        TrackedExercise(Exercise.LUNGE) to FormGuideResources(
            nameRes = R.string.form_guide_lunge_name,
            imageRes = R.drawable.ic_form_guide_placeholder,
            startingPositionRes = R.string.form_guide_lunge_starting_position,
            cuesRes = R.array.form_guide_lunge_cues,
            mistakesRes = R.array.form_guide_lunge_mistakes,
            trackerCheckRes = R.string.form_guide_lunge_tracker_check,
        ),
        TrackedExercise(Exercise.JUMPING_JACK) to FormGuideResources(
            nameRes = R.string.form_guide_jumping_jack_name,
            imageRes = R.drawable.ic_form_guide_placeholder,
            startingPositionRes = R.string.form_guide_jumping_jack_starting_position,
            cuesRes = R.array.form_guide_jumping_jack_cues,
            mistakesRes = R.array.form_guide_jumping_jack_mistakes,
            trackerCheckRes = R.string.form_guide_jumping_jack_tracker_check,
        ),
        TrackedExercise(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK) to FormGuideResources(
            nameRes = R.string.form_guide_step_jack_name,
            imageRes = R.drawable.ic_form_guide_placeholder,
            startingPositionRes = R.string.form_guide_step_jack_starting_position,
            cuesRes = R.array.form_guide_step_jack_cues,
            mistakesRes = R.array.form_guide_step_jack_mistakes,
            trackerCheckRes = R.string.form_guide_step_jack_tracker_check,
        ),
    )

    fun resourcesFor(tracked: TrackedExercise): FormGuideResources = byTrackedExercise.getValue(tracked)
}
