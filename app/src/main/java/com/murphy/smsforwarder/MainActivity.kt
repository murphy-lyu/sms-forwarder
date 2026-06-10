package com.murphy.smsforwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.murphy.smsforwarder.ui.theme.SMSForwarderTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        autoStartServiceIfConfigured()
        setContent {
            SMSForwarderTheme {
                MainScreen()
            }
        }
    }

    private fun autoStartServiceIfConfigured() {
        val prefs = getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getString(ForwardService.KEY_BOT_TOKEN, "").isNullOrBlank()) {
            Log.d("SMSForwarder", "Starting ForwardService")
            startForegroundService(Intent(this, ForwardService::class.java))
        } else {
            Log.d("SMSForwarder", "Bot token not configured, skipping auto-start")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE) }
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    var botToken by remember { mutableStateOf(prefs.getString(ForwardService.KEY_BOT_TOKEN, "") ?: "") }
    var chatId by remember { mutableStateOf(prefs.getString(ForwardService.KEY_CHAT_ID, "") ?: "") }
    var serviceRunning by remember { mutableStateOf(false) }
    var forwardAll by remember { mutableStateOf(prefs.getBoolean(ForwardService.KEY_FORWARD_ALL, false)) }
    var logs by remember { mutableStateOf(loadLogs(prefs)) }
    var saveConfirmed by remember { mutableStateOf(false) }
    var ignoringBatteryOpt by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }

    // Re-check battery optimization status when activity resumes (user may have just granted it)
    DisposableEffect(Unit) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                ignoringBatteryOpt = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        (context as ComponentActivity).lifecycle.addObserver(observer)
        onDispose { (context as ComponentActivity).lifecycle.removeObserver(observer) }
    }

    // Refresh log list whenever KEY_LOGS changes in SharedPreferences
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ForwardService.KEY_LOGS) logs = loadLogs(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Request SMS + notification permissions on first launch
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled by the OS dialog */ }

    LaunchedEffect(Unit) {
        val needed = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        if (needed.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(needed)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SMS Forwarder") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // --- Battery optimization warning banner ---
            if (!ignoringBatteryOpt) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "为确保短信转发稳定运行，请将本 App 加入电池优化白名单",
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("去设置", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // --- Config ---
            item {
                Text("Telegram Configuration", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            item {
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it; saveConfirmed = false },
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it; saveConfirmed = false },
                    label = { Text("Chat ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                Button(
                    onClick = {
                        prefs.edit()
                            .putString(ForwardService.KEY_BOT_TOKEN, botToken.trim())
                            .putString(ForwardService.KEY_CHAT_ID, chatId.trim())
                            .apply()
                        saveConfirmed = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saveConfirmed) "Saved!" else "Save Configuration")
                }
            }

            // --- Service toggle ---
            item { HorizontalDivider() }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Forwarding Service", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            if (serviceRunning) "Running" else "Stopped",
                            fontSize = 12.sp,
                            color = if (serviceRunning) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = serviceRunning,
                        onCheckedChange = { running ->
                            serviceRunning = running
                            val svcIntent = Intent(context, ForwardService::class.java)
                            if (running) context.startForegroundService(svcIntent)
                            else context.stopService(svcIntent)
                        }
                    )
                }
            }

            // --- Forward all SMS toggle ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("转发所有短信", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Text(
                            "关闭时仅转发含验证码的短信",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = forwardAll,
                        onCheckedChange = { value ->
                            forwardAll = value
                            prefs.edit().putBoolean(ForwardService.KEY_FORWARD_ALL, value).apply()
                        }
                    )
                }
            }

            // --- Log list ---
            item { HorizontalDivider() }
            item {
                Text(
                    "Recent Forwards (${logs.size}/20)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            if (logs.isEmpty()) {
                item {
                    Text(
                        "No records yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(logs, key = { it.timestamp }) { log ->
                    LogItem(log)
                }
            }
        }
    }
}

@Composable
private fun LogItem(log: ForwardLog) {
    val timeStr = remember(log.timestamp) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(log.sender, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    text = if (log.success) "Sent" else "Failed",
                    fontSize = 12.sp,
                    color = if (log.success) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }
            Text(log.message, maxLines = 2, fontSize = 13.sp)
            Text(timeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun loadLogs(prefs: SharedPreferences): List<ForwardLog> {
    val json = prefs.getString(ForwardService.KEY_LOGS, "[]") ?: "[]"
    val type = object : TypeToken<List<ForwardLog>>() {}.type
    return Gson().fromJson(json, type) ?: emptyList()
}
