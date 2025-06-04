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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.changedtimer.ui.theme.ChangedTimerTheme
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var sharedPrefs: SharedPreferences
    private var timerReceiver: BroadcastReceiver? = null

    private val _remainingTime = mutableStateOf(0)
    private val _isAppForeground = mutableStateOf(false)

    companion object {
        private const val TAG = "MainActivity"
    }

    // Permission launcher for notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPrefs = getSharedPreferences("TimerAppPrefs", Context.MODE_PRIVATE)
        _remainingTime.value = sharedPrefs.getInt("remaining_time", 0)
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        setContent {
            ChangedTimerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScreenTimeTracker(
                        remainingTime = _remainingTime.value,
                        isAppForeground = _isAppForeground.value,
                        onSetTime = { minutes ->
                            setScreenTime(minutes)
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setupReceiver()
        
        // Notify service that app is in foreground
        Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_APP_FOREGROUND
            startService(this)
        }
    }

    override fun onStop() {
        super.onStop()
        
        // Notify service that app is in background
        Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_APP_BACKGROUND
            startService(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerReceiver?.let { unregisterReceiver(it) }
    }

    private fun setupReceiver() {
        timerReceiver?.let { 
            try { unregisterReceiver(it) } catch (e: Exception) { }
        }

        timerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    _remainingTime.value = it.getIntExtra("remaining_time", 0)
                    _isAppForeground.value = it.getBooleanExtra("is_app_foreground", false)
                }
            }
        }
        
        val filter = IntentFilter("timer_update")
        ContextCompat.registerReceiver(
            this, 
            timerReceiver, 
            filter, 
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun setScreenTime(minutes: Int) {
        val seconds = minutes * 60
        
        // Start or update the timer service
        Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_UPDATE_TIME
            putExtra(TimerService.EXTRA_TIME_SECONDS, seconds)
            startForegroundService(this)
        }
        
        Log.d(TAG, "Set screen time to $minutes minutes ($seconds seconds)")
    }
}

@Composable
fun ScreenTimeTracker(
    remainingTime: Int,
    isAppForeground: Boolean,
    onSetTime: (Int) -> Unit
) {
    var inputMinutes by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Title
        Text(
            text = "Screen Time Tracker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Simple • Effective • Automatic",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Time Display Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (remainingTime > 0) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatTime(remainingTime),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingTime > 0)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                
                Text(
                    text = when {
                        remainingTime <= 0 -> "Time Expired!"
                        isAppForeground -> "Paused (App Open)"
                        else -> "Tracking Screen Time"
                    },
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        // Input Section
        OutlinedTextField(
            value = inputMinutes,
            onValueChange = { 
                // Only allow numeric input
                if (it.all { char -> char.isDigit() }) {
                    inputMinutes = it
                }
            },
            label = { Text("Set Timer (minutes)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            singleLine = true
        )
        
        Button(
            onClick = {
                val minutes = inputMinutes.toIntOrNull() ?: 0
                if (minutes > 0) {
                    onSetTime(minutes)
                    inputMinutes = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = inputMinutes.isNotEmpty()
        ) {
            Text("Start Timer", fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "How it works:",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "• Timer counts down when screen is ON\n" +
                          "• Timer pauses when this app is open\n" +
                          "• Time spent in app is added back\n" +
                          "• Check notification for live updates",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

fun formatTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%d:%02d", minutes, secs)
    }
}