package com.murphy.smsforwarder

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_SENT) return
        val dispatchId = intent.getStringExtra(EXTRA_DISPATCH_ID) ?: return
        val success = resultCode == Activity.RESULT_OK
        ForwardStore.recordSmsPartResult(
            context = context.applicationContext,
            id = dispatchId,
            success = success,
            detail = if (success) null else resultDescription(resultCode)
        )
    }

    private fun resultDescription(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic_failure"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no_service"
        SmsManager.RESULT_ERROR_NULL_PDU -> "null_pdu"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio_off"
        else -> "error_$code"
    }

    companion object {
        const val ACTION_SMS_SENT = "com.murphy.smsforwarder.SMS_SENT"
        const val EXTRA_DISPATCH_ID = "dispatch_id"
    }
}
