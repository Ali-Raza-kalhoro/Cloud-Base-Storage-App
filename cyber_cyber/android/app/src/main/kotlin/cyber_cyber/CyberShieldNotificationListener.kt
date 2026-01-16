package com.example.cyber_cyber

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

class CyberShieldNotificationListener : NotificationListenerService() {
    companion object {
        const val TAG = "NotificationListener"
        const val ACTION_NOTIFICATION_RECEIVED = "cyber_shield.NOTIFICATION_RECEIVED"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔔 Notification Listener Service Created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "✅ Notification Listener CONNECTED - Ready to receive notifications")

        // Test connection by listing current notifications
        try {
            val activeNotifications = activeNotifications
            Log.d(TAG, "📱 Currently ${activeNotifications?.size ?: 0} active notifications")
            activeNotifications?.forEach { sbn ->
                Log.d(TAG, "📲 Active notification from: ${sbn.packageName} - ${sbn.notification?.extras?.getString("android.title")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking active notifications: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName ?: return
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            val title = extras.getString("android.title", "") ?: ""
            val text = extras.getCharSequence("android.text", "")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText", "")?.toString() ?: ""
            val subText = extras.getCharSequence("android.subText", "")?.toString() ?: ""

            Log.d(TAG, "📨 NEW NOTIFICATION from: $packageName")
            Log.d(TAG, "📝 Title: '$title'")
            Log.d(TAG, "📝 Text: '$text'")
            Log.d(TAG, "📝 Big Text: '$bigText'")
            Log.d(TAG, "📝 Sub Text: '$subText'")

            // Enhanced app detection with better email detection
            val isEmail = isEmailApp(packageName, title, text, bigText)
            val isWhatsApp = isWhatsAppApp(packageName)

            if (isEmail || isWhatsApp) {
                val appType = if (isEmail) "EMAIL" else "WHATSAPP"

                Log.d(TAG, "🎯 Detected $appType notification - Processing for spam...")

                // Extract both sender and proper subject
                val (sender, actualSubject) = if (isEmail) {
                    extractEmailSenderAndSubject(title, text, bigText, subText, packageName)
                } else {
                    Pair("", title)
                }

                Log.d(TAG, "👤 Extracted - Sender: '$sender', Subject: '$actualSubject'")

                val intent = Intent(ACTION_NOTIFICATION_RECEIVED).apply {
                    putExtra("isEmail", isEmail)
                    putExtra("packageName", packageName)
                    putExtra("title", actualSubject) // Use the actual subject, not the title
                    putExtra("message", if (text.isNotEmpty()) text else bigText)
                    if (isEmail) {
                        putExtra("sender", sender)
                    }
                }

                // Send broadcast to MainActivity
                sendBroadcast(intent)
                Log.d(TAG, "📤 Broadcast sent successfully for $appType spam detection")

            } else {
                Log.d(TAG, "⚡ Ignored notification from $packageName (not target app)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error processing notification: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun extractEmailSenderAndSubject(title: String, text: String, bigText: String, subText: String, packageName: String): Pair<String, String> {
        val combinedText = "$title $text $bigText $subText"

        Log.d(TAG, "🔍 Extracting sender and subject from: ${combinedText.take(200)}...")

        // Extract sender (email address)
        val sender = extractEmailSender(title, text, bigText, subText, packageName)

        // Extract subject - Use text content instead of title when title is generic
        var subject = title

        // If title is generic ("me", "Gmail", etc.), use the text content as subject
        val genericTitles = listOf("me", "gmail", "email", "message", "new message", "2 new messages", "1 new message")
        if (genericTitles.any { title.equals(it, ignoreCase = true) } && text.isNotBlank()) {
            subject = text
            Log.d(TAG, "✅ Using text as subject: '$subject'")
        }

        // If subject is still generic, try bigText
        if (genericTitles.any { subject.equals(it, ignoreCase = true) } && bigText.isNotBlank()) {
            subject = bigText.split("\n").firstOrNull() ?: bigText
            Log.d(TAG, "✅ Using bigText as subject: '$subject'")
        }

        // Clean up subject
        if (subject.length > 100) {
            subject = subject.substring(0, 100) + "..."
        }

        Log.d(TAG, "📝 Final subject: '$subject', Sender: '$sender'")
        return Pair(sender, subject)
    }

    private fun isEmailApp(packageName: String, title: String, text: String, bigText: String): Boolean {
        // Check package names
        val emailPackages = listOf(
            "com.google.android.gm", // Gmail
            "com.android.email", // AOSP Email
            "com.microsoft.office.outlook", // Outlook
            "com.yahoo.mobile.client.android.mail", // Yahoo Mail
            "com.fsck.k9", // K-9 Mail
            "com.samsung.android.email.provider", // Samsung Email
            "com.google.android.email" // Google Email
        )

        val isEmailPackage = emailPackages.any { packageName.equals(it, ignoreCase = true) }

        // Also check content for email patterns
        val combinedContent = "$title $text $bigText".lowercase()
        val hasEmailPatterns = combinedContent.contains("@") ||
                title.contains("gmail", ignoreCase = true) ||
                combinedContent.contains("subject") ||
                combinedContent.contains("from:") ||
                combinedContent.contains("sent:")

        Log.d(TAG, "📧 Is $packageName email app? $isEmailPackage, Has email patterns: $hasEmailPatterns")

        return isEmailPackage || hasEmailPatterns
    }

    private fun isWhatsAppApp(packageName: String): Boolean {
        val whatsAppApps = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b" // WhatsApp Business
        )

        val isWhatsApp = whatsAppApps.any { packageName.equals(it, ignoreCase = true) }

        Log.d(TAG, "💚 Is $packageName WhatsApp? $isWhatsApp")
        return isWhatsApp
    }

    private fun extractEmailSender(title: String, text: String, bigText: String, subText: String, packageName: String): String {
        val combinedText = "$title $text $bigText $subText"

        Log.d(TAG, "🔍 Extracting sender from: ${combinedText.take(200)}...")

        // Method 1: Look for email patterns first
        val emailRegex = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
        val emailMatches = emailRegex.findAll(combinedText)
        val emails = emailMatches.map { it.value }.toList()

        if (emails.isNotEmpty()) {
            Log.d(TAG, "✅ Found emails: $emails")
            // Return the first email found (usually the sender)
            return emails.first()
        }

        // Method 2: For Gmail, subText often contains the sender email
        if (packageName.contains("gmail") && subText.isNotEmpty()) {
            if (subText.contains("@")) {
                Log.d(TAG, "✅ Using subText as sender: $subText")
                return subText
            }
            // If subText doesn't have email but looks like a name/email, use it
            if (subText.isNotBlank() && subText != title) {
                Log.d(TAG, "✅ Using subText as sender name: $subText")
                return subText
            }
        }

        // Method 3: Look for "From:" patterns
        val fromPatterns = listOf(
            Regex("""from:?\s*([^<\n]+)""", RegexOption.IGNORE_CASE),
            Regex("""sender:?\s*([^<\n]+)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in fromPatterns) {
            val match = pattern.find(combinedText)
            if (match != null) {
                val sender = match.groupValues[1].trim()
                Log.d(TAG, "✅ Found sender via pattern: $sender")
                return sender
            }
        }

        // Method 4: Use title if it contains useful information
        if (title.isNotEmpty() && title != "me" && !title.equals("gmail", ignoreCase = true)) {
            Log.d(TAG, "✅ Using title as sender: $title")
            return title
        }

        // Method 5: Check if text starts with email-like pattern
        if (text.contains("@") || text.contains(".com") || text.contains(".net")) {
            val potentialEmail = text.split(" ").firstOrNull { it.contains("@") }
            if (potentialEmail != null) {
                Log.d(TAG, "✅ Using text prefix as sender: $potentialEmail")
                return potentialEmail
            }
        }

        // Final fallback
        Log.d(TAG, "❌ Could not extract sender, using 'Unknown Sender'")
        return "Unknown Sender"
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: Handle notification removal
        Log.d(TAG, "🗑️ Notification removed: ${sbn.packageName}")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "⚠️ Notification Listener DISCONNECTED")
    }
}