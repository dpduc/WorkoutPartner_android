package com.workoutpartner.app.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** The post-Set prompt to create an Account while still a Guest (ticket 13, spec.md story 3). Shown by [com.workoutpartner.app.WorkoutPartnerApp] after a Guest's Session summary, not by the Session flow itself (ticket 09's own scope boundary). */
@Composable
fun GuestConversionDialog(onSignUp: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create an Account?") },
        text = { Text("Sign up to save this progress permanently and sync it across devices — right now it's only saved on this one.") },
        confirmButton = { TextButton(onClick = onSignUp) { Text("Sign up") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
