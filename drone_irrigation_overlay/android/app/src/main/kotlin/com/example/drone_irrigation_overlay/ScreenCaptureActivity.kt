package com.example.drone_irrigation_overlay

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import java.io.IOException
import java.io.OutputStream
import java.util.*

class ScreenCaptureActivity : Activity() {

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    private var mediaProjection: MediaProjection? = null
    private var projectionManager: MediaProjectionManager? = null
    private var imageReader: ImageReader? = null

    // Bluetooth variables
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val deviceName = "PlantAI_BT"
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val REQUEST_SCREEN_CAPTURE = 1001
    private val REQUEST_ENABLE_BT = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize display metrics
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        // Initialize Bluetooth
        initializeBluetooth()

        // Start screen capture intent
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager?.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE)
    }

    private fun initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            connectToESP32()
        }
    }

    private fun connectToESP32(): Boolean {
        try {
            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter!!.bondedDevices
            val device = pairedDevices.find { it.name == deviceName || it.name?.contains("PlantAI") == true }

            if (device == null) {
                Log.e("Bluetooth", "PlantAI_BT device not found")
                return false
            }

            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket!!.connect()
            outputStream = bluetoothSocket!!.outputStream
            Log.d("Bluetooth", "Connected to ${device.name}")
            return true

        } catch (e: IOException) {
            Log.e("Bluetooth", "Connection failed: ${e.message}")
            return false
        }
    }

    private fun sendBluetoothCommand(command: String) {
        try {
            if (outputStream == null) {
                if (!connectToESP32()) {
                    Toast.makeText(this, "Cannot connect to ESP32", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            outputStream?.write("$command\n".toByteArray())
            outputStream?.flush()
            Log.d("Bluetooth", "Command sent: $command")
        } catch (e: IOException) {
            Log.e("Bluetooth", "Send command failed: ${e.message}")
        }
    }

    private fun disconnectBluetooth() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            outputStream = null
            bluetoothSocket = null
            Log.d("Bluetooth", "Disconnected")
        } catch (e: IOException) {
            Log.e("Bluetooth", "Disconnect error: ${e.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SCREEN_CAPTURE && resultCode == RESULT_OK && data != null) {
            startScreenCapture(data)
        } else if (requestCode == REQUEST_ENABLE_BT) {
            if (bluetoothAdapter!!.isEnabled) connectToESP32()
        } else {
            Toast.makeText(this, "Screen capture cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun startScreenCapture(data: Intent) {
        mediaProjection = projectionManager?.getMediaProjection(RESULT_OK, data)
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight, screenDensity,
            0, imageReader?.surface, null, Handler(Looper.getMainLooper())
        )

        // Capture a single frame after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            val image = imageReader?.acquireLatestImage()
            image?.let {
                val planes = it.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth
                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                it.close()

                // Show watering dialog with screenshot
                showWateringDialog(listOf("Rose", "Tomato", "Lavender","", "Sunflower", "Cactus", "Basil", "Orchid", "Mint").random(), listOf("Healthy", "Needs Water", "Thirsty", "Wilting", "Dry").random(), (100..800).random().toString())
            }
        }, 1000)
    }

    private fun showWateringDialog(plantName: String, health: String, waterAmount: String) {
        val dialogView = layoutInflater.inflate(R.layout.watering_dialog, null)
        val plantNameText = dialogView.findViewById<android.widget.TextView>(R.id.plantNameText)
        val healthText = dialogView.findViewById<android.widget.TextView>(R.id.healthText)
        val waterText = dialogView.findViewById<android.widget.TextView>(R.id.waterText)
        val line1Button = dialogView.findViewById<android.widget.Button>(R.id.line1Button)
        val line2Button = dialogView.findViewById<android.widget.Button>(R.id.line2Button)
        val line3Button = dialogView.findViewById<android.widget.Button>(R.id.line3Button)
        val cancelButton = dialogView.findViewById<android.widget.Button>(R.id.cancelButton)

        plantNameText.text = "Plant: $plantName"
        healthText.text = "Health: $health"
        waterText.text = "Water Needed: ${waterAmount}ml"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Start Watering")
            .setCancelable(false)
            .create()

        line1Button.setOnClickListener {
            sendBluetoothCommand("line1:$waterAmount")
            Toast.makeText(this, "💧 Line 1 Watering Started", Toast.LENGTH_SHORT).show()
        }

        line2Button.setOnClickListener {
            sendBluetoothCommand("line2:$waterAmount")
            Toast.makeText(this, "💧 Line 2 Watering Started", Toast.LENGTH_SHORT).show()
        }

        line3Button.setOnClickListener {
            sendBluetoothCommand("line3:$waterAmount")
            Toast.makeText(this, "💧 Line 3 Watering Started", Toast.LENGTH_SHORT).show()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectBluetooth()
        mediaProjection?.stop()
    }
}