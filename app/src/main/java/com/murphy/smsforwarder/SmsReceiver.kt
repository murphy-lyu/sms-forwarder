package com.murphy.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SMSForwarder", "SmsReceiver.onReceive triggered: action=${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.messageBody }

        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val telegramConfigured = !prefs.getString(ForwardService.KEY_BOT_TOKEN, "").isNullOrBlank() &&
            !prefs.getString(ForwardService.KEY_CHAT_ID, "").isNullOrBlank()
        val smsConfigured = !prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "").isNullOrBlank()
        if (!prefs.getBoolean(ForwardService.KEY_SERVICE_ENABLED, telegramConfigured || smsConfigured)) {
            Log.d("SMSForwarder", "SMS monitoring is disabled")
            return
        }
        val telegramEnabled = telegramConfigured &&
            prefs.getBoolean(ForwardService.KEY_TELEGRAM_ENABLED, true)
        val smsEnabled = smsConfigured && prefs.getBoolean(ForwardService.KEY_SMS_ENABLED, false)
        if (!telegramEnabled && !smsEnabled) {
            Log.d("SMSForwarder", "No forwarding route is enabled")
            return
        }
        val filterMode = MessageFilterMode.fromPreferences(prefs)
        val shouldForward = when (filterMode) {
            MessageFilterMode.OTP_ONLY -> MessageFilterMatcher.matchesOtp(body)
            MessageFilterMode.KEYWORDS -> MessageFilterMatcher.matchesKeywords(
                body,
                prefs.getString(ForwardService.KEY_FILTER_KEYWORDS, "").orEmpty()
            )
            MessageFilterMode.ALL -> true
        }

        Log.d("SMSForwarder", "SMS received from: $sender, length=${body.length}")
        Log.d("SMSForwarder", "filterMode=${filterMode.value}, shouldForward=$shouldForward")

        if (!shouldForward) return

        val appContext = context.applicationContext
        val pending = if (telegramEnabled) {
            ForwardStore.enqueue(appContext, sender, body).also {
                ForwardWorker.schedule(appContext, it.id)
            }
        } else {
            null
        }

        val asyncResult = goAsync()
        executor.execute {
            try {
                if (smsEnabled) {
                    val success = SmsForwarder.send(appContext, sender, body)
                    Log.d("SMSForwarder", "Immediate SMS dispatch result: success=$success")
                    if (pending == null) {
                        ForwardStore.record(appContext, sender, body, success)
                    }
                }
                pending?.let {
                    val result = ForwardStore.attempt(appContext, it.id)
                    Log.d("SMSForwarder", "Immediate Telegram result: $result")
                }
            } finally {
                asyncResult.finish()
            }
        }
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }
}
