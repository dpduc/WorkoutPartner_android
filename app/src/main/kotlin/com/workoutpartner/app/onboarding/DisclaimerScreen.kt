package com.workoutpartner.app.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The safety disclaimer (ticket 13, spec.md story 22): "the app isn't a
 * substitute for professional guidance." Shown once, before any tracking
 * screen is reachable — [com.workoutpartner.app.WorkoutPartnerApp] never
 * routes anywhere else until [onAcknowledge] fires.
 */
@Composable
fun DisclaimerScreen(onAcknowledge: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Before you start", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Workout Partner tracks your movement using your camera to count " +
                    "Reps and estimate form. It isn't a substitute for professional " +
                    "medical or fitness guidance. Stop immediately if you feel pain " +
                    "or discomfort, and consult a professional before starting any " +
                    "new exercise routine.",
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAcknowledge) { Text("I understand") }
        }
    }
}
