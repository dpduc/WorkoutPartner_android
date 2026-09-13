package com.workoutpartner.app

import com.workoutpartner.data.RoutineWithSteps

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
    data object RoutinePicker : AppScreen
    data class Session(val routine: RoutineWithSteps) : AppScreen
}
