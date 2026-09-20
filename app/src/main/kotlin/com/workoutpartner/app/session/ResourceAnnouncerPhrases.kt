package com.workoutpartner.app.session

import android.content.Context
import com.workoutpartner.app.R
import com.workoutpartner.app.beforeyoustart.FormGuides
import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant

/** [AnnouncerPhrases] read from string resources, so every spoken line is translatable with no code changes (`workout-partner-v3` ticket 13). Exercise names reuse the Form Guides' own, so the Athlete hears what they read. */
class ResourceAnnouncerPhrases(context: Context) : AnnouncerPhrases {
    private val resources = context.applicationContext.resources

    override fun exerciseName(exercise: Exercise, variant: ExerciseVariant?): String =
        resources.getString(FormGuides.resourcesFor(TrackedExercise(exercise, variant)).nameRes)

    override fun setStart(exerciseName: String, reps: Int): String =
        resources.getQuantityString(R.plurals.announce_set_start, reps, exerciseName, reps)

    override fun rest(seconds: Int): String = resources.getQuantityString(R.plurals.announce_rest, seconds, seconds)
    override fun getReady(exerciseName: String): String = resources.getString(R.string.announce_get_ready, exerciseName)
    override fun trackingLost(): String = resources.getString(R.string.announce_tracking_lost)
    override fun sessionComplete(): String = resources.getString(R.string.announce_session_complete)
}
