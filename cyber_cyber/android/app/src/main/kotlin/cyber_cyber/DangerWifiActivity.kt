package com.example.cyber_cyber

import android.app.Activity
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DangerWifiActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use a simple programmatic layout instead of XML to avoid layout issues
        val textView = TextView(this).apply {
            text = "🚨 DANGEROUS WIFI DETECTED! 🚨\n\nPlease disconnect immediately!"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF0000.toInt())
            setPadding(50, 100, 50, 100)
        }

        setContentView(textView)

        // Make it fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val ssid = intent.getStringExtra("ssid") ?: "Unknown"
        val score = intent.getIntExtra("score", 0)

        textView.text = "🚨 DANGEROUS WIFI DETECTED! 🚨\n\nNetwork: $ssid\nSafety Score: $score/100\n\nPlease disconnect immediately!"

        // Auto-close after 8 seconds
        textView.postDelayed({
            finish()
        }, 8000)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Notify MainActivity that panic mode should stop
        (applicationContext as? MainActivity)?.stopPanicMode()
    }
}