import 'package:flutter/material.dart';

import '../../widgets/common_widgets.dart';

class ReturnsScreen extends StatelessWidget {
  const ReturnsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: const [
        Text('Returns', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
        SizedBox(height: 12),
        Card(
          child: Padding(
            padding: EdgeInsets.all(16),
            child: Text(
              'Use receipt lookup to validate returns, reverse stock where applicable, and keep USD/ZWG refund records aligned.',
              style: TextStyle(color: AppColors.textMuted),
            ),
          ),
        ),
      ],
    );
  }
}
