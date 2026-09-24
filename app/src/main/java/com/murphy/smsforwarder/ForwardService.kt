package com.murphy.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

data class ForwardLog(
    val timestamp: Long = 0,
    val sender: String = "",
    val message: String = "",
    val success: Boolean = false,
    val id: String = ""
)

class ForwardService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        Log.d("SMSForwarder", "ForwardService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = getString(R.string.notification_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentText(getString(R.string.notification_listening))
            .setSmallIcon(R.drawable.ic_notification_forward)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sms_forwarder_channel"
        const val PREFS_NAME = "sms_forwarder_prefs"
        const val KEY_BOT_TOKEN = "bot_token"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_LOGS = "forward_logs"
        const val KEY_PENDING_FORWARDS = "pending_forwards"
        const val KEY_FORWARD_ALL = "forward_all"
        const val KEY_FILTER_MODE = "filter_mode"
        const val KEY_FILTER_KEYWORDS = "filter_keywords"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        const val KEY_SMS_ENABLED = "sms_enabled"
        const val KEY_SMS_RECIPIENT = "sms_recipient"
    }
}
