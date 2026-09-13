package com.workoutpartner.app

import android.app.Application
import com.workoutpartner.app.di.AppContainer

class WorkoutPartnerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
