package com.murphy.smsforwarder

import android.content.Context
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TelegramForwarder {

    private val client = OkHttpClient()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun send(context: Context, sender: String, message: String): Boolean {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val botToken = prefs.getString(ForwardService.KEY_BOT_TOKEN, "")?.trim()
        val chatId = prefs.getString(ForwardService.KEY_CHAT_ID, "")?.trim()

        if (botToken.isNullOrEmpty() || chatId.isNullOrEmpty()) return false

        val timestamp = timeFormat.format(Date())
        val text = """
            📱 SMS Forwarder
            发件人：$sender
            时间：$timestamp
            内容：$message
        """.trimIndent()

        val url = "https://api.telegram.org/bot$botToken/sendMessage"
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text)
            .build()

        val request = Request.Builder().url(url).post(body).build()

        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }
}
