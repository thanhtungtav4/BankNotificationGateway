package com.example.bankgateway.service

import android.content.Context
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bankgateway.data.local.DatabaseProvider
import com.example.bankgateway.data.preferences.DeviceConfigStore
import com.example.bankgateway.data.remote.ApiClient
import com.example.bankgateway.data.remote.MobileApi
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class HeartbeatWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val config = DeviceConfigStore(applicationContext).config.first()
        val serverUrl = config.serverUrl ?: return Result.retry()
        val deviceId = config.deviceId ?: return Result.retry()
        val deviceSecret = config.deviceSecret ?: return Result.retry()
        val database = DatabaseProvider.get(applicationContext)
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val rawBody = JSONObject()
            .put("battery_level", batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            .put("is_charging", false)
            .put("listener_enabled", true)
            .put("queue_pending", database.queuedNotificationDao().pendingCount())
            .put("app_version", "1.0.0")
            .put("android_version", android.os.Build.VERSION.RELEASE)
            .toString()

        return try {
            val request = MobileApi().signedPostRequest(serverUrl, "/api/v1/mobile/heartbeat", deviceId, deviceSecret, rawBody)
            ApiClient.httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success() else Result.retry()
            }
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
