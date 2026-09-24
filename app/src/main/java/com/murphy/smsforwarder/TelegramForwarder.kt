package com.murphy.smsforwarder

import android.content.Context
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TelegramForwarder {

    data class SendResult(
        val success: Boolean,
        val retryable: Boolean = false,
        val detail: String = ""
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    fun send(context: Context, sender: String, message: String): SendResult {
        val text = context.getString(
            R.string.telegram_message_template,
            sender,
            currentTime(),
            message
        )

        return sendText(context, text)
    }

    fun sendTest(context: Context): Boolean {
        val text = context.getString(R.string.telegram_test_message_template, currentTime())

        return sendText(context, text).success
    }

    private fun sendText(context: Context, text: String): SendResult {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val botToken = prefs.getString(ForwardService.KEY_BOT_TOKEN, "")?.trim()
        val chatId = prefs.getString(ForwardService.KEY_CHAT_ID, "")?.trim()

        if (botToken.isNullOrEmpty() || chatId.isNullOrEmpty()) {
            return SendResult(success = false, detail = "Bot token or Chat ID is missing")
        }

        return try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val body = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .build()
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    SendResult(success = true)
                } else {
                    val responseDetail = response.body?.string()
                        ?.take(MAX_ERROR_DETAIL_LENGTH)
                        .orEmpty()
                    val detail = "HTTP ${response.code}${if (responseDetail.isBlank()) "" else ": $responseDetail"}"
                    val retryable = response.code == 408 ||
                        response.code == 425 ||
                        response.code == 429 ||
                        response.code in 500..599
                    Log.w(TAG, "Telegram send failed: $detail, retryable=$retryable")
                    SendResult(success = false, retryable = retryable, detail = detail)
                }
            }
        } catch (e: IOException) {
            val detail = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
            Log.w(TAG, "Telegram send failed: $detail")
            SendResult(success = false, retryable = true, detail = detail)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Telegram configuration is invalid: ${e.message}")
            SendResult(success = false, detail = "Invalid Bot token or Chat ID")
        }
    }

    private fun currentTime(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private const val TAG = "SMSForwarder"
    private const val MAX_ERROR_DETAIL_LENGTH = 500
}
