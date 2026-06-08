package com.banknotif.gateway.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.banknotif.gateway.data.local.NotificationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(entity: NotificationLogEntity): Long

    @Query("SELECT * FROM notification_logs ORDER BY createdAt DESC LIMIT :limit")
    fun latest(limit: Int = 100): Flow<List<NotificationLogEntity>>
}
