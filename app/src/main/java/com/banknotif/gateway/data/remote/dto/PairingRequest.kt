package com.banknotif.gateway.data.remote.dto

data class PairingRequest(
    val pairingToken: String,
    val deviceName: String,
    val appVersion: String,
    val androidVersion: String
)
