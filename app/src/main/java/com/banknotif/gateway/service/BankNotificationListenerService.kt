package com.banknotif.gateway.service

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.banknotif.gateway.data.local.DatabaseProvider
import com.banknotif.gateway.data.local.ProcessedNotificationEntity
import com.banknotif.gateway.data.preferences.DeviceConfigStore
import com.banknotif.gateway.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

class BankNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val notificationKey = sbn.key ?: "$packageName|${sbn.postTime}|${sbn.id}"

        scope.launch {
            val database = DatabaseProvider.get(applicationContext)
            if (!database.allowedPackageDao().isAllowed(packageName)) return@launch

            val inserted = database.processedNotificationDao()
                .insert(ProcessedNotificationEntity(notificationKey))
            if (inserted == -1L) return@launch

            val appName = resolveAppName(packageName)

            val rawBody = JSONObject()
                .put("package_name", packageName)
                .put("app_name", appName)
                .put("title", title)
                .put("text", text)
                .put("big_text", bigText)
                .put("posted_at", Instant.ofEpochMilli(sbn.postTime).toString())
                .put("notification_key", notificationKey)
                .put("raw", JSONObject().put("id", sbn.id).put("tag", sbn.tag))
                .toString()

            val config = DeviceConfigStore(applicationContext).config.first()
            NotificationRepository(
                queueDao = database.queuedNotificationDao(),
                logDao = database.notificationLogDao(),
                context = applicationContext
            ).sendOrQueue(config, packageName, title, text, rawBody)
        }
    }

    private fun resolveAppName(packageName: String): String? = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
