package com.murphy.smsforwarder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import java.util.UUID

object SmsForwarder {
    fun send(context: Context, sender: String, message: String): Boolean {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val recipient = prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "")?.trim().orEmpty()
        if (recipient.isBlank()) return false

        val text = context.getString(R.string.sms_forward_message_template, sender, message)
        return sendText(context, recipient, text, sender, message)
    }

    fun sendTest(context: Context): Boolean {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val recipient = prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "")?.trim().orEmpty()
        if (recipient.isBlank()) return false
        return sendText(
            context,
            recipient,
            context.getString(R.string.sms_test_message_template),
            context.getString(R.string.test_message_sender),
            context.getString(R.string.sms_test_message_template)
        )
    }

    @Suppress("DEPRECATION")
    private fun sendText(
        context: Context,
        recipient: String,
        text: String,
        sender: String,
        originalMessage: String
    ): Boolean = try {
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        val manager = if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }
        val parts = manager.divideMessage(text)
        val dispatchId = UUID.randomUUID().toString()
        ForwardStore.beginSmsDispatch(context, dispatchId, sender, originalMessage, parts.size)
        val sentIntents = parts.indices.map { partIndex ->
            val intent = Intent(context, SmsSentReceiver::class.java).apply {
                action = SmsSentReceiver.ACTION_SMS_SENT
                putExtra(SmsSentReceiver.EXTRA_DISPATCH_ID, dispatchId)
            }
            PendingIntent.getBroadcast(
                context,
                dispatchId.hashCode() + partIndex,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        if (parts.size == 1) {
            manager.sendTextMessage(recipient, null, text, sentIntents.first(), null)
        } else {
            manager.sendMultipartTextMessage(recipient, null, parts, ArrayList(sentIntents), null)
        }
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "SMS forwarding failed: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    private const val TAG = "SMSForwarder"
}
