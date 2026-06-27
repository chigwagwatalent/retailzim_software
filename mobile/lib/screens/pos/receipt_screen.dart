import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/models.dart';
import '../../providers/app_provider.dart';
import '../../screens/settings/printer_settings_screen.dart';
import '../../services/printer_service.dart';
import '../../widgets/common_widgets.dart';

/// Shown after a successful sale. Displays a formatted receipt and lets
/// the cashier start a new sale or print/reprint the receipt.
class ReceiptScreen extends StatefulWidget {
  final Sale sale;
  final double change;
  final String currency;
  final List<dynamic> cartItems;

  const ReceiptScreen({
    super.key,
    required this.sale,
    required this.change,
    required this.currency,
    required this.cartItems,
  });

  @override
  State<ReceiptScreen> createState() => _ReceiptScreenState();
}

class _ReceiptScreenState extends State<ReceiptScreen> {
  bool _printing = false;

  Sale get sale => widget.sale;
  double get change => widget.change;
  String get currency => widget.currency;
  List<dynamic> get cartItems => widget.cartItems;

  @override
  Widget build(BuildContext context) {
    final isOffline = sale.status == 'PENDING';
    final user = context.watch<AppProvider>().currentUser;
    final companyName = _clean(user?.companyName) ?? 'RetailZW';
    final logoUrl = _clean(user?.companyLogoUrl);
    final companyLines = <String?>[
      _clean(user?.companyAddress),
      [
        _clean(user?.companyCity),
        _clean(user?.companyCountry),
      ].whereType<String>().join(', '),
      _clean(user?.companyPhone),
      _clean(user?.companyEmail),
      _clean(user?.companyWebsite),
      _clean(user?.companyRegistrationNumber) == null
          ? null
          : 'Reg: ${_clean(user?.companyRegistrationNumber)}',
      _clean(user?.companyVatNumber) == null
          ? null
          : 'VAT: ${_clean(user?.companyVatNumber)}',
    ].whereType<String>().where((line) => line.isNotEmpty).toList();
    final receiptFooter =
        _clean(user?.receiptFooter) ?? 'Thank you for your business!';

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.primaryBlue,
        foregroundColor: Colors.white,
        elevation: 0,
        automaticallyImplyLeading: false,
        title: const Text('Sale Complete',
            style: TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          TextButton.icon(
            onPressed: () => _newSale(context),
            icon: const Icon(Icons.add_shopping_cart_rounded,
                color: Colors.white, size: 18),
            label: const Text('New Sale',
                style: TextStyle(
                    color: Colors.white, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Column(
                children: [
                  // Success badge
                  const SizedBox(height: 8),
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      color: isOffline
                          ? AppColors.accentYellow.withValues(alpha: 0.15)
                          : const Color(0xFFE8F5E9),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      isOffline
                          ? Icons.cloud_off_rounded
                          : Icons.check_circle_rounded,
                      color: isOffline
                          ? AppColors.warningOrange
                          : const Color(0xFF2E7D32),
                      size: 40,
                    ),
                  ),
                  const SizedBox(height: 14),
                  Text(
                    isOffline ? 'Saved Offline' : 'Sale Complete!',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w900,
                      color: isOffline
                          ? AppColors.warningOrange
                          : const Color(0xFF1B5E20),
                    ),
                  ),
                  const SizedBox(height: 4),
                  if (isOffline)
                    const Text(
                      'This sale is queued and will sync when the server is reachable.',
                      textAlign: TextAlign.center,
                      style:
                          TextStyle(color: AppColors.textMuted, fontSize: 13),
                    )
                  else
                    Text(
                      'Receipt #${sale.receiptNumber}',
                      style: const TextStyle(
                          color: AppColors.textMuted, fontSize: 14),
                    ),
                  const SizedBox(height: 24),

                  // Receipt card
                  Card(
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                      side: const BorderSide(color: Color(0xFFE0E7EF)),
                    ),
                    child: Column(
                      children: [
                        // Header
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(20),
                          decoration: const BoxDecoration(
                            color: AppColors.primaryBlue,
                            borderRadius:
                                BorderRadius.vertical(top: Radius.circular(16)),
                          ),
                          child: Column(
                            children: [
                              Container(
                                width: 36,
                                height: 36,
                                decoration: BoxDecoration(
                                  color: AppColors.accentYellow,
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                alignment: Alignment.center,
                                clipBehavior: Clip.antiAlias,
                                child: logoUrl == null
                                    ? Text(_initials(companyName),
                                        style: const TextStyle(
                                            color: AppColors.primaryBlue,
                                            fontWeight: FontWeight.w900,
                                            fontSize: 15))
                                    : Image.network(logoUrl,
                                        fit: BoxFit.cover,
                                        width: 36,
                                        height: 36,
                                        errorBuilder: (_, __, ___) => Text(
                                            _initials(companyName),
                                            style: const TextStyle(
                                                color: AppColors.primaryBlue,
                                                fontWeight: FontWeight.w900,
                                                fontSize: 15))),
                              ),
                              const SizedBox(height: 8),
                              Text(companyName,
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 18,
                                      fontWeight: FontWeight.w900)),
                              ...companyLines.take(4).map((line) => Padding(
                                    padding: const EdgeInsets.only(top: 3),
                                    child: Text(line,
                                        textAlign: TextAlign.center,
                                        style: TextStyle(
                                            color: Colors.white
                                                .withValues(alpha: 0.72),
                                            fontSize: 10,
                                            fontWeight: FontWeight.w500)),
                                  )),
                              const SizedBox(height: 4),
                              Text(
                                'RECEIPT',
                                style: TextStyle(
                                    color: Colors.white.withValues(alpha: 0.65),
                                    fontSize: 11,
                                    letterSpacing: 3,
                                    fontWeight: FontWeight.w600),
                              ),
                              if (sale.receiptNumber.isNotEmpty) ...[
                                const SizedBox(height: 6),
                                Text(
                                  '#${sale.receiptNumber}',
                                  style: const TextStyle(
                                      color: AppColors.accentYellow,
                                      fontSize: 13,
                                      fontWeight: FontWeight.w700),
                                ),
                              ],
                            ],
                          ),
                        ),

                        // Date + cashier
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 14, 20, 0),
                          child: Row(
                            children: [
                              const Icon(Icons.calendar_today_rounded,
                                  size: 13, color: AppColors.textMuted),
                              const SizedBox(width: 6),
                              Text(
                                _formatDate(sale.createdAt),
                                style: const TextStyle(
                                    color: AppColors.textMuted, fontSize: 12),
                              ),
                            ],
                          ),
                        ),

                        // Divider
                        const Padding(
                          padding: EdgeInsets.symmetric(
                              horizontal: 20, vertical: 14),
                          child: _DashedDivider(),
                        ),

                        // Line items
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 20),
                          child: Column(
                            children: [
                              // Header row
                              const Row(
                                children: [
                                  Expanded(
                                      child: Text('ITEM',
                                          style: TextStyle(
                                              fontSize: 10,
                                              fontWeight: FontWeight.w700,
                                              color: AppColors.textMuted,
                                              letterSpacing: 0.5))),
                                  Text('QTY',
                                      style: TextStyle(
                                          fontSize: 10,
                                          fontWeight: FontWeight.w700,
                                          color: AppColors.textMuted,
                                          letterSpacing: 0.5)),
                                  SizedBox(width: 20),
                                  SizedBox(
                                    width: 80,
                                    child: Text('AMOUNT',
                                        textAlign: TextAlign.right,
                                        style: TextStyle(
                                            fontSize: 10,
                                            fontWeight: FontWeight.w700,
                                            color: AppColors.textMuted,
                                            letterSpacing: 0.5)),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              ...cartItems.map((item) => Padding(
                                    padding: const EdgeInsets.only(bottom: 8),
                                    child: Row(
                                      children: [
                                        Expanded(
                                          child: Text(
                                            item.product.name,
                                            style: const TextStyle(
                                                fontSize: 13,
                                                fontWeight: FontWeight.w600),
                                          ),
                                        ),
                                        Text(
                                            '${item.quantity.toStringAsFixed(0)}',
                                            style: const TextStyle(
                                                fontSize: 13,
                                                color: AppColors.textMuted)),
                                        const SizedBox(width: 20),
                                        SizedBox(
                                          width: 80,
                                          child: Text(
                                            formatCurrency(
                                                item.lineTotal + item.lineTax,
                                                currency),
                                            textAlign: TextAlign.right,
                                            style: const TextStyle(
                                                fontSize: 13,
                                                fontWeight: FontWeight.w700),
                                          ),
                                        ),
                                      ],
                                    ),
                                  )),
                            ],
                          ),
                        ),

                        // Divider
                        const Padding(
                          padding: EdgeInsets.symmetric(
                              horizontal: 20, vertical: 14),
                          child: _DashedDivider(),
                        ),

                        // Totals
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
                          child: Column(
                            children: [
                              const SizedBox(height: 8),
                              Container(
                                padding:
                                    const EdgeInsets.symmetric(vertical: 10),
                                decoration: const BoxDecoration(
                                  border: Border(
                                    top: BorderSide(color: Color(0xFFE0E7EF)),
                                    bottom:
                                        BorderSide(color: Color(0xFFE0E7EF)),
                                  ),
                                ),
                                child: Row(
                                  children: [
                                    const Text('TOTAL',
                                        style: TextStyle(
                                            fontWeight: FontWeight.w900,
                                            fontSize: 15)),
                                    const Spacer(),
                                    Text(
                                      formatCurrency(sale.grandTotal, currency),
                                      style: const TextStyle(
                                          fontWeight: FontWeight.w900,
                                          fontSize: 22,
                                          color: AppColors.primaryBlue),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 10),
                              _ReceiptRow(
                                  label: 'Tendered',
                                  value: formatCurrency(
                                      sale.grandTotal + change, currency)),
                              if (change > 0)
                                _ReceiptRow(
                                    label: 'Change',
                                    value: formatCurrency(change, currency),
                                    valueColor: const Color(0xFF2E7D32),
                                    bold: true),
                            ],
                          ),
                        ),

                        // Footer
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(16),
                          decoration: const BoxDecoration(
                            color: Color(0xFFF7F9FC),
                            borderRadius: BorderRadius.vertical(
                                bottom: Radius.circular(16)),
                          ),
                          child: Column(
                            children: [
                              Text(receiptFooter,
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(
                                      fontWeight: FontWeight.w700,
                                      fontSize: 13)),
                              const SizedBox(height: 4),
                              const Text('Powered by RetailZW - retailzw.co.zw',
                                  style: TextStyle(
                                      color: AppColors.textMuted,
                                      fontSize: 11)),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 24),

                  // Action buttons
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _printing
                              ? null
                              : () => _reprintReceipt(context, user),
                          icon: _printing
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(
                                      strokeWidth: 2.5,
                                      color: AppColors.primaryBlue))
                              : const Icon(Icons.print_rounded, size: 18),
                          label: Text(
                            _printing ? 'Printing…' : 'Print',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          style: OutlinedButton.styleFrom(
                            foregroundColor: AppColors.primaryBlue,
                            side:
                                const BorderSide(color: AppColors.primaryBlue),
                            padding: const EdgeInsets.symmetric(vertical: 14),
                            shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12)),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: () => _newSale(context),
                          icon: const Icon(Icons.add_rounded, size: 20),
                          label: const Text('New Sale',
                              style: TextStyle(fontWeight: FontWeight.w800)),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppColors.accentYellow,
                            foregroundColor: AppColors.primaryBlue,
                            elevation: 0,
                            padding: const EdgeInsets.symmetric(vertical: 14),
                            shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12)),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _newSale(BuildContext context) {
    Navigator.of(context).pop(sale.status == 'PENDING'
        ? 'Sale saved offline. Will sync when connected.'
        : 'Sale complete: ${sale.receiptNumber}');
  }

  Future<void> _reprintReceipt(BuildContext context, UserInfo? user) async {
    if (_printing) return;
    setState(() => _printing = true);

    final companyName = _clean(user?.companyName) ?? 'RetailZW';
    final companyLines = <String>[
      if (_clean(user?.companyAddress) != null) user!.companyAddress,
      if (_clean(user?.companyPhone) != null) user!.companyPhone,
    ];
    final footer =
        _clean(user?.receiptFooter) ?? 'Thank you for your business!';

    final result = await PrinterService().printReceipt(
      sale: sale,
      cartItems: cartItems,
      companyName: companyName,
      companyLines: companyLines,
      footerText: footer,
      change: change,
      currency: currency,
    );

    if (!mounted) return;
    setState(() => _printing = false);

    if (result.success) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        backgroundColor: Color(0xFF2E7D32),
        content: Row(
          children: [
            Icon(Icons.check_circle_rounded, color: Colors.white, size: 18),
            SizedBox(width: 8),
            Text('Receipt printed!',
                style: TextStyle(
                    color: Colors.white, fontWeight: FontWeight.w600)),
          ],
        ),
      ));
    } else {
      // Show error with option to go to printer settings
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        duration: const Duration(seconds: 6),
        backgroundColor: const Color(0xFF37474F),
        content: Text(
          result.error ?? 'Print failed',
          style: const TextStyle(color: Colors.white),
        ),
        action: SnackBarAction(
          label: 'Setup Printer',
          textColor: AppColors.accentYellow,
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute(
                builder: (_) => const PrinterSettingsScreen()),
          ),
        ),
      ));
    }
  }

  String _formatDate(DateTime dt) {
    return '${dt.day.toString().padLeft(2, '0')}/${dt.month.toString().padLeft(2, '0')}/${dt.year}  ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }

  String? _clean(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }

  String _initials(String value) {
    final words = value.trim().split(RegExp(r'\s+'));
    if (words.isEmpty || words.first.isEmpty) return 'RZ';
    if (words.length == 1) {
      return words.first
          .substring(0, words.first.length < 2 ? 1 : 2)
          .toUpperCase();
    }
    return '${words.first[0]}${words.last[0]}'.toUpperCase();
  }
}

// ─── Receipt helpers ──────────────────────────────────────────────────────

class _ReceiptRow extends StatelessWidget {
  final String label;
  final String value;
  final Color? valueColor;
  final bool bold;

  const _ReceiptRow({
    required this.label,
    required this.value,
    this.valueColor,
    this.bold = false,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        children: [
          Text(label,
              style: const TextStyle(color: AppColors.textMuted, fontSize: 13)),
          const Spacer(),
          Text(
            value,
            style: TextStyle(
              fontSize: 13,
              fontWeight: bold ? FontWeight.w800 : FontWeight.w600,
              color: valueColor ?? AppColors.textDark,
            ),
          ),
        ],
      ),
    );
  }
}

class _DashedDivider extends StatelessWidget {
  const _DashedDivider();

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        const dashWidth = 6.0;
        const dashSpace = 4.0;
        final count = (constraints.maxWidth / (dashWidth + dashSpace)).floor();
        return Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: List.generate(
            count,
            (_) => Container(
              width: dashWidth,
              height: 1,
              color: const Color(0xFFDDE5EF),
            ),
          ),
        );
      },
    );
  }
}
