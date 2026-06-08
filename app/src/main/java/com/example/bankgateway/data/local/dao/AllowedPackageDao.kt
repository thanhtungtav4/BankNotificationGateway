package com.example.bankgateway.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.bankgateway.data.local.AllowedPackageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowedPackageDao {
    @Query("SELECT * FROM allowed_packages WHERE isActive = 1 ORDER BY appName ASC")
    fun activePackages(): Flow<List<AllowedPackageEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM allowed_packages WHERE packageName = :packageName AND isActive = 1)")
    suspend fun isAllowed(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AllowedPackageEntity)

    @Query("UPDATE allowed_packages SET isActive = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}
