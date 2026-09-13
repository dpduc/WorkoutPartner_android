package com.workoutpartner.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test for ticket 01: proves the app shell actually launches and
 * renders, on a real (or emulated) device/instrumentation — the closest
 * thing this scaffold-only ticket has to a behavior to verify.
 */
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launches_and_shows_the_empty_shell() {
        composeTestRule.onNodeWithText("Workout Partner").assertExists()
    }
}
