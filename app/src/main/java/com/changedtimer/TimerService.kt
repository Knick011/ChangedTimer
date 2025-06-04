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
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

class TimerService : Service() {
    
    private lateinit var powerManager: PowerManager
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var remainingTimeSeconds = 0
    private var appForegroundStartTime = 0L
    private var isAppInForeground = false
    
    // Event log for the notification
    private val eventLog = mutableListOf<String>()
    private val maxLogEntries = 5
    
    companion object {
        private const val TAG = "TimerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "screen_time_channel"
        private const val PREFS_NAME = "TimerAppPrefs"
        private const val KEY_REMAINING_TIME = "remaining_time"
        
        const val ACTION_UPDATE_TIME = "update_time"
        const val ACTION_STOP_SERVICE = "stop_service"
        const val ACTION_APP_FOREGROUND = "app_foreground"
        const val ACTION_APP_BACKGROUND = "app_background"
        const val ACTION_START_TIMER = "start_timer"
        const val ACTION_STOP_TIMER = "stop_timer"
        const val EXTRA_TIME_SECONDS = "time_seconds"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        loadSavedTime()
        acquireWakeLock()
        
        Log.d(TAG, "TimerService created with saved time: $remainingTimeSeconds")
        addLogEntry("Service started")
    }

    private fun acquireWakeLock() {
        try {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$TAG::TimerWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10*60*1000L /*10 minutes*/)
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_TIME -> {
                val timeSeconds = intent.getIntExtra(EXTRA_TIME_SECONDS, 0)
                updateRemainingTime(timeSeconds)
                startTimer()
            }
            ACTION_START_TIMER -> {
                startTimer()
            }
            ACTION_STOP_SERVICE, ACTION_STOP_TIMER -> {
                stopTimer()
                stopSelf()
            }
            ACTION_APP_FOREGROUND -> {
                handleAppForeground()
            }
            ACTION_APP_BACKGROUND -> {
                handleAppBackground()
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
        saveTime()
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Time Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows remaining screen time and event log"
            setShowBadge(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun startTimer() {
        Log.d(TAG, "Starting screen time timer")
        
        // Start foreground service with initial notification
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Cancel any existing timer
        timerRunnable?.let { handler.removeCallbacks(it) }
        
        // Start new timer that ticks every second
        timerRunnable = object : Runnable {
            override fun run() {
                tick()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
        
        broadcastUpdate()
        addLogEntry("Timer started")
    }
    
    private fun stopTimer() {
        Log.d(TAG, "Stopping timer")
        timerRunnable?.let {
            handler.removeCallbacks(it)
            timerRunnable = null
        }
        saveTime()
        addLogEntry("Timer stopped")
    }
    
    private fun tick() {
        val isScreenOn = powerManager.isInteractive
        
        // Only count down if screen is on and app is not in foreground
        if (isScreenOn && !isAppInForeground && remainingTimeSeconds > 0) {
            remainingTimeSeconds--
            
            // Log every 30 seconds
            if (remainingTimeSeconds % 30 == 0) {
                addLogEntry("Screen ON: ${formatTime(remainingTimeSeconds)} left")
            }
            
            if (remainingTimeSeconds == 0) {
                handleTimeExpired()
            }
        }
        
        // Update notification every second for live countdown
        updateNotification()
        
        // Save time every 10 seconds
        if (remainingTimeSeconds % 10 == 0) {
            saveTime()
        }
        
        // Broadcast update every second
        broadcastUpdate()
    }
    
    private fun handleAppForeground() {
        if (!isAppInForeground) {
            isAppInForeground = true
            appForegroundStartTime = System.currentTimeMillis()
            addLogEntry("App opened (timer paused)")
            Log.d(TAG, "App entered foreground - timer paused")
        }
    }
    
    private fun handleAppBackground() {
        if (isAppInForeground) {
            isAppInForeground = false
            
            // Calculate time spent in foreground and add it back
            val timeInForeground = (System.currentTimeMillis() - appForegroundStartTime) / 1000
            remainingTimeSeconds += timeInForeground.toInt()
            
            addLogEntry("App closed (+${timeInForeground}s added)")
            Log.d(TAG, "App left foreground - added ${timeInForeground}s back to timer")
            
            saveTime()
        }
    }
    
    private fun handleTimeExpired() {
        Log.d(TAG, "Time expired!")
        addLogEntry("⏰ TIME EXPIRED!")
        
        // Show high priority notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⏰ Screen Time Expired!")
            .setContentText("Your screen time limit has been reached.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(999, notification)
        
        broadcastUpdate()
    }
    
    private fun createNotification(): Notification {
        // Create intent to open app
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Screen Time Remaining")
            .setContentText("${formatTime(remainingTimeSeconds)} - ${getTimerStatus()}")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }
    
    private fun getTimerStatus(): String {
        return when {
            remainingTimeSeconds <= 0 -> "Time Expired!"
            isAppInForeground -> "App Open (Paused)"
            !powerManager.isInteractive -> "Screen Off"
            else -> "Screen On (Counting)"
        }
    }
    
    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun updateRemainingTime(newTime: Int) {
        remainingTimeSeconds = newTime
        saveTime()
        addLogEntry("Time set to ${formatTime(newTime)}")
        Log.d(TAG, "Updated remaining time to $newTime seconds")
    }
    
    private fun addLogEntry(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "$timestamp: $message"
        
        eventLog.add(entry)
        
        // Keep only last N entries
        if (eventLog.size > maxLogEntries * 2) {
            eventLog.subList(0, eventLog.size - maxLogEntries).clear()
        }
        
        Log.d(TAG, "Event: $entry")
    }
    
    private fun loadSavedTime() {
        remainingTimeSeconds = sharedPrefs.getInt(KEY_REMAINING_TIME, 0)
        Log.d(TAG, "Loaded saved time: $remainingTimeSeconds seconds")
    }
    
    private fun saveTime() {
        sharedPrefs.edit().putInt(KEY_REMAINING_TIME, remainingTimeSeconds).apply()
    }
    
    private fun broadcastUpdate() {
        val intent = Intent("timer_update").apply {
            putExtra("remaining_time", remainingTimeSeconds)
            putExtra("is_app_foreground", isAppInForeground)
        }
        sendBroadcast(intent)
    }
    
    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            else -> String.format("%d:%02d", minutes, secs)
        }
    }
}