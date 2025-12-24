package com.example.drone_irrigation_overlay

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.io.IOException
import java.io.OutputStream
import java.util.*

class WateringDialogActivity : Activity() {
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val deviceName = "PlantAI_BT"
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val plantName = intent.getStringExtra("plant_name") ?: "Unknown Plant"
        val plantHealth = intent.getStringExtra("plant_health") ?: "Unknown Health"
        val waterAmount = intent.getStringExtra("water_amount") ?: "500"

        showWateringDialog(plantName, plantHealth, waterAmount)
    }

    private fun showWateringDialog(plantName: String, health: String, waterAmount: String) {
        val dialogView = layoutInflater.inflate(R.layout.watering_dialog, null)

        val plantNameText = dialogView.findViewById<TextView>(R.id.plantNameText)
        val healthText = dialogView.findViewById<TextView>(R.id.healthText)
        val waterText = dialogView.findViewById<TextView>(R.id.waterText)
        val line1Button = dialogView.findViewById<Button>(R.id.line1Button)
        val line2Button = dialogView.findViewById<Button>(R.id.line2Button)
        val line3Button = dialogView.findViewById<Button>(R.id.line3Button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        plantNameText.text = "Plant: $plantName"
        healthText.text = "Health: $health"
        waterText.text = "Water Needed: ${waterAmount}ml"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Start Watering")
            .setCancelable(false)
            .create()

        line1Button.setOnClickListener {
            sendWaterCommand(1, waterAmount)
            dialog.dismiss()
            finish()
        }

        line2Button.setOnClickListener {
            sendWaterCommand(2, waterAmount)
            dialog.dismiss()
            finish()
        }

        line3Button.setOnClickListener {
            sendWaterCommand(3, waterAmount)
            dialog.dismiss()
            finish()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.show()
    }

    private fun connectToESP32(): Boolean {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show()
                return false
            }

            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
            var targetDevice: BluetoothDevice? = null

            for (device in pairedDevices) {
                if (device.name == deviceName || device.name?.contains("PlantAI") == true) {
                    targetDevice = device
                    break
                }
            }

            if (targetDevice == null) {
                Toast.makeText(this, "PlantAI_BT device not found", Toast.LENGTH_SHORT).show()
                return false
            }

            bluetoothSocket = targetDevice.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket!!.connect()
            outputStream = bluetoothSocket!!.outputStream

            Log.d("Bluetooth", "Connected to ${targetDevice.name}")
            return true

        } catch (e: IOException) {
            Log.e("Bluetooth", "Connection failed: ${e.message}")
            Toast.makeText(this, "Bluetooth connection failed", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun sendWaterCommand(line: Int, waterAmount: String) {
        try {
            if (outputStream == null && !connectToESP32()) {
                Toast.makeText(this, "Cannot connect to ESP32", Toast.LENGTH_SHORT).show()
                return
            }

            // Send water command
            val command = "water$line"
            val commandBytes = "$command\n".toByteArray()
            outputStream!!.write(commandBytes)
            outputStream!!.flush()

            Toast.makeText(this, "💧 Watering Line $line started", Toast.LENGTH_LONG).show()
            Log.d("Bluetooth", "Water command sent: $command")

        } catch (e: IOException) {
            Log.e("Bluetooth", "Failed to send command: ${e.message}")
            Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show()
        } finally {
            disconnectBluetooth()
        }
    }

    private fun disconnectBluetooth() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("Bluetooth", "Error disconnecting: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectBluetooth()
    }
}