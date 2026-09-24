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

data class SmsDispatch(
    val id: String = "",
    val remainingParts: Int = 0,
    val failed: Boolean = false,
    val failureDetail: String? = null
)

object ForwardStore {
    private const val MAX_PENDING = 50
    private val gson = Gson()
    private val inFlight = mutableSetOf<String>()

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
        upsertLog(context, pending, success = false, route = ROUTE_TELEGRAM, pending = true)
        return pending
    }

    @Synchronized
    fun pending(context: Context): List<PendingForward> = loadPending(context)

    @Synchronized
    fun beginSmsDispatch(
        context: Context,
        id: String,
        sender: String,
        message: String,
        partCount: Int
    ) {
        val item = PendingForward(id, System.currentTimeMillis(), sender, message)
        val dispatches = loadSmsDispatches(context).toMutableMap().apply {
            put(id, SmsDispatch(id = id, remainingParts = partCount))
        }
        saveSmsDispatches(context, dispatches)
        upsertLog(context, item, success = false, route = ROUTE_SMS, pending = true)
    }

    @Synchronized
    fun recordSmsPartResult(context: Context, id: String, success: Boolean, detail: String?) {
        val dispatches = loadSmsDispatches(context).toMutableMap()
        val current = dispatches[id] ?: return
        val updated = current.copy(
            remainingParts = (current.remainingParts - 1).coerceAtLeast(0),
            failed = current.failed || !success,
            failureDetail = current.failureDetail ?: detail
        )
        if (updated.remainingParts > 0) {
            dispatches[id] = updated
            saveSmsDispatches(context, dispatches)
            return
        }

        dispatches.remove(id)
        saveSmsDispatches(context, dispatches)
        updateLog(
            context = context,
            id = id,
            success = !updated.failed,
            pending = false,
            detail = updated.failureDetail
        )
    }

    fun attempt(context: Context, id: String): ForwardAttemptResult {
        val item = synchronized(this) {
            if (!inFlight.add(id)) return ForwardAttemptResult.RETRYABLE_FAILURE
            loadPending(context).firstOrNull { it.id == id }
        }
        if (item == null) {
            synchronized(this) { inFlight.remove(id) }
            return ForwardAttemptResult.MISSING
        }

        return try {
            val result = TelegramForwarder.send(context, item.sender, item.message)
            synchronized(this) {
                val remaining = loadPending(context).toMutableList()
                if (result.success || !result.retryable) {
                    remaining.removeAll { it.id == id }
                }
                savePending(context, remaining)
                upsertLog(
                    context,
                    item,
                    success = result.success,
                    route = ROUTE_TELEGRAM,
                    pending = false,
                    detail = result.detail.ifBlank { null }
                )
            }
            when {
                result.success -> ForwardAttemptResult.SUCCESS
                result.retryable -> ForwardAttemptResult.RETRYABLE_FAILURE
                else -> ForwardAttemptResult.PERMANENT_FAILURE
            }
        } finally {
            synchronized(this) { inFlight.remove(id) }
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

    private fun loadSmsDispatches(context: Context): Map<String, SmsDispatch> {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val type = object : TypeToken<Map<String, SmsDispatch>>() {}.type
        return runCatching {
            gson.fromJson<Map<String, SmsDispatch>>(
                prefs.getString(ForwardService.KEY_SMS_DISPATCHES, "{}"),
                type
            )
        }.getOrNull().orEmpty()
    }

    private fun saveSmsDispatches(context: Context, dispatches: Map<String, SmsDispatch>) {
        context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ForwardService.KEY_SMS_DISPATCHES, gson.toJson(dispatches))
            .apply()
    }

    private fun upsertLog(
        context: Context,
        item: PendingForward,
        success: Boolean,
        route: String,
        pending: Boolean,
        detail: String? = null
    ) {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val type = object : TypeToken<MutableList<ForwardLog>>() {}.type
        val logs: MutableList<ForwardLog> = gson.fromJson(
            prefs.getString(ForwardService.KEY_LOGS, "[]"),
            type
        ) ?: mutableListOf()
        val log = ForwardLog(
            timestamp = item.timestamp,
            sender = item.sender,
            message = item.message,
            success = success,
            id = item.id,
            route = route,
            pending = pending,
            detail = detail
        )
        val existing = logs.indexOfFirst { it.id == item.id }
        if (existing >= 0) logs[existing] = log else logs.add(0, log)
        if (logs.size > 20) logs.subList(20, logs.size).clear()
        prefs.edit().putString(ForwardService.KEY_LOGS, gson.toJson(logs)).apply()
    }

    private fun updateLog(
        context: Context,
        id: String,
        success: Boolean,
        pending: Boolean,
        detail: String?
    ) {
        val prefs = context.getSharedPreferences(ForwardService.PREFS_NAME, Context.MODE_PRIVATE)
        val type = object : TypeToken<MutableList<ForwardLog>>() {}.type
        val logs = runCatching {
            gson.fromJson<MutableList<ForwardLog>>(
                prefs.getString(ForwardService.KEY_LOGS, "[]"),
                type
            )
        }.getOrNull() ?: mutableListOf()
        val index = logs.indexOfFirst { it.id == id }
        if (index < 0) return
        logs[index] = logs[index].copy(success = success, pending = pending, detail = detail)
        prefs.edit().putString(ForwardService.KEY_LOGS, gson.toJson(logs)).apply()
    }

    const val ROUTE_TELEGRAM = "telegram"
    const val ROUTE_SMS = "sms"
}

enum class ForwardAttemptResult {
    MISSING,
    SUCCESS,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}
