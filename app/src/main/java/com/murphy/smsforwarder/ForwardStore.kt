package com.murphy.smsforwarder

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class PendingForward(
    val id: String = "",
    val timestamp: Long = 0,
    val sender: String = "",
    val message: String = ""
)

object ForwardStore {
    private const val MAX_PENDING = 50
    private val gson = Gson()

    @Synchronized
    fun enqueue(context: Context, sender: String, message: String): PendingForward {
        val pending = PendingForward(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sender = sender,
            message = message
        )
        val items = loadPending(context).toMutableList().apply {
            add(pending)
            if (size > MAX_PENDING) removeAt(0)
        }
        savePending(context, items)
        return pending
    }

    @Synchronized
    fun pending(context: Context): List<PendingForward> = loadPending(context)

    @Synchronized
    fun record(context: Context, sender: String, message: String, success: Boolean) {
        upsertLog(
            context,
            PendingForward(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sender = sender,
                message = message
            ),
            success
        )
    }

    @Synchronized
    fun attempt(context: Context, id: String): ForwardAttemptResult {
        val item = loadPending(context).firstOrNull { it.id == id }
            ?: return ForwardAttemptResult.MISSING
        val result = TelegramForwarder.send(context, item.sender, item.message)
        val remaining = loadPending(context).toMutableList()
        if (result.success || !result.retryable) {
            remaining.removeAll { it.id == id }
        }
        savePending(context, remaining)
        upsertLog(context, item, result.success)
        return when {
            result.success -> ForwardAttemptResult.SUCCESS
            result.retryable -> ForwardAttemptResult.RETRYABLE_FAILURE
            else -> ForwardAttemptResult.PERMANENT_FAILURE
        }
    }

    private fun loadPending(context: Context): List<PendingForward> {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val type = object : TypeToken<List<PendingForward>>() {}.type
        return runCatching {
            gson.fromJson<List<PendingForward>>(
                prefs.getString(ForwardService.KEY_PENDING_FORWARDS, "[]"),
                type
            )
        }.getOrNull().orEmpty()
    }

    private fun savePending(context: Context, items: List<PendingForward>) {
        context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ForwardService.KEY_PENDING_FORWARDS, gson.toJson(items))
            .commit()
    }

    private fun upsertLog(context: Context, item: PendingForward, success: Boolean) {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val type = object : TypeToken<MutableList<ForwardLog>>() {}.type
        val logs: MutableList<ForwardLog> = gson.fromJson(
            prefs.getString(ForwardService.KEY_LOGS, "[]"),
            type
        ) ?: mutableListOf()
        val log = ForwardLog(item.timestamp, item.sender, item.message, success, item.id)
        val existing = logs.indexOfFirst { it.id == item.id }
        if (existing >= 0) logs[existing] = log else logs.add(0, log)
        if (logs.size > 20) logs.subList(20, logs.size).clear()
        prefs.edit().putString(ForwardService.KEY_LOGS, gson.toJson(logs)).apply()
    }
}

enum class ForwardAttemptResult {
    MISSING,
    SUCCESS,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}
