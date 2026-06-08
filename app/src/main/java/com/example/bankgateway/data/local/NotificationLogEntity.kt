package com.example.bankgateway.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val status: String,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
