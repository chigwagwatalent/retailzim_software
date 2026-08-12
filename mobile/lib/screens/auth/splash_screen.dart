import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/models.dart';
import '../../providers/app_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/brand_mark.dart';
import '../../widgets/common_widgets.dart';
import '../home_screen.dart';
import 'login_screen.dart';

// ─────────────────────────────────────────────────────────────────────────────
// SplashScreen — pure-Flutter, zero assets, zero extra packages.
// Navigation logic is IDENTICAL to before: 1450 ms then fade to LoginScreen.
// ─────────────────────────────────────────────────────────────────────────────

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key, this.restoreUser});

  /// Injectable for startup tests. Production restores the encrypted session.
  final Future<UserInfo?> Function()? restoreUser;

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with TickerProviderStateMixin {
  final ApiService _api = ApiService();

  // Logo entrance
  late final AnimationController _logoCtrl;
  late final Animation<double> _logoScale;
  late final Animation<double> _logoFade;

  // Text slide-up
  late final Animation<double> _textSlide;
  late final Animation<double> _textFade;

  // Infinite pulse rings around the logo
  late final AnimationController _pulseCtrl;

  // Progress bar at the very bottom
  late final AnimationController _progressCtrl;

  @override
  void initState() {
    super.initState();

    // ── Logo: scale-spring + fade (800 ms) ───────────────────────────────────
    _logoCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    )..forward();

    _logoScale = Tween<double>(begin: 0.55, end: 1.0).animate(
      CurvedAnimation(parent: _logoCtrl, curve: Curves.elasticOut),
    );
    _logoFade = CurvedAnimation(
      parent: _logoCtrl,
      curve: const Interval(0.0, 0.55, curve: Curves.easeOut),
    );

    // ── Text: slides up 24 px and fades in (starts at 40 % of logo anim) ────
    _textSlide = Tween<double>(begin: 24.0, end: 0.0).animate(
      CurvedAnimation(
        parent: _logoCtrl,
        curve: const Interval(0.42, 1.0, curve: Curves.easeOutCubic),
      ),
    );
    _textFade = CurvedAnimation(
      parent: _logoCtrl,
      curve: const Interval(0.42, 0.95, curve: Curves.easeOut),
    );

    // ── Pulse rings: loop forever ────────────────────────────────────────────
    _pulseCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2200),
    )..repeat();

    // ── Progress bar fills in 1 400 ms ───────────────────────────────────────
    _progressCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..forward();

    // ── Navigate — same timing as before ────────────────────────────────────
    _continueStartup();
  }

  Future<UserInfo?> _restoreUser() async {
    try {
      if (widget.restoreUser != null) return widget.restoreUser!();
      if (!await _api.isLoggedIn()) return null;
      return _api.getSavedUser();
    } catch (_) {
      // Corrupt or unavailable secure storage must never trap startup.
      return null;
    }
  }

  Future<void> _continueStartup() async {
    final results = await Future.wait<Object?>([
      Future<void>.delayed(const Duration(milliseconds: 1450)),
      _restoreUser(),
    ]);
    if (!mounted) return;

    final user = results[1] as UserInfo?;
    if (user != null) {
      context.read<AppProvider>().setUser(user);
    }
    final destination = user == null ? const LoginScreen() : const HomeScreen();

    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        transitionDuration: const Duration(milliseconds: 420),
        pageBuilder: (_, animation, __) => FadeTransition(
          opacity: animation,
          child: destination,
        ),
      ),
    );
  }

  @override
  void dispose() {
    _logoCtrl.dispose();
    _pulseCtrl.dispose();
    _progressCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        fit: StackFit.expand,
        children: [
          // ── Deep navy gradient background ──────────────────────────────────
          const _Background(),

          // ── Subtle dot-grid overlay ────────────────────────────────────────
          CustomPaint(painter: _DotGridPainter()),

          // ── Centre: logo + text ────────────────────────────────────────────
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Logo + pulse rings in a fixed canvas
                AnimatedBuilder(
                  animation: Listenable.merge([_logoCtrl, _pulseCtrl]),
                  builder: (_, __) {
                    return SizedBox(
                      width: 220,
                      height: 220,
                      child: Stack(
                        alignment: Alignment.center,
                        children: [
                          // Three staggered rings (only once logo is visible)
                          if (_logoFade.value > 0.4) ...[
                            _PulseRing(
                              progress: _pulseCtrl.value,
                              maxRadius: 100,
                            ),
                            _PulseRing(
                              progress: (_pulseCtrl.value + 0.34) % 1.0,
                              maxRadius: 100,
                            ),
                            _PulseRing(
                              progress: (_pulseCtrl.value + 0.67) % 1.0,
                              maxRadius: 100,
                            ),
                          ],

                          // Brand mark
                          FadeTransition(
                            opacity: _logoFade,
                            child: ScaleTransition(
                              scale: _logoScale,
                              child: const RetailZwMark(size: 92),
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),

                // App name + tagline
                AnimatedBuilder(
                  animation: _logoCtrl,
                  builder: (_, __) => Opacity(
                    opacity: _textFade.value.clamp(0.0, 1.0),
                    child: Transform.translate(
                      offset: Offset(0, _textSlide.value),
                      child: Column(
                        children: [
                          const Text(
                            'RetailZW',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 36,
                              fontWeight: FontWeight.w900,
                              letterSpacing: 0.4,
                              height: 1,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            "ZIMBABWE'S POS PLATFORM",
                            style: TextStyle(
                              color: Colors.white.withValues(alpha: 0.45),
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              letterSpacing: 3.0,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),

          // ── Yellow progress bar — very bottom ─────────────────────────────
          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: AnimatedBuilder(
              animation: _progressCtrl,
              builder: (_, __) => LinearProgressIndicator(
                value: _progressCtrl.value,
                minHeight: 3,
                backgroundColor: Colors.white.withValues(alpha: 0.07),
                valueColor:
                    const AlwaysStoppedAnimation<Color>(AppColors.accentYellow),
              ),
            ),
          ),

          // ── Domain watermark ───────────────────────────────────────────────
          Positioned(
            bottom: 22,
            left: 0,
            right: 0,
            child: AnimatedBuilder(
              animation: _textFade,
              builder: (_, __) => Opacity(
                opacity: (_textFade.value * 0.38).clamp(0.0, 1.0),
                child: const Text(
                  'retailzw.co.zw',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w500,
                    letterSpacing: 1.2,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Expanding ring — pure widget, no canvas needed
// ─────────────────────────────────────────────────────────────────────────────

class _PulseRing extends StatelessWidget {
  const _PulseRing({required this.progress, required this.maxRadius});

  final double progress; // 0 → 1
  final double maxRadius;

  @override
  Widget build(BuildContext context) {
    // Ease the opacity so rings fade out as they expand
    final opacity = (1.0 - progress) * 0.35;
    final size = 92.0 + (progress * (maxRadius * 2 - 92));
    final strokeWidth = 1.8 * (1.0 - progress * 0.6);

    return Opacity(
      opacity: opacity.clamp(0.0, 1.0),
      child: SizedBox(
        width: size,
        height: size,
        child: DecoratedBox(
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(
              color: AppColors.primaryBlue,
              width: strokeWidth,
            ),
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Background — static gradient, built once
// ─────────────────────────────────────────────────────────────────────────────

class _Background extends StatelessWidget {
  const _Background();

  @override
  Widget build(BuildContext context) {
    return const DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color(0xFF07122E), // near-black navy
            Color(0xFF0C2260), // mid blue
            Color(0xFF0A1A50), // deep blue
          ],
          stops: [0.0, 0.55, 1.0],
        ),
      ),
      child: SizedBox.expand(),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dot-grid overlay — very subtle, drawn once, never repaints
// ─────────────────────────────────────────────────────────────────────────────

class _DotGridPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withValues(alpha: 0.04)
      ..style = PaintingStyle.fill;

    const spacing = 28.0;
    const radius = 1.2;

    for (double x = spacing / 2; x < size.width; x += spacing) {
      for (double y = spacing / 2; y < size.height; y += spacing) {
        canvas.drawCircle(Offset(x, y), radius, paint);
      }
    }

    // Subtle radial fade — more dots visible near centre
    final fadePaint = Paint()
      ..shader = RadialGradient(
        colors: [
          Colors.transparent,
          const Color(0xFF07122E).withValues(alpha: 0.55),
        ],
        stops: const [0.38, 1.0],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));

    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), fadePaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter old) => false;
}

// ─────────────────────────────────────────────────────────────────────────────
// Unused — kept for easy re-use elsewhere if needed
// ─────────────────────────────────────────────────────────────────────────────

// ignore: unused_element
double _easeInOut(double t) =>
    t < 0.5 ? 4 * t * t * t : 1 - math.pow(-2 * t + 2, 3) / 2;
