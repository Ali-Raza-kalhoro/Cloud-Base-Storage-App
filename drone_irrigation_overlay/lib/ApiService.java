import 'dart:io';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter/foundation.dart';
import 'package:drone_irrigation_overlay/plant_model.dart';

class ApiService {
  // ✅ Use the actual API key from your screenshot
  static const String _apiKey = "2b10AAgsTbleaXl61pPObb1pu";
  // ✅ Correct API endpoint
  static const String _endpoint ="https://my-api.plantnet.org/v2/identify/all";

  static Future<PlantResult> analyzePlant(File imageFile) async {
    try {
      debugPrint('🌿 Sending image to Plant.id API for analysis...');

      // ✅ Use multipart request (correct format for Plant.id)
      var request = http.MultipartRequest('POST', Uri.parse(_endpoint))
              ..headers['Api-Key'] = _apiKey
              ..fields['details'] = 'common_names,url,description,treatment,classification,watering'
              ..fields['language'] = 'en'
              ..files.add(await http.MultipartFile.fromPath('images', imageFile.path));

      debugPrint('📤 Sending request to Plant.id API...');

      var response = await request.send();
      var responseBody = await response.stream.bytesToString();
      var data = jsonDecode(responseBody);

      debugPrint('📥 Response status: ${response.statusCode}');
      debugPrint('📥 Response data: $data');

      if (response.statusCode != 200) {
        return _handleApiError(response.statusCode, responseBody);
      }

      return _parseApiResponse(data);

    } catch (e) {
      debugPrint('❌ API Error: $e');
      return _getErrorResult('Network error: ${e.toString()}');
    }
  }

  static PlantResult _parseApiResponse(Map<String, dynamic> data) {
    // Check if we have suggestions
    if (data['suggestions'] == null || data['suggestions'].isEmpty) {
      return _getNoPlantResult(
              'No plant identified. Please try with a clearer photo showing leaves.',
              );
    }

    final suggestion = data['suggestions'][0];
    final plantName = suggestion['plant_name'] ?? 'Unknown Plant';
    final probability = (suggestion['probability'] ?? 0.0) as double;
    final confidence = probability * 100;

    // Extract common name if available
    String displayName = plantName;
    if (suggestion['plant_details'] != null &&
            suggestion['plant_details']['common_names'] != null &&
            suggestion['plant_details']['common_names'].isNotEmpty) {
      displayName = suggestion['plant_details']['common_names'][0];
    }

    // Extract watering information if available
    String waterInfo = '';
    if (suggestion['plant_details'] != null &&
            suggestion['plant_details']['watering'] != null) {
      waterInfo = suggestion['plant_details']['watering']['description'] ?? '';
    }

    debugPrint('✅ Plant identified: $displayName (${confidence.toStringAsFixed(1)}% confidence)');

    return PlantResult(
            isPlant: true,
            plantType: displayName,
            waterAmount: _getWaterAmount(plantName, confidence),
            waterFrequency: _getWaterFrequency(plantName),
            confidence: probability,
            detectionType: 'plant_id_api',
            message: '✅ Identified: $displayName\nConfidence: ${confidence.toStringAsFixed(1)}%',
            scientificName: plantName,
            additionalInfo: waterInfo,
    );
  }

  static PlantResult _handleApiError(int statusCode, String responseBody) {
    String errorMessage;

    switch (statusCode) {
      case 401:
        errorMessage = 'API Key Error: Unauthorized. Please check your API key.';
        break;
      case 402:
        errorMessage = 'API Limit: Insufficient credits. Please check your Kindwise dashboard.';
        break;
      case 400:
        errorMessage = 'Bad Request: Invalid image or parameters.';
        break;
      case 413:
        errorMessage = 'Image too large. Please use a smaller image.';
        break;
      case 429:
        errorMessage = 'Rate limit exceeded. Please try again later.';
        break;
      case 500:
      case 502:
      case 503:
        errorMessage = 'Server error. Please try again later.';
        break;
      default:
        errorMessage = 'API Error $statusCode: $responseBody';
    }

    debugPrint('❌ API Error $statusCode: $errorMessage');
    return _getErrorResult(errorMessage);
  }

  static String _getWaterAmount(String plantName, double confidence) {
    final name = plantName.toLowerCase();
    int baseAmount;

    // Determine base amount by plant type
    if (name.contains('cactus') || name.contains('succulent')) {
      baseAmount = 100;
    } else if (name.contains('tree') || name.contains('shrub')) {
      baseAmount = 2000;
    } else if (name.contains('tomato') || name.contains('vegetable')) {
      baseAmount = 500;
    } else if (name.contains('fern') || name.contains('tropical')) {
      baseAmount = 400;
    } else if (name.contains('mint') || name.contains('basil') || name.contains('herb')) {
      baseAmount = 300;
    } else if (name.contains('rose') || name.contains('flower')) {
      baseAmount = 350;
    } else {
      baseAmount = 250; // Default for unknown plants
    }

    // Adjust based on confidence
    if (confidence < 50) {
      baseAmount = (baseAmount * 0.7).round(); // Reduce for low confidence
    } else if (confidence > 80) {
      baseAmount = (baseAmount * 1.2).round(); // Increase for high confidence
    }

    return '${baseAmount}ml';
  }

  static String _getWaterFrequency(String plantName) {
    final name = plantName.toLowerCase();

    if (name.contains('cactus') || name.contains('succulent')) {
      return 'Every 2-3 weeks';
    } else if (name.contains('tree') || name.contains('shrub')) {
      return 'Weekly';
    } else if (name.contains('tomato') || name.contains('vegetable')) {
      return 'Every 2-3 days';
    } else if (name.contains('fern') || name.contains('tropical')) {
      return 'Every 3-4 days';
    } else if (name.contains('mint') || name.contains('basil') || name.contains('herb')) {
      return 'Every 2 days';
    } else if (name.contains('rose') || name.contains('flower')) {
      return 'Every 4-5 days';
    } else {
      return 'Weekly';
    }
  }

  static PlantResult _getNoPlantResult(String reason) {
    debugPrint('❌ No plant detected: $reason');
    return PlantResult(
            isPlant: false,
            plantType: 'Not identified',
            waterAmount: '0 ml',
            waterFrequency: 'N/A',
            confidence: 0.0,
            detectionType: 'none',
            message: reason,
    );
  }

  static PlantResult _getErrorResult(String error) {
    debugPrint('❌ API Service Error: $error');
    return PlantResult(
            isPlant: false,
            plantType: 'Error',
            waterAmount: '0 ml',
            waterFrequency: 'N/A',
            confidence: 0.0,
            detectionType: 'none',
            message: '❌ $error\n\nPlease try again with a clear plant photo.',
    );
  }
}