package com.changedtimer

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val intentAction = intent.action
            Log.d(TAG, "Received action: $intentAction")
            
            when (intentAction) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    handleBootOrUpdate(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in BootReceiver", e)
        }
    }

    private fun handleBootOrUpdate(context: Context) {
        try {
            Log.d(TAG, "Device booted or app updated - checking timer state")
            
            val sharedPrefs = context.getSharedPreferences("TimerAppPrefs", Context.MODE_PRIVATE)
            val availableTime = sharedPrefs.getInt("available_time", 0)
            
            if (availableTime > 0) {
                Log.d(TAG, "Found saved time: $availableTime seconds")
                
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                
                if (powerManager != null && keyguardManager != null) {
                    val isScreenOn = powerManager.isInteractive
                    val isLocked = keyguardManager.isKeyguardLocked
                    
                    Log.d(TAG, "Device state - ScreenOn: $isScreenOn, Locked: $isLocked")
                    
                    if (isScreenOn && !isLocked) {
                        startTimerService(context, availableTime)
                    } else {
                        Log.d(TAG, "Device is locked - timer will start when unlocked")
                    }
                } else {
                    Log.e(TAG, "Failed to get system services")
                }
            } else {
                Log.d(TAG, "No saved time found - timer will not start")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling boot or update", e)
        }
    }

    private fun startTimerService(context: Context, availableTime: Int) {
        try {
            Log.d(TAG, "Starting timer service - device is unlocked")
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_UPDATE_TIME
                putExtra(TimerService.EXTRA_TIME_SECONDS, availableTime)
            }
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start timer service", e)
        }
    }
}