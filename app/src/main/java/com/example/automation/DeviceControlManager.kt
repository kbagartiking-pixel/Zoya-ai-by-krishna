package com.example.automation

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.services.ZoyaAccessibilityService
import com.example.services.ZoyaAssistantService
import com.example.services.ZoyaNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class BatteryTelemetry(
    val percentage: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float
)

data class DeviceTelemetry(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val battery: BatteryTelemetry,
    val networkStatus: String,
    val isOnline: Boolean,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val freeStorageGb: Long,
    val totalStorageGb: Long
)

data class PermissionSummary(
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val notificationListenerEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val backgroundServiceRunning: Boolean
)

/**
 * Real Android device integration layer executing permitted app launches, system settings,
 * battery/device telemetry, alarms, timers, reminders, and user-confirmed dialer/SMS intents.
 */
class DeviceControlManager(private val context: Context) {

    private val appContext = context.applicationContext

    fun isInternetAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getNetworkLabel(): String {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Offline"
        val network = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }
    }

    fun readBatteryTelemetry(): BatteryTelemetry {
        val batteryStatus = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return BatteryTelemetry(
            percentage = pct,
            isCharging = charging,
            temperatureCelsius = tempTenths / 10f
        )
    }

    fun readDeviceTelemetry(): DeviceTelemetry {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        return DeviceTelemetry(
            model = Build.MODEL ?: "Android Device",
            manufacturer = (Build.MANUFACTURER ?: "").replaceFirstChar { it.uppercase() },
            androidVersion = Build.VERSION.RELEASE ?: "Android",
            sdkInt = Build.VERSION.SDK_INT,
            battery = readBatteryTelemetry(),
            networkStatus = getNetworkLabel(),
            isOnline = isInternetAvailable(),
            availableRamMb = memInfo.availMem / (1024L * 1024L),
            totalRamMb = memInfo.totalMem / (1024L * 1024L),
            freeStorageGb = freeBytes / (1024L * 1024L * 1024L),
            totalStorageGb = totalBytes / (1024L * 1024L * 1024L)
        )
    }

    fun readPermissions(): PermissionSummary {
        val micGranted = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = pm?.isIgnoringBatteryOptimizations(appContext.packageName) == true

        val enabledListeners = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val notifListenerEnabled = enabledListeners.contains(appContext.packageName) ||
            ZoyaNotificationListenerService.isConnected.value

        val enabledAccessibility = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val accessibilityEnabled = enabledAccessibility.contains(appContext.packageName) ||
            ZoyaAccessibilityService.isConnected.value

        return PermissionSummary(
            microphoneGranted = micGranted,
            notificationsGranted = notifGranted,
            accessibilityEnabled = accessibilityEnabled,
            notificationListenerEnabled = notifListenerEnabled,
            batteryOptimizationIgnored = batteryIgnored,
            backgroundServiceRunning = ZoyaAssistantService.isRunning.value
        )
    }

    suspend fun getLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, 0)
        activities
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == appContext.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) null else InstalledAppInfo(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun openPackage(packageName: String): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openAppByQuery(query: String): Pair<Boolean, String> {
        val normalized = query.trim().lowercase()
        return when {
            normalized.contains("youtube") -> openYouTube()
            normalized.contains("instagram") || normalized == "insta" -> openInstagram()
            normalized.contains("whatsapp") || normalized == "wp" -> openWhatsApp()
            normalized.contains("camera") -> openCamera()
            normalized.contains("gallery") || normalized.contains("photos") -> openGallery()
            normalized.contains("chrome") || normalized.contains("browser") -> openChrome()
            else -> {
                val pm = appContext.packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val match = pm.queryIntentActivities(launcherIntent, 0).firstOrNull { info ->
                    val label = info.loadLabel(pm)?.toString()?.lowercase().orEmpty()
                    label == normalized || label.contains(normalized) || normalized.contains(label)
                }
                if (match != null) {
                    val pkg = match.activityInfo.packageName
                    val label = match.loadLabel(pm).toString()
                    val ok = openPackage(pkg)
                    ok to if (ok) label else query
                } else {
                    false to query
                }
            }
        }
    }

    fun openYouTube(): Pair<Boolean, String> {
        if (openPackage("com.google.android.youtube")) return true to "YouTube"
        val webOk = launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")))
        return webOk to "YouTube"
    }

    fun openInstagram(): Pair<Boolean, String> {
        if (openPackage("com.instagram.android")) return true to "Instagram"
        val webOk = launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com")))
        return webOk to "Instagram"
    }

    fun openWhatsApp(): Pair<Boolean, String> {
        if (openPackage("com.whatsapp") || openPackage("com.whatsapp.w4b")) return true to "WhatsApp"
        return false to "WhatsApp"
    }

    fun openCamera(): Pair<Boolean, String> {
        val ok = launchIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        return ok to "Camera"
    }

    fun openGallery(): Pair<Boolean, String> {
        if (openPackage("com.google.android.apps.photos")) return true to "Gallery"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
        }
        return launchIntent(intent) to "Gallery"
    }

    fun openChrome(): Pair<Boolean, String> {
        if (openPackage("com.android.chrome")) return true to "Chrome"
        val webOk = launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        return webOk to "Chrome"
    }

    fun openSystemSetting(settingKey: String): Pair<Boolean, String> {
        val (action, label) = when (settingKey.lowercase()) {
            "wifi", "wi-fi", "internet" -> Settings.ACTION_WIFI_SETTINGS to "Wi-Fi settings"
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth settings"
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to "Location settings"
            "display", "brightness", "screen" -> Settings.ACTION_DISPLAY_SETTINGS to "Display settings"
            "sound", "volume", "audio" -> Settings.ACTION_SOUND_SETTINGS to "Sound settings"
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS to "Battery settings"
            "battery_optimization" -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS to "Battery Optimization"
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS to "Accessibility settings"
            "notification_access" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS to "Notification Access"
            "app_permissions" -> {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", appContext.packageName, null)
                )
                return launchIntent(intent) to "App permissions"
            }
            else -> Settings.ACTION_SETTINGS to "Device settings"
        }
        val ok = launchIntent(Intent(action)) || launchIntent(Intent(Settings.ACTION_SETTINGS))
        return ok to label
    }

    fun setAlarm(hour: Int, minute: Int, message: String = "Zoya Alarm"): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchIntent(intent)
    }

    fun setTimer(seconds: Int, message: String = "Zoya Timer"): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return launchIntent(intent)
    }

    fun scheduleLocalReminder(reminderId: Long, title: String, triggerAtMillis: Long): Boolean {
        return try {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return false
            val intent = Intent(appContext, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
                putExtra(ReminderReceiver.EXTRA_REMINDER_TITLE, title)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun prepareDialerCall(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+#*]"), "")
        val uri = if (cleanNumber.isNotEmpty()) Uri.parse("tel:$cleanNumber") else Uri.parse("tel:")
        return launchIntent(Intent(Intent.ACTION_DIAL, uri))
    }

    fun prepareSmsMessage(phoneNumber: String, messageBody: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanNumber")).apply {
            putExtra("sms_body", messageBody)
        }
        return launchIntent(intent)
    }

    private fun launchIntent(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
