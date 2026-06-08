package com.example.bankgateway

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
import com.example.bankgateway.data.local.AllowedPackageEntity
import com.example.bankgateway.data.local.DatabaseProvider
import com.example.bankgateway.data.local.NotificationLogEntity
import com.example.bankgateway.data.preferences.DeviceConfigStore
import com.example.bankgateway.data.repository.PairingRepository
import com.example.bankgateway.service.HeartbeatWorker
import com.example.bankgateway.service.NotificationRetryWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val config by configStore.config.collectAsState(initial = null)

    var serverUrl by remember { mutableStateOf("http://10.0.2.2:8080") }
    var pairingToken by remember { mutableStateOf("tenant:1") }
    var deviceName by remember { mutableStateOf("Android Bank Phone") }
    var status by remember { mutableStateOf("Not paired") }
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pair Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Use manual pairing first. QR scan can be added after server dashboard creates QR payloads.")

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
            onClick = {
                scope.launch {
                    isLoading = true
                    status = "Pairing..."
                    try {
                        withContext(Dispatchers.IO) {
                            PairingRepository(configStore).pair(serverUrl, pairingToken, deviceName)
                        }
                        status = "Paired successfully"
                    } catch (exception: Throwable) {
                        status = "Pairing failed: ${exception.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }
        ) {
            Text(if (isLoading) "Pairing..." else "Pair Device")
        }

        Divider()
        Text("Status: $status")
        Text("Stored server: ${config?.serverUrl ?: "none"}")
        Text("Stored device: ${config?.deviceId ?: "none"}")
    }
}

@Composable
fun WhitelistScreen() {
    val context = LocalContext.current
    val database = remember { DatabaseProvider.get(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var installedApps by remember { mutableStateOf<List<AppListItem>>(emptyList()) }
    val allowedPackages by database.allowedPackageDao().activePackages().collectAsState(initial = emptyList())
    val allowedSet = allowedPackages.map { it.packageName }.toSet()

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            context.packageManager.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || looksLikeBankApp(it.loadLabel(context.packageManager).toString(), it.packageName) }
                .map {
                    AppListItem(
                        packageName = it.packageName,
                        appName = it.loadLabel(context.packageManager).toString()
                    )
                }
                .sortedBy { it.appName.lowercase() }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Notification Whitelist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Enable only bank apps. Notifications from disabled packages are ignored locally.")
        Spacer(modifier = Modifier.height(12.dp))

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
    val configStore = remember { DeviceConfigStore(context.applicationContext) }
    val config by configStore.config.collectAsState(initial = null)

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Server URL: ${config?.serverUrl ?: "none"}")
        Text("Device ID: ${config?.deviceId ?: "none"}")
        Text("Device Secret: ${if (config?.deviceSecret.isNullOrBlank()) "none" else "stored"}")

        Button(onClick = onOpenNotificationSettings) {
            Text("Open Notification Access Settings")
        }

        Text("Background workers are scheduled every 15 minutes with network connectivity required.")
    }
}

data class AppListItem(
    val packageName: String,
    val appName: String
)

fun looksLikeBankApp(appName: String, packageName: String): Boolean {
    val text = "$appName $packageName".lowercase()
    return listOf(
        "bank",
        "mb",
        "vietcombank",
        "vcb",
        "acb",
        "techcombank",
        "tpbank",
        "vpbank",
        "bidv",
        "vietinbank",
        "msb",
        "seabank",
        "scb",
        "hdbank",
        "ocb",
        "sacombank",
        "nam a",
        "namabank",
        "viet a",
        "vietabank",
        "shb",
        "vib",
        "eximbank",
        "lpbank",
        "lienviet",
        "pvcombank",
        "bac a",
        "baca",
        "kienlong",
        "saigonbank",
        "ncb",
        "bvbank",
        "vietbank",
        "abbank",
        "pgbank",
        "gpbank",
        "cbbank",
        "oceanbank",
        "momo",
        "zalopay"
    ).any { text.contains(it) }
}
