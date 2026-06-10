package com.banknotif.gateway.service

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.banknotif.gateway.MainActivity
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
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        showFloatingDot()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_SHOW_OVERLAY") {
            showFloatingDot()
        } else if (intent?.action == "ACTION_HIDE_OVERLAY") {
            hideFloatingDot()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        hideFloatingDot()
        scope.cancel()
        super.onDestroy()
    }

    private fun showFloatingDot() {
        if (floatingView != null) return // Already showing
        if (!Settings.canDrawOverlays(this)) return // No permission

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = (32 * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val dotView = View(this).apply {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF10B981.toInt()) // AccentGreen
                setStroke((2 * resources.displayMetrics.density).toInt(), 0xFFFFFFFF.toInt()) // White border
            }
            background = shape
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dotView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(dotView, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        val launchIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(launchIntent)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(dotView, params)
            floatingView = dotView
        } catch (_: Exception) {}
    }

    private fun hideFloatingDot() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            floatingView = null
        }
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
