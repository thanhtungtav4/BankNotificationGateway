package com.banknotif.gateway.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allowed_packages")
data class AllowedPackageEntity(
    @PrimaryKey val packageName: String,
    val appName: String?,
    val bankName: String?,
    val isActive: Boolean = true
)
