package com.example.bankgateway.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.bankgateway.data.local.dao.AllowedPackageDao
import com.example.bankgateway.data.local.dao.NotificationLogDao
import com.example.bankgateway.data.local.dao.QueuedNotificationDao

@Database(
    entities = [QueuedNotificationEntity::class, AllowedPackageEntity::class, NotificationLogEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queuedNotificationDao(): QueuedNotificationDao
    abstract fun allowedPackageDao(): AllowedPackageDao
    abstract fun notificationLogDao(): NotificationLogDao
}
