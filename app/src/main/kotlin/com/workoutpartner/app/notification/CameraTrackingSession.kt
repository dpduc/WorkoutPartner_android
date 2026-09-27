package com.workoutpartner.app.notification

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Starts/stops [CameraTrackingService] as [com.workoutpartner.core.posetracking.CameraPoseTracker]
 * instances come and go (camera-session-robustness ticket 02) — wired in as
 * `CameraPoseTracker`'s `onCameraSessionStarted`/`onCameraSessionStopped`
 * callbacks by [com.workoutpartner.app.di.AppContainer], the composition
 * root, so `core-pose-tracking` never depends on this app-module class
 * directly.
 *
 * Reference-counted rather than a plain 1:1 start/stop, because more than
 * one `CameraPoseTracker` can legitimately run in quick succession for what
 * reads as one continuous camera session to the person using the app —
 * Quick Count's Position Check tracker stops right before its Run tracker
 * starts (`workout-partner-v3` ticket 14, two different tracker instances).
 * A plain 1:1 mapping would stop and restart the service (and flash its
 * notification off and back on) for that handoff. Instead, dropping to zero
 * active trackers schedules a *delayed* stop; a new tracker starting within
 * [GRACE_PERIOD_MS] cancels it, so the service just keeps running.
 */
object CameraTrackingSession {

    private var activeCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var pendingStop: Runnable? = null
    private var startFailureListener: ((String) -> Unit)? = null

    /**
     * Call once a [com.workoutpartner.core.posetracking.CameraPoseTracker] has
     * successfully bound the camera.
     *
     * [startFailureListener] is only (re)assigned on the `activeCount == 1`
     * transition — the one call that actually starts the service and can
     * therefore actually fail. A second tracker joining an already-running
     * session (Quick Count's Position Check → Run handoff) must NOT overwrite
     * it: if it did, and the *first* tracker's still-pending, asynchronous
     * `startForeground()` call failed afterward, [reportStartFailure] would
     * route that failure to the second tracker's callback instead of the one
     * whose service-start attempt actually failed.
     */
    fun trackerStarted(context: Context, onStartFailure: (String) -> Unit) {
        pendingStop?.let(handler::removeCallbacks)
        pendingStop = null

        activeCount++
        if (activeCount == 1) {
            startFailureListener = onStartFailure
            ContextCompat.startForegroundService(context.applicationContext, Intent(context, CameraTrackingService::class.java))
        }
    }

    /** Call once that same tracker's `stop()` runs. */
    fun trackerStopped(context: Context) {
        activeCount = (activeCount - 1).coerceAtLeast(0)
        if (activeCount != 0) return

        val stop = Runnable {
            pendingStop = null
            if (activeCount == 0) context.applicationContext.stopService(Intent(context, CameraTrackingService::class.java))
        }
        pendingStop = stop
        handler.postDelayed(stop, GRACE_PERIOD_MS)
    }

    /** [CameraTrackingService] calls this if [androidx.core.app.ServiceCompat.startForeground] itself fails. */
    fun reportStartFailure(message: String) {
        startFailureListener?.invoke(message)
    }

    /** Milliseconds a zero-tracker gap is tolerated before the service actually stops — see this object's own doc comment. */
    private const val GRACE_PERIOD_MS = 2_000L
}
