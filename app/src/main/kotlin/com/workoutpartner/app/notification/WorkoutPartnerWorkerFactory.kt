package com.workoutpartner.app.notification

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.workoutpartner.app.di.AppContainer

/**
 * Hands [DailyReminderWorker] [AppContainer]'s existing singletons instead
 * of it re-constructing its own database/repository instances and reaching
 * past [com.workoutpartner.data.AuthGateway] to raw Firebase — the fix for
 * a real DI/layering issue `/code-review` caught in an earlier draft (see
 * ticket 12's Comments).
 *
 * Registered via [com.workoutpartner.app.WorkoutPartnerApplication] calling
 * `WorkManager.initialize(...)` itself, with the default `androidx.startup`
 * auto-initializer disabled in the manifest — WorkManager's own
 * auto-initializer runs before `Application.onCreate()`, too early for
 * [AppContainer] to exist yet.
 */
class WorkoutPartnerWorkerFactory(private val container: AppContainer) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            DailyReminderWorker::class.java.name -> DailyReminderWorker(
                context = appContext,
                params = workerParameters,
                authGateway = container.authGateway,
                accountRepository = container.accountRepository,
                setRepository = container.setRepository,
            )
            else -> null
        }
}
