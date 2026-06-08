package com.banknotif.gateway.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.banknotif.gateway.data.local.DatabaseProvider
import com.banknotif.gateway.data.preferences.DeviceConfigStore
import com.banknotif.gateway.data.remote.ApiClient
import com.banknotif.gateway.data.remote.MobileApi
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class NotificationRetryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val database = DatabaseProvider.get(applicationContext)
        val config = DeviceConfigStore(applicationContext).config.first()
        val serverUrl = config.serverUrl ?: return Result.retry()
        val deviceId = config.deviceId ?: return Result.retry()
        val deviceSecret = config.deviceSecret ?: return Result.retry()

        val api = MobileApi()
        val pending = database.queuedNotificationDao().pending()
        for (item in pending) {
            try {
                val request = api.signedPostRequest(serverUrl, "/api/v1/mobile/notifications", deviceId, deviceSecret, item.payloadJson)
                ApiClient.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                }
                database.queuedNotificationDao().markAttempt(item.id, "sent", null)
            } catch (exception: Throwable) {
                val nextAttempt = item.attemptCount + 1
                val newStatus = if (nextAttempt >= MAX_ATTEMPTS) "failed" else "pending"
                database.queuedNotificationDao().markAttempt(item.id, newStatus, exception.message)
            }
        }

        return Result.success()
    }

    companion object {
        const val MAX_ATTEMPTS = 10
        private const val UNIQUE_NAME = "notification-retry-immediate"

        fun enqueueImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<NotificationRetryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
