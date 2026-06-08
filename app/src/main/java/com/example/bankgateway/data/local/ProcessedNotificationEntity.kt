package com.example.bankgateway.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_notifications")
data class ProcessedNotificationEntity(
    @PrimaryKey val notificationKey: String,
    val createdAt: Long = System.currentTimeMillis()
)
