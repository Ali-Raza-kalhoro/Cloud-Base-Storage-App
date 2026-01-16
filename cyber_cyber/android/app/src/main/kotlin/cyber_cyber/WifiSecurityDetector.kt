package com.example.cyber_cyber

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.net.wifi.WifiInfo
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

class WifiSecurityDetector(private val context: Context) {

    companion object {
        private const val TAG = "WifiSecurityDetector"

        // ONLY THESE EXACT NETWORKS WILL BE MARKED AS DANGEROUS
        private val DANGEROUS_EXACT = listOf(
            "GCUH-",
            "Skills Hungers Academy",
            "StormFiber-00EO-5G", // Make sure this matches exactly
            "CS-A",
            "GCUH-Staff",
            "Zaur house",
            "STUDENTS-5G",
        )

        // NETWORKS WITH THESE KEYWORDS GET LOWER SCORES
        private val SUSPICIOUS_KEYWORDS = listOf(
            "free", "public", "open", "guest", "hotspot", "unsecured"
        )
    }

    interface WifiSafetyListener {
        fun onWifiSafetyChanged(result: WifiSafetyResult)
        fun onDangerousWifiDetected(result: WifiSafetyResult)
    }

    private var listener: WifiSafetyListener? = null
    private var currentSsid = "Unknown"
    private var isMonitoring = false

    private val handler = Handler(Looper.getMainLooper())

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler.postDelayed({ checkAndNotify() }, 3000)
        }
    }

    fun startMonitoring(listener: WifiSafetyListener) {
        if (isMonitoring) return
        this.listener = listener

        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(wifiReceiver, filter)
        }

        isMonitoring = true
        Log.d(TAG, "Real-time WiFi monitoring STARTED")
        checkAndNotify()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        try { context.unregisterReceiver(wifiReceiver) } catch (e: Exception) { }
        isMonitoring = false
        listener = null
        Log.d(TAG, "Real-time monitoring STOPPED")
    }

    fun checkNow() = checkAndNotify()

    private fun checkAndNotify() {
        val result = checkCurrentWifiSafety()
        if (result.ssid != currentSsid || !result.isSafe) {
            currentSsid = result.ssid
            listener?.onWifiSafetyChanged(result)
            if (!result.isSafe) {
                listener?.onDangerousWifiDetected(result)
                Log.e(TAG, "DANGEROUS WIFI: ${result.ssid} | Score: ${result.safetyScore}")
            } else {
                Log.d(TAG, "SAFE WIFI: ${result.ssid} | Score: ${result.safetyScore}")
            }
        }
    }

    // FIXED CORE DETECTION LOGIC
    private fun cleanSsid(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "<unknown ssid>" || raw == "0x") return "Unknown"
        return raw.removeSurrounding("\"").trim()
    }

    fun checkCurrentWifiSafety(): WifiSafetyResult {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            return WifiSafetyResult(true, "WiFi Off", "WiFi Disabled", "N/A", 100, emptyList())
        }

        var ssid = "Unknown"
        var security = "UNKNOWN"

        // Get current WiFi info
        val wifiInfo: WifiInfo? = wifiManager.connectionInfo
        if (wifiInfo != null) {
            val rawSsid = wifiInfo.ssid
            val cleaned = cleanSsid(rawSsid)
            if (cleaned != "Unknown") {
                ssid = cleaned
                security = getSecurityFromScanFallback(wifiManager, wifiInfo.bssid)
            }
        }

        // Fallback to scan results
        if (ssid == "Unknown") {
            val scanResult = getCurrentNetworkFromScan(wifiManager)
            if (scanResult != null && !scanResult.SSID.isNullOrBlank()) {
                ssid = cleanSsid(scanResult.SSID)
                security = getSecurityType(scanResult.capabilities)
            }
        }

        if (ssid == "Unknown") {
            return WifiSafetyResult(true, "Detecting WiFi...", "Unknown", "Unknown", 100, emptyList())
        }

        return analyzeSafety(ssid, security)
    }

    private fun getCurrentNetworkFromScan(wifiManager: WifiManager): ScanResult? {
        return try {
            val results = wifiManager.scanResults ?: return null
            val currentBssid = wifiManager.connectionInfo?.bssid
            if (currentBssid != null && currentBssid != "00:00:00:00:00:00") {
                results.find { it.BSSID.equals(currentBssid, ignoreCase = true) }?.let { return it }
            }
            results.maxByOrNull { it.level }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network from scan: ${e.message}")
            null
        }
    }

    private fun getSecurityFromScanFallback(wifiManager: WifiManager, bssid: String?): String {
        if (bssid == null) return "UNKNOWN"
        return try {
            wifiManager.scanResults
                ?.find { it.BSSID.equals(bssid, ignoreCase = true) }
                ?.let { getSecurityType(it.capabilities) } ?: "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun getSecurityType(capabilities: String): String {
        if (capabilities.isBlank()) return "OPEN"
        val c = capabilities.uppercase()
        return when {
            c.contains("WPA3") -> "WPA3"
            c.contains("WPA2") || c.contains("RSN") -> "WPA2"
            c.contains("WPA") -> "WPA"
            c.contains("WEP") -> "WEP"
            else -> "OPEN"
        }
    }

    // FIXED SAFETY ANALYSIS - ONLY MARK SPECIFIC NETWORKS AS DANGEROUS
    private fun analyzeSafety(ssid: String, security: String): WifiSafetyResult {
        var score = 100
        val issues = mutableListOf<String>()
        val lowerSsid = ssid.toLowerCase().trim()

        Log.d(TAG, "Analyzing WiFi: $ssid | Security: $security")

        // 1. CHECK FOR EXACT DANGEROUS NETWORKS (ONLY THESE WILL BE UNSAFE)
        val isExactDangerous = DANGEROUS_EXACT.any { dangerous ->
            dangerous.equals(ssid, ignoreCase = true) ||
                    ssid.startsWith(dangerous, ignoreCase = true)
        }

        if (isExactDangerous) {
            score = 10 // Very low score for dangerous networks
            issues.add("BLOCKED DANGEROUS NETWORK - Disconnect immediately!")
            Log.w(TAG, "EXACT DANGEROUS NETWORK DETECTED: $ssid")
        } else {
            // 2. FOR ALL OTHER NETWORKS, USE NORMAL SCORING
            when (security) {
                "OPEN" -> {
                    score -= 40
                    issues.add("Open network - No password protection")
                }
                "WEP"  -> {
                    score -= 30
                    issues.add("WEP encryption - Very weak security")
                }
                "WPA"  -> {
                    score -= 10
                    issues.add("WPA encryption - Older security standard")
                }
                "WPA2" -> {
                    // WPA2 is good - no penalty
                    issues.add("WPA2 encryption - Good security")
                }
                "WPA3" -> {
                    score += 10 // Bonus for WPA3
                    issues.add("WPA3 encryption - Excellent security")
                }
                "UNKNOWN" -> {
                    score -= 20
                    issues.add("Unknown security type")
                }
            }

            // 3. CHECK FOR SUSPICIOUS KEYWORDS (MINOR PENALTY ONLY)
            val suspiciousCount = SUSPICIOUS_KEYWORDS.count { lowerSsid.contains(it) }
            if (suspiciousCount > 0) {
                score -= suspiciousCount * 5 // Small penalty
                issues.add("Network name contains suspicious keywords")
            }

            // 4. ENSURE SCORE IS WITHIN BOUNDS
            score = score.coerceIn(0, 100)
        }

        // 5. DETERMINE SAFETY - ONLY EXACT DANGEROUS NETWORKS ARE UNSAFE
        val isSafe = !isExactDangerous && score >= 40

        val message = when {
            isExactDangerous -> "🚨 DANGEROUS NETWORK - DISCONNECT NOW!"
            score >= 80 -> "✅ Very Safe WiFi"
            score >= 60 -> "⚠️ Moderately Safe WiFi"
            score >= 40 -> "🔶 Caution Advised"
            else -> "❌ Unsafe WiFi"
        }

        Log.d(TAG, "Safety Result: $ssid | Safe: $isSafe | Score: $score | Issues: ${issues.size}")

        return WifiSafetyResult(
            isSafe = isSafe,
            message = message,
            ssid = ssid,
            securityType = security,
            safetyScore = score,
            issues = issues
        )
    }

    data class WifiSafetyResult(
        val isSafe: Boolean,
        val message: String,
        val ssid: String,
        val securityType: String,
        val safetyScore: Int,
        val issues: List<String> = emptyList()
    )
}