package com.murphy.smsforwarder

data class ForwardLog(
    val timestamp: Long = 0,
    val sender: String = "",
    val message: String = "",
    val success: Boolean = false,
    val id: String = "",
    val route: String? = null,
    val pending: Boolean = false,
    val detail: String? = null
)

object ForwardService {
    const val PREFS_NAME = "sms_forwarder_prefs"
    const val KEY_BOT_TOKEN = "bot_token"
    const val KEY_CHAT_ID = "chat_id"
    const val KEY_LOGS = "forward_logs"
    const val KEY_PENDING_FORWARDS = "pending_forwards"
    const val KEY_SMS_DISPATCHES = "sms_dispatches"
    const val KEY_FORWARD_ALL = "forward_all"
    const val KEY_FILTER_MODE = "filter_mode"
    const val KEY_FILTER_KEYWORDS = "filter_keywords"
    const val KEY_SERVICE_ENABLED = "service_enabled"
    const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
    const val KEY_SMS_ENABLED = "sms_enabled"
    const val KEY_SMS_RECIPIENT = "sms_recipient"
}
