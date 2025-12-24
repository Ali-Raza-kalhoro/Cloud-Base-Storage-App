// import 'package:flutter/material.dart';
// import 'package:url_launcher/url_launcher.dart';
//
// import 'overlaybutton.dart';
//
// class MainScreen extends StatefulWidget {
//   const MainScreen({super.key});
//
//   @override
//   State<MainScreen> createState() => _MainScreenState();
// }
//
// class _MainScreenState extends State<MainScreen> {
//   bool showOverlay = true;
//
//   Future<void> captureScreenshot() async {
//     setState(() {
//       showOverlay = false;
//     });
//
//     // Show popup after short delay simulating screenshot complete
//     Future.delayed(const Duration(seconds: 1), () {
//       if (mounted) {
//         showESP32Dialog(context);
//       }
//     });
//
//     Future.delayed(const Duration(seconds: 2), () {
//       if (mounted) {
//         setState(() {
//           showOverlay = true;
//         });
//       }
//     });
//   }
//
//   @override
//   Widget build(BuildContext context) {
//     return Scaffold(
//       body: Stack(
//         children: [
//           const Center(child: Text("Drone Controller")),
//           if (showOverlay)
//             OverlayButton(
//               onCapture: captureScreenshot,
//             )
//         ],
//       ),
//     );
//   }
// }
//
// // Show dialog after screenshot
// Future<void> showESP32Dialog(BuildContext context) async {
//   return showDialog(
//     context: context,
//     builder: (context) {
//       return AlertDialog(
//         title: const Text("Send Water"),
//         content: const Text("Go to browser to send water on selected line?"),
//         actions: [
//           TextButton(
//             onPressed: () {
//               Navigator.of(context).pop(); // Close dialog
//             },
//             child: const Text("Cancel"),
//           ),
//           ElevatedButton(
//             onPressed: () async {
//               Navigator.of(context).pop(); // Close dialog
//               await openESP32Page();
//             },
//             child: const Text("Yes"),
//           ),
//         ],
//       );
//     },
//   );
// }
//
// // Open ESP32 Web Page
// Future<void> openESP32Page() async {
//   final url = Uri.parse("http://192.168.4.1/");
//
//   if (await canLaunchUrl(url)) {
//     await launchUrl(url, mode: LaunchMode.externalApplication);
//   } else {
//     debugPrint("⚠ Failed to open ESP32 page");
//   }
// }
