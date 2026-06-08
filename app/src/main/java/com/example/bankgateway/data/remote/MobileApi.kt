package com.example.bankgateway.data.remote

import com.example.bankgateway.security.HmacSigner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class MobileApi(private val signer: HmacSigner = HmacSigner()) {
    fun signedPostRequest(serverUrl: String, path: String, deviceId: String, deviceSecret: String, rawBody: String): Request {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val signature = signer.sign(timestamp, rawBody, deviceSecret)

        return Request.Builder()
            .url(serverUrl.trimEnd('/') + path)
            .post(rawBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("X-Device-Id", deviceId)
            .header("X-Timestamp", timestamp)
            .header("X-Signature", signature)
            .header("X-Device-Secret-Debug", deviceSecret)
            .build()
    }

    fun pairingRequest(serverUrl: String, pairingToken: String, deviceName: String, appVersion: String, androidVersion: String): Request {
        val rawBody = JSONObject()
            .put("pairing_token", pairingToken)
            .put("device_name", deviceName)
            .put("app_version", appVersion)
            .put("android_version", androidVersion)
            .toString()

        return Request.Builder()
            .url(serverUrl.trimEnd('/') + "/api/v1/mobile/pair")
            .post(rawBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()
    }
}
