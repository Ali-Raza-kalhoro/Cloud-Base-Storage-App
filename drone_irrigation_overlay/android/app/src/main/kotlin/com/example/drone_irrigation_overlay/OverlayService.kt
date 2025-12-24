package com.example.drone_irrigation_overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.Button
import android.widget.Toast
import kotlinx.coroutines.*


class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isProcessing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission needed", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        createNotificationForForeground()
        createOverlay()
    }

    private fun createNotificationForForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "overlay_service_channel"
            val channelName = "Overlay Service"

            val channel = android.app.NotificationChannel(
                channelId, channelName,
                android.app.NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification = android.app.Notification.Builder(this, channelId)
                .setContentTitle("Drone Button Active")
                .setContentText("Tap to capture screenshot")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()

            startForeground(1, notification)
        }
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER or Gravity.END
        params.y = 200

        val button = overlayView!!.findViewById<Button>(R.id.overlay_button)

        button.setOnClickListener {
            if (isProcessing) return@setOnClickListener

            isProcessing = true
            removeOverlay()
            startScreenCaptureActivity()

            // Use Handler with proper imports
            Handler(Looper.getMainLooper()).postDelayed({
                isProcessing = false
            }, 2000)
        }

        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
            overlayView = null
        } catch (_: Exception) {}
    }

    fun restoreOverlay() {
        if (overlayView == null) createOverlay()
    }

    private fun startScreenCaptureActivity() {
        val intent = Intent(this, ScreenCaptureActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        // Restore overlay after delay using Handler
        Handler(Looper.getMainLooper()).postDelayed({
            restoreOverlay()
        }, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}