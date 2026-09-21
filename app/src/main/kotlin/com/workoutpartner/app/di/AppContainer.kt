package com.workoutpartner.app.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.workoutpartner.app.debug.isDebuggableBuild
import com.workoutpartner.core.posetracking.CameraPoseTracker
import com.workoutpartner.core.posetracking.VideoPoseTracker
import java.io.File
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.AuthGateway
import com.workoutpartner.data.AuthRepository
import com.workoutpartner.data.FirebaseAuthGateway
import com.workoutpartner.data.FirestoreSyncGateway
import com.workoutpartner.data.LocalAuthGateway
import com.workoutpartner.data.RemoteSyncGateway
import com.workoutpartner.data.RosterRepository
import com.workoutpartner.data.SetRepository
import com.workoutpartner.data.SyncEngine
import com.workoutpartner.data.TallyRepository
import com.workoutpartner.data.WorkoutPartnerDatabase
import com.workoutpartner.data.createDatabase

/**
 * Manual DI composition root — no DI framework (Hilt/Koin) is in any
 * ticket's scope. One instance lives for the process lifetime, held by
 * [com.workoutpartner.app.WorkoutPartnerApplication].
 *
 * [remoteSyncGateway] stays `by lazy`: constructing it touches
 * `FirebaseFirestore`, which needs Firebase to have been initialized from a
 * real `google-services.json` — still not bundled (ticket 01's original
 * gap, still open here; ticket 07/14 anticipated whoever adds it). Laziness
 * means the app can launch and use everything Room-backed (Sessions, Sets,
 * Roster, Tallies) without crashing at startup; only actually syncing
 * touches Firebase, and will fail until that file lands.
 *
 * [authGateway] used to be a plain `by lazy { FirebaseAuthGateway(...) }`
 * with the resulting `null` construction failure caught one level up, on
 * [authRepository] — ticket 13 first guarded this at its own call site in
 * `MainActivity` with a `runCatching`, but every consumer would have had to
 * remember to re-wrap it the same way, and `/code-review` flagged that as
 * the kind of thing that belongs in the one place that already owns "who
 * touches Firebase and when." Ticket 15 (`docs/auth-roadmap.md` Phase 1)
 * moved the fallback back down to this gateway seam now that there's a
 * real, non-throwing alternative to fall back to: [LocalAuthGateway]. That
 * means [authRepository] is never null anymore — Sign Up/Sign In always
 * render, whether or not Firebase is provisioned on this build.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: WorkoutPartnerDatabase = createDatabase(appContext)

    val accountRepository = AccountRepository(database)
    val setRepository = SetRepository(database, accountRepository)
    val tallyRepository = TallyRepository(database)
    val rosterRepository = RosterRepository(database)

    val authGateway: AuthGateway by lazy {
        runCatching { FirebaseAuthGateway(FirebaseAuth.getInstance()) }.getOrElse { LocalAuthGateway(appContext) }
    }
    val remoteSyncGateway: RemoteSyncGateway by lazy { FirestoreSyncGateway(FirebaseFirestore.getInstance()) }

    val authRepository: AuthRepository by lazy { AuthRepository(authGateway, accountRepository) }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            database.pendingSyncDao(),
            database.setDao(),
            database.tallyDao(),
            database.sessionDao(),
            database.trackedProfileDao(),
            remoteSyncGateway,
        )
    }

    /**
     * A fresh [CameraPoseTracker] per Session/Quick Count run (it owns a
     * camera binding, not a shareable singleton), using the MediaPipe model
     * bundled under `core-pose-tracking/src/main/assets/`.
     *
     * In a debuggable build, a `debug_video.mp4` in the app's private files
     * directory swaps the camera for a [VideoPoseTracker] playing that clip
     * — so reps and Form Scores can be checked without standing in front of
     * the camera (`adb push` it to `/data/local/tmp`, then
     * `run-as com.workoutpartner.app cp` it into `files/`). Release builds
     * never look for it.
     */
    fun createPoseTracker(): PoseTracker {
        val debugVideo = File(appContext.filesDir, DEBUG_VIDEO_NAME)
        return if (appContext.isDebuggableBuild() && debugVideo.exists()) VideoPoseTracker(appContext, debugVideo) else CameraPoseTracker(appContext)
    }

    private companion object {
        const val DEBUG_VIDEO_NAME = "debug_video.mp4"
    }
}
