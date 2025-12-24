import 'package:flutter/material.dart';

class OverlayButton extends StatelessWidget {
  final VoidCallback onCapture;
  final VoidCallback? onClose;

  const OverlayButton({super.key, required this.onCapture, this.onClose});

  @override
  Widget build(BuildContext context) {
    return Positioned(
      bottom: 40,
      right: 20,
      child: Column(
        children: [
          FloatingActionButton(
            onPressed: onCapture,
            child: Icon(Icons.camera_alt),
          ),
          const SizedBox(height: 12),
          FloatingActionButton.small(
            backgroundColor: Colors.red,
            onPressed: onClose ?? () => Navigator.pop(context),
            child: Icon(Icons.close),
          ),
        ],
      ),
    );
  }
}
