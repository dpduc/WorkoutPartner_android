package com.workoutpartner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.workoutpartner.app.ui.theme.WorkoutPartnerTheme

/**
 * Baseline app shell for ticket 01 (project scaffold). Deliberately empty —
 * no feature logic lives here yet; later tickets replace this with the real
 * navigation graph and screens (session flow, progress, quick count, etc.).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutPartnerTheme {
                EmptyShellScreen()
            }
        }
    }
}

@Composable
private fun EmptyShellScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Workout Partner")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyShellScreenPreview() {
    WorkoutPartnerTheme {
        EmptyShellScreen()
    }
}
