import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'common_widgets.dart';

class RetailZwMark extends StatelessWidget {
  const RetailZwMark({
    super.key,
    this.size = 64,
    this.elevated = true,
  });

  final double size;
  final bool elevated;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(size * 0.28),
        boxShadow: elevated
            ? [
                BoxShadow(
                  color: AppColors.primaryBlue.withValues(alpha: 0.22),
                  blurRadius: size * 0.35,
                  offset: Offset(0, size * 0.14),
                ),
              ]
            : null,
      ),
      child: CustomPaint(
        painter: _RetailZwMarkPainter(),
      ),
    );
  }
}

class _RetailZwMarkPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final radius = Radius.circular(size.width * 0.28);
    final body = RRect.fromRectAndRadius(rect, radius);

    canvas.drawRRect(
      body,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF0A4EA3), Color(0xFF2EA8FF)],
        ).createShader(rect),
    );

    final shine = Path()
      ..moveTo(size.width * 0.12, size.height * 0.72)
      ..lineTo(size.width * 0.68, size.height * 0.12)
      ..lineTo(size.width * 0.84, size.height * 0.26)
      ..lineTo(size.width * 0.28, size.height * 0.86)
      ..close();
    canvas.drawPath(
        shine, Paint()..color = Colors.white.withValues(alpha: 0.92));

    final accent = Path()
      ..moveTo(size.width * 0.18, size.height * 0.18)
      ..lineTo(size.width * 0.48, size.height * 0.18)
      ..lineTo(size.width * 0.28, size.height * 0.38)
      ..close();
    canvas.drawPath(accent, Paint()..color = AppColors.accentYellow);

    final counter = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.width * 0.055
      ..strokeCap = StrokeCap.round
      ..color = const Color(0xFF062B5D).withValues(alpha: 0.72);

    canvas.drawArc(
      Rect.fromCenter(
        center: Offset(size.width * 0.58, size.height * 0.58),
        width: size.width * 0.42,
        height: size.height * 0.42,
      ),
      -math.pi * 0.2,
      math.pi * 1.18,
      false,
      counter,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
