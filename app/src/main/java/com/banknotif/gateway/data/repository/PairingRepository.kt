package com.banknotif.gateway.data.repository

import android.os.Build
import com.banknotif.gateway.data.preferences.DeviceConfigStore
import com.banknotif.gateway.data.remote.ApiClient
import com.banknotif.gateway.data.remote.MobileApi
import org.json.JSONObject

class PairingRepository(
    private val configStore: DeviceConfigStore,
    private val mobileApi: MobileApi = MobileApi()
) {
    suspend fun pair(serverUrl: String, pairingToken: String, deviceName: String, appVersion: String = "1.0.0") {
        val normalizedUrl = serverUrl.trimEnd('/')
        val request = mobileApi.pairingRequest(normalizedUrl, pairingToken, deviceName, appVersion, Build.VERSION.RELEASE)
        ApiClient.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Pairing failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            configStore.save(
                serverUrl = normalizedUrl,
                deviceId = json.getString("device_id"),
                deviceSecret = json.getString("device_secret")
            )
        }
    }
}
