// bluetooth_service.dart
import 'dart:typed_data';
import 'package:flutter_bluetooth_serial_plus/flutter_bluetooth_serial_plus.dart';

class BluetoothService {
  static BluetoothConnection? _connection;
  static bool _isConnected = false;
  static const String deviceName = "PlantAI_BT";

  // Callback for received messages - properly defined as static variables
  static Function(String)? _onMessageReceived;
  static Function(String)? _onConnectionStatusChanged;

  // Setters for the callbacks
  static set onMessageReceived(Function(String)? callback) {
    _onMessageReceived = callback;
  }

  static set onConnectionStatusChanged(Function(String)? callback) {
    _onConnectionStatusChanged = callback;
  }

  static Future<bool> connectToDevice() async {
    try {
      print("🔍 Starting Bluetooth device search...");

      // Check if Bluetooth is available
      bool? isAvailable = await FlutterBluetoothSerial.instance.isAvailable;
      if (isAvailable != true) {
        throw Exception("Bluetooth is not available on this device");
      }

      // Check if Bluetooth is enabled
      bool? isEnabled = await FlutterBluetoothSerial.instance.isOn;
      if (isEnabled != true) {
        throw Exception("Bluetooth is not enabled. Please enable Bluetooth and try again.");
      }

      print("📱 Getting bonded devices...");
      List<BluetoothDevice> devices = await FlutterBluetoothSerial.instance.getBondedDevices();
      print("📋 Found ${devices.length} bonded devices");

      // Print all devices for debugging
      for (var device in devices) {
        print("🔍 Device: ${device.name} - ${device.address}");
      }

      // Find our device
      BluetoothDevice? targetDevice;
      for (var device in devices) {
        if (device.name?.contains(deviceName) == true) {
          targetDevice = device;
          break;
        }
      }

      if (targetDevice == null) {
        throw Exception("Device '$deviceName' not found in paired devices. Please pair the ESP32 first in Android Bluetooth settings.");
      }

      print("🔗 Connecting to ${targetDevice.name} (${targetDevice.address})...");

      // Connect to device with timeout
      _connection = await BluetoothConnection.toAddress(targetDevice.address)
          .timeout(const Duration(seconds: 15));

      _isConnected = true;
      print("✅ Successfully connected to ${targetDevice.name}");

      // Listen for incoming data
      _connection!.input!.listen(_handleData).onDone(() {
        _isConnected = false;
        print("🔌 Bluetooth connection closed");
        if (_onConnectionStatusChanged != null) {
          _onConnectionStatusChanged!("disconnected");
        }
      });

      if (_onConnectionStatusChanged != null) {
        _onConnectionStatusChanged!("connected");
      }

      return true;
    } catch (e) {
      _isConnected = false;
      print("❌ Bluetooth connection error: $e");
      if (_onConnectionStatusChanged != null) {
        _onConnectionStatusChanged!("error");
      }
      throw Exception("Failed to connect: ${e.toString()}");
    }
  }

  static void _handleData(Uint8List data) {
    try {
      String message = String.fromCharCodes(data).trim();
      print("📩 BT Received: $message");

      // Notify listeners
      if (_onMessageReceived != null) {
        _onMessageReceived!(message);
      }
    } catch (e) {
      print("❌ Error handling Bluetooth data: $e");
    }
  }

  static Future<void> sendCommand(String command) async {
    if (!_isConnected || _connection == null) {
      throw Exception("Not connected to Bluetooth device. Please connect first.");
    }

    try {
      print("📤 Sending command: $command");
      _connection!.output.add(Uint8List.fromList('$command\n'.codeUnits));
      await _connection!.output.allSent;
      print("✅ Command sent successfully: $command");
    } catch (e) {
      _isConnected = false;
      print("❌ Failed to send command: $e");
      throw Exception("Failed to send command: ${e.toString()}");
    }
  }

  static Future<void> analyzePlant() async {
    await sendCommand("analyze");
  }

  static Future<void> startWatering(int line) async {
    if (line < 1 || line > 3) {
      throw Exception("Line number must be 1, 2, or 3");
    }
    await sendCommand("water$line");
  }

  static Future<void> stopWatering() async {
    await sendCommand("stop");
  }

  static Future<void> getStatus() async {
    await sendCommand("status");
  }

  static Future<void> sendHealthData(String health, int waterAmount) async {
    await sendCommand("health:$health-$waterAmount");
  }

  static Future<void> disconnect() async {
    try {
      await _connection?.close();
      _connection = null;
      _isConnected = false;
      print("🔌 Disconnected from Bluetooth device");
      if (_onConnectionStatusChanged != null) {
        _onConnectionStatusChanged!("disconnected");
      }
    } catch (e) {
      print("❌ Error disconnecting: $e");
    }
  }

  static bool get isConnected => _isConnected;

  static Future<List<BluetoothDevice>> getBondedDevices() async {
    return await FlutterBluetoothSerial.instance.getBondedDevices();
  }

  // Method to manually trigger connection status (for testing)
  static void notifyConnectionStatus(String status) {
    if (_onConnectionStatusChanged != null) {
      _onConnectionStatusChanged!(status);
    }
  }
}