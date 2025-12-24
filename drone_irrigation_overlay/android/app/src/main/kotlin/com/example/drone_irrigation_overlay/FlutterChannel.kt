package com.example.drone_irrigation_overlay

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

object FlutterChannel {
    private const val CHANNEL = "overlay.channel"
    private var messenger: BinaryMessenger? = null

    fun setMessenger(m: BinaryMessenger) {
        messenger = m
    }

    fun notifyScreenshotCaptured() {
        messenger?.let {
            MethodChannel(it, CHANNEL).invokeMethod("screenshot_done", null)
        }
    }
}