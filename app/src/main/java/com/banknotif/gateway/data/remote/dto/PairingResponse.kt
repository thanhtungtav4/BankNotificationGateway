package com.banknotif.gateway.data.remote.dto

data class PairingResponse(
    val deviceId: String,
    val deviceSecret: String,
    val serverUrl: String
)
