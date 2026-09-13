package com.workoutpartner.app.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.workoutpartner.core.posetracking.CameraPoseTracker
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.AuthGateway
import com.workoutpartner.data.AuthRepository
import com.workoutpartner.data.FirebaseAuthGateway
import com.workoutpartner.data.FirestoreSyncGateway
import com.workoutpartner.data.GuestAccountMigration
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
 * [authGateway]/[remoteSyncGateway] (and everything built on them) stay
 * `by lazy`: constructing them touches `FirebaseAuth`/`FirebaseFirestore`,
 * which needs Firebase to have been initialized from a real
 * `google-services.json` — still not bundled (ticket 01's original gap,
 * still open here; ticket 07/14 anticipated whoever adds it). Laziness
 * means the app can launch and use everything Room-backed (Sessions, Sets,
 * Roster, Tallies) without crashing at startup; only actually signing in or
 * syncing touches Firebase, and will fail until that file lands.
 *
 * [authRepository] is nullable for the same reason, one level up: ticket 13
 * first guarded this at its own call site in `MainActivity` with a
 * `runCatching`, but every consumer would have had to remember to re-wrap
 * it the same way — `/code-review` flagged this as the kind of thing that
 * belongs in the one place that already owns "who touches Firebase and
 * when." Caught here instead, `by lazy` permanently memoizes the resulting
 * `null` (the failure never escapes the lazy block, so it's not retried on
 * next access, unlike a bare `by lazy { FirebaseAuthGateway(...) }` would
 * be if it kept throwing).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: WorkoutPartnerDatabase = createDatabase(appContext)

    val accountRepository = AccountRepository(database)
    val setRepository = SetRepository(database, accountRepository)
    val tallyRepository = TallyRepository(database)
    val rosterRepository = RosterRepository(database)

    val authGateway: AuthGateway by lazy { FirebaseAuthGateway(FirebaseAuth.getInstance()) }
    val remoteSyncGateway: RemoteSyncGateway by lazy { FirestoreSyncGateway(FirebaseFirestore.getInstance()) }

    val guestAccountMigration = GuestAccountMigration(accountRepository)

    /** Null if constructing the Firebase-backed auth stack failed (no `google-services.json` yet) — see this class's doc comment. Sign-up/sign-in are genuinely unavailable until that's provisioned; callers should treat null as "not available right now," not crash. */
    val authRepository: AuthRepository? by lazy {
        runCatching { AuthRepository(authGateway, accountRepository, onGuestDataToMigrate = guestAccountMigration::invoke) }.getOrNull()
    }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(database.pendingSyncDao(), database.setDao(), database.tallyDao(), remoteSyncGateway)
    }

    /**
     * A fresh [CameraPoseTracker] per Session/Quick Count run (it owns a
     * camera binding, not a shareable singleton). Needs the MediaPipe
     * `pose_landmarker_lite.task` model asset under
     * `core-pose-tracking/src/main/assets/`, which — per ticket 03's
     * disclosed gap — still isn't bundled; this will fail at runtime until
     * that's provisioned.
     */
    fun createPoseTracker(): PoseTracker = CameraPoseTracker(appContext)
}
