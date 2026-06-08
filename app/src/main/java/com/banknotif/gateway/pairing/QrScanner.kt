package com.banknotif.gateway.pairing

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject

sealed class QrScanResult {
    data class Success(val serverUrl: String, val pairingToken: String) : QrScanResult()
    data class Error(val message: String) : QrScanResult()
    data object Cancelled : QrScanResult()
}

fun scanPairingQr(context: Context, onResult: (QrScanResult) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()

    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val raw = barcode.rawValue
            if (raw.isNullOrBlank()) {
                onResult(QrScanResult.Error("Empty QR payload"))
                return@addOnSuccessListener
            }
            try {
                val json = JSONObject(raw)
                val url = json.optString("server_url").trim()
                val token = json.optString("pairing_token").trim()
                if (url.isEmpty() || token.isEmpty()) {
                    onResult(QrScanResult.Error("QR missing server_url or pairing_token"))
                } else {
                    onResult(QrScanResult.Success(url, token))
                }
            } catch (exception: Throwable) {
                onResult(QrScanResult.Error("Invalid QR payload: ${exception.message}"))
            }
        }
        .addOnCanceledListener { onResult(QrScanResult.Cancelled) }
        .addOnFailureListener { exception ->
            onResult(QrScanResult.Error(exception.message ?: "Scanner failed"))
        }
}
