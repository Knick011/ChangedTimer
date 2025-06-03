package com.changedtimer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Context
import android.content.Intent

class AppLifecycleListener : Application.ActivityLifecycleCallbacks {
    companion object {
        var isAppInForeground: Boolean = false
    }

    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            isAppInForeground = true
            broadcastAppState(activity.applicationContext, true)
        }
    }
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            isAppInForeground = false
            broadcastAppState(activity.applicationContext, false)
        }
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun broadcastAppState(context: Context, isForeground: Boolean) {
        val intent = Intent("com.changedtimer.APP_STATE_CHANGED").apply {
            putExtra("is_foreground", isForeground)
        }
        context.sendBroadcast(intent)

        // Also trigger ScreenStateReceiver logic
        val screenStateIntent = Intent(context, ScreenStateReceiver::class.java)
        context.sendBroadcast(screenStateIntent)
    }
} 