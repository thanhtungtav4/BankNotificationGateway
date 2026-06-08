package com.example.bankgateway.data.repository

import android.os.Build
import com.example.bankgateway.data.preferences.DeviceConfig
import com.example.bankgateway.data.preferences.DeviceConfigStore
import com.example.bankgateway.data.remote.ApiClient
import com.example.bankgateway.data.remote.MobileApi
import org.json.JSONObject

class PairingRepository(
    private val configStore: DeviceConfigStore,
    private val mobileApi: MobileApi = MobileApi()
) {
    suspend fun pair(serverUrl: String, pairingToken: String, deviceName: String, appVersion: String = "1.0.0") {
        val request = mobileApi.pairingRequest(serverUrl, pairingToken, deviceName, appVersion, Build.VERSION.RELEASE)
        ApiClient.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Pairing failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            configStore.save(
                DeviceConfig(
                    serverUrl = json.getString("server_url"),
                    deviceId = json.getString("device_id"),
                    deviceSecret = json.getString("device_secret")
                )
            )
        }
    }
}
