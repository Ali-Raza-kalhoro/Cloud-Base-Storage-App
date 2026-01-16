// import 'package:flutter/material.dart';
// import 'package:flutter/services.dart';
// import 'package:flutter_tts/flutter_tts.dart';
// import 'package:permission_handler/permission_handler.dart';
// import 'dart:math';
//
// void main() {
//   runApp(CyberShieldApp());
// }
//
// class CyberShieldApp extends StatelessWidget {
//   @override
//   Widget build(BuildContext context) {
//     return MaterialApp(
//       title: 'The Deception Detection System',
//       debugShowCheckedModeBanner: false,
//       theme: ThemeData(
//         colorScheme: ColorScheme.fromSeed(
//           seedColor: Colors.blue,
//           brightness: Brightness.dark,
//           background: Color(0xFF0A0E21),
//           surface: Color(0xFF1D1F33),
//           onSurface: Colors.white,
//         ),
//         visualDensity: VisualDensity.adaptivePlatformDensity,
//         useMaterial3: true,
//         fontFamily: 'Inter',
//       ),
//       home: HomeScreen(),
//     );
//   }
// }
//
// class HomeScreen extends StatefulWidget {
//   @override
//   _HomeScreenState createState() => _HomeScreenState();
// }
//
// class _HomeScreenState extends State<HomeScreen> with SingleTickerProviderStateMixin {
//   static const platform = MethodChannel('cyber_shield/channel');
//   final FlutterTts flutterTts = FlutterTts();
//
//   bool _isMonitoring = false;
//   bool _isNotificationAccessEnabled = false;
//   bool _isWifiMonitoring = false;
//   bool _isSoundEnabled = true;
//   int _threatsDetected = 0;
//   int _messagesScanned = 0;
//   List<Map<String, dynamic>> _suspiciousMessages = [];
//   List<Map<String, dynamic>> _suspiciousNetworks = [];
//   Map<String, dynamic> _currentWifiInfo = {};
//
//   Map<String, dynamic> _wifiSafetyStatus = {
//     'isSafe': true,
//     'ssid': 'Not connected',
//     'securityType': 'Unknown',
//     'safetyScore': 100,
//     'issues': <String>[],
//     'message': ''
//   };
//
//   List<String> _blockedEmails = [];
//   List<String> _blockedPackages = [];
//   late AnimationController _animationController;
//   late Animation<double> _radarAnimation;
//
//   @override
//   void initState() {
//     super.initState();
//     _initApp();
//     _setupMethodChannel();
//     _setupTestMessages();
//     _requestPermissions();
//     _getCurrentWifiInfo();
//     _initTts();
//     _loadBlockedItems();
//
//     _animationController = AnimationController(
//       vsync: this,
//       duration: Duration(seconds: 3),
//     )..repeat();
//
//     _radarAnimation = Tween<double>(begin: 0, end: 2 * pi).animate(
//       CurvedAnimation(parent: _animationController, curve: Curves.easeInOut),
//     );
//   }
//
//   @override
//   void dispose() {
//     _animationController.dispose();
//     flutterTts.stop();
//     super.dispose();
//   }
//
//   // UPDATED TTS FUNCTION WITH URDU WARNINGS
//   Future<void> _initTts() async {
//     await flutterTts.setLanguage("ur-PK");
//     await flutterTts.setSpeechRate(0.4);
//     await flutterTts.setVolume(1.0);
//     await flutterTts.setPitch(1.0);
//   }
//
//   // FULL URDU VOICE WARNINGS
//   Future<void> _speakWarning(String type) async {
//     if (!_isSoundEnabled) return;
//
//     String message = "";
//     switch (type) {
//       case 'whatsapp':
//         message = "خطرہ! واٹسپ پیغام خطرہ ہے!";
//         break;
//       case 'email':
//         message = "خطرہ! نقصان دہ ای میل ملی ہے!";
//         break;
//       case 'wifi':
//         message = "خطرہ! یہ وائی فائی بالکل خطرناک ہے! فوراً ڈس کنیکٹ کریں!";
//         break;
//       default:
//         message = "خطرہ دریافت ہوا ہے!";
//     }
//     await flutterTts.speak(message);
//   }
//
//   Future<void> _initApp() async => await _checkNotificationAccess();
//
//   Future<void> _checkNotificationAccess() async {
//     try {
//       final bool isEnabled = await platform.invokeMethod('isNotificationAccessEnabled') ?? false;
//       setState(() => _isNotificationAccessEnabled = isEnabled);
//     } catch (e) {
//       print("Notification access check failed: $e");
//     }
//   }
//
//   Future<void> _requestPermissions() async {
//     await Permission.notification.request();
//     await platform.invokeMethod('requestPermissions');
//   }
//
//   Future<void> _loadBlockedItems() async {
//     try {
//       final result = await platform.invokeMethod('getBlockedItems');
//       setState(() {
//         _blockedEmails = List<String>.from(result['emails'] ?? []);
//         _blockedPackages = List<String>.from(result['packages'] ?? []);
//       });
//     } catch (e) {
//       print("Failed to load blocked items: $e");
//     }
//   }
//
//   // CRITICAL FIX - UPDATED METHOD CHANNEL HANDLER
//   void _setupMethodChannel() {
//     platform.setMethodCallHandler((call) async {
//       print("FROM ANDROID → ${call.method}: ${call.arguments}");
//
//       switch (call.method) {
//         case 'onNotificationReceived':
//           _handleNotification(call.arguments);
//           break;
//
//         case 'onEmailSpamDetected':
//           if (call.arguments is Map) {
//             _handleEmailSpam(call.arguments);
//           }
//           break;
//
//         case 'onMessageSpamDetected':
//           if (call.arguments is Map) {
//             _handleMessageSpam(call.arguments);
//             setState(() => _threatsDetected++);
//           }
//           break;
//
//         case 'onWifiSafetyChecked':
//           if (call.arguments is Map) {
//             _handleWifiSafetyChecked(call.arguments);
//           }
//           break;
//
//         case 'onWifiScanResults':
//           _handleWifiScanResults(call.arguments);
//           break;
//
//         case 'onMessageScanned':
//           setState(() => _messagesScanned++);
//           break;
//
//         case 'onSpamBlocked':
//           final isEmail = call.arguments['isEmail'] ?? false;
//           final identifier = call.arguments['identifier'] ?? '';
//           _showSnackbar('${isEmail ? 'Email' : 'Package'} blocked: $identifier');
//           _loadBlockedItems();
//           break;
//
//         case 'onPermissionsGranted':
//           _showSnackbar('All permissions granted');
//           break;
//
//         case 'onPermissionsDenied':
//           _showSnackbar('Some permissions were denied');
//           break;
//
//         case 'onNotificationAccessRequired':
//           _showSnackbar('Please enable notification access in settings');
//           break;
//       }
//     });
//   }
//
//   void _handleNotification(dynamic data) {
//     final String message = data['message'] ?? '';
//     final String title = data['title'] ?? '';
//     final bool isEmail = data['isEmail'] ?? false;
//     print(isEmail ? "Email: $title" : "WhatsApp: $title - $message");
//   }
//
//   void _handleEmailSpam(dynamic data) {
//     final String sender = data['sender'] ?? '';
//     final String subject = data['subject'] ?? '';
//     final String body = data['body'] ?? '';
//
//     setState(() {
//       _threatsDetected++;
//       _suspiciousMessages.add({
//         'title': subject,
//         'message': 'From: $sender\n${body.length > 100 ? body.substring(0, 100) + '...' : body}',
//         'time': DateTime.now(),
//         'type': 'Email',
//         'spamScore': data['spamScore'] ?? 9,
//         'keywords': data['detectedKeywords'] ?? [],
//         'sender': sender,
//       });
//     });
//
//     _showAlertDialog('EMAIL SPAM DETECTED!', 'From: $sender\nSubject: $subject\n\n$body');
//     _speakWarning('email');
//   }
//
//   void _handleMessageSpam(dynamic data) {
//     final String title = data['title'] ?? 'Unknown';
//     final String message = data['message'] ?? '';
//
//     setState(() {
//       _threatsDetected++;
//       _suspiciousMessages.add({
//         'title': 'WhatsApp: $title',
//         'message': message,
//         'time': DateTime.now(),
//         'type': 'WhatsApp',
//         'spamScore': data['spamScore'] ?? 8,
//         'keywords': data['detectedKeywords'] ?? [],
//       });
//     });
//
//     _showAlertDialog('WHATSAPP SPAM DETECTED!', 'Message: $message');
//     _speakWarning('whatsapp');
//   }
//
//   void _handleWifiSafetyChecked(dynamic data) {
//     setState(() {
//       _wifiSafetyStatus = {
//         'isSafe': data['isSafe'] ?? true,
//         'ssid': data['ssid'] ?? 'Unknown',
//         'securityType': data['securityType'] ?? 'Unknown',
//         'safetyScore': data['safetyScore'] ?? 100,
//         'issues': List<String>.from(data['issues'] ?? []),
//         'message': data['message'] ?? ''
//       };
//     });
//
//     if (!(data['isSafe'] as bool)) {
//       setState(() => _threatsDetected++);
//       _showAlertDialog(
//         'UNSAFE WIFI DETECTED!',
//         'نیٹ ورک: ${_wifiSafetyStatus['ssid']}\n'
//             'سیکیورٹی: ${_wifiSafetyStatus['securityType']}\n'
//             'سیفٹی سکور: ${_wifiSafetyStatus['safetyScore']}/100\n\n'
//             'مسائل:\n${_wifiSafetyStatus['issues'].join('\n')}\n\n'
//             'فوراً ڈس کنیکٹ کریں!',
//       );
//       _speakWarning('wifi');
//     }
//   }
//
//   void _handleWifiScanResults(dynamic data) {
//     final List<dynamic> networks = data['suspiciousNetworks'] ?? [];
//     setState(() => _suspiciousNetworks = List<Map<String, dynamic>>.from(networks));
//     if (networks.isNotEmpty) {
//       _showAlertDialog('SUSPICIOUS WIFI DETECTED!', 'Found ${networks.length} dangerous networks nearby!');
//     }
//   }
//
//   Future<void> _getCurrentWifiInfo() async {
//     try {
//       final result = await platform.invokeMethod('getCurrentWifiInfo');
//       setState(() => _currentWifiInfo = Map<String, dynamic>.from(result));
//     } catch (e) {
//       setState(() => _currentWifiInfo = {'ssid': 'Error', 'isConnected': false});
//     }
//   }
//
//   void _showAlertDialog(String title, String message) {
//     showDialog(
//       context: context,
//       barrierDismissible: false,
//       builder: (_) => AlertDialog(
//         backgroundColor: Color(0xFF1D1F33),
//         shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
//         title: Text(title, style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
//         content: SingleChildScrollView(child: Text(message, style: TextStyle(color: Colors.white70))),
//         actions: [
//           TextButton(
//             onPressed: () => Navigator.pop(context),
//             child: Text('OK', style: TextStyle(color: Colors.red, fontSize: 18)),
//           ),
//         ],
//       ),
//     );
//   }
//
//   void _showSnackbar(String message) {
//     ScaffoldMessenger.of(context).showSnackBar(
//       SnackBar(
//           content: Text(message),
//           backgroundColor: Color(0xFF1D1F33),
//           behavior: SnackBarBehavior.floating
//       ),
//     );
//   }
//
//   void _setupTestMessages() {
//     Future.delayed(Duration(seconds: 3), () {
//       platform.invokeMethod('checkEmailForSpam', {
//         'sender': "prize@winner.com",
//         'subject': "آپ نے 10 لاکھ روپے جیت لیے!",
//         'body': "مبارک ہو! آپ ہمارے لاتری کے فاتح ہیں۔"
//       });
//       platform.invokeMethod('checkMessageForSpam', {'message': "FREE iPhone 15! Click now"});
//     });
//   }
//
//   Future<void> _openNotificationSettings() async => await platform.invokeMethod('openNotificationSettings');
//
//   Future<void> _toggleMonitoring() async {
//     if (!_isNotificationAccessEnabled) {
//       _openNotificationSettings();
//       return;
//     }
//     setState(() => _isMonitoring = !_isMonitoring);
//     _isMonitoring
//         ? await platform.invokeMethod('startMonitoring')
//         : await platform.invokeMethod('stopMonitoring');
//   }
//
//   Future<void> _toggleWifiMonitoring() async {
//     setState(() => _isWifiMonitoring = !_isWifiMonitoring);
//     _isWifiMonitoring
//         ? await platform.invokeMethod('startWifiMonitoring')
//         : await platform.invokeMethod('stopWifiMonitoring');
//   }
//
//   // TEST FUNCTIONS
//   void _testEmailDetection() => platform.invokeMethod('checkEmailForSpam', {
//     'sender': "lottery@scam.com",
//     'subject': "CONGRATULATIONS! You Won \$1,000,000!",
//     'body': "Claim your prize now!"
//   });
//
//   void _testMessageDetection() {
//     ["آپ نے انعام جیت لیا", "Free iPhone", "Bank alert"].forEach((msg) {
//       platform.invokeMethod('checkMessageForSpam', {'message': msg});
//     });
//   }
//
//   // UI BUILDING FUNCTIONS
//   Widget _buildFloatingButton(String text, Color color, IconData icon, Function onPressed) {
//     return Container(
//       width: 80,
//       height: 32,
//       decoration: BoxDecoration(
//         color: color.withOpacity(0.9),
//         borderRadius: BorderRadius.circular(16),
//         boxShadow: [
//           BoxShadow(
//             color: color.withOpacity(0.5),
//             blurRadius: 10,
//             offset: Offset(0, 3),
//           ),
//         ],
//       ),
//       child: TextButton.icon(
//         onPressed: () => onPressed(),
//         icon: Icon(icon, color: Colors.white, size: 14),
//         label: Text(
//           text,
//           style: TextStyle(
//             color: Colors.white,
//             fontSize: 10,
//             fontWeight: FontWeight.bold,
//           ),
//         ),
//         style: TextButton.styleFrom(
//           padding: EdgeInsets.symmetric(horizontal: 8),
//           shape: RoundedRectangleBorder(
//             borderRadius: BorderRadius.circular(16),
//           ),
//         ),
//       ),
//     );
//   }
//
//   Widget _buildThreatsButton() {
//     return Container(
//       width: 80,
//       height: 32,
//       decoration: BoxDecoration(
//         color: _threatsDetected > 0 ? Colors.red.withOpacity(0.9) : Colors.orange.withOpacity(0.9),
//         borderRadius: BorderRadius.circular(16),
//         boxShadow: [
//           BoxShadow(
//             color: (_threatsDetected > 0 ? Colors.red : Colors.orange).withOpacity(0.5),
//             blurRadius: 10,
//             offset: Offset(0, 3),
//           ),
//         ],
//       ),
//       child: TextButton.icon(
//         onPressed: _showThreatHistory,
//         icon: Icon(Icons.warning, color: Colors.white, size: 14),
//         label: Text(
//           '$_threatsDetected',
//           style: TextStyle(
//             color: Colors.white,
//             fontSize: 12,
//             fontWeight: FontWeight.bold,
//           ),
//         ),
//         style: TextButton.styleFrom(
//           padding: EdgeInsets.symmetric(horizontal: 8),
//           shape: RoundedRectangleBorder(
//             borderRadius: BorderRadius.circular(16),
//           ),
//         ),
//       ),
//     );
//   }
//
//   Widget _buildThreatDot(Color color, double size) {
//     return Container(
//       width: size,
//       height: size,
//       decoration: BoxDecoration(
//         color: color,
//         shape: BoxShape.circle,
//         boxShadow: [
//           BoxShadow(
//             color: color.withOpacity(0.5),
//             blurRadius: 8,
//             spreadRadius: 2,
//           ),
//         ],
//       ),
//     );
//   }
//
//   List<Widget> _buildCompactFeatureCards() {
//     final features = [
//       {
//         'icon': Icons.chat,
//         'title': 'WhatsApp',
//         'enabled': _isNotificationAccessEnabled,
//         'color': Colors.green,
//       },
//       {
//         'icon': Icons.email,
//         'title': 'Email',
//         'enabled': true,
//         'color': Colors.purple,
//       },
//       {
//         'icon': Icons.wifi,
//         'title': 'WiFi',
//         'enabled': _isWifiMonitoring,
//         'color': Colors.blue,
//         'action': Switch(
//           value: _isWifiMonitoring,
//           onChanged: (value) => _toggleWifiMonitoring(),
//           activeColor: Colors.blue,
//           materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
//         ),
//       },
//       {
//         'icon': Icons.volume_up,
//         'title': 'Voice',
//         'enabled': _isSoundEnabled,
//         'color': Colors.orange,
//         'action': Switch(
//           value: _isSoundEnabled,
//           onChanged: (value) => setState(() => _isSoundEnabled = value),
//           activeColor: Colors.orange,
//           materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
//         ),
//       },
//     ];
//
//     return features.map((feature) {
//       return Container(
//         margin: EdgeInsets.only(bottom: 10),
//         decoration: BoxDecoration(
//           color: Color(0xFF1D1F33),
//           borderRadius: BorderRadius.circular(12),
//           border: Border.all(color: Colors.white.withOpacity(0.05)),
//         ),
//         child: ListTile(
//           contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 4),
//           leading: Container(
//             padding: EdgeInsets.all(8),
//             decoration: BoxDecoration(
//               color: (feature['color'] as Color).withOpacity(0.2),
//               shape: BoxShape.circle,
//             ),
//             child: Icon(feature['icon'] as IconData,
//                 color: feature['color'] as Color, size: 18),
//           ),
//           title: Text(
//             feature['title'] as String,
//             style: TextStyle(
//               fontWeight: FontWeight.w500,
//               color: Colors.white,
//               fontSize: 14,
//             ),
//           ),
//           trailing: feature['action'] as Widget? ?? Container(
//             width: 8,
//             height: 8,
//             decoration: BoxDecoration(
//               color: (feature['enabled'] as bool) ? Colors.green : Colors.grey,
//               shape: BoxShape.circle,
//             ),
//           ),
//         ),
//       );
//     }).toList();
//   }
//
//   // DIALOGS AND SETTINGS
//   void _showSettings() {
//     showMenu(
//       context: context,
//       position: RelativeRect.fromLTRB(MediaQuery.of(context).size.width - 200, 100, 0, 0),
//       items: [
//         PopupMenuItem(
//           child: ListTile(
//             leading: Icon(Icons.settings, color: Colors.white70),
//             title: Text('Settings', style: TextStyle(color: Colors.white)),
//             onTap: () {
//               Navigator.pop(context);
//               _showSettingsDialog();
//             },
//           ),
//         ),
//         PopupMenuItem(
//           child: ListTile(
//             leading: Icon(Icons.history, color: Colors.white70),
//             title: Text('Threat History', style: TextStyle(color: Colors.white)),
//             onTap: () {
//               Navigator.pop(context);
//               _showThreatHistory();
//             },
//           ),
//         ),
//         PopupMenuItem(
//           child: ListTile(
//             leading: Icon(Icons.block, color: Colors.white70),
//             title: Text('Blocked Items', style: TextStyle(color: Colors.white)),
//             onTap: () {
//               Navigator.pop(context);
//               _showBlockedItems();
//             },
//           ),
//         ),
//         PopupMenuItem(
//           child: ListTile(
//             leading: Icon(Icons.info, color: Colors.white70),
//             title: Text('About', style: TextStyle(color: Colors.white)),
//             onTap: () {
//               Navigator.pop(context);
//               _showAboutDialog();
//             },
//           ),
//         ),
//       ],
//     );
//   }
//
//   void _showThreatHistory() {
//     showDialog(
//       context: context,
//       builder: (context) => Dialog(
//         backgroundColor: Color(0xFF1D1F33),
//         shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
//         child: Container(
//           width: double.maxFinite,
//           padding: EdgeInsets.all(24),
//           child: Column(
//             mainAxisSize: MainAxisSize.min,
//             crossAxisAlignment: CrossAxisAlignment.start,
//             children: [
//               Row(
//                 mainAxisAlignment: MainAxisAlignment.spaceBetween,
//                 children: [
//                   Text('Threat History', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white)),
//                   IconButton(
//                     icon: Icon(Icons.close, color: Colors.white70),
//                     onPressed: () => Navigator.pop(context),
//                   ),
//                 ],
//               ),
//               SizedBox(height: 8),
//               Text('$_threatsDetected threats detected', style: TextStyle(color: Colors.white70)),
//               SizedBox(height: 20),
//               _suspiciousMessages.isEmpty
//                   ? Container(
//                 padding: EdgeInsets.symmetric(vertical: 40),
//                 child: Column(
//                   children: [
//                     Icon(Icons.security, size: 64, color: Colors.green),
//                     SizedBox(height: 16),
//                     Text('No threats detected!', style: TextStyle(fontSize: 18, color: Colors.white70)),
//                     SizedBox(height: 8),
//                     Text('Your device is secure', style: TextStyle(fontSize: 14, color: Colors.white54)),
//                   ],
//                 ),
//               )
//                   : Container(
//                 height: 400,
//                 child: ListView.builder(
//                   itemCount: _suspiciousMessages.length,
//                   itemBuilder: (context, index) {
//                     final threat = _suspiciousMessages[index];
//                     Color severityColor = Colors.grey;
//                     if (threat['spamScore'] > 7) severityColor = Colors.red;
//                     if (threat['spamScore'] > 4) severityColor = Colors.orange;
//
//                     return Container(
//                       margin: EdgeInsets.only(bottom: 12),
//                       decoration: BoxDecoration(
//                         gradient: LinearGradient(
//                           colors: [Colors.black.withOpacity(0.3), Colors.black.withOpacity(0.1)],
//                           begin: Alignment.topLeft,
//                           end: Alignment.bottomRight,
//                         ),
//                         borderRadius: BorderRadius.circular(16),
//                         border: Border.all(color: severityColor.withOpacity(0.3), width: 1),
//                       ),
//                       child: ListTile(
//                         leading: Container(
//                           padding: EdgeInsets.all(8),
//                           decoration: BoxDecoration(
//                             color: severityColor.withOpacity(0.2),
//                             shape: BoxShape.circle,
//                           ),
//                           child: Icon(Icons.warning, color: severityColor, size: 24),
//                         ),
//                         title: Text(threat['title'] ?? 'Unknown', style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w600)),
//                         subtitle: Column(
//                           crossAxisAlignment: CrossAxisAlignment.start,
//                           children: [
//                             SizedBox(height: 4),
//                             Text(threat['message'], maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(color: Colors.white70, fontSize: 14)),
//                             SizedBox(height: 6),
//                             Row(
//                               children: [
//                                 Container(
//                                   padding: EdgeInsets.symmetric(horizontal: 8, vertical: 4),
//                                   decoration: BoxDecoration(
//                                     color: severityColor.withOpacity(0.2),
//                                     borderRadius: BorderRadius.circular(8),
//                                   ),
//                                   child: Text('Score: ${threat['spamScore']}/10', style: TextStyle(color: severityColor, fontSize: 12, fontWeight: FontWeight.bold)),
//                                 ),
//                                 SizedBox(width: 8),
//                                 Text(threat['type'], style: TextStyle(color: Colors.white54, fontSize: 12)),
//                               ],
//                             ),
//                           ],
//                         ),
//                       ),
//                     );
//                   },
//                 ),
//               ),
//               SizedBox(height: 20),
//               if (_suspiciousMessages.isNotEmpty)
//                 Row(
//                   mainAxisAlignment: MainAxisAlignment.end,
//                   children: [
//                     TextButton(
//                       onPressed: () {
//                         setState(() {
//                           _suspiciousMessages.clear();
//                           _threatsDetected = 0;
//                         });
//                         Navigator.pop(context);
//                         _showSnackbar('Threat history cleared');
//                       },
//                       child: Text('Clear History', style: TextStyle(color: Colors.red)),
//                     ),
//                   ],
//                 ),
//             ],
//           ),
//         ),
//       ),
//     );
//   }
//
//   void _showBlockedItems() {
//     showDialog(
//       context: context,
//       builder: (context) => Dialog(
//         backgroundColor: Color(0xFF1D1F33),
//         shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
//         child: Container(
//           padding: EdgeInsets.all(20),
//           child: Column(
//             mainAxisSize: MainAxisSize.min,
//             crossAxisAlignment: CrossAxisAlignment.start,
//             children: [
//               Text('Blocked Items', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white)),
//               SizedBox(height: 16),
//               Text('Blocked Emails (${_blockedEmails.length})', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.bold)),
//               SizedBox(height: 8),
//               _blockedEmails.isEmpty
//                   ? Text('No blocked emails', style: TextStyle(color: Colors.white54))
//                   : Container(
//                 height: 100,
//                 child: ListView.builder(
//                   itemCount: _blockedEmails.length,
//                   itemBuilder: (context, index) => ListTile(
//                     leading: Icon(Icons.email, color: Colors.red),
//                     title: Text(_blockedEmails[index], style: TextStyle(color: Colors.white)),
//                     trailing: IconButton(
//                       icon: Icon(Icons.delete, color: Colors.red),
//                       onPressed: () => _unblockEmail(_blockedEmails[index]),
//                     ),
//                   ),
//                 ),
//               ),
//               SizedBox(height: 16),
//               Text('Blocked Packages (${_blockedPackages.length})', style: TextStyle(color: Colors.white70, fontWeight: FontWeight.bold)),
//               SizedBox(height: 8),
//               _blockedPackages.isEmpty
//                   ? Text('No blocked packages', style: TextStyle(color: Colors.white54))
//                   : Container(
//                 height: 100,
//                 child: ListView.builder(
//                   itemCount: _blockedPackages.length,
//                   itemBuilder: (context, index) => ListTile(
//                     leading: Icon(Icons.apps, color: Colors.orange),
//                     title: Text(_blockedPackages[index], style: TextStyle(color: Colors.white)),
//                     trailing: IconButton(
//                       icon: Icon(Icons.delete, color: Colors.red),
//                       onPressed: () => _unblockPackage(_blockedPackages[index]),
//                     ),
//                   ),
//                 ),
//               ),
//               SizedBox(height: 20),
//               Row(
//                 mainAxisAlignment: MainAxisAlignment.end,
//                 children: [
//                   TextButton(
//                     onPressed: () => Navigator.pop(context),
//                     child: Text('Close', style: TextStyle(color: Colors.white70)),
//                   ),
//                 ],
//               ),
//             ],
//           ),
//         ),
//       ),
//     );
//   }
//
//   Future<void> _unblockEmail(String email) async {
//     try {
//       await platform.invokeMethod('unblockEmail', {'email': email});
//       _showSnackbar('Email unblocked: $email');
//       _loadBlockedItems();
//     } catch (e) {
//       _showSnackbar('Failed to unblock: $e');
//     }
//   }
//
//   Future<void> _unblockPackage(String package) async {
//     try {
//       await platform.invokeMethod('unblockPackage', {'package': package});
//       _showSnackbar('Package unblocked: $package');
//       _loadBlockedItems();
//     } catch (e) {
//       _showSnackbar('Failed to unblock: $e');
//     }
//   }
//
//   void _showSettingsDialog() {
//     showDialog(
//       context: context,
//       builder: (context) => Dialog(
//         backgroundColor: Color(0xFF1D1F33),
//         shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
//         child: Container(
//           padding: EdgeInsets.all(20),
//           child: Column(
//             mainAxisSize: MainAxisSize.min,
//             crossAxisAlignment: CrossAxisAlignment.start,
//             children: [
//               Text('Settings', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white)),
//               SizedBox(height: 20),
//               _buildSettingsOption('Sound Alerts', _isSoundEnabled, (value) => setState(() => _isSoundEnabled = value)),
//               SizedBox(height: 15),
//               _buildSettingsOption('Auto-Update', true, (value) {}),
//               SizedBox(height: 20),
//               Row(
//                 mainAxisAlignment: MainAxisAlignment.end,
//                 children: [
//                   TextButton(
//                     onPressed: () => Navigator.pop(context),
//                     child: Text('Close', style: TextStyle(color: Colors.white70)),
//                   ),
//                 ],
//               ),
//             ],
//           ),
//         ),
//       ),
//     );
//   }
//
//   void _showAboutDialog() {
//     showDialog(
//       context: context,
//       builder: (context) => Dialog(
//         backgroundColor: Color(0xFF1D1F33),
//         shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
//         child: Container(
//           padding: EdgeInsets.all(20),
//           child: Column(
//             mainAxisSize: MainAxisSize.min,
//             children: [
//               Icon(Icons.security, size: 50, color: Colors.blue),
//               SizedBox(height: 16),
//               Text('Deception Detection System', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white)),
//               SizedBox(height: 8),
//               Text('Version 1.0.0', style: TextStyle(color: Colors.white70)),
//               SizedBox(height: 16),
//               Text(
//                 'Protect your device from spam and threats',
//                 textAlign: TextAlign.center,
//                 style: TextStyle(color: Colors.white70),
//               ),
//               SizedBox(height: 20),
//               ElevatedButton(
//                 onPressed: () => Navigator.pop(context),
//                 child: Text('OK'),
//               ),
//             ],
//           ),
//         ),
//       ),
//     );
//   }
//
//   Widget _buildSettingsOption(String title, bool value, Function(bool) onChanged) {
//     return Row(
//       mainAxisAlignment: MainAxisAlignment.spaceBetween,
//       children: [
//         Text(title, style: TextStyle(color: Colors.white, fontSize: 14)),
//         Switch(
//           value: value,
//           onChanged: onChanged,
//           activeColor: Colors.blue,
//           materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
//         ),
//       ],
//     );
//   }
//
//   @override
//   Widget build(BuildContext context) {
//     return Scaffold(
//       backgroundColor: Theme.of(context).colorScheme.background,
//       body: CustomScrollView(
//         slivers: [
//           SliverAppBar(
//             backgroundColor: Colors.transparent,
//             elevation: 0,
//             floating: true,
//             pinned: true,
//             expandedHeight: 80,
//             collapsedHeight: 60,
//             flexibleSpace: FlexibleSpaceBar(
//               background: Container(
//                 decoration: BoxDecoration(
//                   gradient: LinearGradient(
//                     colors: [Color(0xFF1D1F33), Color(0xFF0A0E21)],
//                     begin: Alignment.topCenter,
//                     end: Alignment.bottomCenter,
//                   ),
//                 ),
//               ),
//               title: Row(
//                 children: [
//                   Container(
//                     padding: EdgeInsets.all(6),
//                     decoration: BoxDecoration(
//                       gradient: LinearGradient(colors: [Colors.blue, Colors.blueAccent]),
//                       shape: BoxShape.circle,
//                     ),
//                     child: Icon(Icons.security, color: Colors.white, size: 20),
//                   ),
//                   SizedBox(width: 8),
//                   Text('Deception Detection System', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.white)),
//                 ],
//               ),
//               centerTitle: false,
//               titlePadding: EdgeInsets.only(left: 16, bottom: 16),
//             ),
//             actions: [
//               IconButton(icon: Icon(Icons.history), onPressed: _showThreatHistory),
//               IconButton(icon: Icon(Icons.block), onPressed: _showBlockedItems),
//               IconButton(icon: Icon(Icons.more_vert), onPressed: _showSettings),
//             ],
//           ),
//
//           SliverToBoxAdapter(
//             child: Padding(
//               padding: const EdgeInsets.all(20.0),
//               child: Column(
//                 crossAxisAlignment: CrossAxisAlignment.start,
//                 children: [
//                   Container(
//                     height: 280,
//                     child: Stack(
//                       alignment: Alignment.center,
//                       children: [
//                         Container(
//                           width: double.infinity,
//                           height: 250,
//                           decoration: BoxDecoration(
//                             gradient: LinearGradient(
//                               colors: [Color(0xFF1D1F33), Color(0xFF25273C)],
//                               begin: Alignment.topCenter,
//                               end: Alignment.bottomCenter,
//                             ),
//                             borderRadius: BorderRadius.circular(24),
//                             boxShadow: [
//                               BoxShadow(
//                                 color: Colors.black.withOpacity(0.3),
//                                 blurRadius: 20,
//                                 offset: Offset(0, 10),
//                               ),
//                             ],
//                           ),
//                         ),
//
//                         Positioned(
//                           top: 25,
//                           child: Container(
//                             width: 200,
//                             height: 200,
//                             child: Stack(
//                               alignment: Alignment.center,
//                               children: [
//                                 Container(
//                                   width: 200,
//                                   height: 200,
//                                   decoration: BoxDecoration(
//                                     shape: BoxShape.circle,
//                                     gradient: RadialGradient(
//                                       colors: [
//                                         Colors.blue.withOpacity(0.1),
//                                         Colors.transparent,
//                                       ],
//                                     ),
//                                   ),
//                                 ),
//
//                                 ...List.generate(3, (index) {
//                                   final size = 180.0 - (index * 40.0);
//                                   return Container(
//                                     width: size,
//                                     height: size,
//                                     decoration: BoxDecoration(
//                                       shape: BoxShape.circle,
//                                       border: Border.all(
//                                         color: Colors.blue.withOpacity(0.2 + (index * 0.1)),
//                                         width: 1,
//                                       ),
//                                     ),
//                                   );
//                                 }),
//
//                                 Positioned(
//                                   top: 40,
//                                   left: 90,
//                                   child: _buildThreatDot(Colors.red, 8),
//                                 ),
//                                 Positioned(
//                                   top: 80,
//                                   right: 70,
//                                   child: _buildThreatDot(Colors.blue, 6),
//                                 ),
//                                 Positioned(
//                                   bottom: 70,
//                                   left: 70,
//                                   child: _buildThreatDot(Colors.green, 4),
//                                 ),
//
//                                 AnimatedBuilder(
//                                   animation: _radarAnimation,
//                                   builder: (context, child) {
//                                     return Transform.rotate(
//                                       angle: _radarAnimation.value,
//                                       child: CustomPaint(
//                                         painter: EnhancedRadarPainter(_isMonitoring),
//                                         size: Size(180, 180),
//                                       ),
//                                     );
//                                   },
//                                 ),
//
//                                 Container(
//                                   width: 20,
//                                   height: 20,
//                                   decoration: BoxDecoration(
//                                     shape: BoxShape.circle,
//                                     gradient: RadialGradient(
//                                       colors: [
//                                         Colors.white,
//                                         Colors.blue.shade300,
//                                       ],
//                                     ),
//                                     boxShadow: [
//                                       BoxShadow(
//                                         color: Colors.blue.withOpacity(0.5),
//                                         blurRadius: 15,
//                                         spreadRadius: 2,
//                                       ),
//                                     ],
//                                   ),
//                                 ),
//                               ],
//                             ),
//                           ),
//                         ),
//
//                         Positioned(
//                           top: 10,
//                           left: 20,
//                           child: _buildFloatingButton(
//                             'ACTIVE',
//                             _isMonitoring ? Colors.green : Colors.grey,
//                             Icons.play_arrow,
//                             _toggleMonitoring,
//                           ),
//                         ),
//
//                         Positioned(
//                           top: 10,
//                           right: 20,
//                           child: _buildFloatingButton(
//                             'INACTIVE',
//                             _isMonitoring ? Colors.grey : Colors.red,
//                             Icons.stop,
//                             _toggleMonitoring,
//                           ),
//                         ),
//
//                         Positioned(
//                           bottom: 10,
//                           left: MediaQuery.of(context).size.width / 2 - 40,
//                           child: _buildThreatsButton(),
//                         ),
//
//                         Positioned(
//                           bottom: 15,
//                           child: Container(
//                             padding: EdgeInsets.symmetric(horizontal: 12, vertical: 6),
//                             decoration: BoxDecoration(
//                               color: Colors.black.withOpacity(0.5),
//                               borderRadius: BorderRadius.circular(15),
//                               border: Border.all(color: Colors.white.withOpacity(0.1)),
//                             ),
//                             child: Column(
//                               children: [
//                                 Text(
//                                   _isMonitoring ? 'SCANNING' : 'IDLE',
//                                   style: TextStyle(
//                                     color: _isMonitoring ? Colors.green : Colors.grey,
//                                     fontWeight: FontWeight.bold,
//                                     fontSize: 10,
//                                     letterSpacing: 1.2,
//                                   ),
//                                 ),
//                                 SizedBox(height: 2),
//                                 Text(
//                                   '$_messagesScanned scanned',
//                                   style: TextStyle(
//                                     color: Colors.white70,
//                                     fontSize: 8,
//                                   ),
//                                 ),
//                               ],
//                             ),
//                           ),
//                         ),
//                       ],
//                     ),
//                   ),
//
//                   SizedBox(height: 25),
//
//                   Row(
//                     mainAxisAlignment: MainAxisAlignment.spaceBetween,
//                     children: [
//                       Text(
//                         'Features',
//                         style: TextStyle(
//                           fontSize: 18,
//                           fontWeight: FontWeight.bold,
//                           color: Colors.white,
//                         ),
//                       ),
//                       Container(
//                         padding: EdgeInsets.symmetric(horizontal: 10, vertical: 4),
//                         decoration: BoxDecoration(
//                           color: Colors.blue.withOpacity(0.2),
//                           borderRadius: BorderRadius.circular(10),
//                           border: Border.all(color: Colors.blue.withOpacity(0.3)),
//                         ),
//                         child: Text(
//                           '${_isNotificationAccessEnabled ? 'Active' : 'Setup'}',
//                           style: TextStyle(
//                             color: _isNotificationAccessEnabled ? Colors.green : Colors.orange,
//                             fontSize: 10,
//                             fontWeight: FontWeight.bold,
//                           ),
//                         ),
//                       ),
//                     ],
//                   ),
//
//                   SizedBox(height: 15),
//
//                   ..._buildCompactFeatureCards(),
//
//                   SizedBox(height: 40),
//                 ],
//               ),
//             ),
//           ),
//         ],
//       ),
//     );
//   }
// }
//
// class EnhancedRadarPainter extends CustomPainter {
//   final bool isMonitoring;
//
//   EnhancedRadarPainter(this.isMonitoring);
//
//   @override
//   void paint(Canvas canvas, Size size) {
//     final center = Offset(size.width / 2, size.height / 2);
//     final radius = size.width / 2;
//
//     for (int i = 0; i < 3; i++) {
//       final angle = pi / 6 * i;
//       final linePaint = Paint()
//         ..color = isMonitoring
//             ? Colors.green.withOpacity(0.6 - (i * 0.15))
//             : Colors.blue.withOpacity(0.4 - (i * 0.1))
//         ..style = PaintingStyle.stroke
//         ..strokeWidth = 1.5
//         ..strokeCap = StrokeCap.round;
//
//       final x = center.dx + radius * cos(angle);
//       final y = center.dy + radius * sin(angle);
//       canvas.drawLine(center, Offset(x, y), linePaint);
//     }
//
//     final sweepPaint = Paint()
//       ..shader = SweepGradient(
//         colors: isMonitoring
//             ? [
//           Colors.green.withOpacity(0.0),
//           Colors.green.withOpacity(0.6),
//           Colors.green.withOpacity(0.0),
//         ]
//             : [
//           Colors.blue.withOpacity(0.0),
//           Colors.blue.withOpacity(0.4),
//           Colors.blue.withOpacity(0.0),
//         ],
//         stops: [0.0, 0.5, 1.0],
//       ).createShader(Rect.fromCircle(center: center, radius: radius))
//       ..style = PaintingStyle.fill;
//
//     canvas.drawCircle(center, radius, sweepPaint);
//   }
//
//   @override
//   bool shouldRepaint(covariant CustomPainter oldDelegate) {
//     return true;
//   }
// }
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:math';

void main() {
  runApp(CyberShieldApp());
}

class CyberShieldApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CyberGuard Pro',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Color(0xFF6366F1),
          brightness: Brightness.dark,
          background: Color(0xFF0F172A),
          surface: Color(0xFF1E293B),
          primary: Color(0xFF6366F1),
          secondary: Color(0xFF06B6D4),
          tertiary: Color(0xFFEF4444),
          surfaceTint: Color(0xFF6366F1),
        ),
        fontFamily: 'Inter',
        useMaterial3: true,
      ),
      home: HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  @override
  _HomeScreenState createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with TickerProviderStateMixin {
  static const platform = MethodChannel('cyber_shield/channel');
  final FlutterTts flutterTts = FlutterTts();

  bool _isMonitoring = false;
  bool _isNotificationAccessEnabled = false;
  bool _isWifiMonitoring = false;
  bool _isSoundEnabled = true;
  int _threatsDetected = 0;
  int _messagesScanned = 0;
  List<Map<String, dynamic>> _suspiciousMessages = [];
  List<Map<String, dynamic>> _suspiciousNetworks = [];

  // Animation Controllers
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    _initApp();
    _setupMethodChannel();
    _setupTestMessages();
    _requestPermissions();
    _initTts();
    _initAnimations();
  }

  void _initAnimations() {
    _pulseController = AnimationController(
      vsync: this,
      duration: Duration(milliseconds: 2000),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 0.95, end: 1.05).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    flutterTts.stop();
    super.dispose();
  }

  Future<void> _initTts() async {
    await flutterTts.setLanguage("en-US");
    await flutterTts.setSpeechRate(0.5);
    await flutterTts.setVolume(1.0);
    await flutterTts.setPitch(0.9);
  }

  Future<void> _speakWarning(String type) async {
    if (!_isSoundEnabled) return;

    String message = "";
    switch (type) {
      case 'whatsapp':
        message = "Security Alert! Suspicious WhatsApp message detected!";
        break;
      case 'email':
        message = "Security Alert! Potential spam email detected!";
        break;
      case 'wifi':
        message = "Security Alert! Unsafe Wi-Fi network detected!";
        break;
      default:
        message = "Security Alert! Potential threat detected!";
    }

    await flutterTts.speak(message);
  }

  Future<void> _initApp() async => await _checkNotificationAccess();

  Future<void> _checkNotificationAccess() async {
    try {
      final bool isEnabled = await platform.invokeMethod('isNotificationAccessEnabled') ?? false;
      setState(() => _isNotificationAccessEnabled = isEnabled);
    } catch (e) {
      print("Notification access check failed: $e");
    }
  }

  Future<void> _requestPermissions() async {
    await Permission.notification.request();
    await platform.invokeMethod('requestPermissions');
  }

  void _setupMethodChannel() {
    platform.setMethodCallHandler((call) async {
      print("FROM ANDROID → ${call.method}: ${call.arguments}");

      switch (call.method) {
        case 'onNotificationReceived':
          _handleNotification(call.arguments);
          break;

        case 'onEmailSpamDetected':
          if (call.arguments is Map) {
            _handleEmailSpam(call.arguments);
          }
          break;

        case 'onMessageSpamDetected':
          if (call.arguments is Map) {
            _handleMessageSpam(call.arguments);
            setState(() => _threatsDetected++);
          }
          break;

        case 'onWifiSafetyChecked':
          if (call.arguments is Map) {
            _handleWifiSafetyChecked(call.arguments);
          }
          break;

        case 'onMessageScanned':
          setState(() => _messagesScanned++);
          break;

        case 'onSpamBlocked':
          final isEmail = call.arguments['isEmail'] ?? false;
          final identifier = call.arguments['identifier'] ?? '';
          _showSnackbar('${isEmail ? 'Email' : 'Package'} blocked: $identifier');
          break;

        case 'onPermissionsGranted':
          _showSnackbar('All permissions granted');
          break;
      }
    });
  }

  void _handleNotification(dynamic data) {
    final String message = data['message'] ?? '';
    final String title = data['title'] ?? '';
    final bool isEmail = data['isEmail'] ?? false;
    print(isEmail ? "Email: $title" : "WhatsApp: $title - $message");
  }

  void _handleEmailSpam(dynamic data) {
    final String sender = data['sender'] ?? '';
    final String subject = data['subject'] ?? '';
    final String body = data['body'] ?? '';

    setState(() {
      _threatsDetected++;
      _suspiciousMessages.add({
        'title': subject,
        'message': 'From: $sender\n${body.length > 80 ? body.substring(0, 80) + '...' : body}',
        'time': DateTime.now(),
        'type': 'Email',
        'spamScore': data['spamScore'] ?? 9,
        'sender': sender,
      });
    });

    _showAlertDialog('🚨 Email Threat Detected', 'From: $sender\nSubject: $subject');
    _speakWarning('email');
  }

  void _handleMessageSpam(dynamic data) {
    final String title = data['title'] ?? 'Unknown';
    final String message = data['message'] ?? '';

    setState(() {
      _threatsDetected++;
      _suspiciousMessages.add({
        'title': 'WhatsApp: $title',
        'message': message,
        'time': DateTime.now(),
        'type': 'WhatsApp',
        'spamScore': data['spamScore'] ?? 8,
      });
    });

    _showAlertDialog('⚠️ WhatsApp Threat Detected', 'Message: ${message.length > 100 ? message.substring(0, 100) + '...' : message}');
    _speakWarning('whatsapp');
  }

  void _handleWifiSafetyChecked(dynamic data) {
    final bool isSafe = data['isSafe'] ?? true;
    if (!isSafe) {
      setState(() => _threatsDetected++);
      final String ssid = data['ssid'] ?? 'Unknown Network';
      _showAlertDialog('🌐 Unsafe Wi-Fi Detected', 'Network: $ssid\nPlease disconnect immediately!');
      _speakWarning('wifi');
    }
  }

  void _showAlertDialog(String title, String message) {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => AlertDialog(
        backgroundColor: Color(0xFF1E293B),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: Color(0xFF6366F1).withOpacity(0.3), width: 1),
        ),
        title: Row(
          children: [
            Icon(Icons.warning_amber_rounded, color: Color(0xFFEF4444), size: 24),
            SizedBox(width: 12),
            Expanded(
              child: Text(
                title,
                style: TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                  fontSize: 18,
                ),
              ),
            ),
          ],
        ),
        content: SingleChildScrollView(
          child: Text(
            message,
            style: TextStyle(color: Colors.white70, fontSize: 14, height: 1.5),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            style: TextButton.styleFrom(
              foregroundColor: Colors.white,
              backgroundColor: Color(0xFF6366F1).withOpacity(0.1),
              padding: EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
                side: BorderSide(color: Color(0xFF6366F1).withOpacity(0.3)),
              ),
            ),
            child: Text('Dismiss', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
          ),
        ],
      ),
    );
  }

  void _showSnackbar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            Icon(Icons.check_circle, color: Color(0xFF06B6D4), size: 20),
            SizedBox(width: 10),
            Expanded(child: Text(message, style: TextStyle(fontSize: 13))),
          ],
        ),
        backgroundColor: Color(0xFF1E293B),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: BorderSide(color: Colors.white.withOpacity(0.1)),
        ),
        duration: Duration(seconds: 2),
      ),
    );
  }

  void _setupTestMessages() {
    Future.delayed(Duration(seconds: 3), () {
      platform.invokeMethod('checkEmailForSpam', {
        'sender': "prize@winner.com",
        'subject': "You've won 10 lakh rupees!",
        'body': "Congratulations! You are the winner of our lottery."
      });
      platform.invokeMethod('checkMessageForSpam', {'message': "FREE iPhone 15! Click now"});
    });
  }

  Future<void> _openNotificationSettings() async => await platform.invokeMethod('openNotificationSettings');

  Future<void> _toggleMonitoring() async {
    if (!_isNotificationAccessEnabled) {
      _openNotificationSettings();
      return;
    }
    setState(() => _isMonitoring = !_isMonitoring);
    _isMonitoring
        ? await platform.invokeMethod('startMonitoring')
        : await platform.invokeMethod('stopMonitoring');
  }

  Future<void> _toggleWifiMonitoring() async {
    setState(() => _isWifiMonitoring = !_isWifiMonitoring);
    _isWifiMonitoring
        ? await platform.invokeMethod('startWifiMonitoring')
        : await platform.invokeMethod('stopWifiMonitoring');
  }

  void _testEmailDetection() => platform.invokeMethod('checkEmailForSpam', {
    'sender': "lottery@scam.com",
    'subject': "CONGRATULATIONS! You Won \$1,000,000!",
    'body': "Claim your prize now!"
  });

  void _testMessageDetection() {
    ["You've won a prize", "Free iPhone", "Bank alert"].forEach((msg) {
      platform.invokeMethod('checkMessageForSpam', {'message': msg});
    });
  }

  Widget _buildSecurityShield() {
    return AnimatedBuilder(
      animation: _pulseAnimation,
      builder: (context, child) {
        return Transform.scale(
          scale: _pulseAnimation.value,
          child: Container(
            width: 140,
            height: 140,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: LinearGradient(
                colors: _isMonitoring
                    ? [Color(0xFF6366F1), Color(0xFF06B6D4)]
                    : [Color(0xFF475569), Color(0xFF334155)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              boxShadow: [
                BoxShadow(
                  color: (_isMonitoring ? Color(0xFF6366F1) : Color(0xFF475569)).withOpacity(0.4),
                  blurRadius: 20,
                  spreadRadius: 5,
                ),
              ],
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  Icons.security_rounded,
                  color: Colors.white,
                  size: 48,
                ),
                SizedBox(height: 8),
                Text(
                  _isMonitoring ? 'ACTIVE' : 'READY',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 1.5,
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildStatCard({
    required String title,
    required String value,
    required IconData icon,
    required Color color,
  }) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: LinearGradient(
          colors: [color.withOpacity(0.15), color.withOpacity(0.05)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        border: Border.all(color: color.withOpacity(0.2)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top row - fixed height to prevent overflow
            SizedBox(
              height: 40, // Fixed height for the top row
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: color.withOpacity(0.2),
                    ),
                    child: Icon(icon, color: color, size: 20),
                  ),
                  Container(
                    padding: EdgeInsets.symmetric(horizontal: 8, vertical: 4), // Reduced padding
                    decoration: BoxDecoration(
                      color: color.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(8), // Reduced radius
                    ),
                    child: Text(
                      'LIVE',
                      style: TextStyle(
                        color: color,
                        fontSize: 9, // Reduced font size
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.3, // Reduced letter spacing
                      ),
                    ),
                  ),
                ],
              ),
            ),
            SizedBox(height: 10), // Consistent spacing
            // Text content with proper constraints
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 11, // Smaller font
                    fontWeight: FontWeight.w500,
                    height: 1.2, // Reduced line height
                  ),
                  maxLines: 2, // Allow 2 lines if needed
                  overflow: TextOverflow.ellipsis,
                ),
                SizedBox(height: 2), // Minimal spacing
                Text(
                  value,
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 19, // Slightly smaller
                    fontWeight: FontWeight.w800,
                    height: 1.0, // Minimal line height
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFeatureCard({
    required String title,
    required String description,
    required IconData icon,
    required Color color,
    required bool enabled,
    required VoidCallback onTap,
  }) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        color: Color(0xFF1E293B),
        border: Border.all(
          color: enabled ? color.withOpacity(0.3) : Colors.transparent,
        ),
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: LinearGradient(
                      colors: enabled
                          ? [color, color.withOpacity(0.7)]
                          : [Colors.white.withOpacity(0.1), Colors.white.withOpacity(0.05)],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                    ),
                  ),
                  child: Icon(icon, color: enabled ? Colors.white : Colors.white70, size: 22),
                ),
                SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      SizedBox(height: 4),
                      Text(
                        description,
                        style: TextStyle(
                          color: Colors.white70,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                SizedBox(width: 16),
                Switch.adaptive(
                  value: enabled,
                  onChanged: (value) => onTap(),
                  activeColor: color,
                  activeTrackColor: color.withOpacity(0.3),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildQuickAction({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
    required Color color,
    bool isActive = false,
  }) {
    return Expanded(
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(16),
          child: Container(
            padding: EdgeInsets.symmetric(vertical: 20, horizontal: 8),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              gradient: LinearGradient(
                colors: isActive
                    ? [color.withOpacity(0.15), color.withOpacity(0.05)]
                    : [Color(0xFF1E293B), Color(0xFF1E293B)],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ),
              border: Border.all(
                color: isActive ? color.withOpacity(0.3) : Colors.transparent,
              ),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: LinearGradient(
                      colors: isActive
                          ? [color, color.withOpacity(0.7)]
                          : [Colors.white.withOpacity(0.1), Colors.white.withOpacity(0.05)],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                    ),
                  ),
                  child: Icon(
                    icon,
                    color: isActive ? Colors.white : Colors.white70,
                    size: 22,
                  ),
                ),
                SizedBox(height: 12),
                Text(
                  label,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: isActive ? Colors.white : Colors.white70,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Color(0xFF0F172A),
      body: SafeArea(
        child: SingleChildScrollView(
          physics: BouncingScrollPhysics(),
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'CyberGuard',
                          style: TextStyle(
                            fontSize: 32,
                            fontWeight: FontWeight.w900,
                            color: Colors.white,
                            letterSpacing: -0.5,
                          ),
                        ),
                        SizedBox(height: 4),
                        Text(
                          'Security Detection System',
                          style: TextStyle(
                            color: Color(0xFF06B6D4),
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 1,
                          ),
                        ),
                      ],
                    ),
                    Row(
                      children: [
                        IconButton(
                          onPressed: _showThreatHistory,
                          icon: Icon(Icons.history_toggle_off_rounded, color: Colors.white70, size: 24),
                        ),
                        IconButton(
                          onPressed: _showSettings,
                          icon: Icon(Icons.settings_rounded, color: Colors.white70, size: 24),
                        ),
                      ],
                    ),
                  ],
                ),
                SizedBox(height: 32),

                // Security Shield
                Center(child: _buildSecurityShield()),
                SizedBox(height: 24),

                // Status Text
                Center(
                  child: Container(
                    padding: EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: _isMonitoring
                            ? [Color(0xFF6366F1).withOpacity(0.2), Color(0xFF06B6D4).withOpacity(0.2)]
                            : [Color(0xFF475569).withOpacity(0.2), Color(0xFF334155).withOpacity(0.2)],
                      ),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          _isMonitoring ? Icons.check_circle : Icons.circle,
                          color: _isMonitoring ? Color(0xFF06B6D4) : Colors.white70,
                          size: 16,
                        ),
                        SizedBox(width: 8),
                        Text(
                          _isMonitoring ? 'LIVE PROTECTION ACTIVE' : 'PROTECTION READY',
                          style: TextStyle(
                            color: _isMonitoring ? Color(0xFF06B6D4) : Colors.white70,
                            fontWeight: FontWeight.w700,
                            fontSize: 13,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                SizedBox(height: 32),

                // Stats Grid
                GridView(
                  shrinkWrap: true,
                  physics: NeverScrollableScrollPhysics(),
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.2,
                  ),
                  children: [
                    _buildStatCard(
                      title: 'Threats Blocked',
                      value: '$_threatsDetected',
                      icon: Icons.shield_outlined,
                      color: Color(0xFFEF4444),
                    ),
                    _buildStatCard(
                      title: 'Messages Scanned',
                      value: '$_messagesScanned',
                      icon: Icons.analytics_outlined,
                      color: Color(0xFF06B6D4),
                    ),
                    _buildStatCard(
                      title: 'Active Protection',
                      value: _isMonitoring ? 'ON' : 'OFF',
                      icon: Icons.security_rounded,
                      color: Color(0xFF6366F1),
                    ),
                    _buildStatCard(
                      title: 'Wi-Fi Security',
                      value: _isWifiMonitoring ? 'ON' : 'OFF',
                      icon: Icons.wifi_find_rounded,
                      color: Color(0xFF10B981),
                    ),
                  ],
                ),
                SizedBox(height: 32),

                // Features Section
                Text(
                  'Security Features',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                  ),
                ),
                SizedBox(height: 16),

                Column(
                  children: [
                    _buildFeatureCard(
                      title: 'WhatsApp Protection',
                      description: 'Real-time message scanning & analysis',
                      icon: Icons.chat_bubble_rounded,
                      color: Color(0xFF25D366),
                      enabled: _isNotificationAccessEnabled,
                      onTap: _openNotificationSettings,
                    ),
                    SizedBox(height: 12),
                    _buildFeatureCard(
                      title: 'Email Security',
                      description: 'Advanced spam & phishing detection',
                      icon: Icons.email_rounded,
                      color: Color(0xFFEA4335),
                      enabled: true,
                      onTap: () {},
                    ),
                    SizedBox(height: 12),
                    _buildFeatureCard(
                      title: 'Wi-Fi Security',
                      description: 'Network threat detection & prevention',
                      icon: Icons.wifi_find_rounded,
                      color: Color(0xFF6366F1),
                      enabled: _isWifiMonitoring,
                      onTap: _toggleWifiMonitoring,
                    ),
                    SizedBox(height: 12),
                    _buildFeatureCard(
                      title: 'Voice Alerts',
                      description: 'Real-time threat notifications',
                      icon: Icons.volume_up_rounded,
                      color: Color(0xFFEF4444),
                      enabled: _isSoundEnabled,
                      onTap: () => setState(() => _isSoundEnabled = !_isSoundEnabled),
                    ),
                  ],
                ),
                SizedBox(height: 32),

                // Quick Actions
                Text(
                  'Quick Actions',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                  ),
                ),
                SizedBox(height: 16),

                Row(
                  children: [
                    _buildQuickAction(
                      icon: _isMonitoring ? Icons.stop_circle : Icons.play_circle_fill,
                      label: _isMonitoring ? 'Stop Scan' : 'Start Scan',
                      onTap: _toggleMonitoring,
                      color: Color(0xFF06B6D4),
                      isActive: _isMonitoring,
                    ),
                    SizedBox(width: 12),
                    _buildQuickAction(
                      icon: Icons.wifi_find_rounded,
                      label: 'Wi-Fi Scan',
                      onTap: _toggleWifiMonitoring,
                      color: Color(0xFF6366F1),
                      isActive: _isWifiMonitoring,
                    ),
                    SizedBox(width: 12),
                    _buildQuickAction(
                      icon: Icons.email_rounded,
                      label: 'Test Email',
                      onTap: _testEmailDetection,
                      color: Color(0xFFEF4444),
                      isActive: false,
                    ),
                    SizedBox(width: 12),
                    _buildQuickAction(
                      icon: Icons.chat_bubble_rounded,
                      label: 'Test Message',
                      onTap: _testMessageDetection,
                      color: Color(0xFF25D366),
                      isActive: false,
                    ),
                  ],
                ),
                SizedBox(height: 32),

                // Footer
                Container(
                  padding: EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(16),
                    gradient: LinearGradient(
                      colors: [Color(0xFF1E293B), Color(0xFF0F172A)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    border: Border.all(color: Colors.white.withOpacity(0.1)),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: LinearGradient(
                            colors: [Color(0xFF6366F1), Color(0xFF06B6D4)],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                        ),
                        child: Icon(Icons.security_rounded, color: Colors.white, size: 24),
                      ),
                      SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Protected by CyberGuard',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            SizedBox(height: 4),
                            Text(
                              '${_messagesScanned} messages analyzed • ${_threatsDetected} threats blocked',
                              style: TextStyle(
                                color: Colors.white70,
                                fontSize: 13,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                SizedBox(height: 20),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showThreatHistory() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) => Container(
        height: MediaQuery.of(context).size.height * 0.8,
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(32),
            topRight: Radius.circular(32),
          ),
        ),
        child: Column(
          children: [
            Container(
              width: 40,
              height: 4,
              margin: EdgeInsets.only(top: 12, bottom: 20),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.3),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Threat History',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w900,
                      color: Colors.white,
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: Icon(Icons.close, color: Colors.white70, size: 24),
                  ),
                ],
              ),
            ),
            SizedBox(height: 20),
            Expanded(
              child: _suspiciousMessages.isEmpty
                  ? Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.verified_user_rounded,
                      size: 80,
                      color: Color(0xFF6366F1).withOpacity(0.3),
                    ),
                    SizedBox(height: 20),
                    Text(
                      'No threats detected',
                      style: TextStyle(
                        color: Colors.white70,
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'Your device is secure',
                      style: TextStyle(color: Colors.white54, fontSize: 14),
                    ),
                  ],
                ),
              )
                  : ListView.builder(
                padding: EdgeInsets.symmetric(horizontal: 20),
                itemCount: _suspiciousMessages.length,
                itemBuilder: (context, index) {
                  final threat = _suspiciousMessages[index];
                  return _buildThreatItem(threat);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildThreatItem(Map<String, dynamic> threat) {
    Color severityColor = threat['spamScore'] > 7
        ? Color(0xFFEF4444)
        : threat['spamScore'] > 4
        ? Color(0xFFF59E0B)
        : Color(0xFF06B6D4);

    return Container(
      margin: EdgeInsets.only(bottom: 12),
      padding: EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: severityColor.withOpacity(0.2)),
      ),
      child: Row(
        children: [
          Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: severityColor.withOpacity(0.1),
            ),
            child: Icon(
              threat['type'] == 'Email' ? Icons.email_rounded : Icons.chat_bubble_rounded,
              color: severityColor,
              size: 22,
            ),
          ),
          SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        threat['title'],
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    Container(
                      padding: EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: severityColor.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        '${threat['spamScore']}/10',
                        style: TextStyle(
                          color: severityColor,
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
                SizedBox(height: 6),
                Text(
                  threat['message'],
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 13,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                SizedBox(height: 10),
                Row(
                  children: [
                    Container(
                      padding: EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.05),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        threat['type'],
                        style: TextStyle(
                          color: Colors.white70,
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    SizedBox(width: 10),
                    Text(
                      '${DateTime.now().difference(threat['time']).inMinutes}m ago',
                      style: TextStyle(
                        color: Colors.white54,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _showSettings() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        height: MediaQuery.of(context).size.height * 0.6,
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(32),
            topRight: Radius.circular(32),
          ),
        ),
        child: Column(
          children: [
            Container(
              width: 40,
              height: 4,
              margin: EdgeInsets.only(top: 12, bottom: 20),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.3),
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Settings',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w900,
                      color: Colors.white,
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: Icon(Icons.close, color: Colors.white70, size: 24),
                  ),
                ],
              ),
            ),
            SizedBox(height: 20),
            Expanded(
              child: ListView(
                padding: EdgeInsets.symmetric(horizontal: 20),
                children: [
                  _buildSettingsOption('Voice Alerts', _isSoundEnabled, Icons.volume_up_rounded, Color(0xFF06B6D4)),
                  SizedBox(height: 12),
                  _buildSettingsOption('Wi-Fi Monitoring', _isWifiMonitoring, Icons.wifi_rounded, Color(0xFF6366F1)),
                  SizedBox(height: 12),
                  _buildSettingsOption('Auto Block', true, Icons.block_rounded, Color(0xFFEF4444)),
                  SizedBox(height: 12),
                  _buildSettingsOption('Dark Mode', true, Icons.dark_mode_rounded, Color(0xFFF59E0B)),
                  SizedBox(height: 12),
                  _buildSettingsOption('Notification Access', _isNotificationAccessEnabled, Icons.notifications_rounded, Color(0xFF25D366)),
                ],
              ),
            ),
            SizedBox(height: 20),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: 20),
              child: SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () => Navigator.pop(context),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Color(0xFF1E293B),
                    foregroundColor: Colors.white,
                    padding: EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                      side: BorderSide(color: Colors.white.withOpacity(0.1)),
                    ),
                  ),
                  child: Text('Close', style: TextStyle(fontWeight: FontWeight.w600)),
                ),
              ),
            ),
            SizedBox(height: 20),
          ],
        ),
      ),
    );
  }

  Widget _buildSettingsOption(String title, bool value, IconData icon, Color color) {
    return Container(
      decoration: BoxDecoration(
        color: Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withOpacity(0.1)),
      ),
      child: ListTile(
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        leading: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: LinearGradient(
              colors: [color.withOpacity(0.2), color.withOpacity(0.1)],
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
            ),
          ),
          child: Icon(icon, color: color, size: 22),
        ),
        title: Text(
          title,
          style: TextStyle(
            color: Colors.white,
            fontSize: 16,
            fontWeight: FontWeight.w600,
          ),
        ),
        trailing: Switch.adaptive(
          value: value,
          onChanged: (val) {
            setState(() {
              if (title.contains('Voice')) _isSoundEnabled = val;
              if (title.contains('Wi-Fi')) _toggleWifiMonitoring();
              if (title.contains('Notification')) _openNotificationSettings();
            });
          },
          activeColor: color,
          activeTrackColor: color.withOpacity(0.3),
        ),
      ),
    );
  }
}