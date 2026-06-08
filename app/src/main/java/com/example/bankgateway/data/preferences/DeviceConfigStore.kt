package com.example.bankgateway.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.deviceConfigDataStore by preferencesDataStore(name = "device_config")

class DeviceConfigStore(private val context: Context) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val deviceSecretKey = stringPreferencesKey("device_secret")

    val config: Flow<DeviceConfig> = context.deviceConfigDataStore.data.map { preferences ->
        DeviceConfig(preferences[serverUrlKey], preferences[deviceIdKey], preferences[deviceSecretKey])
    }

    suspend fun save(config: DeviceConfig) {
        context.deviceConfigDataStore.edit { preferences ->
            config.serverUrl?.let { preferences[serverUrlKey] = it }
            config.deviceId?.let { preferences[deviceIdKey] = it }
            config.deviceSecret?.let { preferences[deviceSecretKey] = it }
        }
    }
}

data class DeviceConfig(val serverUrl: String?, val deviceId: String?, val deviceSecret: String?)
