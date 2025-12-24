package com.example.drone_irrigation_overlay

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object ScreenshotStorage {
    var lastPath: String? = null

    fun saveBitmap(context: Context, bitmap: Bitmap) {
        val file = File(context.cacheDir, "last_screenshot.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        lastPath = file.absolutePath
    }
}
