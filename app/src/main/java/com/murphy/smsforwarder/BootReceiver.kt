package com.murphy.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val telegramConfigured = !prefs.getString(ForwardService.KEY_BOT_TOKEN, "").isNullOrBlank()
        val smsConfigured = !prefs.getString(ForwardService.KEY_SMS_RECIPIENT, "").isNullOrBlank()
        val hasRoute = telegramConfigured || smsConfigured
        val serviceEnabled = prefs.getBoolean(ForwardService.KEY_SERVICE_ENABLED, hasRoute)
        if (hasRoute && serviceEnabled) {
            Log.d("SMSForwarder", "BootReceiver: starting ForwardService (action=$action)")
            context.startForegroundService(Intent(context, ForwardService::class.java))
        }
    }
}
