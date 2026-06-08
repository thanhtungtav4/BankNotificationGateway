package com.example.bankgateway.data.repository

import android.content.Context
import com.example.bankgateway.data.local.NotificationLogEntity
import com.example.bankgateway.data.local.QueuedNotificationEntity
import com.example.bankgateway.data.local.dao.NotificationLogDao
import com.example.bankgateway.data.local.dao.QueuedNotificationDao
import com.example.bankgateway.data.preferences.DeviceConfig
import com.example.bankgateway.data.remote.ApiClient
import com.example.bankgateway.data.remote.MobileApi
import com.example.bankgateway.service.NotificationRetryWorker

class NotificationRepository(
    private val queueDao: QueuedNotificationDao,
    private val logDao: NotificationLogDao,
    private val context: Context,
    private val mobileApi: MobileApi = MobileApi()
) {
    suspend fun sendOrQueue(config: DeviceConfig, packageName: String, title: String?, text: String?, rawBody: String) {
        if (!config.isPaired) {
            queue(rawBody, packageName, title, text, "Device is not paired")
            return
        }

        try {
            val request = mobileApi.signedPostRequest(
                config.serverUrl!!,
                "/api/v1/mobile/notifications",
                config.deviceId!!,
                config.deviceSecret!!,
                rawBody
            )
            ApiClient.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
            }
            logDao.insert(NotificationLogEntity(packageName = packageName, title = title, text = text, status = "sent"))
        } catch (exception: Throwable) {
            queue(rawBody, packageName, title, text, exception.message)
        }
    }

    private suspend fun queue(rawBody: String, packageName: String, title: String?, text: String?, error: String?) {
        queueDao.insert(QueuedNotificationEntity(payloadJson = rawBody, lastError = error))
        logDao.insert(NotificationLogEntity(packageName = packageName, title = title, text = text, status = "queued", error = error))
        NotificationRetryWorker.enqueueImmediate(context)
    }
}
