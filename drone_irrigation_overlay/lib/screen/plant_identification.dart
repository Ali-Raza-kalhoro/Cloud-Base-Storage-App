import 'dart:io';
import 'dart:math';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';
import '../services/http_service.dart';

class PlantIdentificationScreen extends StatefulWidget {
  final ImageSource source;

  const PlantIdentificationScreen({Key? key, required this.source})
      : super(key: key);

  @override
  State<PlantIdentificationScreen> createState() =>
      _PlantIdentificationScreenState();
}

class _PlantIdentificationScreenState extends State<PlantIdentificationScreen> {
  File? _image;
  bool _isAnalyzing = false;
  bool _isWatering = false;
  String? _plantName;
  double? _confidence;
  String? _waterRecommendation;
  final ImagePicker _picker = ImagePicker();
  final Random _random = Random();

  // ✅ CORRECT PlantNet API credentials
  final String apiKey = "2b106Adp5FI9vAhJBLn8TbtrO";
  final String baseUrl = "https://my-api.plantnet.org/v2/identify/all";

  @override
  void initState() {
    super.initState();
    _pickImage();
  }

  Future<void> _pickImage() async {
    try {
      final XFile? image = await _picker.pickImage(
        source: widget.source,
        maxWidth: 1200,
        maxHeight: 1200,
        imageQuality: 80,
      );

      if (image != null) {
        setState(() => _image = File(image.path));
        _identifyPlant(_image!);
      } else {
        if (mounted) Navigator.pop(context);
      }
    } catch (e) {
      print("❌ Error picking image: $e");
      _showError("Failed to pick image: $e");
    }
  }

  Future<void> _identifyPlant(File imageFile) async {
    if (!mounted) return;

    setState(() {
      _isAnalyzing = true;
      _plantName = null;
      _confidence = null;
      _waterRecommendation = null;
    });

    try {
      print("🌿 Uploading image to PlantNet API...");
      print("📁 Image path: ${imageFile.path}");

      // Create custom HTTP client to handle SSL issues
      final httpClient = HttpClient();
      httpClient.badCertificateCallback =
          (X509Certificate cert, String host, int port) => true;

      final ioClient = IOClient(httpClient);

      // ✅ CORRECT PlantNet API request
      var request = http.MultipartRequest(
        'POST',
        Uri.parse('$baseUrl?api-key=$apiKey'),
      )
        ..fields['organs'] = 'leaf' // ✅ REQUIRED parameter
        ..files.add(await http.MultipartFile.fromPath('images', imageFile.path));

      print("📤 Sending request to: $baseUrl");

      // Send request using custom client
      var streamedResponse = await ioClient.send(request);
      var response = await http.Response.fromStream(streamedResponse);

      print("📥 Response status: ${response.statusCode}");

      if (response.statusCode == 200) {
        var jsonResponse = json.decode(response.body);
        print("✅ API call successful");
        _parsePlantNetResponse(jsonResponse);
      } else if (response.statusCode == 404) {
        _showNonPlantDialog("API endpoint not found. Please check the API URL.");
      } else if (response.statusCode == 401) {
        _showNonPlantDialog("Invalid API key. Please check your PlantNet API key.");
      } else {
        print("❌ API error: ${response.statusCode} - ${response.body}");
        _showNonPlantDialog("API Error ${response.statusCode}. Please try again.");
      }
    } catch (e) {
      print("⚠️ Exception while identifying plant: $e");
      _showNonPlantDialog("Connection error: Please check your internet connection and try again.");
    } finally {
      if (mounted) {
        setState(() => _isAnalyzing = false);
      }
    }
  }

  void _parsePlantNetResponse(Map<String, dynamic> jsonResponse) {
    try {
      print("🔍 Parsing API response...");

      if (jsonResponse['results'] != null && jsonResponse['results'].isNotEmpty) {
        final result = jsonResponse['results'][0];

        // Extract plant name
        String plantName = 'Unknown Plant';
        if (result['species'] != null) {
          plantName = result['species']['scientificNameWithoutAuthor'] ??
              result['species']['scientificName'] ??
              'Unknown Plant';
        }

        // Extract confidence score
        double confidence = (result['score'] ?? 0.0).toDouble();

        print("🌱 Identified: $plantName");
        print("📊 Confidence: ${(confidence * 100).toStringAsFixed(1)}%");

        if (mounted) {
          setState(() {
            _plantName = plantName;
            _confidence = confidence;
            _waterRecommendation = _generateSmartWaterAmount(plantName, confidence);
          });
        }
      } else {
        _showNonPlantDialog("No plant detected. Please take a clear photo of plant leaves.");
      }
    } catch (e) {
      print("❌ Error parsing response: $e");
      _showNonPlantDialog("Error processing the plant identification.");
    }
  }

  String _generateSmartWaterAmount(String plantName, double confidence) {
    final name = plantName.toLowerCase();
    int baseAmount = 250; // Default amount

    // Determine water amount based on plant type
    if (name.contains('cactus') || name.contains('succulent')) {
      baseAmount = 100;
    } else if (name.contains('tree') || name.contains('shrub')) {
      baseAmount = 800;
    } else if (name.contains('tomato') || name.contains('solanum')) {
      baseAmount = 500;
    } else if (name.contains('fern')) {
      baseAmount = 400;
    } else if (name.contains('mint') || name.contains('mentha') || name.contains('herb')) {
      baseAmount = 300;
    } else if (name.contains('rose') || name.contains('flower')) {
      baseAmount = 350;
    }

    // Adjust based on confidence
    if (confidence < 0.5) {
      baseAmount = (baseAmount * 0.7).round();
    } else if (confidence > 0.8) {
      baseAmount = (baseAmount * 1.2).round();
    }

    // Ensure reasonable limits
    baseAmount = baseAmount.clamp(50, 2000);

    return '${baseAmount}ml';
  }

  void _showNonPlantDialog(String message) {
    if (!mounted) return;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        Future.delayed(const Duration(seconds: 3), () {
          if (mounted && Navigator.canPop(context)) {
            Navigator.pop(context);
            _pickImage();
          }
        });

        return AlertDialog(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: const Row(
            children: [
              Icon(Icons.help_outline, color: Colors.orange),
              SizedBox(width: 8),
              Text("Plant Identification"),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                message,
                style: const TextStyle(fontSize: 16),
              ),
              const SizedBox(height: 16),
              const Center(
                child: CircularProgressIndicator(),
              ),
              const SizedBox(height: 8),
              const Center(
                child: Text(
                  "Trying again...",
                  style: TextStyle(color: Colors.grey, fontSize: 12),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  void _showWateringOptions() {
    if (_plantName == null || _waterRecommendation == null) return;

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.opacity, color: Colors.blue),
            SizedBox(width: 8),
            Text("Start Watering"),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text("Plant: $_plantName",
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            const SizedBox(height: 12),
            Row(
              children: [
                const Icon(Icons.water_drop, color: Colors.blue, size: 20),
                const SizedBox(width: 8),
                Text("Water: $_waterRecommendation",
                    style: const TextStyle(color: Colors.blue, fontSize: 16)),
              ],
            ),
            if (_confidence != null) ...[
              const SizedBox(height: 8),
              Text("Confidence: ${(_confidence! * 100).toStringAsFixed(1)}%",
                  style: TextStyle(
                      color: _confidence! > 0.7 ? Colors.green : Colors.orange,
                      fontWeight: FontWeight.w500
                  )),
            ],
            const SizedBox(height: 16),
            const Text("Select irrigation line:",
                style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            const Text("Choose which line to water this plant"),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text("Cancel"),
          ),
          ElevatedButton(
            onPressed: _isWatering ? null : () => _sendWaterCommand(1),
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF4CAF50)),
            child: _isWatering
                ? const SizedBox(
              height: 20, width: 20,
              child: CircularProgressIndicator(strokeWidth: 2, valueColor: AlwaysStoppedAnimation<Color>(Colors.white)),
            )
                : const Text("Line 1", style: TextStyle(color: Colors.white)),
          ),
          ElevatedButton(
            onPressed: _isWatering ? null : () => _sendWaterCommand(2),
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF2196F3)),
            child: _isWatering
                ? const SizedBox(
              height: 20, width: 20,
              child: CircularProgressIndicator(strokeWidth: 2, valueColor: AlwaysStoppedAnimation<Color>(Colors.white)),
            )
                : const Text("Line 2", style: TextStyle(color: Colors.white)),
          ),
          ElevatedButton(
            onPressed: _isWatering ? null : () => _sendWaterCommand(3),
            style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFFF9800)),
            child: _isWatering
                ? const SizedBox(
              height: 20, width: 20,
              child: CircularProgressIndicator(strokeWidth: 2, valueColor: AlwaysStoppedAnimation<Color>(Colors.white)),
            )
                : const Text("Line 3", style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  Future<void> _sendWaterCommand(int lineNumber) async {
    if (_waterRecommendation == null || _plantName == null) return;

    Navigator.pop(context); // Close the dialog

    setState(() {
      _isWatering = true;
    });

    try {
      // Connect to Bluetooth if not connected
      if (!BluetoothService.isConnected) {
        await BluetoothService.connectToDevice();
      }

      // Get water amount as integer
      final waterAmount = _getWaterAmount();

      // Send health data to ESP32
      await BluetoothService.sendHealthData(_plantName!, waterAmount);

      // Start watering on specific line
      await BluetoothService.startWatering(lineNumber);

      _showSuccess("✅ Watering command sent to Line $lineNumber!");

    } catch (e) {
      _showError("❌ Failed to send command: ${e.toString()}");
    } finally {
      if (mounted) {
        setState(() {
          _isWatering = false;
        });
      }
    }
  }

  int _getWaterAmount() {
    if (_waterRecommendation == null) return 150;
    final regex = RegExp(r'(\d+)');
    final match = regex.firstMatch(_waterRecommendation!);
    if (match != null) {
      return int.tryParse(match.group(0!) ?? '150') ?? 150;
    }
    return 150;
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.red,
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 3),
      ),
    );
  }

  void _showSuccess(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.green,
        behavior: SnackBarBehavior.floating,
        duration: const Duration(seconds: 3),
      ),
    );
  }

  Widget _buildImagePreview() {
    return Container(
      width: double.infinity,
      height: 300,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.2),
            blurRadius: 10,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: _image != null
            ? Image.file(_image!, fit: BoxFit.cover, errorBuilder: (context, error, stackTrace) {
          return const Center(child: Icon(Icons.error, size: 50, color: Colors.red));
        })
            : const Center(child: CircularProgressIndicator()),
      ),
    );
  }

  Widget _buildAnalysisInProgress() {
    return Column(
      children: [
        const CircularProgressIndicator(color: Color(0xFF2E7D32)),
        const SizedBox(height: 20),
        Text(
          _image != null ? "Analyzing plant image..." : "Loading image...",
          style: const TextStyle(fontSize: 16, color: Colors.grey),
        ),
        const SizedBox(height: 10),
        if (_confidence != null)
          Text(
            "Confidence: ${(_confidence! * 100).toStringAsFixed(1)}%",
            style: TextStyle(
              color: _confidence! > 0.7 ? Colors.green : Colors.orange,
              fontWeight: FontWeight.w500,
            ),
          ),
      ],
    );
  }

  Widget _buildResults() {
    return Container(
      padding: const EdgeInsets.all(25),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.1),
            blurRadius: 15,
            offset: const Offset(0, 5),
          ),
        ],
        border: Border.all(color: const Color(0xFF2E7D32), width: 2),
      ),
      child: Column(
        children: [
          const Icon(Icons.eco, size: 70, color: Color(0xFF2E7D32)),
          const SizedBox(height: 20),
          Text(
            _plantName!,
            style: const TextStyle(
              fontSize: 22, // Reduced to prevent overflow
              fontWeight: FontWeight.bold,
              color: Color(0xFF2E7D32),
            ),
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          if (_confidence != null) ...[
            const SizedBox(height: 8),
            Text(
              "Confidence: ${(_confidence! * 100).toStringAsFixed(1)}%",
              style: TextStyle(
                fontSize: 14,
                color: _confidence! > 0.7 ? Colors.green : Colors.orange,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            decoration: BoxDecoration(
              color: Colors.blue[50],
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: Colors.blue[100]!),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.water_drop, color: Colors.blue),
                const SizedBox(width: 8),
                Text(
                  _waterRecommendation!,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: Colors.blue,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 25),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _isWatering ? null : _showWateringOptions,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2E7D32),
                padding: const EdgeInsets.symmetric(vertical: 16),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: _isWatering
                  ? const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SizedBox(
                    height: 20, width: 20,
                    child: CircularProgressIndicator(strokeWidth: 2, valueColor: AlwaysStoppedAnimation<Color>(Colors.white)),
                  ),
                  SizedBox(width: 10),
                  Text("Sending Command...", style: TextStyle(fontSize: 18, color: Colors.white)),
                ],
              )
                  : const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.opacity, color: Colors.white),
                  SizedBox(width: 10),
                  Text("Start Watering", style: TextStyle(fontSize: 18, color: Colors.white)),
                ],
              ),
            ),
          ),
          const SizedBox(height: 10),
          TextButton(
            onPressed: _isWatering ? null : _pickImage,
            child: const Text("Analyze Another Plant", style: TextStyle(color: Colors.grey)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.source == ImageSource.camera ? "📷 Camera" : "🖼️ Gallery"),
        backgroundColor: const Color(0xFF2E7D32),
        actions: [
          if (_image != null && !_isAnalyzing && !_isWatering)
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _pickImage,
              tooltip: "Try another image",
            ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            if (_image != null) _buildImagePreview(),
            const SizedBox(height: 30),
            if (_isAnalyzing) _buildAnalysisInProgress(),
            if (_plantName != null && !_isAnalyzing) _buildResults(),
          ],
        ),
      ),
    );
  }
}