package com.example.bankgateway.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.bankgateway.data.local.DatabaseProvider
import com.example.bankgateway.data.preferences.DeviceConfigStore
import com.example.bankgateway.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

class BankNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val notificationKey = sbn.key ?: "$packageName|${sbn.postTime}"

        scope.launch {
            val database = DatabaseProvider.get(applicationContext)
            if (!database.allowedPackageDao().isAllowed(packageName)) return@launch

            val rawBody = JSONObject()
                .put("package_name", packageName)
                .put("app_name", packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString())
                .put("title", title)
                .put("text", text)
                .put("big_text", bigText)
                .put("posted_at", Instant.ofEpochMilli(sbn.postTime).toString())
                .put("notification_key", notificationKey)
                .put("raw", JSONObject().put("id", sbn.id).put("tag", sbn.tag))
                .toString()

            val config = DeviceConfigStore(applicationContext).config.first()
            NotificationRepository(database.queuedNotificationDao(), database.notificationLogDao()).sendOrQueue(config, packageName, title, text, rawBody)
        }
    }
}
