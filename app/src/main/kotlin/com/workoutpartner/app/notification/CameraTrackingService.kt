package com.workoutpartner.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.workoutpartner.app.R

/**
 * A `foregroundServiceType="camera"` [Service] (camera-session-robustness
 * ticket 02) that exists only to keep the camera alive if the app briefly
 * leaves the foreground while [com.workoutpartner.core.posetracking.CameraPoseTracker]
 * is running a Session/Quick Count — an incoming call, a notification
 * tap-away, a split-screen swap. Android 14+ can otherwise reclaim the
 * camera or kill the process the moment the app stops being the
 * foreground-focused one.
 *
 * Never started directly — [CameraTrackingSession] owns the actual
 * start/stop decisions (reference-counted across possibly more than one
 * [com.workoutpartner.core.posetracking.CameraPoseTracker] instance in a
 * row, see its own doc comment) and is what `CameraPoseTracker` itself
 * talks to. Unbound (`onBind` returns null): nothing needs to bind to this,
 * it's purely a start/stop signal plus the notification Android 14+
 * requires while it runs.
 *
 * Not unit-tested — a thin `Service`/`NotificationManager` framework
 * adapter, the same caveat as `CameraPoseTracker` itself; verified on a
 * real device only.
 */
class CameraTrackingService : Service() {

    override fun onCreate() {
        super.onCreate()
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } catch (e: Exception) {
            // Missing FOREGROUND_SERVICE_CAMERA (or the manifest's foregroundServiceType
            // declaration) throws a SecurityException here, not synchronously at whatever
            // called startForegroundService() — CameraTrackingSession is what's listening
            // for this, on behalf of whichever CameraPoseTracker is currently active.
            CameraTrackingSession.reportStartFailure(e.message ?: "Couldn't start camera tracking.")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    // Nothing binds to this service — it's controlled entirely through start/stop Intents.
    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.camera_tracking_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.camera_tracking_notification_title))
            .setContentText(getString(R.string.camera_tracking_notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "camera_tracking"
        const val NOTIFICATION_ID = 2
    }
}
