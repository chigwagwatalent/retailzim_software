import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

// ─── App Colors ──────────────────────────────────────────────────────────────

class AppColors {
  static const Color primaryBlue = Color(0xFF1565C0);
  static const Color accentYellow = Color(0xFFFFD600);
  static const Color background = Color(0xFFF4F8FD);
  static const Color cardWhite = Colors.white;
  static const Color successGreen = Color(0xFF2E7D32);
  static const Color errorRed = Color(0xFFC62828);
  static const Color warningOrange = Color(0xFFE65100);
  static const Color textDark = Color(0xFF1A1A2E);
  static const Color textMuted = Color(0xFF6B7280);
}

// ─── Formatting helpers ───────────────────────────────────────────────────────

String formatCurrency(double amount, String currency) {
  final fmt = NumberFormat('#,##0.00');
  if (currency == 'USD') {
    return '\$${fmt.format(amount)}';
  }
  return 'ZWG ${fmt.format(amount)}';
}

String timeAgo(DateTime dt) {
  final diff = DateTime.now().difference(dt);
  if (diff.inSeconds < 60) return 'Just now';
  if (diff.inMinutes < 60) {
    final m = diff.inMinutes;
    return '$m minute${m == 1 ? '' : 's'} ago';
  }
  if (diff.inHours < 24) {
    final h = diff.inHours;
    return '$h hour${h == 1 ? '' : 's'} ago';
  }
  if (diff.inDays < 7) {
    final d = diff.inDays;
    return '$d day${d == 1 ? '' : 's'} ago';
  }
  return DateFormat('dd MMM yyyy').format(dt);
}

// ─── RetailZWButton ───────────────────────────────────────────────────────────

class RetailZWButton extends StatelessWidget {
  const RetailZWButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.color,
    this.textColor,
    this.loading = false,
    this.icon,
    this.outlined = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final Color? color;
  final Color? textColor;
  final bool loading;
  final IconData? icon;
  final bool outlined;

  @override
  Widget build(BuildContext context) {
    final bg = color ?? AppColors.accentYellow;
    final fg = textColor ?? AppColors.primaryBlue;

    if (outlined) {
      return OutlinedButton(
        onPressed: loading ? null : onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: bg,
          side: BorderSide(color: bg, width: 1.5),
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
        ),
        child: _buildChild(bg),
      );
    }

    return ElevatedButton(
      onPressed: loading ? null : onPressed,
      style: ElevatedButton.styleFrom(
        backgroundColor: bg,
        foregroundColor: fg,
        elevation: 0,
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
      ),
      child: _buildChild(fg),
    );
  }

  Widget _buildChild(Color fg) {
    if (loading) {
      return SizedBox(
        width: 20,
        height: 20,
        child: CircularProgressIndicator(
            strokeWidth: 2, valueColor: AlwaysStoppedAnimation<Color>(fg)),
      );
    }
    if (icon != null) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 18),
          const SizedBox(width: 8),
          Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
        ],
      );
    }
    return Text(label, style: const TextStyle(fontWeight: FontWeight.w700));
  }
}

// ─── StatusBadge ──────────────────────────────────────────────────────────────

class StatusBadge extends StatelessWidget {
  const StatusBadge(this.status, {super.key});
  final String status;

  @override
  Widget build(BuildContext context) {
    final cfg = _config(status.toUpperCase());
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: cfg.$1.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: cfg.$1.withValues(alpha: 0.4)),
      ),
      child: Text(
        cfg.$2,
        style: TextStyle(
          color: cfg.$1,
          fontSize: 11,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.5,
        ),
      ),
    );
  }

  (Color, String) _config(String s) {
    return switch (s) {
      'COMPLETED' => (AppColors.successGreen, 'COMPLETED'),
      'VOIDED' => (AppColors.errorRed, 'VOIDED'),
      'PENDING' => (AppColors.warningOrange, 'PENDING'),
      'REFUNDED' => (const Color(0xFF7B1FA2), 'REFUNDED'),
      'OPEN' => (AppColors.successGreen, 'OPEN'),
      'CLOSED' => (AppColors.textMuted, 'CLOSED'),
      'SYNCED' => (AppColors.successGreen, 'SYNCED'),
      _ => (AppColors.textMuted, s),
    };
  }
}

// ─── CurrencyDisplay ─────────────────────────────────────────────────────────

class CurrencyDisplay extends StatelessWidget {
  const CurrencyDisplay(
      {super.key, required this.usd, required this.zwg, this.style});
  final double usd;
  final double zwg;
  final TextStyle? style;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(formatCurrency(usd, 'USD'),
            style: style ??
                const TextStyle(
                    fontWeight: FontWeight.w700,
                    color: AppColors.primaryBlue)),
        Text(formatCurrency(zwg, 'ZWG'),
            style: (style ?? const TextStyle()).copyWith(
              fontSize: 12,
              color: AppColors.textMuted,
            )),
      ],
    );
  }
}

// ─── LoadingOverlay ──────────────────────────────────────────────────────────

class LoadingOverlay extends StatelessWidget {
  const LoadingOverlay({super.key, this.message});
  final String? message;

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black.withValues(alpha: 0.4),
      child: Center(
        child: Card(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: Padding(
            padding:
                const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const CircularProgressIndicator(
                    valueColor: AlwaysStoppedAnimation<Color>(
                        AppColors.primaryBlue)),
                if (message != null) ...[
                  const SizedBox(height: 16),
                  Text(message!,
                      style: const TextStyle(color: AppColors.textDark)),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ─── EmptyState ───────────────────────────────────────────────────────────────

class EmptyState extends StatelessWidget {
  const EmptyState(this.message, this.icon, {super.key, this.action});
  final String message;
  final IconData icon;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 64, color: AppColors.textMuted.withValues(alpha: 0.4)),
          const SizedBox(height: 16),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: AppColors.textMuted,
              fontSize: 15,
            ),
          ),
          if (action != null) ...[
            const SizedBox(height: 16),
            action!,
          ],
        ],
      ),
    );
  }
}

// ─── LoyaltyTierBadge ────────────────────────────────────────────────────────

class LoyaltyTierBadge extends StatelessWidget {
  const LoyaltyTierBadge(this.tier, {super.key});
  final String tier;

  @override
  Widget build(BuildContext context) {
    final cfg = _tierConfig(tier.toUpperCase());
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        gradient: LinearGradient(colors: [cfg.$1, cfg.$2]),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.star, size: 12, color: Colors.white),
          const SizedBox(width: 4),
          Text(
            cfg.$3,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 11,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }

  (Color, Color, String) _tierConfig(String t) {
    return switch (t) {
      'BRONZE' => (
          const Color(0xFFCD7F32),
          const Color(0xFFB8690A),
          'Bronze'
        ),
      'SILVER' => (
          const Color(0xFF9E9E9E),
          const Color(0xFF616161),
          'Silver'
        ),
      'GOLD' => (
          const Color(0xFFFFD700),
          const Color(0xFFFF8F00),
          'Gold'
        ),
      'PLATINUM' => (
          const Color(0xFF78909C),
          const Color(0xFF37474F),
          'Platinum'
        ),
      _ => (AppColors.textMuted, AppColors.textMuted, t),
    };
  }
}

// ─── SectionHeader ────────────────────────────────────────────────────────────

class SectionHeader extends StatelessWidget {
  const SectionHeader(this.title, {super.key, this.action});
  final String title;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(title,
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: AppColors.textDark,
            )),
        const Spacer(),
        if (action != null) action!,
      ],
    );
  }
}

// ─── AppCard ──────────────────────────────────────────────────────────────────

class AppCard extends StatelessWidget {
  const AppCard({
    super.key,
    required this.child,
    this.padding,
    this.onTap,
    this.color,
  });
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final VoidCallback? onTap;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: color ?? Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 1,
      shadowColor: Colors.black12,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: padding ?? const EdgeInsets.all(16),
          child: child,
        ),
      ),
    );
  }
}
