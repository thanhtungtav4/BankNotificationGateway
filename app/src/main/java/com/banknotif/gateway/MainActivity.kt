package com.banknotif.gateway

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.banknotif.gateway.data.local.AllowedPackageEntity
import com.banknotif.gateway.data.local.DatabaseProvider
import com.banknotif.gateway.data.local.NotificationLogEntity
import com.banknotif.gateway.data.preferences.DeviceConfigStore
import com.banknotif.gateway.data.repository.NotificationRepository
import com.banknotif.gateway.data.repository.PairingRepository
import com.banknotif.gateway.pairing.QrScanResult
import com.banknotif.gateway.pairing.scanPairingQr
import com.banknotif.gateway.service.HeartbeatWorker
import com.banknotif.gateway.service.NotificationRetryWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleBackgroundWorkers()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BankGatewayApp(
                        onOpenNotificationSettings = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    private fun scheduleBackgroundWorkers() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val heartbeat = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        val retry = PeriodicWorkRequestBuilder<NotificationRetryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "device-heartbeat",
            ExistingPeriodicWorkPolicy.UPDATE,
            heartbeat
        )

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "notification-retry",
            ExistingPeriodicWorkPolicy.UPDATE,
            retry
        )
    }
}

@Composable
fun BankGatewayApp(onOpenNotificationSettings: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pairing", "Whitelist", "Logs", "Settings")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Bank Notification Gateway",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }

        when (selectedTab) {
            0 -> PairingScreen()
            1 -> WhitelistScreen()
            2 -> LogsScreen()
            3 -> SettingsScreen(onOpenNotificationSettings)
        }
    }
}

@Composable
fun PairingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configStore = remember { DeviceConfigStore(context.applicationContext) }
    val config by configStore.config.collectAsState()

    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8080") }
    var pairingToken by remember { mutableStateOf("tenant:1") }
    var deviceName by remember { mutableStateOf("Android Bank Phone") }
    var status by remember { mutableStateOf("Not paired") }
    var isLoading by remember { mutableStateOf(false) }

    fun pairNow(url: String, token: String, name: String) {
        if (!isValidServerUrl(url)) {
            status = "Invalid server URL. Use http:// or https://"
            return
        }
        scope.launch {
            isLoading = true
            status = "Pairing..."
            try {
                withContext(Dispatchers.IO) {
                    PairingRepository(configStore).pair(url.trim(), token.trim(), name.trim())
                }
                status = "Paired successfully"
            } catch (exception: Throwable) {
                status = "Pairing failed: ${exception.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pair Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Scan the QR shown on the server dashboard or paste the values manually.")

        Button(
            enabled = !isLoading,
            onClick = {
                status = "Opening scanner..."
                scanPairingQr(context) { result ->
                    when (result) {
                        is QrScanResult.Success -> {
                            serverUrl = result.serverUrl
                            pairingToken = result.pairingToken
                            status = "QR detected, pairing..."
                            pairNow(result.serverUrl, result.pairingToken, deviceName)
                        }
                        is QrScanResult.Error -> {
                            status = "Scan failed: ${result.message}"
                        }
                        QrScanResult.Cancelled -> {
                            status = "Scan cancelled"
                        }
                    }
                }
            }
        ) {
            Text("Scan QR")
        }

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = pairingToken,
            onValueChange = { pairingToken = it },
            label = { Text("Pairing Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            enabled = !isLoading,
            onClick = { pairNow(serverUrl, pairingToken, deviceName) }
        ) {
            Text(if (isLoading) "Pairing..." else "Pair Device")
        }

        Divider()
        Text("Status: $status")
        Text("Stored server: ${config.serverUrl ?: "none"}")
        Text("Stored device: ${config.deviceId ?: "none"}")
    }
}

@Composable
fun WhitelistScreen() {
    val context = LocalContext.current
    val database = remember { DatabaseProvider.get(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var installedApps by remember { mutableStateOf<List<AppListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val allowedPackages by database.allowedPackageDao().activePackages().collectAsState(initial = emptyList())
    val allowedSet = allowedPackages.map { it.packageName }.toSet()

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { AppListItem(packageName = it.packageName, appName = it.loadLabel(pm).toString()) }
                .sortedBy { it.appName.lowercase() }
        }
        isLoading = false
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Notification Whitelist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Enable only bank apps. Notifications from disabled packages are ignored locally.")
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Text("Loading installed apps...")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(installedApps, key = { it.packageName }) { app ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, fontWeight = FontWeight.Bold)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(
                            checked = allowedSet.contains(app.packageName),
                            onCheckedChange = { checked ->
                                scope.launch(Dispatchers.IO) {
                                    database.allowedPackageDao().upsert(
                                        AllowedPackageEntity(
                                            packageName = app.packageName,
                                            appName = app.appName,
                                            bankName = app.appName,
                                            isActive = checked
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val database = remember { DatabaseProvider.get(context.applicationContext) }
    val logs by database.notificationLogDao().latest().collectAsState(initial = emptyList())

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Notification Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Shows local sent, queued, and failed records.")
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(logs, key = { it.id }) { log ->
                LogCard(log)
            }
        }
    }
}

@Composable
fun LogCard(log: NotificationLogEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(log.status.uppercase(), fontWeight = FontWeight.Bold)
            Text(log.packageName)
            log.title?.let { Text("Title: $it") }
            log.text?.let { Text("Text: $it") }
            log.error?.let { Text("Error: $it") }
        }
    }
}

@Composable
fun SettingsScreen(onOpenNotificationSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configStore = remember { DeviceConfigStore(context.applicationContext) }
    val config by configStore.config.collectAsState()
    val isPaired = config.isPaired
    val listenerEnabled = isNotificationListenerEnabled(context)
    var testStatus by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Server URL: ${config.serverUrl ?: "none"}")
        Text("Device ID: ${config.deviceId ?: "none"}")
        Text("Device Secret: ${if (config.deviceSecret.isNullOrBlank()) "none" else "stored (encrypted)"}")
        Text("Notification Access: ${if (listenerEnabled) "GRANTED" else "DENIED"}")

        Button(onClick = onOpenNotificationSettings) {
            Text("Open Notification Access Settings")
        }

        Divider()
        Text("Connection Test", fontWeight = FontWeight.Bold)
        Text("Sends a fake Vietcombank notification to the paired server.")
        Button(
            enabled = isPaired && !isSending,
            onClick = {
                val currentConfig = config
                scope.launch {
                    isSending = true
                    testStatus = "Sending..."
                    try {
                        val rawBody = buildTestNotificationPayload()
                        withContext(Dispatchers.IO) {
                            val database = DatabaseProvider.get(context.applicationContext)
                            NotificationRepository(
                                queueDao = database.queuedNotificationDao(),
                                logDao = database.notificationLogDao(),
                                context = context.applicationContext
                            ).sendOrQueue(
                                config = currentConfig,
                                packageName = "com.banknotif.gateway.test",
                                title = "Vietcombank",
                                text = "TK 0123456789 +500,000VND luc 08/06/2026 14:30. So du 2,500,000VND. ND: TEST GATEWAY",
                                rawBody = rawBody
                            )
                        }
                        testStatus = "Sent. Check Logs tab to see sent or queued result."
                    } catch (exception: Throwable) {
                        testStatus = "Error: ${exception.message}"
                    } finally {
                        isSending = false
                    }
                }
            }
        ) {
            Text(if (isSending) "Sending..." else "Send Test Notification")
        }
        if (testStatus.isNotBlank()) {
            Text(testStatus)
        }
        if (!isPaired) {
            Text("Pair the device first to enable the test button.")
        }

        Divider()
        Text("Pairing Management", fontWeight = FontWeight.Bold)
        Button(
            enabled = isPaired && !isResetting,
            onClick = {
                scope.launch {
                    isResetting = true
                    try {
                        configStore.clear()
                        testStatus = "Pairing cleared. Re-pair on the Pairing tab."
                    } finally {
                        isResetting = false
                    }
                }
            }
        ) {
            Text(if (isResetting) "Clearing..." else "Disconnect / Reset Pairing")
        }

        Divider()
        Text("Background workers are scheduled every 15 minutes with network connectivity required.")
    }
}

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.split(":").any { it.startsWith("${context.packageName}/") }
}

internal fun isValidServerUrl(url: String): Boolean {
    val trimmed = url.trim()
    return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) && trimmed.length > 8
}

private fun buildTestNotificationPayload(): String {
    val now = Instant.now()
    val notificationKey = "test|${now.toEpochMilli()}"
    val json = JSONObject()
 .put("package_name", "com.banknotif.gateway.test")
        .put("app_name", "Vietcombank (Test)")
        .put("title", "Vietcombank")
        .put("text", "TK 0123456789 +500,000VND luc 08/06/2026 14:30. So du 2,500,000VND. ND: TEST GATEWAY")
        .put("posted_at", now.toString())
        .put("notification_key", notificationKey)
    val raw = JSONObject()
        .put("id", 0)
        .put("tag", "test")
        .put("source", "manual-test")
    json.put("raw", raw)
    return json.toString()
}

data class AppListItem(
    val packageName: String,
    val appName: String
)
