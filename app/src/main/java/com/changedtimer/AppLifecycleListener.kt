package com.changedtimer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.util.Log

class AppLifecycleListener : Application.ActivityLifecycleCallbacks {
    companion object {
        private const val TAG = "AppLifecycleListener"
        
        @JvmStatic
        var isAppInForeground: Boolean = false
            private set
    }

    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d(TAG, "Activity created: ${activity::class.simpleName}")
    }
    
    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            // App went to foreground
            isAppInForeground = true
            Log.d(TAG, "📱 APP WENT TO FOREGROUND - Timer should PAUSE")
            broadcastAppState(activity.applicationContext, true)
        }
        Log.d(TAG, "Activity started: ${activity::class.simpleName} (refs: $activityReferences)")
    }
    
    override fun onActivityResumed(activity: Activity) {
        Log.d(TAG, "Activity resumed: ${activity::class.simpleName}")
    }
    
    override fun onActivityPaused(activity: Activity) {
        Log.d(TAG, "Activity paused: ${activity::class.simpleName}")
    }
    
    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            // App went to background
            isAppInForeground = false
            Log.d(TAG, "🏠 APP WENT TO BACKGROUND - Timer should START (if phone unlocked)")
            broadcastAppState(activity.applicationContext, false)
        }
        Log.d(TAG, "Activity stopped: ${activity::class.simpleName} (refs: $activityReferences)")
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d(TAG, "Activity saving state: ${activity::class.simpleName}")
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "Activity destroyed: ${activity::class.simpleName}")
    }

    private fun broadcastAppState(context: Context, isForeground: Boolean) {
        val intent = Intent("com.changedtimer.APP_STATE_CHANGED").apply {
            putExtra("is_foreground", isForeground)
        }
        context.sendBroadcast(intent)
        
        Log.d(TAG, "Broadcasted app state change: foreground=$isForeground")
    }
}