package com.murphy.smsforwarder

import android.content.SharedPreferences

enum class MessageFilterMode(val value: String) {
    OTP_ONLY("otp_only"),
    KEYWORDS("keywords"),
    ALL("all");

    companion object {
        fun fromPreferences(prefs: SharedPreferences): MessageFilterMode {
            val saved = prefs.getString(ForwardService.KEY_FILTER_MODE, null)
            if (saved != null) return entries.firstOrNull { it.value == saved } ?: OTP_ONLY

            return if (prefs.getBoolean(ForwardService.KEY_FORWARD_ALL, false)) ALL else OTP_ONLY
        }
    }
}
