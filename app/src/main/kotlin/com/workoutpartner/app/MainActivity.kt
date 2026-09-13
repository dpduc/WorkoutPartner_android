package com.workoutpartner.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.workoutpartner.app.data.BundledRoutines
import com.workoutpartner.app.di.AppContainer
import com.workoutpartner.app.onboarding.DisclaimerScreen
import com.workoutpartner.app.onboarding.GuestConversionDialog
import com.workoutpartner.app.onboarding.OnboardingPrefs
import com.workoutpartner.app.onboarding.SignInScreen
import com.workoutpartner.app.onboarding.SignUpScreen
import com.workoutpartner.app.onboarding.WelcomeScreen
import com.workoutpartner.app.progress.ProgressScreen
import com.workoutpartner.app.quickcount.QuickCountRunScreen
import com.workoutpartner.app.quickcount.QuickCountSetupScreen
import com.workoutpartner.app.quickcount.RosterScreen
import com.workoutpartner.app.quickcount.TallyHistoryScreen
import com.workoutpartner.app.session.RoutinePickerScreen
import com.workoutpartner.app.session.SessionScreen
import com.workoutpartner.app.ui.theme.WorkoutPartnerTheme
import com.workoutpartner.data.AuthState
import com.workoutpartner.data.RoutineWithSteps
import kotlinx.coroutines.flow.flowOf

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
 * The app's root composable: safety disclaimer -> "how do you want to
 * start" (Guest/Sign up/Sign in) on first launch only (ticket 13, spec.md
 * stories 1, 22), persisted via [OnboardingPrefs] so returning users skip
 * straight to [AppScreen.RoutinePicker]; seeds the bundled Routines on
 * first run; gates the Session/Quick Count flows behind the Camera runtime
 * permission; best-effort requests the notification permission ticket 12's
 * daily reminder needs on Android 13+; and switches over [AppScreen].
 *
 * `accountId` is real Guest-vs-Account state (ticket 13), derived from
 * [com.workoutpartner.data.AuthRepository.authState] — not the hardcoded
 * `null` earlier tickets used as a placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPartnerApp(container: AppContainer) {
    val context = LocalContext.current
    val prefs = remember { OnboardingPrefs(context) }
    var screen by remember {
        mutableStateOf<AppScreen>(
            when {
                !prefs.hasSeenDisclaimer -> AppScreen.Disclaimer
                !prefs.hasChosenHowToStart -> AppScreen.Welcome
                else -> AppScreen.RoutinePicker
            },
        )
    }
    var routines by remember { mutableStateOf<List<RoutineWithSteps>>(emptyList()) }
    var showGuestConversionPrompt by remember { mutableStateOf(false) }

    // container.authRepository is null if Firebase isn't configured yet
    // (still no google-services.json — ticket 01/07's disclosed gap; see
    // AppContainer's doc comment for why that's handled there, not here).
    // Falls back to Guest behavior instead of crashing; Sign up/Sign in
    // stay genuinely unavailable until that file lands.
    val authRepository = container.authRepository
    val authState by (authRepository?.authState ?: flowOf(AuthState.Guest)).collectAsState(initial = AuthState.Guest)
    val accountId = (authState as? AuthState.SignedIn)?.accountId

    LaunchedEffect(Unit) {
        BundledRoutines.seedIfEmpty(container.database.routineDao())
        routines = container.database.routineDao().getAllRoutinesWithSteps()
    }

    NotificationPermissionRequester()

    when (val current = screen) {
        AppScreen.Disclaimer -> DisclaimerScreen(
            onAcknowledge = {
                prefs.hasSeenDisclaimer = true
                screen = if (prefs.hasChosenHowToStart) AppScreen.RoutinePicker else AppScreen.Welcome
            },
        )
        AppScreen.Welcome -> WelcomeScreen(
            onContinueAsGuest = { prefs.hasChosenHowToStart = true; screen = AppScreen.RoutinePicker },
            onSignUp = { screen = AppScreen.SignUp },
            onSignIn = { screen = AppScreen.SignIn },
        )
        AppScreen.SignUp -> {
            val repo = authRepository
            if (repo == null) {
                AuthUnavailableScreen(onBack = { screen = AppScreen.Welcome })
            } else {
                SignUpScreen(
                    authRepository = repo,
                    onSignedUp = { prefs.hasChosenHowToStart = true; screen = AppScreen.RoutinePicker },
                    onCancel = { screen = AppScreen.Welcome },
                )
            }
        }
        AppScreen.SignIn -> {
            val repo = authRepository
            if (repo == null) {
                AuthUnavailableScreen(onBack = { screen = AppScreen.Welcome })
            } else {
                SignInScreen(
                    authRepository = repo,
                    onSignedIn = { prefs.hasChosenHowToStart = true; screen = AppScreen.RoutinePicker },
                    onCancel = { screen = AppScreen.Welcome },
                )
            }
        }
        AppScreen.RoutinePicker -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Workout Partner") },
                    actions = {
                        TextButton(onClick = { screen = AppScreen.Progress }) { Text("Progress") }
                        // Roster/Quick Count is Account-holder only (spec.md
                        // stories 34-41) — hidden for a Guest, same
                        // accountId == null gating as Progress.
                        if (accountId != null) {
                            TextButton(onClick = { screen = AppScreen.Roster }) { Text("Roster") }
                        } else {
                            TextButton(onClick = { screen = AppScreen.SignUp }) { Text("Sign up") }
                        }
                    },
                )
            },
        ) { padding ->
            RoutinePickerScreen(
                routines = routines,
                onRoutineSelected = { screen = AppScreen.Session(it) },
                modifier = Modifier.padding(padding),
            )
        }
        is AppScreen.Session -> {
            // Shown at most once per Session (not once per Set — spec
            // story 3 asks for the prompt "after finishing a Set," but
            // re-showing it after every Set in a multi-Set Routine would
            // be a nag, not a nudge). Fresh `remember` per new Session
            // screen instance, since Compose leaves and re-enters this
            // branch's composition each time a Session starts.
            var hasShownGuestPromptThisSession by remember { mutableStateOf(false) }

            CameraPermissionGate {
                SessionScreen(
                    routine = current.routine,
                    accountId = accountId,
                    setRepository = container.setRepository,
                    accountRepository = container.accountRepository,
                    poseTrackerFactory = container::createPoseTracker,
                    onSetFinished = {
                        // The post-Set prompt to create an Account while
                        // still a Guest (ticket 13, spec.md story 3) — this
                        // hook, not the Session flow itself (ticket 09's own
                        // scope boundary).
                        if (accountId == null && !hasShownGuestPromptThisSession) {
                            showGuestConversionPrompt = true
                            hasShownGuestPromptThisSession = true
                        }
                    },
                    onSessionComplete = { screen = AppScreen.RoutinePicker },
                )
            }
        }
        AppScreen.Progress -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Progress") },
                    navigationIcon = { TextButton(onClick = { screen = AppScreen.RoutinePicker }) { Text("Back") } },
                )
            },
        ) { padding ->
            ProgressScreen(
                accountId = accountId,
                accountRepository = container.accountRepository,
                setRepository = container.setRepository,
                modifier = Modifier.padding(padding),
            )
        }
        AppScreen.Roster -> {
            if (accountId == null) {
                screen = AppScreen.RoutinePicker
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Roster") },
                            navigationIcon = { TextButton(onClick = { screen = AppScreen.RoutinePicker }) { Text("Back") } },
                        )
                    },
                ) { padding ->
                    RosterScreen(
                        accountId = accountId,
                        rosterRepository = container.rosterRepository,
                        onProfileSelected = { screen = AppScreen.QuickCountSetup(it) },
                        onViewHistory = { screen = AppScreen.TallyHistory(it) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
        is AppScreen.QuickCountSetup -> QuickCountSetupScreen(
            profile = current.profile,
            onStart = { exercise, target -> screen = AppScreen.QuickCountRun(current.profile, exercise, target) },
        )
        is AppScreen.QuickCountRun -> CameraPermissionGate {
            QuickCountRunScreen(
                trackedProfileId = current.profile.id,
                exercise = current.exercise,
                target = current.target,
                tallyRepository = container.tallyRepository,
                poseTrackerFactory = container::createPoseTracker,
                onDone = { screen = AppScreen.Roster },
            )
        }
        is AppScreen.TallyHistory -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("History") },
                    navigationIcon = { TextButton(onClick = { screen = AppScreen.Roster }) { Text("Back") } },
                )
            },
        ) { padding ->
            TallyHistoryScreen(
                profile = current.profile,
                tallyRepository = container.tallyRepository,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showGuestConversionPrompt) {
        GuestConversionDialog(
            onSignUp = { showGuestConversionPrompt = false; screen = AppScreen.SignUp },
            onDismiss = { showGuestConversionPrompt = false },
        )
    }
}

@Composable
private fun AuthUnavailableScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sign-up/sign-in isn't available on this build yet.", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
            }
        }
    }
}

/**
 * Best-effort requests the POST_NOTIFICATIONS permission (ticket 12) once,
 * on Android 13+ where it's required at all. Unlike [CameraPermissionGate],
 * this doesn't block anything — the daily reminder is a background,
 * non-critical feature (this ticket's own scope: "no push/FCM... a simple
 * reminder"), so there's no screen worth withholding over a denial; the
 * Worker itself checks the permission again before posting and silently
 * skips if it's still not granted.
 */
@Composable
private fun NotificationPermissionRequester() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Requests the Camera permission (manifest-declared since ticket 01) before showing anything that needs it, per Android's runtime-permission model. */
@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera access is needed to track your Reps.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Grant camera access")
                    }
                }
            }
        }
    }
}
