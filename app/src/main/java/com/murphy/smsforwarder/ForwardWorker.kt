package com.murphy.smsforwarder

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ForwardWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getString(KEY_PENDING_ID) ?: return Result.failure()
        return when (ForwardStore.attempt(applicationContext, id)) {
            ForwardAttemptResult.MISSING,
            ForwardAttemptResult.SUCCESS,
            ForwardAttemptResult.PERMANENT_FAILURE -> Result.success()
            ForwardAttemptResult.RETRYABLE_FAILURE -> Result.retry()
        }
    }

    companion object {
        private const val KEY_PENDING_ID = "pending_id"

        fun schedule(context: Context, id: String) {
            val request = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(workDataOf(KEY_PENDING_ID to id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(15, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "forward-$id",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
