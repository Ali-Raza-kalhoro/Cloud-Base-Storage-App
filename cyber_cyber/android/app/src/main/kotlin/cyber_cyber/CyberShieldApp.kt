package com.example.cyber_cyber

import android.app.Application
import android.util.Log

class CyberShieldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("CyberShieldApp", "Application started")
    }
}