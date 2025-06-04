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
        Log.d(TAG, "🔔 Received action: $action")
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        
        val isScreenOn = powerManager.isInteractive
        val isLocked = keyguardManager.isKeyguardLocked
        
        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "📱 SCREEN STATE: OFF")
                Log.d(TAG, "🔒 PHONE STATE: LOCKED (Screen OFF)")
                Log.d(TAG, "═══════════════════════════════════════")
                handleDeviceStateChange(context, true, false)
            }
            
            Intent.ACTION_SCREEN_ON -> {
                if (isLocked) {
                    Log.d(TAG, "═══════════════════════════════════════")
                    Log.d(TAG, "📱 SCREEN STATE: ON")
                    Log.d(TAG, "🔒 PHONE STATE: LOCKED (Lock screen showing)")
                    Log.d(TAG, "═══════════════════════════════════════")
                } else {
                    Log.d(TAG, "═══════════════════════════════════════")
                    Log.d(TAG, "📱 SCREEN STATE: ON")
                    Log.d(TAG, "🔓 PHONE STATE: UNLOCKED")
                    Log.d(TAG, "═══════════════════════════════════════")
                }
                handleDeviceStateChange(context, isLocked, true)
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "🔓 PHONE STATE: UNLOCKED (User dismissed keyguard)")
                Log.d(TAG, "📱 User is now present and device is UNLOCKED")
                Log.d(TAG, "═══════════════════════════════════════")
                handleDeviceStateChange(context, false, true)
            }
            
            "com.changedtimer.APP_STATE_CHANGED" -> {
                val isForeground = intent.getBooleanExtra("is_foreground", false)
                val appState = intent.getStringExtra("app_state") ?: "UNKNOWN"
                Log.d(TAG, "📱 App state changed notification received")
                Log.d(TAG, "   └─ App is now: $appState")
                handleDeviceStateChange(context, isLocked, isScreenOn)
            }
        }
    }
    
    private fun handleDeviceStateChange(context: Context, isLocked: Boolean, isScreenOn: Boolean) {
        // Get current app state
        val isAppInForeground = try {
            val clazz = Class.forName("com.changedtimer.AppLifecycleListener")
            val field = clazz.getDeclaredField("isAppInForeground")
            field.isAccessible = true
            field.getBoolean(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check app foreground state", e)
            false
        }
        
        val appState = try {
            val clazz = Class.forName("com.changedtimer.AppLifecycleListener")
            val field = clazz.getDeclaredField("currentAppState")
            field.isAccessible = true
            field.get(null) as String
        } catch (e: Exception) {
            "UNKNOWN"
        }
        
        // Log complete state
        Log.d(TAG, "")
        Log.d(TAG, "┌─────────────── DEVICE STATE SUMMARY ─────────────┐")
        Log.d(TAG, "│ 🔒 Phone Locked: ${if (isLocked) "YES" else "NO"}")
        Log.d(TAG, "│ 📺 Screen On: ${if (isScreenOn) "YES" else "NO"}")
        Log.d(TAG, "│ 📱 App State: $appState")
        Log.d(TAG, "│ 📍 App in Foreground: ${if (isAppInForeground) "YES" else "NO"}")
        Log.d(TAG, "└──────────────────────────────────────────────────┘")
        
        // Broadcast the state change to MainActivity
        val intent = Intent("com.changedtimer.DEVICE_STATE_CHANGED").apply {
            putExtra("is_locked", isLocked)
            putExtra("is_screen_on", isScreenOn)
            putExtra("app_state", appState)
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
        
        // Get current available time from shared preferences
        val sharedPrefs = context.getSharedPreferences("TimerAppPrefs", Context.MODE_PRIVATE)
        val availableTime = sharedPrefs.getInt("available_time", 0)
        
        // Timer logic
        val shouldTimerRun = !isLocked && !isAppInForeground && availableTime > 0
        
        Log.d(TAG, "")
        Log.d(TAG, "┌─────────────── TIMER DECISION ───────────────┐")
        Log.d(TAG, "│ Should Timer Run: ${if (shouldTimerRun) "YES ✅" else "NO ❌"}")
        Log.d(TAG, "│ Reason:")
        
        if (shouldTimerRun) {
            Log.d(TAG, "│   ✓ Phone is UNLOCKED")
            Log.d(TAG, "│   ✓ App is in BACKGROUND")
            Log.d(TAG, "│   ✓ Time available: ${availableTime}s")
            Log.d(TAG, "│ ➡️ STARTING TIMER")
        } else {
            if (isLocked) Log.d(TAG, "│   ✗ Phone is LOCKED")
            if (isAppInForeground) Log.d(TAG, "│   ✗ App is in FOREGROUND")
            if (availableTime <= 0) Log.d(TAG, "│   ✗ No time available")
            Log.d(TAG, "│ ⏸️ STOPPING/NOT STARTING TIMER")
        }
        Log.d(TAG, "└──────────────────────────────────────────────┘")
        Log.d(TAG, "")
        
        if (shouldTimerRun) {
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START_TIMER
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP_TIMER
            }
            context.startService(serviceIntent)
        }
    }
}