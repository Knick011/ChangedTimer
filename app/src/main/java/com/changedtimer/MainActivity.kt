package com.changedtimer

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.core.content.ContextCompat
import com.changedtimer.ui.theme.ChangedTimerTheme

class MainActivity : ComponentActivity() {
    private lateinit var sharedPrefs: SharedPreferences
    private var timerReceiver: BroadcastReceiver? = null
    private var deviceStateReceiver: BroadcastReceiver? = null

    private val _remainingTime = mutableStateOf(0)
    private val _timerStatus = mutableStateOf("Timer Stopped")
    private val _eventLog = mutableStateOf(listOf<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            sharedPrefs = getSharedPreferences("TimerAppPrefs", Context.MODE_PRIVATE)
            _remainingTime.value = sharedPrefs.getInt("available_time", 0)
            
            // Add initial log entry
            addLogEntry("📱 App started")
            
            setContent {
                ChangedTimerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TimerScreen(
                            onStartTimer = { timeInSeconds ->
                                startTimerService(timeInSeconds)
                            },
                            onStopTimer = {
                                stopTimerService()
                            },
                            remainingTime = _remainingTime.value,
                            timerStatus = _timerStatus.value,
                            eventLog = _eventLog.value
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onCreate", e)
        }
    }

    override fun onStart() {
        super.onStart()
        
        try {
            setupReceivers()
            addLogEntry("📱 App resumed/started")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting up receivers", e)
        }
    }

    private fun addLogEntry(message: String) {
        val timestamp = System.currentTimeMillis() % 100000
        val entry = "$timestamp: $message"
        _eventLog.value = listOf(entry) + _eventLog.value.take(9)
        android.util.Log.d("MainActivity", "Event: $entry")
    }

    private fun setupReceivers() {
        // Unregister any existing receivers first
        try {
            timerReceiver?.let { unregisterReceiver(it) }
            deviceStateReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            // Ignore if already unregistered
        }

        // Timer broadcast receiver
        timerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    android.util.Log.d("MainActivity", "Timer broadcast received: ${intent?.action}")
                    
                    when (intent?.action) {
                        "timer_started" -> {
                            _timerStatus.value = "⏱️ Timer RUNNING (Background Usage)"
                            addLogEntry("⏱️ Timer Started")
                        }
                        "timer_stopped" -> {
                            _timerStatus.value = "⏸️ Timer PAUSED"
                            addLogEntry("⏸️ Timer Paused")
                        }
                        "time_tick" -> {
                            val time = intent.getIntExtra("time_remaining", 0)
                            _remainingTime.value = time
                            // Don't log every tick, too noisy
                        }
                        "time_expired" -> {
                            _timerStatus.value = "❌ Time Expired!"
                            addLogEntry("❌ Time Expired!")
                            _remainingTime.value = 0
                        }
                    }
                    
                    // Always update remaining time if present
                    intent?.getIntExtra("time_remaining", -1)?.let { t ->
                        if (t >= 0) _remainingTime.value = t
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error in timer receiver", e)
                }
            }
        }
        
        val timerFilter = IntentFilter().apply {
            addAction("timer_started")
            addAction("timer_stopped")
            addAction("time_tick")
            addAction("time_expired")
        }
        
        // Use RECEIVER_NOT_EXPORTED for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, timerReceiver, timerFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timerReceiver, timerFilter)
        }

        // Device state broadcast receiver
        deviceStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    android.util.Log.d("MainActivity", "Device state broadcast received")
                    
                    val isLocked = intent?.getBooleanExtra("is_locked", false) ?: false
                    val isScreenOn = intent?.getBooleanExtra("is_screen_on", false) ?: false
                    
                    val stateMsg = when {
                        isLocked -> "🔒 Device Locked - Timer Paused"
                        !isScreenOn -> "📱 Screen Off - Timer Paused" 
                        else -> "🔓 Device Unlocked - Timer Ready"
                    }
                    
                    addLogEntry(stateMsg)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error in device state receiver", e)
                }
            }
        }
        
        val deviceFilter = IntentFilter("com.changedtimer.DEVICE_STATE_CHANGED")
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, deviceStateReceiver, deviceFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(deviceStateReceiver, deviceFilter)
        }

        addLogEntry("📡 Receivers registered")
    }

    override fun onStop() {
        super.onStop()
        addLogEntry("🏠 App going to background")
        // Don't unregister receivers here - we need them while in background
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            timerReceiver?.let { unregisterReceiver(it) }
            deviceStateReceiver?.let { unregisterReceiver(it) }
            addLogEntry("📱 App destroyed")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error unregistering receivers", e)
        }
    }

    private fun startTimerService(timeInSeconds: Int) {
        try {
            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_UPDATE_TIME
                putExtra(TimerService.EXTRA_TIME_SECONDS, timeInSeconds)
            }
            startForegroundService(intent)
            addLogEntry("🚀 Timer service started with ${timeInSeconds}s")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error starting timer service", e)
            addLogEntry("❌ Failed to start timer service")
        }
    }

    private fun stopTimerService() {
        try {
            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP_TIMER
            }
            startService(intent)
            addLogEntry("🛑 Timer service stop requested")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error stopping timer service", e)
            addLogEntry("❌ Failed to stop timer service")
        }
    }
}

@Composable
fun TimerScreen(
    onStartTimer: (Int) -> Unit,
    onStopTimer: () -> Unit,
    remainingTime: Int,
    timerStatus: String,
    eventLog: List<String>
) {
    var timeInput by remember { mutableStateOf("") }
    var isTimerRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = formatTime(remainingTime),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = timerStatus,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OutlinedTextField(
            value = timeInput,
            onValueChange = { timeInput = it },
            label = { Text("Time in minutes") },
            enabled = !isTimerRunning,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val minutes = timeInput.toIntOrNull() ?: 0
                if (minutes > 0) {
                    onStartTimer(minutes * 60)
                    isTimerRunning = true
                }
            },
            enabled = !isTimerRunning && timeInput.isNotEmpty()
        ) {
            Text("Start Timer")
        }
        if (isTimerRunning) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onStopTimer()
                    isTimerRunning = false
                }
            ) {
                Text("Stop Timer")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Event Log:", fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.height(200.dp)) {
            items(eventLog) { event ->
                Text(
                    text = event,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
        
        // Debug info
        Text(
            text = "Events: ${eventLog.size}",
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

fun formatTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds)
        else -> String.format("%02d:%02d", minutes, remainingSeconds)
    }
}