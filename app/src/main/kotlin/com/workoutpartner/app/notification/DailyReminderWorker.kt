package com.workoutpartner.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.workoutpartner.app.R
import com.workoutpartner.app.progress.ProgressStats
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.AuthGateway
import com.workoutpartner.data.SetRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * The streak-aware daily reminder (ticket 12, spec.md user story 33) — a
 * local WorkManager job, no push/FCM (this ticket's own scope: "no
 * push/FCM — this is local-only per spec's module description").
 *
 * [authGateway]/[accountRepository]/[setRepository] are [AppContainer][com.workoutpartner.app.di.AppContainer]'s
 * own singletons, handed in by [WorkoutPartnerWorkerFactory] — not
 * re-constructed here, and not reached-for via raw `FirebaseAuth` either.
 * An earlier draft did both (a fresh `createDatabase(...)` per run, and
 * `FirebaseAuth.getInstance().currentUser` bypassing ticket 07's
 * [AuthGateway] abstraction); `/code-review` caught it, see ticket 12's
 * Comments for the reasoning.
 *
 * [AuthGateway.currentUserId] null means Guest, the same distinction
 * [com.workoutpartner.data.AuthState] uses — since `workout-partner-v3`
 * ticket 06 (ADR-0007), a Guest can enable this reminder too, so that no
 * longer skips the check; it instead reads the device's single Guest's
 * [com.workoutpartner.data.GuestProfileEntity] row in place of an Account's.
 *
 * Not unit-tested itself — [ReminderPolicy] carries the logic worth
 * testing; this class is thin fetch-and-notify glue needing a real Android
 * runtime (WorkManager, NotificationManager) to exercise, unverified beyond
 * compilation this session (no emulator/device available, google-services.json
 * still not bundled per ticket 01/07's disclosed gap).
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val authGateway: AuthGateway,
    private val accountRepository: AccountRepository,
    private val setRepository: SetRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accountId = authGateway.currentUserId.first()
        val today = LocalDate.now(clock)
        val activeDays = ProgressStats.activeDays(setRepository.getSetsForAccount(accountId), clock.zone)

        val (weeklyTarget, notificationsEnabled) = if (accountId != null) {
            val account = accountRepository.getAccount(accountId) ?: return Result.success()
            account.weeklyTarget to account.notificationsEnabled
        } else {
            val guest = accountRepository.getGuestProfile() ?: return Result.success()
            guest.weeklyTarget to guest.notificationsEnabled
        }

        if (ReminderPolicy.shouldRemind(activeDays, today, weeklyTarget, notificationsEnabled)) {
            showNotification()
        }
        return Result.success()
    }

    private fun showNotification() {
        val context = applicationContext
        ensureChannel(context)

        // Android 13+ requires the runtime POST_NOTIFICATIONS permission;
        // silently skip rather than crash if it hasn't been granted (the
        // permission request itself is wired from MainActivity).
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 1
        private const val UNIQUE_WORK_NAME = "daily_reminder_work"

        /** Schedules the recurring check — idempotent (KEEP), so calling this on every app start doesn't reset the schedule. */
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
