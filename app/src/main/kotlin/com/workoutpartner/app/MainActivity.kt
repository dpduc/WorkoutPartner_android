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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.workoutpartner.app.beforeyoustart.BeforeYouStartScreen
import com.workoutpartner.app.beforeyoustart.FormGuidePrefs
import com.workoutpartner.app.data.BundledRoutines
import com.workoutpartner.app.di.AppContainer
import com.workoutpartner.app.onboarding.DisclaimerScreen
import com.workoutpartner.app.onboarding.GuestConversionDialog
import com.workoutpartner.app.onboarding.OnboardingPrefs
import com.workoutpartner.app.onboarding.ProfileSetupScreen
import com.workoutpartner.app.onboarding.SignInScreen
import com.workoutpartner.app.onboarding.SignUpScreen
import com.workoutpartner.app.onboarding.WelcomeScreen
import com.workoutpartner.app.progress.ProgressScreen
import com.workoutpartner.app.quickcount.QuickCountRunScreen
import com.workoutpartner.app.quickcount.QuickCountSetupScreen
import com.workoutpartner.app.quickcount.RosterScreen
import com.workoutpartner.app.quickcount.TallyHistoryScreen
import com.workoutpartner.app.routines.BodyStats
import com.workoutpartner.app.routines.RoutineDifficulty
import com.workoutpartner.app.routines.toBodyStats
import com.workoutpartner.app.session.RoutinePickerScreen
import com.workoutpartner.app.session.SessionScreen
import com.workoutpartner.app.settings.SettingsScreen
import com.workoutpartner.app.ui.components.LogoOrientation
import com.workoutpartner.app.ui.components.WorkoutPartnerBrandLogo
import com.workoutpartner.app.ui.theme.WorkoutPartnerTheme
import com.workoutpartner.data.AuthState
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
    val formGuidePrefs = remember { FormGuidePrefs(context) }
    var screen by remember {
        mutableStateOf<AppScreen>(
            when {
                !prefs.hasSeenDisclaimer -> AppScreen.Disclaimer
                !prefs.hasChosenHowToStart -> AppScreen.Welcome
                !prefs.hasCompletedProfile -> AppScreen.ProfileSetup
                else -> AppScreen.MainMenu
            },
        )
    }
    var routines by remember { mutableStateOf<List<RoutineWithSteps>>(emptyList()) }
    var showGuestConversionPrompt by remember { mutableStateOf(false) }

    // container.authRepository is always available (ticket 15): it falls
    // back to a local, offline LocalAuthGateway when Firebase isn't
    // configured (see AppContainer's doc comment), so Sign up/Sign in
    // always render regardless of whether google-services.json exists.
    val authRepository = container.authRepository
    val authState by authRepository.authState.collectAsState(initial = AuthState.Guest)
    val accountId = (authState as? AuthState.SignedIn)?.accountId

    // The Account's (or, while a Guest, the GuestProfileEntity's) body
    // stats — feeds RoutineDifficulty (workout-partner-v2 ticket 02).
    // Re-fetched whenever accountId changes (Guest -> Account on sign-up/
    // in) so a stale pre-signup Guest reading never lingers after that.
    var bodyStats by remember { mutableStateOf<BodyStats?>(null) }
    LaunchedEffect(accountId) {
        bodyStats = if (accountId != null) {
            container.accountRepository.getAccount(accountId)?.toBodyStats()
        } else {
            container.accountRepository.getGuestProfile()?.toBodyStats()
        }
    }
    val difficultyTier = RoutineDifficulty.compute(bodyStats)

    LaunchedEffect(Unit) {
        BundledRoutines.seedIfEmpty(container.database.routineDao())
        routines = container.database.routineDao().getAllRoutinesWithSteps()
    }

    NotificationPermissionRequester()

    when (val current = screen) {
        AppScreen.Disclaimer -> DisclaimerScreen(
            onAcknowledge = {
                prefs.hasSeenDisclaimer = true
                screen = when {
                    !prefs.hasChosenHowToStart -> AppScreen.Welcome
                    !prefs.hasCompletedProfile -> AppScreen.ProfileSetup
                    else -> AppScreen.MainMenu
                }
            },
        )
        AppScreen.Welcome -> WelcomeScreen(
            onContinueAsGuest = { prefs.hasChosenHowToStart = true; screen = AppScreen.ProfileSetup },
            onSignUp = { screen = AppScreen.SignUp },
            onSignIn = { screen = AppScreen.SignIn },
        )
        AppScreen.SignUp -> SignUpScreen(
            authRepository = authRepository,
            onSignedUp = {
                prefs.hasChosenHowToStart = true
                // A Guest who already answered ProfileSetup and is only now
                // converting (GuestConversionDialog) had that answer already
                // claimed onto the new Account by signUp's own migration
                // (AccountRepository.claimGuestData) — no need to ask again.
                screen = if (prefs.hasCompletedProfile) AppScreen.MainMenu else AppScreen.ProfileSetup
            },
            onCancel = { screen = AppScreen.Welcome },
        )
        AppScreen.SignIn -> SignInScreen(
            authRepository = authRepository,
            onSignedIn = {
                prefs.hasChosenHowToStart = true
                // An existing Account already has a profile from its own
                // prior sign-up — nothing to collect on this device.
                prefs.hasCompletedProfile = true
                screen = AppScreen.MainMenu
            },
            onCancel = { screen = AppScreen.Welcome },
        )
        AppScreen.ProfileSetup -> ProfileSetupScreen(
            onSubmit = { name, age, heightCm, weightKg, activityLevel ->
                val currentAccountId = accountId
                if (currentAccountId != null) {
                    container.accountRepository.updateProfile(currentAccountId, name, age, heightCm, weightKg, activityLevel)
                } else {
                    container.accountRepository.saveGuestProfile(name, age, heightCm, weightKg, activityLevel)
                }
                // The accountId-keyed LaunchedEffect above won't re-fire for
                // a Guest (accountId stays null) — update directly so
                // RoutineDifficulty sees this answer immediately.
                bodyStats = BodyStats(age, heightCm, weightKg, activityLevel)
            },
            onDone = { prefs.hasCompletedProfile = true; screen = AppScreen.MainMenu },
        )
        AppScreen.MainMenu -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { WorkoutPartnerBrandLogo(orientation = LogoOrientation.HORIZONTAL, size = 36.dp) },
                    actions = {
                        OverflowMenu(
                            onProgress = { screen = AppScreen.Progress },
                            onSettings = { screen = AppScreen.Settings },
                        )
                    },
                )
            },
        ) { padding ->
            MainMenuScreen(
                onWorkouts = { screen = AppScreen.RoutinePicker },
                // Quick Count works fully for a Guest since
                // `workout-partner-v3` ticket 06 (ADR-0007) — no more
                // Roster-or-Sign-up branch here.
                onQuickCount = { screen = AppScreen.Roster },
                modifier = Modifier.padding(padding),
            )
        }
        AppScreen.RoutinePicker -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Workouts") },
                    navigationIcon = { TextButton(onClick = { screen = AppScreen.MainMenu }) { Text("Back") } },
                )
            },
        ) { padding ->
            RoutinePickerScreen(
                routines = routines,
                difficultyTier = difficultyTier,
                onRoutineSelected = { screen = AppScreen.WorkoutOverview(it) },
                modifier = Modifier.padding(padding),
            )
        }
        is AppScreen.WorkoutOverview -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(current.routine.routine.name) },
                    navigationIcon = { TextButton(onClick = { screen = AppScreen.RoutinePicker }) { Text("Back") } },
                )
            },
        ) { padding ->
            BeforeYouStartScreen(
                routine = current.routine,
                difficultyTier = difficultyTier,
                defaultToStepJack = RoutineDifficulty.isObese(bodyStats),
                formGuidePrefs = formGuidePrefs,
                onReadyForSession = { jumpingJackVariant -> screen = AppScreen.Session(current.routine, jumpingJackVariant) },
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
                    difficultyTier = difficultyTier,
                    jumpingJackVariant = current.jumpingJackVariant,
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
        AppScreen.Settings -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = { TextButton(onClick = { screen = AppScreen.MainMenu }) { Text("Back") } },
                )
            },
        ) { padding ->
            SettingsScreen(
                accountId = accountId,
                accountRepository = container.accountRepository,
                authRepository = authRepository,
                onSignedOut = { screen = AppScreen.MainMenu },
                onSignUp = { screen = AppScreen.SignUp },
                onSignIn = { screen = AppScreen.SignIn },
                onViewProgress = { screen = AppScreen.Progress },
                modifier = Modifier.padding(padding),
            )
        }
        AppScreen.Progress -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Progress") },
                    navigationIcon = { TextButton(onClick = { screen = AppScreen.MainMenu }) { Text("Back") } },
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
        AppScreen.Roster -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Roster") },
                    navigationIcon = { TextButton(onClick = { screen = AppScreen.MainMenu }) { Text("Back") } },
                )
            },
        ) { padding ->
            // accountId null (Guest) works the same as an Account holder's
            // Roster — `workout-partner-v3` ticket 06 (ADR-0007).
            RosterScreen(
                accountId = accountId,
                rosterRepository = container.rosterRepository,
                onProfileSelected = { screen = AppScreen.QuickCountSetup(it) },
                onViewHistory = { screen = AppScreen.TallyHistory(it) },
                modifier = Modifier.padding(padding),
            )
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

/**
 * [AppScreen.MainMenu]'s own overflow menu: Progress and Settings. Roster/
 * Sign-up used to live here too before `workout-partner-v2` ticket 02
 * promoted Quick Count to its own top-level tile on [MainMenuScreen] — kept
 * as a plain top-bar overflow rather than adding a third tile, since
 * Progress/Settings aren't first-class sections the way Workouts/Quick
 * Count are. Both entries work the same for a Guest and an Account holder
 * (`workout-partner-v3` ticket 06, ADR-0007), so there's nothing left for
 * this menu itself to branch on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowMenu(onProgress: () -> Unit, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("Progress") }, onClick = { expanded = false; onProgress() })
        DropdownMenuItem(text = { Text("Settings") }, onClick = { expanded = false; onSettings() })
    }
}

/**
 * The app's home screen (`workout-partner-v2` ticket 02): two top-level
 * sections — Workouts (pre-built Routines, tuned/tagged per
 * [com.workoutpartner.app.routines.RoutineDifficulty]) and Quick Count, both
 * available to a Guest and an Account holder alike (`workout-partner-v3`
 * ticket 06, ADR-0007).
 */
@Composable
private fun MainMenuScreen(onWorkouts: () -> Unit, onQuickCount: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onWorkouts)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Workouts", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "HIIT, Tabata, and AMRAP-tagged Routines, tuned to you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onQuickCount)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Quick Count", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Count reps for one Exercise — reps, time, and average form score.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
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
