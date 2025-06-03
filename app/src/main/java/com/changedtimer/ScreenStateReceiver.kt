package com.changedtimer

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

class ScreenStateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenStateReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        
        val isScreenOn = powerManager.isInteractive
        val isLocked = keyguardManager.isKeyguardLocked
        
        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen turned OFF - Device is locked")
                handleDeviceStateChange(context, true, false)
            }
            
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen turned ON - isLocked: $isLocked")
                if (isLocked) {
                    // Screen on but still locked (lock screen showing)
                    handleDeviceStateChange(context, true, true)
                } else {
                    // Screen on and unlocked
                    handleDeviceStateChange(context, false, true)
                }
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User dismissed keyguard - Device unlocked")
                handleDeviceStateChange(context, false, true)
            }
            
            "com.changedtimer.APP_STATE_CHANGED" -> {
                Log.d(TAG, "App foreground/background state changed")
                handleDeviceStateChange(context, isLocked, isScreenOn)
            }
        }
    }
    
    private fun handleDeviceStateChange(context: Context, isLocked: Boolean, isScreenOn: Boolean) {
        Log.d(TAG, "Device state - Locked: $isLocked, ScreenOn: $isScreenOn")
        
        // Broadcast the state change to MainActivity
        val intent = Intent("com.changedtimer.DEVICE_STATE_CHANGED").apply {
            putExtra("is_locked", isLocked)
            putExtra("is_screen_on", isScreenOn)
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
        
        // Get current available time from shared preferences
        val sharedPrefs = context.getSharedPreferences("TimerAppPrefs", Context.MODE_PRIVATE)
        val availableTime = sharedPrefs.getInt("available_time", 0)
        
        // Check if app is in foreground
        val isAppInForeground = try {
            val clazz = Class.forName("com.changedtimer.AppLifecycleListener")
            val field = clazz.getDeclaredField("isAppInForeground")
            field.isAccessible = true
            field.getBoolean(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check app foreground state", e)
            false
        }
        
        // CORRECTED LOGIC:
        // Timer should run when:
        // 1. Phone is unlocked (!isLocked)
        // 2. App is in background (!isAppInForeground) 
        // 3. There is available time (availableTime > 0)
        //
        // Timer should pause when:
        // 1. Phone is locked (isLocked) OR
        // 2. App is in foreground (isAppInForeground) OR
        // 3. No time left (availableTime <= 0)
        
        val shouldTimerRun = !isLocked && !isAppInForeground && availableTime > 0
        
        if (shouldTimerRun) {
            Log.d(TAG, "✅ STARTING timer - Phone unlocked, app in background, time available")
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START_TIMER
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            val reason = when {
                isLocked -> "phone is locked"
                isAppInForeground -> "app is in foreground"
                availableTime <= 0 -> "no time available"
                else -> "unknown reason"
            }
            Log.d(TAG, "⏸️ STOPPING timer - $reason")
            
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP_TIMER
            }
            context.startService(serviceIntent)
        }
    }
}