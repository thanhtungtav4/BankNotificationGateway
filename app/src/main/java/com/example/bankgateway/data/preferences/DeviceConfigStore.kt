package com.example.bankgateway.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DeviceConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "device_config_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _config = MutableStateFlow(read())
    val config: StateFlow<DeviceConfig> = _config.asStateFlow()

    suspend fun save(serverUrl: String?, deviceId: String?, deviceSecret: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            serverUrl?.let { putString(KEY_SERVER_URL, it) }
            deviceId?.let { putString(KEY_DEVICE_ID, it) }
            deviceSecret?.let { putString(KEY_DEVICE_SECRET, it) }
        }.apply()
        _config.value = read()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
        _config.value = DeviceConfig(null, null, null)
    }

    private fun read(): DeviceConfig = DeviceConfig(
        serverUrl = prefs.getString(KEY_SERVER_URL, null),
        deviceId = prefs.getString(KEY_DEVICE_ID, null),
        deviceSecret = prefs.getString(KEY_DEVICE_SECRET, null)
    )

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_SECRET = "device_secret"
    }
}

data class DeviceConfig(val serverUrl: String?, val deviceId: String?, val deviceSecret: String?) {
    val isPaired: Boolean
        get() = !serverUrl.isNullOrBlank() && !deviceId.isNullOrBlank() && !deviceSecret.isNullOrBlank()
}
