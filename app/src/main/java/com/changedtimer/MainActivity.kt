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
        sharedPrefs = getSharedPreferences("TimerAppPrefs", Context.MODE_PRIVATE)
        _remainingTime.value = sharedPrefs.getInt("available_time", 0)
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
    }

    override fun onStart() {
        super.onStart()
        timerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "timer_started" -> {
                        _timerStatus.value = "Timer Running (Background)"
                        _eventLog.value = listOf("Timer started") + _eventLog.value
                    }
                    "timer_stopped" -> {
                        _timerStatus.value = "Timer Stopped"
                        _eventLog.value = listOf("Timer stopped") + _eventLog.value
                    }
                    "time_tick" -> {
                        val time = intent.getIntExtra("time_remaining", 0)
                        _remainingTime.value = time
                    }
                    "time_expired" -> {
                        _timerStatus.value = "Time Expired"
                        _eventLog.value = listOf("Time expired") + _eventLog.value
                        _remainingTime.value = 0
                    }
                }
                // Always update remaining time if present
                intent?.getIntExtra("time_remaining", -1)?.let { t ->
                    if (t >= 0) _remainingTime.value = t
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("timer_started")
            addAction("timer_stopped")
            addAction("time_tick")
            addAction("time_expired")
        }
        registerReceiver(timerReceiver, filter)

        deviceStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val isLocked = intent?.getBooleanExtra("is_locked", false) ?: false
                val isScreenOn = intent?.getBooleanExtra("is_screen_on", false) ?: false
                val stateMsg = if (isLocked) "Device Locked" else if (isScreenOn) "Device Unlocked" else "Screen Off"
                _eventLog.value = listOf(stateMsg) + _eventLog.value
            }
        }
        val deviceFilter = IntentFilter("com.changedtimer.DEVICE_STATE_CHANGED")
        registerReceiver(deviceStateReceiver, deviceFilter)
    }

    override fun onStop() {
        super.onStop()
        timerReceiver?.let { unregisterReceiver(it) }
        deviceStateReceiver?.let { unregisterReceiver(it) }
    }

    private fun startTimerService(timeInSeconds: Int) {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_UPDATE_TIME
            putExtra(TimerService.EXTRA_TIME_SECONDS, timeInSeconds)
        }
        startForegroundService(intent)
    }

    private fun stopTimerService() {
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP_TIMER
        }
        startService(intent)
    }
}

@Composable
fun TimerScreen(
    onStartTimer: (Int) -> Unit,
    onStopTimer: () -> Unit,
    remainingTime: Int = 0,
    timerStatus: String = "Timer Stopped",
    eventLog: List<String> = emptyList()
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
        LazyColumn(modifier = Modifier.height(120.dp)) {
            items(eventLog.take(10)) { event ->
                Text(event)
            }
        }
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