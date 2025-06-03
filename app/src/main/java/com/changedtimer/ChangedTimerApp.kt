package com.changedtimer

import android.app.Application

class ChangedTimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(AppLifecycleListener())
    }
} 