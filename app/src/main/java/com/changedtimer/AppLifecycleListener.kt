package com.changedtimer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Intent
import android.util.Log

class AppLifecycleListener : Application.ActivityLifecycleCallbacks {
    companion object {
        private const val TAG = "AppLifecycleListener"
    }

    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    
    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            // App entered foreground
            Log.d(TAG, "App entered foreground")
            notifyService(activity, true)
        }
    }
    
    override fun onActivityResumed(activity: Activity) {}
    
    override fun onActivityPaused(activity: Activity) {}
    
    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            // App entered background
            Log.d(TAG, "App entered background")
            notifyService(activity, false)
        }
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    
    override fun onActivityDestroyed(activity: Activity) {}

    private fun notifyService(activity: Activity, isForeground: Boolean) {
        try {
            val intent = Intent(activity, TimerService::class.java).apply {
                action = if (isForeground) {
                    TimerService.ACTION_APP_FOREGROUND
                } else {
                    TimerService.ACTION_APP_BACKGROUND
                }
            }
            activity.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify service", e)
        }
    }
}