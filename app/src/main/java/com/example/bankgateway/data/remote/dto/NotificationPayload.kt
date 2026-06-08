package com.example.bankgateway.data.remote.dto

data class NotificationPayload(
    val packageName: String,
    val appName: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val postedAt: String,
    val notificationKey: String,
    val raw: Map<String, Any?> = emptyMap()
)
