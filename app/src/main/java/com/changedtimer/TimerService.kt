package com.changedtimer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class TimerService : Service() {
    
    private lateinit var powerManager: PowerManager
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var notificationManager: NotificationManagerCompat
    
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var availableTimeSeconds = 0
    private var isTimerRunning = false
    
    companion object {
        private const val TAG = "TimerService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "timer_service_channel"
        private const val PREFS_NAME = "TimerAppPrefs"
        private const val KEY_AVAILABLE_TIME = "available_time"
        
        const val ACTION_START_TIMER = "start_timer"
        const val ACTION_STOP_TIMER = "stop_timer"
        const val ACTION_UPDATE_TIME = "update_time"
        const val EXTRA_TIME_SECONDS = "time_seconds"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        notificationManager = NotificationManagerCompat.from(this)
        
        createNotificationChannel()
        loadSavedTime()
        
        acquireWakeLock()
        
        Log.d(TAG, "TimerService created with saved time: $availableTimeSeconds")
    }

    private fun acquireWakeLock() {
        try {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$TAG::TimerWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes max
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: $action")
        
        when (action) {
            ACTION_START_TIMER -> {
                startTimer()
            }
            ACTION_STOP_TIMER -> {
                stopTimer()
            }
            ACTION_UPDATE_TIME -> {
                val timeSeconds = intent.getIntExtra(EXTRA_TIME_SECONDS, 0)
                updateAvailableTime(timeSeconds)
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TimerService destroyed")
        releaseWakeLock()
        stopTimer()
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
    
    private fun createNotificationChannel() {
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background timer service notifications"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channel", e)
        }
    }
    
    private fun startTimer() {
        if (isTimerRunning) {
            Log.d(TAG, "Timer already running, ignoring start request")
            return
        }
        
        if (availableTimeSeconds <= 0) {
            Log.d(TAG, "No time available, cannot start timer")
            broadcastTimerUpdate("time_expired")
            return
        }
        
        Log.d(TAG, "Starting background timer with ${availableTimeSeconds}s remaining")
        isTimerRunning = true
        
        try {
            val notification = createNotification("Timer Running", "Time remaining: ${formatTime(availableTimeSeconds)}")
            startForeground(NOTIFICATION_ID, notification)
            
            timerRunnable = object : Runnable {
                override fun run() {
                    if (isTimerRunning && availableTimeSeconds > 0) {
                        tick()
                        handler.postDelayed(this, 1000)
                    }
                }
            }
            
            handler.post(timerRunnable!!)
            broadcastTimerUpdate("timer_started")
            
            Log.d(TAG, "Timer started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start timer", e)
            stopTimer()
        }
    }
    
    private fun stopTimer() {
        if (!isTimerRunning) {
            Log.d(TAG, "Timer not running, ignoring stop request")
            return
        }
        
        Log.d(TAG, "Stopping background timer")
        isTimerRunning = false
        
        timerRunnable?.let {
            handler.removeCallbacks(it)
            timerRunnable = null
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            Log.d(TAG, "Stopped foreground service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop foreground service", e)
        }
        
        broadcastTimerUpdate("timer_stopped")
    }
    
    private fun tick() {
        availableTimeSeconds--
        saveTime()
        
        Log.v(TAG, "Tick: ${availableTimeSeconds}s remaining")
        
        // Update notification every 30 seconds
        if (availableTimeSeconds % 30 == 0) {
            updateNotification()
        }
        
        // Broadcast every second for UI updates
        broadcastTimerUpdate("time_tick")
        
        if (availableTimeSeconds <= 0) {
            handleTimeExpired()
        }
    }
    
    private fun handleTimeExpired() {
        Log.d(TAG, "Time expired!")
        stopTimer()
        
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⏰ Time Expired!")
                .setContentText("Your screen time has run out!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
                
            try {
                if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(999, notification)
                } else {
                    Log.w(TAG, "No POST_NOTIFICATIONS permission, cannot show notification")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: POST_NOTIFICATIONS permission missing at runtime", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show time expired notification", e)
        }
        
        broadcastTimerUpdate("time_expired")
    }
    
    private fun updateNotification() {
        try {
            val timeText = formatTime(availableTimeSeconds)
            val notification = createNotification("Timer Running", "Time remaining: $timeText")
            
            try {
                if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } else {
                    Log.w(TAG, "No POST_NOTIFICATIONS permission, cannot show notification")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: POST_NOTIFICATIONS permission missing at runtime", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
    
    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateAvailableTime(newTime: Int) {
        Log.d(TAG, "Updating available time from ${availableTimeSeconds}s to ${newTime}s")
        availableTimeSeconds = newTime
        saveTime()
        
        // Always broadcast the time update
        broadcastTimerUpdate("time_tick")
        
        if (availableTimeSeconds > 0 && !isTimerRunning) {
            Log.d(TAG, "Time available and timer not running - ready to start when conditions met")
        } else if (availableTimeSeconds <= 0 && isTimerRunning) {
            Log.d(TAG, "No time left - stopping timer")
            stopTimer()
        }
    }
    
    private fun loadSavedTime() {
        availableTimeSeconds = sharedPrefs.getInt(KEY_AVAILABLE_TIME, 0)
        Log.d(TAG, "Loaded saved time: ${availableTimeSeconds}s")
    }
    
    private fun saveTime() {
        sharedPrefs.edit().putInt(KEY_AVAILABLE_TIME, availableTimeSeconds).apply()
    }
    
    private fun broadcastTimerUpdate(action: String) {
        val intent = Intent(action).apply {
            putExtra("time_remaining", availableTimeSeconds)
            putExtra("is_timer_running", isTimerRunning)
        }
        sendBroadcast(intent)
        Log.v(TAG, "Broadcast sent: $action (time: ${availableTimeSeconds}s, running: $isTimerRunning)")
    }
    
    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        
        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
            else -> String.format("%02d:%02d", minutes, remainingSeconds)
        }
    }
}