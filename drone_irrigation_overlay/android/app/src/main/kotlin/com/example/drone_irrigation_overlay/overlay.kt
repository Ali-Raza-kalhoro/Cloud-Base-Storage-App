package com.example.drone_irrigation_overlay

import android.app.Application

class OverlayApp : Application() {
    companion object {
        var mainActivity: MainActivity? = null
    }

    override fun onCreate() {
        super.onCreate()
    }
}