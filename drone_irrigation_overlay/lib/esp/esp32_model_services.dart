// import 'package:http/http.dart' as http;
//
// class ESP32Service {
//   final String baseUrl = "http://192.168.4.1";
//
//   Future<String> sendWaterCommand(int lineNumber) async {
//     try {
//       final response = await http.get(Uri.parse("$baseUrl/line$lineNumber"));
//       if (response.statusCode == 200) {
//         return "✅ Water sent to Line $lineNumber!";
//       }
//       return "⚠️ ESP32 Error: ${response.statusCode}";
//     } catch (e) {
//       return "❌ Failed: $e";
//     }
//   }
// }
