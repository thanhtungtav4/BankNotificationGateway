package com.example.bankgateway.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bankgateway.data.local.DatabaseProvider
import com.example.bankgateway.data.preferences.DeviceConfigStore
import com.example.bankgateway.data.remote.ApiClient
import com.example.bankgateway.data.remote.MobileApi
import kotlinx.coroutines.flow.first

class NotificationRetryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val database = DatabaseProvider.get(applicationContext)
        val config = DeviceConfigStore(applicationContext).config.first()
        val serverUrl = config.serverUrl ?: return Result.retry()
        val deviceId = config.deviceId ?: return Result.retry()
        val deviceSecret = config.deviceSecret ?: return Result.retry()

        val api = MobileApi()
        database.queuedNotificationDao().pending().forEach { item ->
            try {
                val request = api.signedPostRequest(serverUrl, "/api/v1/mobile/notifications", deviceId, deviceSecret, item.payloadJson)
                ApiClient.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                }
                database.queuedNotificationDao().markAttempt(item.id, "sent", null)
            } catch (exception: Throwable) {
                database.queuedNotificationDao().markAttempt(item.id, "pending", exception.message)
            }
        }

        return Result.success()
    }
}
