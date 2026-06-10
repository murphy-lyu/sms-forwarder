package com.murphy.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SMSForwarder", "SmsReceiver.onReceive triggered: action=${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.messageBody }

        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val forwardAll = prefs.getBoolean(ForwardService.KEY_FORWARD_ALL, false)
        val isOtp = forwardAll || isOtpMessage(body)

        Log.d("SMSForwarder", "SMS received from: $sender, body: $body")
        Log.d("SMSForwarder", "forwardAll=$forwardAll, isOtp=$isOtp")

        if (!isOtp) return

        val serviceIntent = Intent(context, ForwardService::class.java).apply {
            action = ForwardService.ACTION_FORWARD
            putExtra(ForwardService.EXTRA_SENDER, sender)
            putExtra(ForwardService.EXTRA_MESSAGE, body)
        }
        context.startForegroundService(serviceIntent)
    }

    // Both a keyword AND a 4-8 digit sequence must be present
    private fun isOtpMessage(text: String): Boolean {
        val lower = text.lowercase()
        val hasKeyword = OTP_KEYWORDS.any { lower.contains(it) }
        val hasDigitSequence = text.contains(Regex("\\d{4,8}"))
        return hasKeyword && hasDigitSequence
    }

    companion object {
        private val OTP_KEYWORDS = listOf(
            "验证码", "验证", "码", "动态密码", "一次性密码",
            "code", "otp", "verify", "verification", "activation", "password"
        )
    }
}
