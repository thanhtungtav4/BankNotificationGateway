package com.banknotif.gateway.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.banknotif.gateway.data.local.dao.AllowedPackageDao
import com.banknotif.gateway.data.local.dao.NotificationLogDao
import com.banknotif.gateway.data.local.dao.ProcessedNotificationDao
import com.banknotif.gateway.data.local.dao.QueuedNotificationDao

@Database(
    entities = [
        QueuedNotificationEntity::class,
        AllowedPackageEntity::class,
        NotificationLogEntity::class,
        ProcessedNotificationEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queuedNotificationDao(): QueuedNotificationDao
    abstract fun allowedPackageDao(): AllowedPackageDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun processedNotificationDao(): ProcessedNotificationDao
}
