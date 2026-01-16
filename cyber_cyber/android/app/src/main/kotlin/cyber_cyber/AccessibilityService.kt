package com.example.cyber_cyber

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CyberShieldAccessibilityService : AccessibilityService() {
    companion object {
        const val TAG = "CyberShieldAccessibility"
        const val NOTIFICATION_ACTION = "CYBER_SHIELD_NOTIFICATION"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: ""

            // Monitor WhatsApp and other messaging apps
            if (packageName.contains("whatsapp", ignoreCase = true) ||
                packageName.contains("sms", ignoreCase = true) ||
                packageName.contains("messag", ignoreCase = true)) {

                val text = event.text?.joinToString(" ") ?: ""
                Log.d(TAG, "Detected notification from $packageName: $text")

                // Send broadcast using modern approach
                val intent = Intent(NOTIFICATION_ACTION).apply {
                    putExtra("package", packageName)
                    putExtra("text", text)
                    // Set package to ensure it's only received by your app
                    setPackage(applicationContext.packageName)
                }
                applicationContext.sendBroadcast(intent)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Cyber Shield Accessibility service connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.DEFAULT
        }
        this.serviceInfo = info
    }
}