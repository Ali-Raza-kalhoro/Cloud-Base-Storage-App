package com.example.cyber_cyber

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.app.AlertDialog
import android.view.WindowManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.content.BroadcastReceiver
import android.media.RingtoneManager
import android.net.Uri
import android.content.SharedPreferences

class CyberShieldNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "CyberShieldAlertChannel"
        const val NOTIFICATION_ID = 102
        const val ACTION_NOTIFICATION_RECEIVED = "com.example.cyber_cyber.NOTIFICATION_RECEIVED"
        const val ACTION_SPAM_BLOCKED = "com.example.cyber_cyber.SPAM_BLOCKED"
        const val EXTRA_IS_EMAIL = "is_email"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_SENDER = "sender"
    }

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var notificationReceiver: BroadcastReceiver
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        setupMediaPlayer()
        setupNotificationReceiver()
        sharedPreferences = getSharedPreferences("cyber_shield", Context.MODE_PRIVATE)
        Log.d("NotificationService", "Service created")
    }

    private fun setupMediaPlayer() {
        try {
            mediaPlayer = MediaPlayer()
        } catch (e: Exception) {
            Log.e("NotificationService", "Error creating media player: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        if (intent != null && intent.action == ACTION_NOTIFICATION_RECEIVED) {
            handleIncomingIntent(intent)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupNotificationReceiver() {
        notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == ACTION_NOTIFICATION_RECEIVED) {
                    handleIncomingIntent(intent)
                }
            }
        }

        val filter = IntentFilter(ACTION_NOTIFICATION_RECEIVED)
        registerReceiver(notificationReceiver, filter)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val isEmail = intent.getBooleanExtra(EXTRA_IS_EMAIL, false)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        Log.d("NotificationService", "Processing: $title")

        if (isEmail) {
            val sender = intent.getStringExtra(EXTRA_SENDER) ?: ""
            handleSpamDetection(isEmail, sender, title, message, packageName)
        } else {
            handleSpamDetection(isEmail, "", title, message, packageName)
        }
    }

    private fun handleSpamDetection(isEmail: Boolean, sender: String, title: String, message: String, packageName: String) {
        if (isBlocked(isEmail, if (isEmail) sender else packageName)) {
            Log.d("SpamHandler", "Source is blocked")
            return
        }

        showSpamAlert(isEmail, sender, title, message, packageName)
        playAlertSound()
    }

    private fun showSpamAlert(isEmail: Boolean, sender: String, title: String, message: String, packageName: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                val alertType = if (isEmail) "EMAIL SPAM" else "WHATSAPP SPAM"
                val alertMessage = if (isEmail) "From: $sender\n$title\n\n$message" else "Message: $message"

                val alertDialog = AlertDialog.Builder(applicationContext)
                    .setTitle("🚨 $alertType DETECTED!")
                    .setMessage(alertMessage)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setNegativeButton("BLOCK") { dialog, _ ->
                        blockSpamSource(isEmail, sender, packageName)
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .create()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                }

                alertDialog.window?.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )

                alertDialog.show()
            } catch (e: Exception) {
                Log.e("SpamAlert", "Error showing alert: ${e.message}")
            }
        }
    }

    private fun blockSpamSource(isEmail: Boolean, sender: String, packageName: String) {
        val editor = sharedPreferences.edit()
        if (isEmail) {
            val blockedEmails = sharedPreferences.getStringSet("blocked_emails", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            blockedEmails.add(sender.toLowerCase())
            editor.putStringSet("blocked_emails", blockedEmails)
        } else {
            val blockedPackages = sharedPreferences.getStringSet("blocked_packages", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            blockedPackages.add(packageName)
            editor.putStringSet("blocked_packages", blockedPackages)
        }
        editor.apply()

        val intent = Intent(ACTION_SPAM_BLOCKED).apply {
            putExtra(EXTRA_IS_EMAIL, isEmail)
            putExtra(if (isEmail) EXTRA_SENDER else EXTRA_PACKAGE_NAME, if (isEmail) sender else packageName)
        }
        sendBroadcast(intent)
    }

    private fun isBlocked(isEmail: Boolean, identifier: String): Boolean {
        return if (isEmail) {
            val blockedEmails = sharedPreferences.getStringSet("blocked_emails", setOf()) ?: setOf()
            blockedEmails.contains(identifier.toLowerCase())
        } else {
            val blockedPackages = sharedPreferences.getStringSet("blocked_packages", setOf()) ?: setOf()
            blockedPackages.contains(identifier)
        }
    }

    private fun playAlertSound() {
        try {
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer.reset()
            mediaPlayer.setDataSource(applicationContext, defaultSoundUri)
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("AlertSound", "Error playing sound: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cyber Shield Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for spam detection"
                enableVibration(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cyber Shield Alerts")
            .setContentText("Active spam detection")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer.release()
            unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {
            Log.e("NotificationService", "Error in onDestroy: ${e.message}")
        }
    }
}