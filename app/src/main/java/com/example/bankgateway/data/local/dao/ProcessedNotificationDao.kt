package com.example.bankgateway.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bankgateway.data.local.ProcessedNotificationEntity

@Dao
interface ProcessedNotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProcessedNotificationEntity): Long

    @Query("DELETE FROM processed_notifications WHERE createdAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
