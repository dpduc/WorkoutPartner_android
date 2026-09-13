package com.workoutpartner.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The first-launch entry point (ticket 13, spec.md story 1): "Continue as Guest" needs no Account; Sign up/Sign in are the alternative for a returning or new Account holder. */
@Composable
fun WelcomeScreen(onContinueAsGuest: () -> Unit, onSignUp: () -> Unit, onSignIn: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Workout Partner", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Track your Reps and form with your camera.",
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onContinueAsGuest) { Text("Continue as Guest") }
            OutlinedButton(onClick = onSignUp, modifier = Modifier.padding(top = 12.dp)) { Text("Sign up") }
            TextButton(onClick = onSignIn, modifier = Modifier.padding(top = 4.dp)) { Text("Already have an Account? Sign in") }
        }
    }
}
