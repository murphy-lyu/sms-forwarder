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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.murphy.smsforwarder.ui.theme.SMSForwarderTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        autoStartServiceIfConfigured()
        setContent { SMSForwarderTheme { MainScreen() } }
    }

    private fun autoStartServiceIfConfigured() {
        val prefs = getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val telegramConfigured = !prefs.getString(ForwardService.KEY_BOT_TOKEN, "").isNullOrBlank()
        val smsConfigured = !prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "").isNullOrBlank()
        val serviceEnabled = prefs.getBoolean(
            ForwardService.KEY_SERVICE_ENABLED,
            telegramConfigured || smsConfigured
        )
        if ((telegramConfigured || smsConfigured) && serviceEnabled) {
            Log.d("SMSForwarder", "Starting ForwardService")
            startForegroundService(Intent(this, ForwardService::class.java))
        } else {
            Log.d("SMSForwarder", "No forwarding route configured, skipping auto-start")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE) }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var botToken by remember { mutableStateOf(prefs.getString(ForwardService.KEY_BOT_TOKEN, "").orEmpty()) }
    var chatId by remember { mutableStateOf(prefs.getString(ForwardService.KEY_CHAT_ID, "").orEmpty()) }
    var smsRecipient by remember {
        mutableStateOf(prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "").orEmpty())
    }
    var serviceRunning by remember {
        mutableStateOf(
            prefs.getBoolean(
                ForwardService.KEY_SERVICE_ENABLED,
                botToken.isNotBlank() || smsRecipient.isNotBlank()
            )
        )
    }
    var telegramEnabled by remember {
        mutableStateOf(prefs.getBoolean(ForwardService.KEY_TELEGRAM_ENABLED, true))
    }
    var smsEnabled by remember { mutableStateOf(prefs.getBoolean(ForwardService.KEY_SMS_ENABLED, false)) }
    var filterMode by remember { mutableStateOf(MessageFilterMode.fromPreferences(prefs)) }
    var filterKeywords by remember {
        mutableStateOf(prefs.getString(ForwardService.KEY_FILTER_KEYWORDS, "").orEmpty())
    }
    var logs by remember { mutableStateOf(loadLogs(prefs)) }
    var configEditing by remember { mutableStateOf(botToken.isBlank() || chatId.isBlank()) }
    var smsConfigEditing by remember { mutableStateOf(smsRecipient.isBlank()) }
    var detailPage by remember { mutableStateOf(DetailPage.NONE) }
    var batteryAllowed by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    DisposableEffect(Unit) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                batteryAllowed = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        (context as ComponentActivity).lifecycle.addObserver(observer)
        onDispose { context.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ForwardService.KEY_LOGS) logs = loadLogs(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val needed = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    val telegramConfigured = botToken.isNotBlank() && chatId.isNotBlank()
    val smsConfigured = smsRecipient.isNotBlank()
    val openBatterySettings = {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
    val setServiceRunning: (Boolean) -> Unit = { running ->
        serviceRunning = running
        prefs.edit().putBoolean(ForwardService.KEY_SERVICE_ENABLED, running).apply()
        val serviceIntent = Intent(context, ForwardService::class.java)
        if (running) context.startForegroundService(serviceIntent) else context.stopService(serviceIntent)
    }

    BackHandler(detailPage != DetailPage.NONE) { detailPage = DetailPage.NONE }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = when (detailPage) {
                    DetailPage.TELEGRAM -> stringResource(R.string.destination_telegram)
                    DetailPage.SMS -> stringResource(R.string.destination_sms)
                    DetailPage.SETTINGS -> stringResource(R.string.title_settings)
                    DetailPage.HISTORY -> stringResource(R.string.title_forwarding_logs)
                    DetailPage.RULES -> stringResource(R.string.title_forwarding_rules)
                    DetailPage.NONE -> stringResource(R.string.app_name)
                },
                showBack = detailPage != DetailPage.NONE,
                showSettings = detailPage == DetailPage.NONE,
                onBack = { detailPage = DetailPage.NONE },
                onSettings = { detailPage = DetailPage.SETTINGS }
            )
        }
    ) { padding ->
        when (detailPage) {
            DetailPage.TELEGRAM -> TelegramRoutePage(
                padding = padding,
                botToken = botToken,
                chatId = chatId,
                editing = configEditing,
                hasChanges = botToken.trim() != prefs.getString(ForwardService.KEY_BOT_TOKEN, "").orEmpty() ||
                    chatId.trim() != prefs.getString(ForwardService.KEY_CHAT_ID, "").orEmpty(),
                telegramEnabled = telegramEnabled,
                onBack = { detailPage = DetailPage.NONE },
                onBotTokenChange = { botToken = it },
                onChatIdChange = { chatId = it },
                onEdit = { configEditing = true },
                onSave = {
                    prefs.edit()
                        .putString(ForwardService.KEY_BOT_TOKEN, botToken.trim())
                        .putString(ForwardService.KEY_CHAT_ID, chatId.trim())
                        .apply()
                    configEditing = false
                },
                onTelegramEnabledChange = { enabled ->
                    telegramEnabled = enabled
                    prefs.edit().putBoolean(ForwardService.KEY_TELEGRAM_ENABLED, enabled).apply()
                }
            )
            DetailPage.RULES -> RulesPage(
                padding = padding,
                filterMode = filterMode,
                filterKeywords = filterKeywords,
                onSave = { mode, keywords ->
                    filterMode = mode
                    filterKeywords = keywords
                    prefs.edit()
                        .putString(ForwardService.KEY_FILTER_MODE, mode.value)
                        .putString(ForwardService.KEY_FILTER_KEYWORDS, keywords)
                        .remove(ForwardService.KEY_FORWARD_ALL)
                        .apply()
                }
            )
            DetailPage.SMS -> SmsRoutePage(
                padding = padding,
                recipient = smsRecipient,
                editing = smsConfigEditing,
                hasChanges = smsRecipient.trim() !=
                    prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "").orEmpty(),
                enabled = smsEnabled,
                onRecipientChange = { smsRecipient = it },
                onEdit = { smsConfigEditing = true },
                onSave = {
                    smsRecipient = smsRecipient.trim()
                    prefs.edit()
                        .putString(ForwardService.KEY_SMS_RECIPIENT, smsRecipient)
                        .commit()
                    smsConfigEditing = false
                },
                onEnabledChange = { enabled ->
                    smsEnabled = enabled
                    prefs.edit().putBoolean(ForwardService.KEY_SMS_ENABLED, enabled).apply()
                }
            )
            DetailPage.SETTINGS -> SettingsPage(
                padding = padding,
                batteryAllowed = batteryAllowed,
                onBack = { detailPage = DetailPage.NONE },
                onBatterySettings = openBatterySettings
            )
            DetailPage.HISTORY -> LogsPage(
                padding = padding,
                logs = logs,
                onClear = { prefs.edit().remove(ForwardService.KEY_LOGS).commit() }
            )
            DetailPage.NONE -> HomePage(
                padding = padding,
                serviceRunning = serviceRunning,
                telegramEnabled = telegramEnabled,
                telegramConfigured = telegramConfigured,
                smsEnabled = smsEnabled,
                smsConfigured = smsConfigured,
                filterMode = filterMode,
                batteryAllowed = batteryAllowed,
                logs = logs,
                onSettings = { detailPage = DetailPage.SETTINGS },
                onServiceChange = setServiceRunning,
                onBatterySettings = openBatterySettings,
                onOpenTelegram = { detailPage = DetailPage.TELEGRAM },
                onOpenSms = { detailPage = DetailPage.SMS },
                onOpenRules = { detailPage = DetailPage.RULES },
                onOpenLogs = { detailPage = DetailPage.HISTORY }
            )
        }
    }
}

private enum class DetailPage { NONE, TELEGRAM, SMS, SETTINGS, HISTORY, RULES }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    title: String,
    showBack: Boolean,
    showSettings: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                title,
                style = if (showSettings) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.titleLarge,
                fontWeight = if (showSettings) FontWeight.Bold else FontWeight.SemiBold
            )
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        },
        actions = {
            if (showSettings) {
                IconButton(onClick = onSettings) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.action_settings)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun HomePage(
    padding: PaddingValues,
    serviceRunning: Boolean,
    telegramEnabled: Boolean,
    telegramConfigured: Boolean,
    smsEnabled: Boolean,
    smsConfigured: Boolean,
    filterMode: MessageFilterMode,
    batteryAllowed: Boolean,
    logs: List<ForwardLog>,
    onSettings: () -> Unit,
    onServiceChange: (Boolean) -> Unit,
    onBatterySettings: () -> Unit,
    onOpenTelegram: () -> Unit,
    onOpenSms: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenLogs: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionHeader(stringResource(R.string.overview))
        }
        item {
            AppCard {
                SettingRow(
                    title = stringResource(R.string.sms_monitoring),
                    description = if (serviceRunning) stringResource(R.string.sms_monitoring_active)
                    else stringResource(R.string.sms_monitoring_inactive),
                    checked = serviceRunning,
                    onCheckedChange = onServiceChange,
                    compact = true
                )
            }
        }
        if (!batteryAllowed) {
            item { BatteryBanner(onOpenSettings = onBatterySettings) }
        }
        item { RulesSummaryCard(filterMode, onOpenRules) }
        item {
            SectionHeader(stringResource(R.string.forwarding_routes))
        }
        item {
            RouteCard(
                iconRes = R.drawable.ic_telegram,
                title = stringResource(R.string.destination_telegram),
                subtitle = when {
                    !telegramConfigured -> stringResource(R.string.status_needs_configuration)
                    !telegramEnabled -> stringResource(R.string.status_route_disabled)
                    serviceRunning -> stringResource(R.string.status_running)
                    else -> stringResource(R.string.status_forwarding_paused)
                },
                active = telegramConfigured && serviceRunning && telegramEnabled,
                onClick = onOpenTelegram
            )
        }
        item {
            RouteCard(
                iconRes = R.drawable.ic_sms,
                title = stringResource(R.string.destination_sms),
                subtitle = when {
                    !smsConfigured -> stringResource(R.string.status_needs_configuration)
                    !smsEnabled -> stringResource(R.string.status_route_disabled)
                    serviceRunning -> stringResource(R.string.status_running)
                    else -> stringResource(R.string.status_forwarding_paused)
                },
                active = smsConfigured && serviceRunning && smsEnabled,
                onClick = onOpenSms
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    stringResource(R.string.recent_history),
                    subtitle = null,
                    modifier = Modifier.weight(1f)
                )
                if (logs.isNotEmpty()) TextButton(onClick = onOpenLogs) {
                    Text(stringResource(R.string.action_view_all))
                }
            }
        }
        if (logs.isEmpty()) {
            item { EmptyLogState() }
        } else {
            items(logs.take(3), key = { "home-${it.timestamp}-${it.sender}" }) { LogItem(it) }
        }
    }

}

@Composable
private fun RulesSummaryCard(mode: MessageFilterMode, onClick: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.title_forwarding_rules),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    filterModeTitle(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
        }
    }
}

@Composable
private fun LogsPage(
    padding: PaddingValues,
    logs: List<ForwardLog>,
    onClear: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (logs.isEmpty()) stringResource(R.string.no_history)
                    else stringResource(R.string.history_count, logs.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (logs.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.action_clear_history))
                    }
                }
            }
        }
        if (logs.isEmpty()) item { EmptyLogState() }
        else items(logs, key = { "logs-${it.timestamp}-${it.sender}" }) {
            LogItem(it, showFullMessage = true)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_message)) },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    }
                ) {
                    Text(
                        stringResource(R.string.action_clear),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }
}

@Composable
private fun SmsRoutePage(
    padding: PaddingValues,
    recipient: String,
    editing: Boolean,
    hasChanges: Boolean,
    enabled: Boolean,
    onRecipientChange: (String) -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var testState by remember { mutableStateOf(TestMessageState.IDLE) }
    var permissionRequestedForTest by remember { mutableStateOf(false) }
    val sendTest = {
        testState = TestMessageState.SENDING
        Thread {
            val success = SmsForwarder.sendTest(context.applicationContext)
            ContextCompat.getMainExecutor(context).execute {
                testState = if (success) TestMessageState.SUCCESS else TestMessageState.FAILED
            }
        }.start()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (permissionRequestedForTest) sendTest() else onEnabledChange(true)
        }
        permissionRequestedForTest = false
    }
    val hasSendPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(editing) {
        if (editing) testState = TestMessageState.IDLE
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            RouteStatusCard(
                description = stringResource(
                    if (enabled) R.string.sms_route_enabled_description
                    else R.string.sms_route_disabled_description
                ),
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked && !hasSendPermission) {
                        permissionRequestedForTest = false
                        permissionLauncher.launch(Manifest.permission.SEND_SMS)
                    } else {
                        onEnabledChange(checked)
                    }
                },
                footer = stringResource(R.string.sms_cost_notice)
            )
        }
        item {
            AppCard {
                SectionHeader(
                    stringResource(R.string.route_sms_sms),
                    stringResource(R.string.sms_route_description)
                )
                Spacer(Modifier.height(18.dp))
                if (editing) {
                    AppTextField(
                        value = recipient,
                        onValueChange = onRecipientChange,
                        label = stringResource(R.string.recipient_phone_number),
                        placeholder = stringResource(R.string.recipient_phone_number_hint),
                        enabled = true
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            onSave()
                        },
                        enabled = recipient.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            stringResource(
                                if (hasChanges) R.string.action_save_changes else R.string.action_done
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                RoundedCornerShape(15.dp)
                            )
                            .clickable(onClick = onEdit)
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.recipient_phone_number),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                recipient,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.action_edit_configuration),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        if (!editing) {
            item {
                AppCard {
                    SectionHeader(
                        stringResource(R.string.test_route),
                        stringResource(R.string.test_sms_route_description)
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            if (!hasSendPermission) {
                                permissionRequestedForTest = true
                                permissionLauncher.launch(Manifest.permission.SEND_SMS)
                            } else {
                                sendTest()
                            }
                        },
                        enabled = testState != TestMessageState.SENDING,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        if (testState == TestMessageState.SENDING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            stringResource(
                                if (testState == TestMessageState.SENDING) R.string.action_sending
                                else R.string.action_send_test_sms
                            ),
                            maxLines = 1
                        )
                    }
                AnimatedVisibility(testState == TestMessageState.SUCCESS || testState == TestMessageState.FAILED) {
                    Text(
                        text = stringResource(
                            if (testState == TestMessageState.SUCCESS) R.string.test_sms_success
                            else R.string.test_sms_failure
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = if (testState == TestMessageState.SUCCESS) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun TelegramRoutePage(
    padding: PaddingValues,
    botToken: String,
    chatId: String,
    editing: Boolean,
    hasChanges: Boolean,
    telegramEnabled: Boolean,
    onBack: () -> Unit,
    onBotTokenChange: (String) -> Unit,
    onChatIdChange: (String) -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    onTelegramEnabledChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            RouteStatusCard(
                description = stringResource(
                    if (telegramEnabled) R.string.route_enabled_description
                    else R.string.route_disabled_description
                ),
                checked = telegramEnabled,
                onCheckedChange = onTelegramEnabledChange
            )
        }
        item {
            TelegramConfigCard(
                botToken,
                chatId,
                editing,
                hasChanges,
                onBotTokenChange,
                onChatIdChange,
                onEdit,
                onSave
            )
        }
        if (!editing) {
            item { TelegramTestCard() }
        }
    }
}

@Composable
private fun RulesPage(
    padding: PaddingValues,
    filterMode: MessageFilterMode,
    filterKeywords: String,
    onSave: (MessageFilterMode, String) -> Unit
) {
    var draftMode by remember(filterMode) { mutableStateOf(filterMode) }
    var draftKeywords by remember(filterKeywords) { mutableStateOf(filterKeywords) }
    val keywordsValid = draftMode != MessageFilterMode.KEYWORDS ||
        MessageFilterMatcher.parseKeywords(draftKeywords).isNotEmpty()
    val hasChanges = draftMode != filterMode || draftKeywords.trim() != filterKeywords.trim()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            RulesCard(
                filterMode = draftMode,
                filterKeywords = draftKeywords,
                showKeywordError = draftMode == MessageFilterMode.KEYWORDS && !keywordsValid,
                onFilterModeChange = { draftMode = it },
                onFilterKeywordsChange = { draftKeywords = it }
            )
        }
        item {
            Button(
                onClick = { onSave(draftMode, draftKeywords.trim()) },
                enabled = keywordsValid && hasChanges,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text(
                    stringResource(if (hasChanges) R.string.action_save_changes else R.string.action_done),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    padding: PaddingValues,
    batteryAllowed: Boolean,
    onBack: () -> Unit,
    onBatterySettings: () -> Unit
) {
    val context = LocalContext.current
    var languageDialogVisible by remember { mutableStateOf(false) }
    val selectedLanguage = remember { AppLanguageManager.current(context) }
    val githubUrl = stringResource(R.string.github_url)
    val smsAllowed = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED
    val notificationAllowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AppCard {
                SectionHeader(
                    stringResource(R.string.language),
                    stringResource(R.string.language_description)
                )
                Spacer(Modifier.height(10.dp))
                LanguageRow(
                    language = selectedLanguage,
                    onClick = { languageDialogVisible = true }
                )
            }
        }
        item {
            AppCard {
                SectionHeader(
                    stringResource(R.string.runtime_protection),
                    stringResource(R.string.runtime_protection_description)
                )
                Spacer(Modifier.height(14.dp))
                PermissionRow(stringResource(R.string.permission_sms), smsAllowed)
                PermissionRow(stringResource(R.string.permission_notifications), notificationAllowed)
                PermissionRow(
                    stringResource(R.string.permission_battery),
                    batteryAllowed,
                    if (!batteryAllowed) onBatterySettings else null
                )
                PermissionRow(stringResource(R.string.permission_boot), true)
            }
        }
        item {
            AppCard {
                SectionHeader(stringResource(R.string.about), stringResource(R.string.app_name))
                Spacer(Modifier.height(10.dp))
                AboutInfoRow(
                    label = stringResource(R.string.version),
                    value = stringResource(R.string.version_name)
                )
                AboutInfoRow(
                    label = stringResource(R.string.author),
                    value = stringResource(R.string.author_name)
                )
                AboutInfoRow(
                    label = stringResource(R.string.github),
                    value = stringResource(R.string.github_repository),
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                        )
                    }
                )
            }
        }
    }

    if (languageDialogVisible) {
        LanguageDialog(
            selected = selectedLanguage,
            onDismiss = { languageDialogVisible = false },
            onSelect = { language ->
                languageDialogVisible = false
                AppLanguageManager.apply(context as ComponentActivity, language)
            }
        )
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
        }
    }
}

@Composable
private fun LanguageRow(language: AppLanguage, onClick: () -> Unit) {
    val label = when (language) {
        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
        AppLanguage.SIMPLIFIED_CHINESE -> stringResource(R.string.language_chinese)
        AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.language), Modifier.weight(1f))
        Text(
            label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun LanguageDialog(
    selected: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    val label = when (language) {
                        AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                        AppLanguage.SIMPLIFIED_CHINESE -> stringResource(R.string.language_chinese)
                        AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == selected,
                            onClick = { onSelect(language) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RouteCard(iconRes: Int, title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(
                stringResource(if (active) R.string.status_enabled else R.string.status_view),
                active
            )
        }
    }
}

@Composable
private fun RouteStatusCard(
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    footer: String? = null
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.enable_route),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (footer != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComingSoonCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.coming_soon),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, allowed: Boolean, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f))
        Text(
            stringResource(if (allowed) R.string.status_configured else R.string.status_action_required),
            color = if (allowed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun HeroHeader(serviceRunning: Boolean, configured: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusPill(
                    stringResource(if (serviceRunning) R.string.status_service_running else R.string.status_service_stopped),
                    serviceRunning
                )
                StatusPill(
                    stringResource(if (configured) R.string.status_telegram_configured else R.string.status_waiting_configuration),
                    configured
                )
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Row(
        Modifier
            .background(
                if (active) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(
                    if (active) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.outline,
                    CircleShape
                )
        )
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun BatteryBanner(modifier: Modifier = Modifier, onOpenSettings: () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).background(
                    MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.battery_warning_title),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    stringResource(R.string.battery_warning_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.action_open_settings), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TelegramConfigCard(
    botToken: String,
    chatId: String,
    editing: Boolean,
    hasChanges: Boolean,
    onBotTokenChange: (String) -> Unit,
    onChatIdChange: (String) -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var tokenVisible by remember { mutableStateOf(false) }

    AppCard(modifier) {
        SectionHeader("Telegram", stringResource(R.string.telegram_description))
        Spacer(Modifier.height(18.dp))
        if (editing) {
            AppTextField(
                value = botToken,
                onValueChange = onBotTokenChange,
                label = stringResource(R.string.bot_token),
                placeholder = stringResource(R.string.bot_token_hint),
                enabled = true,
                visualTransformation = if (tokenVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingActionLabel = stringResource(if (tokenVisible) R.string.action_hide else R.string.action_show),
                onTrailingAction = { tokenVisible = !tokenVisible }
            )
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = chatId,
                onValueChange = onChatIdChange,
                label = stringResource(R.string.recipient_chat_id),
                placeholder = stringResource(R.string.recipient_chat_id_hint),
                enabled = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onSave()
                },
                enabled = botToken.isNotBlank() && chatId.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    stringResource(
                        if (hasChanges) R.string.action_save_changes else R.string.action_done
                    ),
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        RoundedCornerShape(15.dp)
                    )
                    .clickable(onClick = onEdit)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.bot_token),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "••••••••${botToken.takeLast(4)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.recipient_chat_id),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        chatId,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.action_edit_configuration),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TelegramTestCard() {
    val context = LocalContext.current
    var testState by remember { mutableStateOf(TestMessageState.IDLE) }

    AppCard {
        SectionHeader(
            stringResource(R.string.test_route),
            stringResource(R.string.test_telegram_route_description)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = {
                testState = TestMessageState.SENDING
                Thread {
                    val success = TelegramForwarder.sendTest(context.applicationContext)
                    ContextCompat.getMainExecutor(context).execute {
                        testState = if (success) TestMessageState.SUCCESS else TestMessageState.FAILED
                    }
                }.start()
            },
            enabled = testState != TestMessageState.SENDING,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(15.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            if (testState == TestMessageState.SENDING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(7.dp))
            }
            Text(
                stringResource(
                    if (testState == TestMessageState.SENDING) R.string.action_sending
                    else R.string.action_send_test_message
                ),
                maxLines = 1
            )
        }
        AnimatedVisibility(testState == TestMessageState.SUCCESS || testState == TestMessageState.FAILED) {
            Text(
                text = if (testState == TestMessageState.SUCCESS) {
                    stringResource(R.string.test_message_success)
                } else {
                    stringResource(R.string.test_message_failure)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = if (testState == TestMessageState.SUCCESS) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private enum class TestMessageState {
    IDLE,
    SENDING,
    SUCCESS,
    FAILED
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingActionLabel: String? = null,
    onTrailingAction: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            visualTransformation = visualTransformation,
            trailingIcon = if (trailingActionLabel != null && onTrailingAction != null) {
                {
                    TextButton(onClick = onTrailingAction) {
                        Text(trailingActionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                null
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        )
        Text(
            text = label,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp),
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun RulesCard(
    filterMode: MessageFilterMode,
    filterKeywords: String,
    showKeywordError: Boolean,
    onFilterModeChange: (MessageFilterMode) -> Unit,
    onFilterKeywordsChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterDialogVisible by remember { mutableStateOf(false) }

    AppCard(modifier) {
        SectionHeader(
            stringResource(R.string.title_forwarding_rules),
            stringResource(R.string.forwarding_rules_description)
        )
        Spacer(Modifier.height(8.dp))
        FilterModeRow(filterMode, onClick = { filterDialogVisible = true })
        if (filterMode == MessageFilterMode.KEYWORDS) {
            Spacer(Modifier.height(4.dp))
            AppTextField(
                value = filterKeywords,
                onValueChange = onFilterKeywordsChange,
                label = stringResource(R.string.custom_keywords),
                placeholder = stringResource(R.string.custom_keywords_hint),
                enabled = true
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (showKeywordError) R.string.custom_keywords_required
                    else R.string.custom_keywords_separator_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (showKeywordError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (filterDialogVisible) {
        FilterModeDialog(
            selected = filterMode,
            onDismiss = { filterDialogVisible = false },
            onSelect = { mode ->
                filterDialogVisible = false
                onFilterModeChange(mode)
            }
        )
    }
}

@Composable
private fun FilterModeRow(mode: MessageFilterMode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.message_scope), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(
                filterModeTitle(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(16.dp))
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 24.sp)
    }
}

@Composable
private fun FilterModeDialog(
    selected: MessageFilterMode,
    onDismiss: () -> Unit,
    onSelect: (MessageFilterMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_scope)) },
        text = {
            Column {
                MessageFilterMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == selected,
                            onClick = { onSelect(mode) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(filterModeTitle(mode))
                            Text(
                                filterModeDescription(mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun filterModeTitle(mode: MessageFilterMode): String = when (mode) {
    MessageFilterMode.OTP_ONLY -> stringResource(R.string.filter_otp_only)
    MessageFilterMode.KEYWORDS -> stringResource(R.string.filter_keywords)
    MessageFilterMode.ALL -> stringResource(R.string.filter_all)
}

@Composable
private fun filterModeDescription(mode: MessageFilterMode): String = when (mode) {
    MessageFilterMode.OTP_ONLY -> stringResource(R.string.filter_otp_only_description)
    MessageFilterMode.KEYWORDS -> stringResource(R.string.filter_keywords_description)
    MessageFilterMode.ALL -> stringResource(R.string.filter_all_description)
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    warning: Boolean = false,
    compact: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning && checked) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked,
            onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = if (warning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) { Column(Modifier.padding(20.dp), content = content) }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyLogState(modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.empty_history_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_history_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogItem(
    log: ForwardLog,
    modifier: Modifier = Modifier,
    showFullMessage: Boolean = false
) {
    val timeText = remember(log.timestamp) {
        SimpleDateFormat("MM-dd  HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(38.dp).background(
                    if (log.success) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (log.success) "↑" else "!",
                    color = if (log.success) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        log.sender,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(if (log.success) R.string.log_sent else R.string.log_failed),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (log.success) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    log.message,
                    maxLines = if (showFullMessage) Int.MAX_VALUE else 2,
                    overflow = if (showFullMessage) TextOverflow.Clip else TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)
                )
                Spacer(Modifier.height(8.dp))
                Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun loadLogs(prefs: SharedPreferences): List<ForwardLog> {
    val json = prefs.getString(ForwardService.KEY_LOGS, "[]") ?: "[]"
    val type = object : TypeToken<List<ForwardLog>>() {}.type
    return runCatching { Gson().fromJson<List<ForwardLog>>(json, type) }
        .getOrNull()
        .orEmpty()
}
