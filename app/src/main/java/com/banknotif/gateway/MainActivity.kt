package com.banknotif.gateway

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.banknotif.gateway.data.local.AllowedPackageEntity
import com.banknotif.gateway.data.local.DatabaseProvider
import com.banknotif.gateway.data.local.NotificationLogEntity
import com.banknotif.gateway.data.preferences.DeviceConfigStore
import com.banknotif.gateway.data.remote.ApiClient
import com.banknotif.gateway.data.repository.NotificationRepository
import com.banknotif.gateway.data.repository.PairingRepository
import com.banknotif.gateway.pairing.QrScanResult
import com.banknotif.gateway.pairing.scanPairingQr
import com.banknotif.gateway.service.HeartbeatWorker
import com.banknotif.gateway.service.NotificationRetryWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

// Custom colors
private val DarkBg = Color(0xFF0F172A)
private val DarkSurface = Color(0xFF1E293B)
private val DarkCard = Color(0xFF334155)
private val AccentGreen = Color(0xFF10B981)
private val AccentBlue = Color(0xFF3B82F6)
private val AccentOrange = Color(0xFFF59E0B)
private val AccentRed = Color(0xFFEF4444)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleBackgroundWorkers()

        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(
                    primary = AccentBlue,
                    onPrimary = Color.White,
                    primaryContainer = AccentBlue.copy(alpha = 0.2f),
                    secondary = AccentGreen,
                    onSecondary = Color.White,
                    background = DarkBg,
                    surface = DarkSurface,
                    surfaceVariant = DarkCard,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary,
                    onSurfaceVariant = TextSecondary,
                    error = AccentRed,
                    onError = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
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
            .setConstraints(constraints).build()

        val retry = PeriodicWorkRequestBuilder<NotificationRetryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("device-heartbeat", ExistingPeriodicWorkPolicy.UPDATE, heartbeat)
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("notification-retry", ExistingPeriodicWorkPolicy.UPDATE, retry)
    }
}

@Composable
fun BankGatewayApp(onOpenNotificationSettings: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pair", "Apps", "Logs", "Settings")

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkSurface, DarkBg)
                    )
                )
                .padding(top = 32.dp, bottom = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(AccentBlue, AccentGreen))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellular4Bar,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Bank Gateway",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Notification Forwarder",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = TextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AccentBlue
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    selectedContentColor = AccentBlue,
                    unselectedContentColor = TextSecondary
                )
            }
        }

        // Content
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

    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Show paired view if already paired
    if (config.isPaired) {
        PairedView(configStore = configStore)
        return
    }

    fun pairNow(url: String, token: String, name: String) {
        if (!isValidServerUrl(url)) {
            status = "Invalid server URL"
            return
        }
        scope.launch {
            isLoading = true
            status = "Connecting..."
            try {
                withContext(Dispatchers.IO) {
                    PairingRepository(configStore).pair(url.trim(), token.trim(), name.trim())
                }
            } catch (exception: Throwable) {
                status = "Error: ${exception.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // QR Scanner Button
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.3f), AccentBlue.copy(alpha = 0.05f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            OutlinedButton(
                onClick = {
                    status = "Opening scanner..."
                    scanPairingQr(context) { result ->
                        when (result) {
                            is QrScanResult.Success -> {
                                status = "Connecting..."
                                pairNow(result.serverUrl, result.pairingToken, "Android Bank Phone")
                            }
                            is QrScanResult.Error -> {
                                status = "Scan failed"
                            }
                            QrScanResult.Cancelled -> {
                                status = ""
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.size(150.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentBlue
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = AccentBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan QR",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Pair with Server",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scan the QR code from your server dashboard",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Status
        if (status.isNotEmpty()) {
            val isError = status.contains("Error") || status.contains("failed")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) AccentRed.copy(alpha = 0.1f) else AccentGreen.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) AccentRed else AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = status,
                        color = if (isError) AccentRed else AccentGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PairedView(configStore: DeviceConfigStore) {
    val config by configStore.config.collectAsState()
    val scope = rememberCoroutineScope()
    var showSuccess by remember { mutableStateOf(true) }

    val scale by animateFloatAsState(
        targetValue = if (showSuccess) 1f else 0.8f,
        animationSpec = tween(500),
        label = "success_scale"
    )

    LaunchedEffect(Unit) {
        delay(2000)
        showSuccess = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success animation
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentGreen.copy(alpha = 0.4f), AccentGreen.copy(alpha = 0.1f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connected",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your device is paired and ready",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                InfoRow(label = "Server", value = config.serverUrl ?: "-")
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(label = "Device ID", value = config.deviceId?.takeLast(12) ?: "-", mono = true)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Change Device Button
        OutlinedButton(
            onClick = {
                scope.launch {
                    val currentConfig = config
                    if (currentConfig.serverUrl != null && currentConfig.deviceId != null && currentConfig.deviceSecret != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                val requestBody = JSONObject()
                                    .put("device_id", currentConfig.deviceId)
                                    .put("device_secret", currentConfig.deviceSecret)
                                    .toString()
                                val request = Request.Builder()
                                    .url(currentConfig.serverUrl.trimEnd('/') + "/api/v1/mobile/unpair")
                                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                                    .header("Content-Type", "application/json")
                                    .build()
                                ApiClient.httpClient.newCall(request).execute().use { }
                            } catch (_: Throwable) { }
                        }
                    }
                    configStore.clear()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
        ) {
            Text("Change Device", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Bank Apps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${installedApps.size} apps installed",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(installedApps, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        isEnabled = allowedSet.contains(app.packageName),
                        onToggle = { checked ->
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

@Composable
fun AppCard(app: AppListItem, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) AccentGreen.copy(alpha = 0.1f) else DarkCard,
        animationSpec = tween(300),
        label = "card_bg"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.CheckboxDefaults.colors(
                    checkedColor = AccentGreen,
                    uncheckedColor = TextSecondary
                )
            )
        }
    }
}

@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val database = remember { DatabaseProvider.get(context.applicationContext) }
    val logs by database.notificationLogDao().latest().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Activity Log",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "${logs.size} entries",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No notifications yet", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogCard(log)
                }
            }
        }
    }
}

@Composable
fun LogCard(log: NotificationLogEntity) {
    val statusColor = when (log.status.lowercase()) {
        "sent" -> AccentGreen
        "queued" -> AccentOrange
        else -> AccentRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.status.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = log.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (!log.title.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = log.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!log.error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentRed
                )
            }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Notification Access Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (listenerEnabled) AccentGreen.copy(alpha = 0.1f) else AccentOrange.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (listenerEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (listenerEnabled) AccentGreen else AccentOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification Access",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (listenerEnabled) "Granted" else "Not granted",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (listenerEnabled) AccentGreen else AccentOrange
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onOpenNotificationSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
        ) {
            Text("Open Access Settings")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Connection Test
        if (isPaired) {
            Text(
                text = "Connection Test",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val currentConfig = config
                    scope.launch {
                        isSending = true
                        try {
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
                                    text = "Test notification",
                                    rawBody = JSONObject()
                                        .put("package_name", "com.banknotif.gateway.test")
                                        .put("app_name", "Gateway Test")
                                        .put("title", "Vietcombank")
                                        .put("text", "Test notification")
                                        .put("posted_at", Instant.now().toString())
                                        .put("notification_key", "test_key_" + System.currentTimeMillis())
                                        .toString()
                                )
                            }
                            testStatus = "Test sent!"
                        } catch (e: Throwable) {
                            testStatus = "Error: ${e.message}"
                        } finally {
                            isSending = false
                        }
                    }
                },
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(if (isSending) "Sending..." else "Send Test Notification")
            }

            if (testStatus.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = testStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Device Info",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(label = "Status", value = if (isPaired) "Paired" else "Not paired")
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow(label = "Server", value = config.serverUrl?.takeLast(30) ?: "-")
            }
        }
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

data class AppListItem(val packageName: String, val appName: String)