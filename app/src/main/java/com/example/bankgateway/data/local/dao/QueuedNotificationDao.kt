package com.example.bankgateway.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.bankgateway.data.local.QueuedNotificationEntity

@Dao
interface QueuedNotificationDao {
    @Insert
    suspend fun insert(entity: QueuedNotificationEntity): Long

    @Query("SELECT * FROM queued_notifications WHERE status = 'pending' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pending(limit: Int = 200): List<QueuedNotificationEntity>

    @Query("UPDATE queued_notifications SET status = :status, attemptCount = attemptCount + 1, lastError = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markAttempt(id: Long, status: String, error: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM queued_notifications WHERE status = 'pending'")
    suspend fun pendingCount(): Int
}
