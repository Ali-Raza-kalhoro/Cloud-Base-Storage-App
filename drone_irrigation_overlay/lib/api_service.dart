import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';

class ApiService {
  final String apiKey = "2b10htEnkwSkcnpkXI6AwnSe";
  // ✅ CORRECT PlantNet API endpoint
  final String baseUrl = "https://my-api.plantnet.org/v2/identify/all";

  Future<Map<String, dynamic>?> identifyPlant(File imageFile) async {
    try {
      print("🌿 Uploading image to PlantNet API...");

      // Create HTTP client that bypasses SSL verification
      final httpClient = HttpClient();
      httpClient.badCertificateCallback =
          (X509Certificate cert, String host, int port) => true;

      final ioClient = IOClient(httpClient);

      // ✅ CORRECT PlantNet API request format
      var request = http.MultipartRequest('POST', Uri.parse('$baseUrl?api-key=$apiKey'))
        ..fields['organs'] = 'leaf' // ✅ REQUIRED parameter
        ..files.add(await http.MultipartFile.fromPath('images', imageFile.path));

      print("📤 Sending request to: $baseUrl");
      print("🔑 API Key: ${apiKey.substring(0, 10)}...");

      // Send request using the custom client
      var streamedResponse = await ioClient.send(request);
      var response = await http.Response.fromStream(streamedResponse);

      print("📥 Response status: ${response.statusCode}");

      if (response.statusCode == 200) {
        var responseData = response.body;
        var jsonResponse = json.decode(responseData);
        print("✅ PlantNet API response received successfully");
        return jsonResponse;
      } else {
        print("❌ Error ${response.statusCode}: ${response.body}");
        return {
          'error': 'Failed to identify plant. (${response.statusCode})',
          'details': response.body,
        };
      }
    } catch (e) {
      print("⚠️ Exception while identifying plant: $e");
      return {
        'error': 'An error occurred while connecting to PlantNet.',
        'details': e.toString(),
      };
    }
  }
}