package com.workoutpartner.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.workoutpartner.app.data.BundledRoutines
import com.workoutpartner.app.di.AppContainer
import com.workoutpartner.app.session.RoutinePickerScreen
import com.workoutpartner.app.session.SessionScreen
import com.workoutpartner.app.ui.theme.WorkoutPartnerTheme
import com.workoutpartner.data.RoutineWithSteps

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as WorkoutPartnerApplication).container
        setContent {
            WorkoutPartnerTheme {
                WorkoutPartnerApp(container)
            }
        }
    }
}

/**
 * The app's root composable (ticket 09 onward): seeds the bundled Routines
 * on first run, gates the Session flow behind the Camera runtime permission
 * (declared in the manifest since ticket 01, requested here since it's the
 * first ticket that actually needs it), and switches over [AppScreen].
 *
 * Guest by default (`accountId = null`) until ticket 13 wires in real
 * Guest-vs-signed-in state from `AuthRepository.authState` — out of this
 * ticket's scope.
 */
@Composable
fun WorkoutPartnerApp(container: AppContainer) {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.RoutinePicker) }
    var routines by remember { mutableStateOf<List<RoutineWithSteps>>(emptyList()) }
    val accountId: String? = null

    LaunchedEffect(Unit) {
        BundledRoutines.seedIfEmpty(container.database.routineDao())
        routines = container.database.routineDao().getAllRoutinesWithSteps()
    }

    when (val current = screen) {
        AppScreen.RoutinePicker -> RoutinePickerScreen(
            routines = routines,
            onRoutineSelected = { screen = AppScreen.Session(it) },
        )
        is AppScreen.Session -> CameraPermissionGate {
            SessionScreen(
                routine = current.routine,
                accountId = accountId,
                setRepository = container.setRepository,
                accountRepository = container.accountRepository,
                poseTrackerFactory = container::createPoseTracker,
                onSessionComplete = { screen = AppScreen.RoutinePicker },
            )
        }
    }
}

/** Requests the Camera permission (manifest-declared since ticket 01) before showing anything that needs it, per Android's runtime-permission model. */
@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        content()
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera access is needed to track your Reps.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Grant camera access")
                    }
                }
            }
        }
    }
}
