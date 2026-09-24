package com.murphy.smsforwarder

import android.content.Context
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log

object SmsForwarder {
    fun send(context: Context, sender: String, message: String): Boolean {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val recipient = prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "")?.trim().orEmpty()
        if (recipient.isBlank()) return false

        val text = context.getString(R.string.sms_forward_message_template, sender, message)
        return sendText(recipient, text)
    }

    fun sendTest(context: Context): Boolean {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val recipient = prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "")?.trim().orEmpty()
        if (recipient.isBlank()) return false
        return sendText(recipient, context.getString(R.string.sms_test_message_template))
    }

    @Suppress("DEPRECATION")
    private fun sendText(recipient: String, text: String): Boolean = try {
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        val manager = if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }
        val parts = manager.divideMessage(text)
        if (parts.size == 1) {
            manager.sendTextMessage(recipient, null, text, null, null)
        } else {
            manager.sendMultipartTextMessage(recipient, null, parts, null, null)
        }
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "SMS forwarding failed: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    private const val TAG = "SMSForwarder"
}
