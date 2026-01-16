package com.example.cyber_cyber

import com.example.cyber_cyber.WifiSecurityDetector.WifiSafetyResult
import com.example.cyber_cyber.WifiSecurityDetector
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodCall
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import android.net.wifi.WifiManager
import android.content.Context.WIFI_SERVICE
import android.os.Handler
import android.os.Looper
import android.app.AlertDialog
import android.os.Build
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.widget.Toast
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.hardware.camera2.CameraManager
import android.os.Vibrator
import android.media.RingtoneManager
import androidx.core.app.ActivityCompat
import android.hardware.camera2.CameraCharacteristics
import android.net.wifi.WifiConfiguration

class MainActivity: FlutterActivity() {
    private val CHANNEL = "cyber_shield/channel"
    private lateinit var methodChannel: MethodChannel
    private lateinit var notificationReceiver: BroadcastReceiver
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var tts: TextToSpeech

    private var wifiManager: WifiManager? = null
    private var connectivityManager: ConnectivityManager? = null

    // Track notifications
    private var lastNotifiedSsid: String? = null
    private var lastWifiCheckTime: Long = 0
    private val WIFI_CHECK_INTERVAL = 30000L

    // Permission handling for Android 14
    private val PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
    }

    private var isTtsInitialized = false
    private var isPanicActive = false
    private var currentUnsafeWifiSsid: String? = null

    // Aggressive WiFi Disconnection Variables
    private var isWifiDisabledFor30Min = false
    private var wifiDisableHandler = Handler(Looper.getMainLooper())
    private var wifiReenableTime: Long = 0
    private var isAggressiveDisconnectActive = false
    private var aggressiveDisconnectHandler = Handler(Looper.getMainLooper())
    private var wifiStateReceiver: BroadcastReceiver? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("APP_CRASH", "Crash in thread: ${thread.name}", throwable)
            // Send crash report to Flutter
            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onAppCrash", mapOf(
                    "thread" to thread.name,
                    "error" to throwable.message
                ))
            }
        }

        super.configureFlutterEngine(flutterEngine)

        try {
            methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            sharedPreferences = getSharedPreferences("cyber_shield", Context.MODE_PRIVATE)

            initializeTTS()
            setupNotificationReceiver()
            setupNetworkMonitoring()
            checkAndRequestPermissions()
            requestOverlayPermission()

            methodChannel.setMethodCallHandler { call, result ->
                handleMethodCall(call, result)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                debugAppStatus()
                if (hasRequiredPermissions()) {
                    forceWifiScan()
                    checkCurrentWifiSafety()
                }
            }, 3000)

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in configureFlutterEngine: ${e.message}")
        }
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "isNotificationAccessEnabled" -> result.success(isNotificationAccessEnabled())
            "openNotificationSettings" -> {
                openNotificationSettings()
                result.success(null)
            }
            "startMonitoring" -> {
                startMonitoringService()
                result.success(null)
            }
            "stopMonitoring" -> {
                stopMonitoringService()
                result.success(null)
            }
            "checkEmailForSpam" -> {
                val emailData = call.arguments as? Map<String, Any> ?: emptyMap()
                val sender = emailData["sender"] as? String ?: ""
                val subject = emailData["subject"] as? String ?: ""
                val body = emailData["body"] as? String ?: ""
                val spamResult = checkEmailForSpam(sender, subject, body)
                result.success(spamResult)
            }
            "checkMessageForSpam" -> {
                val messageData = call.arguments as? Map<String, Any> ?: emptyMap()
                val message = messageData["message"] as? String ?: ""
                val spamResult = checkWhatsAppForSpam(message)
                result.success(spamResult)
            }
            "scanWifiNetworks" -> {
                if (hasLocationPermission()) {
                    forceWifiScan()
                    result.success(null)
                } else {
                    result.error("PERMISSION_DENIED", "Location permission required for WiFi scanning", null)
                }
            }
            "getCurrentWifiInfo" -> {
                val wifiInfo = getCurrentWifiInfo()
                result.success(wifiInfo)
            }
            "checkCurrentWifiSafety" -> {
                if (hasLocationPermission()) {
                    val wifiResult = checkCurrentWifiSafety()
                    result.success(mapOf(
                        "isSafe" to wifiResult.isSafe,
                        "ssid" to wifiResult.ssid,
                        "securityType" to wifiResult.securityType,
                        "safetyScore" to wifiResult.safetyScore,
                        "issues" to wifiResult.issues,
                        "message" to wifiResult.message
                    ))
                } else {
                    result.error("PERMISSION_DENIED", "Location permission required", null)
                }
            }
            "getBlockedItems" -> {
                val blockedEmails = sharedPreferences.getStringSet("blocked_emails", setOf())?.toList() ?: listOf()
                val blockedPackages = sharedPreferences.getStringSet("blocked_packages", setOf())?.toList() ?: listOf()
                val blockedWifis = sharedPreferences.getStringSet("blocked_wifis", setOf())?.toList() ?: listOf()
                result.success(mapOf(
                    "emails" to blockedEmails,
                    "packages" to blockedPackages,
                    "wifis" to blockedWifis
                ))
            }
            "unblockEmail" -> {
                val email = call.arguments as? String ?: ""
                unblockEmail(email)
                result.success(null)
            }
            "unblockPackage" -> {
                val packageName = call.arguments as? String ?: ""
                unblockPackage(packageName)
                result.success(null)
            }
            "unblockWifi" -> {
                val ssid = call.arguments as? String ?: ""
                unblockWifi(ssid)
                result.success(null)
            }
            "blockWifi" -> {
                val ssid = call.arguments as? String ?: ""
                blockWifiNetwork(ssid)
                result.success(null)
            }
            "testNotification" -> {
                testNotificationReception()
                result.success(null)
            }
            "testSpamDetection" -> {
                testSpamDetection()
                result.success(null)
            }
            "testWifiDetection" -> {
                if (hasLocationPermission()) {
                    testWifiDetection()
                    result.success(null)
                } else {
                    result.error("PERMISSION_DENIED", "Location permission required", null)
                }
            }
            "speakWarning" -> {
                val message = call.arguments as? String ?: "Security alert detected"
                speakWarning(message)
                result.success(null)
            }
            "speakEmailAlert" -> {
                val emailData = call.arguments as? Map<String, Any> ?: emptyMap()
                val sender = emailData["sender"] as? String ?: ""
                val subject = emailData["subject"] as? String ?: ""
                speakEmailAlert(sender, subject)
                result.success(null)
            }
            "stopPanicMode" -> {
                stopPanicMode()
                result.success(null)
            }
            "checkPermissions" -> {
                result.success(hasRequiredPermissions())
            }
            "requestPermissions" -> {
                checkAndRequestPermissions()
                result.success(null)
            }
            // New WiFi Disconnection Methods
            "enableWifiManually" -> {
                enableWifiManually()
                result.success(null)
            }
            "getWifiDisableStatus" -> {
                result.success(mapOf(
                    "isWifiDisabled" to isWifiDisabledFor30Min,
                    "reenableTime" to wifiReenableTime,
                    "timeRemaining" to if (isWifiDisabledFor30Min) (wifiReenableTime - System.currentTimeMillis()) else 0
                ))
            }
            "stopAggressiveDisconnect" -> {
                stopAggressiveDisconnect()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun checkCurrentWifiSafety(): WifiSafetyResult {
        return try {
            if (!hasLocationPermission()) {
                Log.w("WifiSafety", "Location permission not granted")
                return WifiSafetyResult(
                    isSafe = true,
                    message = "Permission required",
                    ssid = "Unknown",
                    securityType = "Unknown",
                    safetyScore = 100,
                    issues = emptyList()
                )
            }

            val wifiDetector = WifiSecurityDetector(this)
            val wifiResult = wifiDetector.checkCurrentWifiSafety()

            Log.d("WifiSafety", "WiFi: ${wifiResult.ssid} | Safe: ${wifiResult.isSafe} | Score: ${wifiResult.safetyScore}")

            if (!wifiResult.isSafe && shouldShowWifiNotification(wifiResult.ssid)) {
                Log.e("DANGER", "UNSAFE WIFI DETECTED: ${wifiResult.ssid}")
                currentUnsafeWifiSsid = wifiResult.ssid

                // Show alert for unsafe networks
                showUnsafeWifiAlert(wifiResult)

                // Trigger panic mode for unsafe networks
                triggerFullPanicMode(wifiResult)

                // Start aggressive disconnection immediately
                startAggressiveDisconnection(wifiResult.ssid)

                lastNotifiedSsid = wifiResult.ssid
                lastWifiCheckTime = System.currentTimeMillis()
            }

            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onWifiSafetyChecked", mapOf(
                    "isSafe" to wifiResult.isSafe,
                    "ssid" to wifiResult.ssid,
                    "securityType" to wifiResult.securityType,
                    "safetyScore" to wifiResult.safetyScore,
                    "issues" to wifiResult.issues
                ))
            }
            wifiResult
        } catch (e: Exception) {
            Log.e("WifiSafety", "Error: ${e.message}")
            WifiSafetyResult(
                isSafe = true,
                message = "Error",
                ssid = "Unknown",
                securityType = "Unknown",
                safetyScore = 100,
                issues = emptyList()
            )
        }
    }

    private fun showUnsafeWifiAlert(wifiResult: WifiSafetyResult) {
        Handler(Looper.getMainLooper()).post {
            try {
                val isBlocked = isWifiBlocked(wifiResult.ssid)
                val message = if (isBlocked) {
                    "Network: ${wifiResult.ssid}\n" +
                            "Security: ${wifiResult.securityType}\n" +
                            "Safety Score: ${wifiResult.safetyScore}/100\n\n" +
                            "⚠️ This WiFi network is BLOCKED but you're still connected!\n" +
                            "We are forcing disconnection for 40 SECONDS to prevent reconnection.\n\n" +
                            "Issues detected:\n" +
                            wifiResult.issues.joinToString("\n• ", "• ")
                } else {
                    "Network: ${wifiResult.ssid}\n" +
                            "Security: ${wifiResult.securityType}\n" +
                            "Safety Score: ${wifiResult.safetyScore}/100\n\n" +
                            "This WiFi network has been identified as potentially dangerous.\n" +
                            "We are forcing disconnection for 40 SECONDS to prevent reconnection.\n\n" +
                            "Issues detected:\n" +
                            wifiResult.issues.joinToString("\n• ", "• ")
                }

                val buttonText = if (isBlocked) "RE-BLOCK & DISCONNECT" else "BLOCK & DISCONNECT"

                val alertDialog = AlertDialog.Builder(this)
                    .setTitle("🚨 UNSAFE WIFI DETECTED!")
                    .setMessage(message)
                    .setPositiveButton(buttonText) { dialog, _ ->
                        // Auto-block and start aggressive disconnection when button is clicked
                        blockWifiNetwork(wifiResult.ssid)
                        dialog.dismiss()
                    }
                    .setNegativeButton("IGNORE") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .create()

                alertDialog.show()
                Log.d("WifiAlert", "📢 Unsafe WiFi alert shown for: ${wifiResult.ssid} (Blocked: $isBlocked)")
            } catch (e: Exception) {
                Log.e("WifiAlert", "Failed to show unsafe WiFi alert: ${e.message}")
            }
        }
    }

    // ========== AGGRESSIVE WIFI DISCONNECTION METHODS (40 SECONDS) ==========

    private fun startAggressiveDisconnection(ssid: String) {
        if (isAggressiveDisconnectActive) return

        isAggressiveDisconnectActive = true
        Log.d("AggressiveDisconnect", "🚨 Starting 40-second aggressive disconnection for: $ssid")

        val aggressiveRunnable = object : Runnable {
            override fun run() {
                if (!isAggressiveDisconnectActive) return

                try {
                    // Method 1: Force disconnect
                    wifiManager?.disconnect()
                    Log.d("AggressiveDisconnect", "🔌 Forced disconnect attempt")

                    // Method 2: Remove network configurations
                    removeAllNetworkConfigurations(ssid)

                    // Method 3: Check if still connected and take more aggressive action
                    val currentWifi = getCurrentWifiInfo()
                    if (currentWifi["ssid"] == ssid) {
                        Log.e("AggressiveDisconnect", "🚨 STILL CONNECTED to $ssid! Taking extreme measures...")

                        // Extreme measure: Toggle WiFi off/on
                        wifiManager?.isWifiEnabled = false
                        Handler(Looper.getMainLooper()).postDelayed({
                            wifiManager?.isWifiEnabled = true
                        }, 3000)
                    }

                    // Continue aggressive disconnection for 40 seconds
                    aggressiveDisconnectHandler.postDelayed(this, 2000) // Check every 2 seconds

                } catch (e: Exception) {
                    Log.e("AggressiveDisconnect", "Error in aggressive disconnection: ${e.message}")
                }
            }
        }

        // Start aggressive disconnection
        aggressiveDisconnectHandler.post(aggressiveRunnable)

        // Stop aggressive disconnection after 40 seconds
        aggressiveDisconnectHandler.postDelayed({
            stopAggressiveDisconnect()
            Log.d("AggressiveDisconnect", "✅ 40-second aggressive disconnection completed for: $ssid")
            Toast.makeText(this@MainActivity, "40-second WiFi protection completed for: $ssid", Toast.LENGTH_LONG).show()
        }, 80000) // 40 seconds
    }

    private fun stopAggressiveDisconnect() {
        isAggressiveDisconnectActive = false
        aggressiveDisconnectHandler.removeCallbacksAndMessages(null)
        Log.d("AggressiveDisconnect", "🛑 Stopped aggressive disconnection")
    }

    private fun removeAllNetworkConfigurations(ssid: String) {
        try {
            val configurations = wifiManager?.configuredNetworks
            var removedCount = 0

            configurations?.forEach { config ->
                val configSsid = config.SSID.removeSurrounding("\"")
                if (configSsid == ssid) {
                    // Remove network
                    val success = wifiManager?.removeNetwork(config.networkId) ?: false
                    if (success) {
                        removedCount++
                        Log.d("NetworkRemove", "✅ Removed network: $ssid (ID: ${config.networkId})")
                    } else {
                        Log.e("NetworkRemove", "❌ Failed to remove network: $ssid")
                    }

                    // Disable network
                    wifiManager?.disableNetwork(config.networkId)
                }
            }

            // Save changes
            wifiManager?.saveConfiguration()

            if (removedCount > 0) {
                Log.d("NetworkRemove", "🎯 Removed $removedCount network configurations for: $ssid")
            }
        } catch (e: Exception) {
            Log.e("NetworkRemove", "Error removing network configurations: ${e.message}")
        }
    }

    private fun blockWifiNetwork(ssid: String) {
        try {
            if (ssid.isBlank() || ssid == "Unknown") {
                Toast.makeText(this, "Cannot block unknown WiFi network", Toast.LENGTH_SHORT).show()
                return
            }

            // Add to blocked list in SharedPreferences
            val blockedWifis = sharedPreferences.getStringSet("blocked_wifis", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            blockedWifis.add(ssid)
            sharedPreferences.edit().putStringSet("blocked_wifis", blockedWifis).apply()

            // Start aggressive disconnection immediately
            startAggressiveDisconnection(ssid)

            // Remove from saved networks to forget password permanently and prevent auto-reconnect
            removeAllNetworkConfigurations(ssid)

            Log.d("WifiBlock", "✅ Successfully blocked WiFi: $ssid")
            Toast.makeText(this, "WiFi '$ssid' has been blocked - 40-second protection activated!", Toast.LENGTH_LONG).show()

            // Notify Flutter
            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onWifiBlocked", mapOf(
                    "ssid" to ssid
                ))
            }

        } catch (e: Exception) {
            Log.e("WifiBlock", "Error blocking WiFi: ${e.message}")
            Toast.makeText(this, "Error blocking WiFi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disableWifiFor30Minutes() {
        try {
            if (isWifiDisabledFor30Min) {
                Log.d("WifiDisable", "⚠️ WiFi already disabled for 30 minutes")
                return
            }

            isWifiDisabledFor30Min = true
            wifiReenableTime = System.currentTimeMillis() + (30 * 60 * 1000) // 30 minutes in milliseconds

            // Turn off WiFi immediately
            wifiManager?.isWifiEnabled = false
            Log.d("WifiDisable", "📡 WiFi DISABLED for 30 MINUTES")

            // Show persistent notification
            show30MinuteDisableNotification()

            // Notify Flutter
            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onWifiDisabled", mapOf(
                    "duration" to 30,
                    "reenableTime" to wifiReenableTime
                ))
            }

            // Start monitoring to prevent manual re-enable
            startWifiEnableMonitor()

            // Schedule re-enable after 30 minutes
            wifiDisableHandler.postDelayed({
                enableWifiAfter30Minutes()
            }, 30 * 60 * 1000) // 30 minutes

            Toast.makeText(this,
                "🔒 WiFi has been DISABLED for 30 MINUTES for your safety\n\n" +
                        "This prevents automatic reconnection to dangerous networks\n" +
                        "WiFi will auto-enable at ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(wifiReenableTime))}",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e("WifiDisable", "Error disabling WiFi: ${e.message}")
            Toast.makeText(this, "Error disabling WiFi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableWifiAfter30Minutes() {
        try {
            isWifiDisabledFor30Min = false
            wifiManager?.isWifiEnabled = true
            Log.d("WifiDisable", "📡 WiFi ENABLED after 30 minutes")

            // Stop monitoring
            stopWifiEnableMonitor()

            // Notify Flutter
            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onWifiEnabled", mapOf(
                    "message" to "WiFi has been re-enabled after 30 minutes"
                ))
            }

            Toast.makeText(this,
                "✅ WiFi has been ENABLED after 30 minutes\nYou can now connect to safe networks",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e("WifiDisable", "Error enabling WiFi after 30 minutes: ${e.message}")
        }
    }

    private fun enableWifiManually() {
        try {
            isWifiDisabledFor30Min = false
            wifiDisableHandler.removeCallbacksAndMessages(null)
            stopAggressiveDisconnect()
            wifiManager?.isWifiEnabled = true
            Log.d("WifiDisable", "📡 WiFi MANUALLY ENABLED by user")

            // Stop monitoring
            stopWifiEnableMonitor()

            // Notify Flutter
            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onWifiEnabled", mapOf(
                    "message" to "WiFi has been manually enabled"
                ))
            }

            Toast.makeText(this,
                "✅ WiFi has been MANUALLY ENABLED\nUse with caution and avoid unsafe networks",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e("WifiDisable", "Error manually enabling WiFi: ${e.message}")
        }
    }

    private fun startWifiEnableMonitor() {
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    if (wifiState == WifiManager.WIFI_STATE_ENABLED && isWifiDisabledFor30Min) {
                        Log.e("WifiMonitor", "🚨 User tried to enable WiFi during 30-minute disable period!")
                        // Immediately disable WiFi again
                        Handler(Looper.getMainLooper()).postDelayed({
                            wifiManager?.isWifiEnabled = false
                            Toast.makeText(this@MainActivity,
                                "⚠️ WiFi is disabled for safety for ${getRemainingTime()} more minutes",
                                Toast.LENGTH_LONG
                            ).show()
                        }, 1000)
                    }
                }
            }
        }

        val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(wifiStateReceiver, filter)
    }

    private fun stopWifiEnableMonitor() {
        try {
            wifiStateReceiver?.let {
                unregisterReceiver(it)
                wifiStateReceiver = null
            }
        } catch (e: Exception) {
            Log.e("WifiMonitor", "Error unregistering WiFi state receiver: ${e.message}")
        }
    }

    private fun getRemainingTime(): String {
        val remainingMs = wifiReenableTime - System.currentTimeMillis()
        val remainingMinutes = (remainingMs / (60 * 1000)).toInt()
        return if (remainingMinutes > 0) remainingMinutes.toString() else "0"
    }

    private fun show30MinuteDisableNotification() {
        Handler(Looper.getMainLooper()).post {
            try {
                AlertDialog.Builder(this)
                    .setTitle("🔒 WiFi Disabled for Safety")
                    .setMessage("WiFi has been disabled for 30 minutes to protect you from unsafe networks.\n\n" +
                            "Remaining time: ${getRemainingTime()} minutes\n\n" +
                            "This prevents automatic reconnection to dangerous networks. " +
                            "You can manually enable WiFi in the app if needed.")
                    .setPositiveButton("UNDERSTOOD") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .create()
                    .show()
            } catch (e: Exception) {
                Log.e("DisableNotification", "Failed to show 30-minute disable notification: ${e.message}")
            }
        }
    }

    private fun unblockWifi(ssid: String) {
        val blockedWifis = sharedPreferences.getStringSet("blocked_wifis", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        blockedWifis.remove(ssid)
        sharedPreferences.edit().putStringSet("blocked_wifis", blockedWifis).apply()

        Handler(Looper.getMainLooper()).post {
            methodChannel.invokeMethod("onWifiUnblocked", mapOf(
                "ssid" to ssid
            ))
        }
    }

    private fun isWifiBlocked(ssid: String): Boolean {
        val blockedWifis = sharedPreferences.getStringSet("blocked_wifis", setOf()) ?: setOf()
        return blockedWifis.contains(ssid)
    }

    private fun forceWifiScan() {
        try {
            if (!hasLocationPermission()) {
                Log.w("WifiScan", "Location permission required for WiFi scanning")
                return
            }

            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, use startScan() with proper permissions
                val success = wifiManager.startScan()
                Log.d("WifiScan", "WiFi scan initiated: $success")

                if (success) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkCurrentWifiSafety()
                    }, 3000)
                }
            } else {
                @Suppress("DEPRECATION")
                val success = wifiManager.startScan()
                Log.d("WifiScan", "WiFi scan initiated: $success")

                if (success) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkCurrentWifiSafety()
                    }, 3000)
                }
            }
        } catch (e: Exception) {
            Log.e("WifiScan", "Error forcing WiFi scan: ${e.message}")
        }
    }

    private fun debugAppStatus() {
        Log.d("AppDebug", "=== APP DEBUG INFO ===")
        Log.d("AppDebug", "📱 App started successfully")
        Log.d("AppDebug", "🔔 Notification Access Enabled: ${isNotificationAccessEnabled()}")
        Log.d("AppDebug", "📡 WiFi Enabled: ${wifiManager?.isWifiEnabled ?: false}")
        Log.d("AppDebug", "📍 Location Permission: ${hasLocationPermission()}")
        Log.d("AppDebug", "🔧 Android Version: ${Build.VERSION.SDK_INT}")
        Log.d("AppDebug", "⏰ WiFi 30-min Disable Active: $isWifiDisabledFor30Min")
        Log.d("AppDebug", "🔌 Aggressive Disconnect Active: $isAggressiveDisconnectActive")

        val wifiResult = checkCurrentWifiSafety()
        Log.d("AppDebug", "📶 Current WiFi: ${wifiResult.ssid}, Safe: ${wifiResult.isSafe}")
        Log.d("AppDebug", "=== DEBUG COMPLETE ===")
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d("Permissions", "Requesting missing permissions: $missingPermissions")
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d("Permissions", "✅ All required permissions granted")
            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onPermissionsGranted", null)
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.d("Permissions", "✅ All permissions granted")
                Handler(Looper.getMainLooper()).post {
                    methodChannel.invokeMethod("onPermissionsGranted", null)
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    forceWifiScan()
                    checkCurrentWifiSafety()
                }, 1000)
            } else {
                Log.d("Permissions", "❌ Some permissions denied")
                Handler(Looper.getMainLooper()).post {
                    methodChannel.invokeMethod("onPermissionsDenied", null)
                }
                Toast.makeText(this, "Some permissions are required for full functionality", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("OverlayPermission", "Cannot request overlay permission: ${e.message}")
            }
        }
    }

    private fun initializeTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Set to English US for clear pronunciation
                var result = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts.setLanguage(Locale.ENGLISH)
                }

                // Set male-like voice parameters
                tts.setPitch(0.85f) // Lower pitch for male voice (0.5-2.0 range, lower = more masculine)
                tts.setSpeechRate(0.48f) // Normal speaking rate

                isTtsInitialized = true
                Log.d("TTS", "✅ English male voice Text-to-Speech initialized successfully")
            } else {
                Log.e("TTS", "❌ Text-to-Speech initialization failed")
            }
        }
    }

    private fun speakWarning(message: String) {
        if (isTtsInitialized) {
            // Set male voice parameters
            tts.setPitch(0.85f)
            tts.setSpeechRate(0.48f)
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d("TTS", "🔊 Speaking warning: $message")
        }
    }

    private fun speakEmailAlert(sender: String, subject: String) {
        if (isTtsInitialized) {
            val alertMessage = "Security Alert! Dangerous email detected. Sender: ${sender.take(20)}. Subject: ${subject.take(30)}"

            // Set male voice parameters
            tts.setPitch(0.85f)
            tts.setSpeechRate(0.48f)
            tts.speak(alertMessage, TextToSpeech.QUEUE_FLUSH, null, null)

            Log.d("TTS", "🔊 Speaking email alert: $alertMessage")
        }
    }

    private fun setupNotificationReceiver() {
        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "cyber_shield.NOTIFICATION_RECEIVED") {
                    val isEmail = intent.getBooleanExtra("isEmail", false)
                    val packageName = intent.getStringExtra("packageName") ?: ""
                    val title = intent.getStringExtra("title") ?: ""
                    val message = intent.getStringExtra("message") ?: ""

                    Log.d("NotificationReceiver", "📨 Received broadcast - Email: $isEmail, Package: $packageName, Title: '$title'")

                    if (isEmail) {
                        val sender = intent.getStringExtra("sender") ?: "Unknown Sender"
                        Log.d("NotificationReceiver", "📧 Processing email from: $sender")
                        handleEmailNotification(sender, title, message, packageName)
                    } else {
                        Log.d("NotificationReceiver", "💚 Processing WhatsApp message")
                        handleWhatsAppNotification(title, message, packageName)
                    }
                }
            }
        }

        val filter = IntentFilter("cyber_shield.NOTIFICATION_RECEIVED")
        registerReceiver(notificationReceiver, filter)
    }

    private fun handleEmailNotification(sender: String, subject: String, message: String, packageName: String) {
        if (isBlockedEmail(sender)) {
            Log.d("EmailBlock", "Email from $sender is blocked")
            return
        }

        Log.d("EmailProcessing", "🔍 Checking email spam - Sender: '$sender', Subject: '$subject', Message: '${message.take(50)}...'")

        val spamResult = checkEmailForSpam(sender, subject, message)
        Log.d("SpamCheck", "Email spam result: $spamResult")

        if (spamResult["isSpam"] == true) {
            Log.e("SPAM_DETECTED", "🚨 SPAM EMAIL DETECTED: '$subject' from '$sender'")

            // Speak alert with male voice
            speakEmailAlert(sender, subject)

            // Show local alert
            showLocalSpamAlert("EMAIL SPAM DETECTED", "From: $sender\nSubject: $subject\n\nThis email contains suspicious content!")

            // Send to Flutter
            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onEmailSpamDetected", mapOf(
                    "sender" to sender,
                    "subject" to subject,
                    "body" to message,
                    "packageName" to packageName,
                    "spamScore" to spamResult["spamScore"],
                    "detectedKeywords" to spamResult["detectedKeywords"],
                    "suspiciousSender" to spamResult["suspiciousSender"]
                ))
            }
        } else {
            Log.d("SpamCheck", "✅ Email is not spam: '$subject'")
        }
    }

    private fun handleWhatsAppNotification(title: String, message: String, packageName: String) {
        if (isBlockedPackage(packageName)) return

        Log.d("WhatsAppProcessing", "🔍 Checking WhatsApp spam - Title: '$title', Message: '$message'")

        val spamResult = checkWhatsAppForSpam(message)
        Log.d("SpamCheck", "WhatsApp spam result: $spamResult")

        if (spamResult["isSpam"] == true) {
            Log.e("SPAM_DETECTED", "🚨 WHATSAPP SPAM DETECTED: '$message'")

            // Speak alert with male voice
            speakWarning("Security Alert! Dangerous WhatsApp message detected")

            // Show local alert
            showLocalSpamAlert("WHATSAPP SPAM DETECTED", "Message: $message")

            Handler(Looper.getMainLooper()).post {
                methodChannel.invokeMethod("onMessageSpamDetected", mapOf(
                    "title" to title,
                    "message" to message,
                    "packageName" to packageName,
                    "spamScore" to spamResult["spamScore"],
                    "detectedKeywords" to spamResult["detectedKeywords"]
                ))
            }
        } else {
            Log.d("SpamCheck", "✅ WhatsApp message is not spam")
        }
    }

    private fun showLocalSpamAlert(title: String, message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                AlertDialog.Builder(this)
                    .setTitle("🚨 $title")
                    .setMessage(message)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setCancelable(false)
                    .create()
                    .show()
                Log.d("Alert", "📢 Spam alert shown: $title")
            } catch (e: Exception) {
                Log.e("Alert", "Failed to show alert: ${e.message}")
            }
        }
    }

    private fun setupNetworkMonitoring() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("NetworkMonitor", "WiFi network available")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (hasLocationPermission()) {
                            forceWifiScan()
                            checkCurrentWifiSafety()
                        }
                    }, 2000)
                }

                override fun onLost(network: Network) {
                    Log.d("NetworkMonitor", "WiFi network lost")
                    lastNotifiedSsid = null
                }
            }

            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Error setting up network monitoring: ${e.message}")
        }
    }

    private fun shouldShowWifiNotification(currentSsid: String): Boolean {
        val currentTime = System.currentTimeMillis()
        return lastNotifiedSsid != currentSsid || (currentTime - lastWifiCheckTime) > WIFI_CHECK_INTERVAL
    }

    private fun triggerFullPanicMode(result: WifiSafetyResult) {
        if (isPanicActive) return
        isPanicActive = true

        try {
            speakDangerWifiAlert(result.ssid)
            safeStartFlashlightSOS()
            safeStartVibration()
            safePlayLoudAlarm()

            // Auto-stop panic mode after 8 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                stopPanicMode()
            }, 8000)
        } catch (e: Exception) {
            Log.e("PanicMode", "Error in panic mode: ${e.message}")
            isPanicActive = false
        }
    }

    private fun speakDangerWifiAlert(ssid: String) {
        if (!isTtsInitialized) return

        val englishAlert = "Security Alert! Dangerous Wi-Fi network detected! Disconnect immediately! Network: $ssid"

        // Set male voice parameters
        tts.setPitch(0.85f)
        tts.setSpeechRate(0.48f)
        tts.speak(englishAlert, TextToSpeech.QUEUE_FLUSH, null, "tts_english")

        Log.d("TTS", "🔊 Speaking WiFi danger alert: $englishAlert")
    }

    private fun safeStartFlashlightSOS() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.w("Flashlight", "Camera permission not granted")
                return
            }

            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null || cameraManager.cameraIdList.isEmpty()) {
                Log.d("Flashlight", "Camera not available")
                return
            }

            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } else {
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                Log.d("Flashlight", "No suitable camera found")
                return
            }

            val handler = Handler(Looper.getMainLooper())
            var flashCount = 0
            val maxFlashes = 8

            val runnable = object : Runnable {
                override fun run() {
                    if (!isPanicActive || flashCount >= maxFlashes) {
                        try {
                            cameraManager.setTorchMode(cameraId, false)
                            Log.d("Flashlight", "🔦 Flashlight stopped")
                        } catch (e: Exception) {
                            Log.e("Flashlight", "Error turning off flashlight: ${e.message}")
                        }
                        return
                    }

                    val isFlashOn = flashCount % 2 == 0
                    try {
                        cameraManager.setTorchMode(cameraId, isFlashOn)
                        Log.d("Flashlight", if (isFlashOn) "🔦 Flash ON" else "🔦 Flash OFF")
                    } catch (e: Exception) {
                        Log.e("Flashlight", "Error controlling flashlight: ${e.message}")
                        return
                    }

                    flashCount++
                    handler.postDelayed(this, 500)
                }
            }

            handler.post(runnable)
            Log.d("Flashlight", "🔦 Starting 4-second flashlight SOS")

        } catch (e: Exception) {
            Log.e("Flashlight", "Flashlight error: ${e.message}")
        }
    }

    private fun safeStartVibration() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator == null || !vibrator.hasVibrator()) {
                Log.d("Vibration", "Vibrator not available")
                return
            }

            // 4-second vibration pattern
            val pattern = longArrayOf(0, 500, 500, 500, 500, 500, 500, 500)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }

            Log.d("Vibration", "📳 Starting 4-second vibration")

            // Stop vibration after 4 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                vibrator.cancel()
                Log.d("Vibration", "📳 Vibration stopped after 4 seconds")
            }, 4000)

        } catch (e: Exception) {
            Log.e("Vibration", "Vibration error: ${e.message}")
        }
    }

    private fun safePlayLoudAlarm() {
        try {
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val ringtone = RingtoneManager.getRingtone(this, alarmUri)
            ringtone?.play()

            // Stop alarm after 5 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                ringtone?.stop()
            }, 5000)
        } catch (e: Exception) {
            Log.e("Alarm", "Alarm sound failed: ${e.message}")
        }
    }

    fun stopPanicMode() {
        isPanicActive = false
        try {
            // Stop flashlight
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager?.let {
                try {
                    for (cameraId in it.cameraIdList) {
                        it.setTorchMode(cameraId, false)
                    }
                } catch (e: Exception) {
                    Log.e("Flashlight", "Error turning off flashlight: ${e.message}")
                }
            }

            // Stop vibration
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()

            Log.d("PanicMode", "🛑 Panic mode stopped")
        } catch (e: Exception) {
            Log.e("PanicMode", "Error stopping panic mode: ${e.message}")
        }
    }

    private fun getCurrentWifiInfo(): Map<String, Any> {
        return try {
            if (!hasLocationPermission()) {
                return mapOf(
                    "ssid" to "Permission required",
                    "bssid" to "Unknown",
                    "signalStrength" to 0,
                    "isConnected" to false
                )
            }

            val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
            if (wifiInfo != null && wifiInfo.ssid != null && wifiInfo.ssid != "<unknown ssid>" && wifiInfo.ssid != "0x") {
                mapOf(
                    "ssid" to wifiInfo.ssid.replace("\"", ""),
                    "bssid" to (wifiInfo.bssid ?: "Unknown"),
                    "signalStrength" to wifiInfo.rssi,
                    "isConnected" to true
                )
            } else {
                mapOf(
                    "ssid" to "Not connected",
                    "bssid" to "Unknown",
                    "signalStrength" to 0,
                    "isConnected" to false
                )
            }
        } catch (e: Exception) {
            mapOf(
                "ssid" to "Error",
                "bssid" to "Unknown",
                "signalStrength" to 0,
                "isConnected" to false
            )
        }
    }

    private fun isBlockedEmail(sender: String): Boolean {
        val blockedEmails = sharedPreferences.getStringSet("blocked_emails", setOf()) ?: setOf()
        return blockedEmails.contains(sender.lowercase())
    }

    private fun isBlockedPackage(packageName: String): Boolean {
        val blockedPackages = sharedPreferences.getStringSet("blocked_packages", setOf()) ?: setOf()
        return blockedPackages.contains(packageName)
    }

    private fun unblockEmail(email: String) {
        val blockedEmails = sharedPreferences.getStringSet("blocked_emails", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        blockedEmails.remove(email.lowercase())
        sharedPreferences.edit().putStringSet("blocked_emails", blockedEmails).apply()
    }

    private fun unblockPackage(packageName: String) {
        val blockedPackages = sharedPreferences.getStringSet("blocked_packages", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        blockedPackages.remove(packageName)
        sharedPreferences.edit().putStringSet("blocked_packages", blockedPackages).apply()
    }

    private fun testNotificationReception() {
        // Test notification reception
        Handler(Looper.getMainLooper()).post {
            methodChannel.invokeMethod("onTestNotification", mapOf(
                "status" to "success",
                "message" to "Notification system working"
            ))
        }
    }

    private fun testSpamDetection() {
        // Test spam detection with safe email
        handleEmailNotification(
            sender = "safe@sender.com",
            subject = "Normal Email",
            message = "This is a normal email without any spam content.",
            packageName = "com.google.android.gm"
        )
    }

    private fun testWifiDetection() {
        Handler(Looper.getMainLooper()).postDelayed({
            checkCurrentWifiSafety()
        }, 1000)
    }

    private fun isNotificationAccessEnabled(): Boolean {
        return try {
            val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            val packageName = packageName
            enabledListeners != null && enabledListeners.contains(packageName)
        } catch (e: Exception) {
            false
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open notification settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startMonitoringService() {
        try {
            val intent = Intent(this, CyberShieldForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d("Monitoring", "🟢 Monitoring service started")
        } catch (e: Exception) {
            Log.e("Monitoring", "Error starting monitoring service: ${e.message}")
        }
    }

    private fun stopMonitoringService() {
        try {
            val intent = Intent(this, CyberShieldForegroundService::class.java)
            stopService(intent)
            Log.d("Monitoring", "🔴 Monitoring service stopped")
        } catch (e: Exception) {
            Log.e("Monitoring", "Error stopping monitoring service: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
        try {
            stopWifiEnableMonitor()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping WiFi monitor: ${e.message}")
        }
        try {
            if (::tts.isInitialized) {
                tts.stop()
                tts.shutdown()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error shutting down TTS: ${e.message}")
        }
        stopPanicMode()
        stopAggressiveDisconnect()
        wifiDisableHandler.removeCallbacksAndMessages(null)
        aggressiveDisconnectHandler.removeCallbacksAndMessages(null)
    }

    private fun checkEmailForSpam(sender: String, subject: String, body: String): Map<String, Any> {
        val lowerSubject = subject.lowercase()
        val lowerBody = body.lowercase()
        val lowerSender = sender.lowercase()

        Log.d("SpamCheck", "🔍 Checking email - Sender: '$sender', Subject: '$subject', Body: '${body.take(50)}...'")

        val spamKeywords = listOf(
            "nigerian prince", "inheritance", "lottery winner", "urgent transfer",
            "bank account", "security alert", "password reset", "account verification",
            "free money", "investment opportunity", "bitcoin investment", "crypto offer",
            "million dollars", "fortune", "prize", "winner", "congratulations", "selected",
            "claim your", "click here", "verify your", "urgent action", "urgent", "action required",
            "immediate action", "your account", "suspended", "verify account", "security update",
            "password expired", "login attempt", "unusual activity"
        )

        val foundKeywords = spamKeywords.filter { keyword ->
            lowerSubject.contains(keyword) || lowerBody.contains(keyword)
        }

        val suspiciousSender = isSuspiciousEmailSender(lowerSender)
        var spamScore = foundKeywords.size * 2

        // Score adjustments
        if (suspiciousSender) spamScore += 3
        if (subject.contains("URGENT", ignoreCase = true)) spamScore += 3
        if (subject.contains("FREE", ignoreCase = true)) spamScore += 3
        if (subject.contains("WINNER", ignoreCase = true)) spamScore += 3
        if (subject.contains("PRIZE", ignoreCase = true)) spamScore += 3
        if (subject.contains("ACTION", ignoreCase = true)) spamScore += 2
        if (subject.contains("VERIFY", ignoreCase = true)) spamScore += 2
        if (subject.contains("SECURITY", ignoreCase = true)) spamScore += 2
        if (body.contains("click here", ignoreCase = true)) spamScore += 3
        if (body.contains("http://") || body.contains("https://")) spamScore += 3
        if (body.contains("bank", ignoreCase = true)) spamScore += 2
        if (body.contains("money", ignoreCase = true)) spamScore += 2
        if (body.contains("$") || body.contains("dollar")) spamScore += 2
        if (body.contains("password", ignoreCase = true)) spamScore += 2
        if (body.contains("account", ignoreCase = true)) spamScore += 2

        val isSpam = spamScore >= 2

        Log.d("SpamCheck", "📊 Spam Score: $spamScore, Keywords: $foundKeywords, IsSpam: $isSpam")

        return mapOf(
            "isSpam" to isSpam,
            "spamScore" to spamScore,
            "detectedKeywords" to foundKeywords,
            "suspiciousSender" to suspiciousSender
        )
    }

    private fun checkWhatsAppForSpam(message: String): Map<String, Any> {
        val lowerMessage = message.lowercase()
        val spamKeywords = listOf(
            "free", "win", "winner", "prize", "congratulations", "selected", "chosen",
            "lottery", "reward", "claim", "offer", "limited", "exclusive", "discount",
            "cash", "money", "gift", "bonus"
        )

        val foundKeywords = spamKeywords.filter { lowerMessage.contains(it) }
        val hasLinks = containsLinks(lowerMessage)
        var spamScore = foundKeywords.size
        if (hasLinks) spamScore += 3
        if (message.contains("FREE", ignoreCase = true)) spamScore += 2
        if (message.contains("URGENT", ignoreCase = true)) spamScore += 2
        if (message.contains("WINNER", ignoreCase = true)) spamScore += 2

        val isSpam = spamScore >= 2

        return mapOf(
            "isSpam" to isSpam,
            "spamScore" to spamScore,
            "detectedKeywords" to foundKeywords
        )
    }

    private fun isSuspiciousEmailSender(sender: String): Boolean {
        if (sender == "Unknown Sender" || sender.isEmpty() || sender == "me") {
            Log.d("SpamCheck", "❓ Suspicious sender: '$sender'")
            return true
        }

        val suspiciousDomains = listOf(
            "freeoffer", "winning", "prize", "lottery", "money4u", "quickcash",
            "investment", "crypto", "bitcoin", "banking", "security", "verify",
            "nigerian", "prince", "inheritance", "million", "fortune", "reward"
        )

        return try {
            val domain = sender.substringAfter('@').substringBeforeLast('.')
            val isSuspiciousDomain = suspiciousDomains.any { domain.contains(it, ignoreCase = true) }

            Log.d("SpamCheck", "🔍 Sender '$sender' domain: '$domain', suspicious: $isSuspiciousDomain")
            isSuspiciousDomain
        } catch (e: Exception) {
            Log.d("SpamCheck", "❌ Error checking sender '$sender': ${e.message}")
            true
        }
    }

    private fun containsLinks(message: String): Boolean {
        return message.contains("http://") || message.contains("https://") ||
                message.contains("www.") || message.contains(".com") ||
                message.contains(".net") || message.contains(".org")
    }
}