package com.banknotif.gateway.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_notifications")
data class QueuedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val status: String = "pending",
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
