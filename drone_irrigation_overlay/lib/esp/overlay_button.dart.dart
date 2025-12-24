import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

import '../services/http_service.dart';

class OverlayController {
  static const channel = MethodChannel("overlay.channel"); // Fixed channel name

  Future<void> requestPermission() async {
    try {
      final allowed = await channel.invokeMethod("checkOverlayPermission");
      if (!allowed) {
        await channel.invokeMethod("openOverlayPermission");
      }
    } catch (e) {
      print("Error requesting permission: $e");
      rethrow;
    }
  }

  Future<void> startOverlay() async {
    try {
      await channel.invokeMethod("startOverlay");
    } catch (e) {
      print("Error starting overlay: $e");
      rethrow;
    }
  }

  Future<void> stopOverlay() async {
    try {
      await channel.invokeMethod("stopOverlay");
    } catch (e) {
      print("Error stopping overlay: $e");
      rethrow;
    }
  }

  static void registerListener(Function(String, int) analysisCallback) {
    channel.setMethodCallHandler((call) async {
      if (call.method == "screenshot_done") {
        // Simulate analysis data (in real app, this would come from image processing)
        final health = "Needs Water"; // This would be determined by ML model
        final water = 750; // This would be calculated based on analysis
        analysisCallback(health, water);
      }
      return null;
    });
  }

// overlay_button.dart.dart - Update the sendWaterCommand method
  static Future<void> sendWaterCommand(int line) async {
    try {
      if (!BluetoothService.isConnected) {
        await BluetoothService.connectToDevice();
      }
      await BluetoothService.startWatering(line);
      print("✅ Watering command sent to line $line via Bluetooth");
    } catch (e) {
      print("❌ Failed sending command to line $line: $e");
      throw e;
    }
  }}