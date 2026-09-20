package com.workoutpartner.app.beforeyoustart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Before You Start countdown (`workout-partner-v3` ticket 13): huge
 * digits, readable from the ~2m the Athlete now stands at, counting down to
 * the first Set. Layout only — [BeforeYouStartScreen] drives the ticks and
 * the first-Set announcement.
 */
@Composable
fun CountdownScreen(secondsRemaining: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Get ready", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp))
                Text(
                    text = secondsRemaining.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 240.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}
