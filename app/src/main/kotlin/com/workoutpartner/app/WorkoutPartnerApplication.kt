package com.workoutpartner.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.workoutpartner.app.di.AppContainer
import com.workoutpartner.app.notification.DailyReminderWorker
import com.workoutpartner.app.notification.WorkoutPartnerWorkerFactory

class WorkoutPartnerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // WorkManager's own auto-initializer is disabled in the manifest
        // (it would run before this line, too early for `container` to
        // exist) — initialize it here instead, with a WorkerFactory that
        // hands DailyReminderWorker AppContainer's real singletons.
        WorkManager.initialize(this, Configuration.Builder().setWorkerFactory(WorkoutPartnerWorkerFactory(container)).build())
        // Idempotent (KEEP) — safe to call on every process start.
        DailyReminderWorker.schedule(WorkManager.getInstance(this))
    }
}
