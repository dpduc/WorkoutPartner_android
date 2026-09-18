package com.workoutpartner.app

import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.RoutineWithSteps
import com.workoutpartner.data.TrackedProfileEntity

/**
 * The app's top-level navigation state — a plain sealed hierarchy switched
 * on in [WorkoutPartnerApp] rather than the Navigation-Compose library: this
 * app's screen count doesn't yet justify that dependency, and a `when` over
 * a sealed interface is simpler to reason about and test.
 *
 * [RoutinePicker] is the whole app for now — ticket 13 inserts the safety
 * disclaimer / Guest-vs-Account entry point in front of it (out of this
 * ticket's scope), and later tickets (10, 11) add their own top-level
 * screens alongside it.
 */
sealed interface AppScreen {
    data object Disclaimer : AppScreen
    data object Welcome : AppScreen
    data object SignUp : AppScreen
    data object SignIn : AppScreen

    /**
     * Body-stats capture (`workout-partner-v2` ticket 01), shown once right
     * after choosing Guest or completing Sign up — never after Sign in,
     * since an existing Account already answered this at its own sign-up.
     */
    data object ProfileSetup : AppScreen

    /**
     * The app's true home screen (`workout-partner-v2` ticket 02): two
     * top-level sections, Workouts ([RoutinePicker]'s pre-built Routines)
     * and Quick Count ([Roster]), plus Progress/Settings/Sign-up in an
     * overflow menu — [RoutinePicker] used to carry that overflow menu
     * itself; it's now purely the Routine list.
     */
    data object MainMenu : AppScreen
    data object RoutinePicker : AppScreen

    /**
     * The Workout Overview (`workout-partner-v3` ticket 03): between
     * picking a Routine and its first Set — see
     * [com.workoutpartner.app.beforeyoustart.WorkoutOverviewScreen].
     */
    data class WorkoutOverview(val routine: RoutineWithSteps) : AppScreen
    data class Session(val routine: RoutineWithSteps) : AppScreen
    data object Progress : AppScreen
    data object Settings : AppScreen
    data object Roster : AppScreen
    data class QuickCountSetup(val profile: TrackedProfileEntity) : AppScreen
    data class QuickCountRun(val profile: TrackedProfileEntity, val exercise: Exercise, val target: Int?) : AppScreen
    data class TallyHistory(val profile: TrackedProfileEntity) : AppScreen
}
