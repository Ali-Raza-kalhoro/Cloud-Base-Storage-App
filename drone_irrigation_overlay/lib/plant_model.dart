class PlantResult {
  final bool isPlant;
  final String plantType;
  final String waterAmount;
  final String waterFrequency;
  final double confidence;
  final String detectionType;
  final String message;

  PlantResult({
    required this.isPlant,
    required this.plantType,
    required this.waterAmount,
    required this.waterFrequency,
    required this.confidence,
    required this.detectionType,
    required this.message,
  });

  factory PlantResult.fromJson(Map<String, dynamic> json) {
    return PlantResult(
      isPlant: json['is_plant'] ?? false,
      plantType: json['plant_type'] ?? 'Unknown Plant',
      waterAmount: json['water_amount'] ?? '0 ml',
      waterFrequency: json['water_frequency'] ?? 'N/A',
      confidence: (json['confidence'] ?? 0.0).toDouble(),
      detectionType: json['detection_type'] ?? 'unknown',
      message: json['message'] ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'is_plant': isPlant,
      'plant_type': plantType,
      'water_amount': waterAmount,
      'water_frequency': waterFrequency,
      'confidence': confidence,
      'detection_type': detectionType,
      'message': message,
    };
  }
}