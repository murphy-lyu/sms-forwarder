package com.murphy.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ForwardLog(
    val timestamp: Long = 0,
    val sender: String = "",
    val message: String = "",
    val success: Boolean = false
)

class ForwardService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val gson = Gson()
    private val smsReceiver = SmsReceiver()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsForwarder::WakeLock")
        // Dynamic registration works on API 26+ where static SMS_RECEIVED is blocked
        Log.d("SMSForwarder", "ForwardService created, registering SmsReceiver")
        registerReceiver(
            smsReceiver,
            IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            RECEIVER_EXPORTED
        )
        Log.d("SMSForwarder", "SmsReceiver registered for action=${Telephony.Sms.Intents.SMS_RECEIVED_ACTION}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FORWARD) {
            val sender = intent.getStringExtra(EXTRA_SENDER) ?: "Unknown"
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return START_STICKY
            forwardAsync(sender, message)
        }
        return START_STICKY
    }

    private fun forwardAsync(sender: String, message: String) {
        // Acquire a 30-second wake lock so the network call isn't interrupted
        wakeLock?.acquire(30_000L)
        Thread {
            try {
                val success = TelegramForwarder.send(applicationContext, sender, message)
                // Store only a 30-char summary in the log to keep SharedPreferences compact
                saveLog(ForwardLog(System.currentTimeMillis(), sender, message.take(30), success))
            } finally {
                if (wakeLock?.isHeld == true) wakeLock?.release()
            }
        }.start()
    }

    private fun saveLog(log: ForwardLog) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val type = object : TypeToken<MutableList<ForwardLog>>() {}.type
        val logs: MutableList<ForwardLog> = gson.fromJson(
            prefs.getString(KEY_LOGS, "[]"), type
        ) ?: mutableListOf()
        logs.add(0, log)
        if (logs.size > 20) logs.subList(20, logs.size).clear()
        prefs.edit().putString(KEY_LOGS, gson.toJson(logs)).apply()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SMS Forwarder",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Keeps the SMS forwarding service alive" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forwarder")
            .setContentText("Listening for OTP messages…")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_FORWARD = "com.murphy.smsforwarder.ACTION_FORWARD"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE = "extra_message"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sms_forwarder_channel"
        const val PREFS_NAME = "sms_forwarder_prefs"
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_LOGS = "forward_logs"
        const val KEY_FORWARD_ALL = "forward_all"
    }
}
