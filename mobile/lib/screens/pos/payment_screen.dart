import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../models/models.dart';
import '../../providers/app_provider.dart';
import '../../services/api_service.dart';
import '../../services/printer_service.dart';
import '../../widgets/common_widgets.dart';
import 'receipt_screen.dart';

class PaymentScreen extends StatefulWidget {
  final AppProvider provider;
  final CashSession session;
  final ApiService api;
  final bool embedded;
  final String initialMethod;

  const PaymentScreen({
    super.key,
    required this.provider,
    required this.session,
    required this.api,
    this.embedded = false,
    this.initialMethod = 'CASH',
  });

  @override
  State<PaymentScreen> createState() => _PaymentScreenState();
}

class _PaymentScreenState extends State<PaymentScreen> {
  String _method = 'CASH';
  String _currency = 'USD';
  final _amountCtrl = TextEditingController();
  bool _processing = false;
  String? _error;
  Borrower? _borrower;
  bool _holdChange = false;
  String? _heldChangeName;
  String? _heldChangePhone;

  static const _methods = [
    _PayMethod('CASH', Icons.payments_rounded, 'Cash'),
    _PayMethod('CARD', Icons.credit_card_rounded, 'Card'),
    _PayMethod('ECOCASH', Icons.phone_android_rounded, 'EcoCash'),
    _PayMethod('INNBUCKS', Icons.account_balance_wallet_rounded, 'InnBucks'),
    _PayMethod('STORE_CREDIT', Icons.handshake_rounded, 'Borrower Credit'),
    _PayMethod('OTHER', Icons.account_balance_rounded, 'Transfer'),
  ];

  @override
  void initState() {
    super.initState();
    _method = widget.initialMethod;
    _currency = widget.provider.currency;
    final total = widget.provider.cartTotal();
    _amountCtrl.text = total.toStringAsFixed(2);
  }

  @override
  void dispose() {
    _amountCtrl.dispose();
    super.dispose();
  }

  double get _total => widget.provider.cartTotal();
  double get _tendered =>
      double.tryParse(_amountCtrl.text.replaceAll(',', '')) ?? 0.0;
  double get _change => (_tendered - _total).clamp(0.0, double.infinity);

  @override
  Widget build(BuildContext context) {
    final isTablet = MediaQuery.of(context).size.width >= 700;

    final content = SafeArea(
      child: isTablet
          ? Row(
              children: [
                Expanded(child: _summaryPanel()),
                Expanded(child: _paymentPanel(compact: widget.embedded)),
              ],
            )
          : Column(
              children: [
                _summaryPanel(compact: true),
                Expanded(child: _paymentPanel(compact: true)),
              ],
            ),
    );

    if (widget.embedded) {
      return Material(
        color: AppColors.background,
        child: Column(
          children: [
            Container(
              height: 62,
              padding: const EdgeInsets.symmetric(horizontal: 20),
              decoration: const BoxDecoration(
                color: Colors.white,
                border: Border(bottom: BorderSide(color: Color(0xFFDDE5EF))),
              ),
              child: Row(
                children: [
                  const Icon(Icons.lock_outline_rounded,
                      size: 20, color: AppColors.successGreen),
                  const SizedBox(width: 10),
                  const Text('Secure checkout',
                      style:
                          TextStyle(fontSize: 17, fontWeight: FontWeight.w900)),
                  const Spacer(),
                  Text('${widget.provider.cartItemCount} item(s)',
                      style: const TextStyle(color: AppColors.textMuted)),
                  const SizedBox(width: 14),
                  IconButton(
                    tooltip: 'Close checkout',
                    onPressed:
                        _processing ? null : () => Navigator.pop(context),
                    icon: const Icon(Icons.close_rounded),
                  ),
                ],
              ),
            ),
            Expanded(child: content),
          ],
        ),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.primaryBlue,
        foregroundColor: Colors.white,
        elevation: 0,
        title: const Text('Payment',
            style: TextStyle(fontWeight: FontWeight.w800)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: content,
    );
  }

  // ─── Order summary ────────────────────────────────────────────────────────

  Widget _summaryPanel({bool compact = false}) {
    final provider = widget.provider;
    return Container(
      color: compact ? Colors.white : AppColors.primaryBlue,
      child: compact ? _compactSummary(provider) : _fullSummary(provider),
    );
  }

  Widget _fullSummary(AppProvider provider) {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 16),
          Text(
            formatCurrency(_total, _currency),
            style: const TextStyle(
              color: Colors.white,
              fontSize: 48,
              fontWeight: FontWeight.w900,
            ),
          ),
          Text(
            '${provider.cart.fold<int>(0, (s, i) => s + i.quantity.toInt())} item(s)',
            style: TextStyle(
                color: Colors.white.withValues(alpha: 0.7), fontSize: 16),
          ),
          const SizedBox(height: 32),
          const Divider(color: Colors.white24),
          const SizedBox(height: 16),
          ...provider.cart.map((item) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        '${item.quantity.toStringAsFixed(0)}x  ${item.product.name}',
                        style:
                            const TextStyle(color: Colors.white, fontSize: 14),
                      ),
                    ),
                    Text(
                      formatCurrency(item.lineTotal + item.lineTax, _currency),
                      style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w700,
                          fontSize: 14),
                    ),
                  ],
                ),
              )),
          const Divider(color: Colors.white24),
          const SizedBox(height: 10),
          Row(children: [
            const Expanded(
                child: Text('TOTAL',
                    style: TextStyle(
                        color: Colors.white, fontWeight: FontWeight.w900))),
            Text(
              formatCurrency(_total, _currency),
              style: const TextStyle(
                  color: AppColors.accentYellow,
                  fontWeight: FontWeight.w900,
                  fontSize: 20),
            ),
          ]),
          const Spacer(),
        ],
      ),
    );
  }

  Widget _compactSummary(AppProvider provider) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
      decoration: const BoxDecoration(
        color: AppColors.primaryBlue,
      ),
      child: Row(
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                formatCurrency(_total, _currency),
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 28,
                    fontWeight: FontWeight.w900),
              ),
              Text(
                '${provider.cart.fold<int>(0, (s, i) => s + i.quantity.toInt())} item(s)',
                style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.7), fontSize: 13),
              ),
            ],
          ),
          const Spacer(),
          ElevatedButton(
            onPressed: () => _showOrderDetails(provider),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.white.withValues(alpha: 0.15),
              foregroundColor: Colors.white,
              elevation: 0,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            child: const Text('Details'),
          ),
        ],
      ),
    );
  }

  void _showOrderDetails(AppProvider provider) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
      builder: (_) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Order Details',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
            const SizedBox(height: 16),
            ...provider.cart.map((item) => Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    children: [
                      Expanded(
                          child: Text(
                              '${item.quantity.toStringAsFixed(0)}x  ${item.product.name}')),
                      Text(
                          formatCurrency(
                              item.lineTotal + item.lineTax, _currency),
                          style: const TextStyle(fontWeight: FontWeight.w700)),
                    ],
                  ),
                )),
            const Divider(),
            Row(children: [
              const Expanded(
                  child: Text('TOTAL',
                      style: TextStyle(fontWeight: FontWeight.w900))),
              Text(formatCurrency(_total, _currency),
                  style: const TextStyle(
                      fontWeight: FontWeight.w900,
                      fontSize: 18,
                      color: AppColors.primaryBlue)),
            ]),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  // ─── Payment panel ────────────────────────────────────────────────────────

  Widget _paymentPanel({bool compact = false}) {
    return SingleChildScrollView(
      padding: EdgeInsets.all(compact ? 16 : 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Payment Method',
              style: TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 15,
                  color: AppColors.textDark)),
          const SizedBox(height: 12),

          // Method selector
          GridView.count(
            crossAxisCount: compact ? 3 : 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            childAspectRatio: compact ? 2.35 : 3.2,
            crossAxisSpacing: 10,
            mainAxisSpacing: 10,
            children: _methods
                .map((m) => _MethodTile(
                      method: m,
                      selected: _method == m.value,
                      compact: compact,
                      onTap: () => setState(() {
                        _method = m.value;
                        _holdChange = false;
                        if (_method == 'STORE_CREDIT') {
                          _amountCtrl.text = _total.toStringAsFixed(2);
                        }
                      }),
                    ))
                .toList(),
          ),

          SizedBox(height: compact ? 16 : 24),

          if (_method == 'STORE_CREDIT') ...[
            const Text('Borrower Account',
                style: TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 15,
                    color: AppColors.textDark)),
            const SizedBox(height: 8),
            InkWell(
              onTap: _showBorrowerPicker,
              borderRadius: BorderRadius.circular(12),
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFFDDE5EF)),
                ),
                child: Row(children: [
                  const Icon(Icons.person_search_rounded,
                      color: AppColors.primaryBlue),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _borrower == null
                        ? const Text('Search downloaded borrowers',
                            style: TextStyle(color: AppColors.textMuted))
                        : Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(_borrower!.fullName,
                                  style: const TextStyle(
                                      fontWeight: FontWeight.w800)),
                              Text(
                                  '${_borrower!.accountNumber} - available ${formatCurrency(_borrower!.availableCredit, _borrower!.currency)}',
                                  style: const TextStyle(
                                      color: AppColors.textMuted,
                                      fontSize: 12)),
                            ],
                          ),
                  ),
                  const Icon(Icons.chevron_right_rounded),
                ]),
              ),
            ),
            SizedBox(height: compact ? 16 : 24),
          ],

          // Currency
          const Text('Currency',
              style: TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 15,
                  color: AppColors.textDark)),
          const SizedBox(height: 12),
          Row(
            children: ['USD', 'ZWG'].map((c) {
              final selected = _currency == c;
              return Expanded(
                child: GestureDetector(
                  onTap: () {
                    setState(() {
                      _currency = c;
                      _amountCtrl.text =
                          widget.provider.cartTotal().toStringAsFixed(2);
                    });
                  },
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 180),
                    margin: EdgeInsets.only(right: c == 'USD' ? 8 : 0),
                    padding: EdgeInsets.symmetric(vertical: compact ? 11 : 14),
                    decoration: BoxDecoration(
                      color: selected ? AppColors.primaryBlue : Colors.white,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(
                        color: selected
                            ? AppColors.primaryBlue
                            : const Color(0xFFDDE5EF),
                      ),
                    ),
                    alignment: Alignment.center,
                    child: Text(
                      c == 'USD' ? '🇺🇸  USD' : '🇿🇼  ZWG',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        fontSize: 14,
                        color: selected ? Colors.white : AppColors.textDark,
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),

          SizedBox(height: compact ? 16 : 24),

          // Amount tendered
          const Text('Amount Tendered',
              style: TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 15,
                  color: AppColors.textDark)),
          const SizedBox(height: 8),
          TextField(
            controller: _amountCtrl,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            inputFormatters: [
              FilteringTextInputFormatter.allow(RegExp(r'[\d.]'))
            ],
            style: const TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.w900,
                color: AppColors.primaryBlue),
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              prefixText: '$_currency  ',
              prefixStyle: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 16,
                  fontWeight: FontWeight.w600),
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: const BorderSide(color: Color(0xFFDDE5EF)),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide:
                    const BorderSide(color: AppColors.primaryBlue, width: 2),
              ),
              filled: true,
              fillColor: Colors.white,
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            ),
          ),

          // Quick amount buttons
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _quickAmounts().map((a) {
              return OutlinedButton(
                onPressed: () =>
                    setState(() => _amountCtrl.text = a.toStringAsFixed(2)),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.primaryBlue,
                  side: const BorderSide(color: Color(0xFFDDE5EF)),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                ),
                child: Text(
                  formatCurrency(a, _currency),
                  style: const TextStyle(
                      fontWeight: FontWeight.w700, fontSize: 12),
                ),
              );
            }).toList(),
          ),

          // Change display
          if (_method == 'CASH' && _tendered > _total) ...[
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFFE8F5E9),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFA5D6A7)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.price_check_rounded,
                      color: Color(0xFF2E7D32), size: 24),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Change Due',
                          style: TextStyle(
                              color: Color(0xFF2E7D32),
                              fontSize: 12,
                              fontWeight: FontWeight.w600)),
                      Text(
                        formatCurrency(_change, _currency),
                        style: const TextStyle(
                          color: Color(0xFF1B5E20),
                          fontSize: 24,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: _captureHeldChange,
              icon: Icon(_holdChange
                  ? Icons.check_circle_rounded
                  : Icons.schedule_rounded),
              label: Text(_holdChange
                  ? 'Change held for $_heldChangeName'
                  : 'Leave change for later collection'),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.primaryBlue,
                minimumSize: const Size(double.infinity, 48),
              ),
            ),
          ],

          if (_tendered < _total && _amountCtrl.text.isNotEmpty) ...[
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.errorRed.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                    color: AppColors.errorRed.withValues(alpha: 0.3)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.warning_rounded,
                      color: AppColors.errorRed, size: 18),
                  const SizedBox(width: 10),
                  Text(
                    'Short by ${formatCurrency(_total - _tendered, _currency)}',
                    style: const TextStyle(
                        color: AppColors.errorRed, fontWeight: FontWeight.w700),
                  ),
                ],
              ),
            ),
          ],

          if (_error != null) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.errorRed.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                children: [
                  const Icon(Icons.error_outline,
                      color: AppColors.errorRed, size: 18),
                  const SizedBox(width: 8),
                  Expanded(
                      child: Text(_error!,
                          style: const TextStyle(color: AppColors.errorRed))),
                ],
              ),
            ),
          ],

          SizedBox(height: compact ? 18 : 28),

          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _canConfirm ? _confirm : null,
              icon: _processing
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2.5, color: AppColors.primaryBlue))
                  : const Icon(Icons.check_circle_rounded, size: 22),
              label: Text(
                _processing
                    ? 'Processing…'
                    : 'Confirm ${formatCurrency(_total, _currency)}',
                style:
                    const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.accentYellow,
                foregroundColor: AppColors.primaryBlue,
                disabledBackgroundColor: const Color(0xFFE8ECF0),
                disabledForegroundColor: AppColors.textMuted,
                elevation: 0,
                padding: const EdgeInsets.symmetric(vertical: 18),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14)),
              ),
            ),
          ),
          const SizedBox(height: 12),
        ],
      ),
    );
  }

  // ─── Logic ────────────────────────────────────────────────────────────────

  Future<void> _showBorrowerPicker() async {
    final borrowers = await widget.api.loadCachedBorrowers();
    if (!mounted) return;
    if (borrowers.isEmpty) {
      setState(() => _error =
          'No active borrowers were downloaded. Reopen the shift online after the admin adds borrower accounts.');
      return;
    }
    final searchCtrl = TextEditingController();
    final selected = await showModalBottomSheet<Borrower>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) {
          final needle = searchCtrl.text.trim().toLowerCase();
          final filtered = borrowers
              .where((b) =>
                  needle.isEmpty ||
                  b.fullName.toLowerCase().contains(needle) ||
                  b.phone.toLowerCase().contains(needle) ||
                  b.accountNumber.toLowerCase().contains(needle))
              .toList();
          return SafeArea(
            child: Padding(
              padding: EdgeInsets.only(
                  left: 18,
                  right: 18,
                  top: 18,
                  bottom: MediaQuery.of(ctx).viewInsets.bottom + 18),
              child: SizedBox(
                height: MediaQuery.of(ctx).size.height * .68,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Select Borrower',
                        style: TextStyle(
                            fontSize: 20, fontWeight: FontWeight.w900)),
                    const SizedBox(height: 12),
                    TextField(
                      controller: searchCtrl,
                      autofocus: true,
                      onChanged: (_) => setModalState(() {}),
                      decoration: const InputDecoration(
                          prefixIcon: Icon(Icons.search_rounded),
                          hintText: 'Name, phone or account number'),
                    ),
                    const SizedBox(height: 12),
                    Expanded(
                      child: ListView.separated(
                        itemCount: filtered.length,
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (_, index) {
                          final borrower = filtered[index];
                          final canUse = borrower.isActive &&
                              borrower.currency == _currency &&
                              borrower.availableCredit >= _total;
                          return ListTile(
                            enabled: canUse,
                            title: Text(borrower.fullName,
                                style: const TextStyle(
                                    fontWeight: FontWeight.w800)),
                            subtitle: Text(
                                '${borrower.phone}\n${borrower.accountNumber} - ${formatCurrency(borrower.availableCredit, borrower.currency)} available'),
                            isThreeLine: true,
                            trailing: Icon(canUse
                                ? Icons.chevron_right_rounded
                                : Icons.block_rounded),
                            onTap: canUse
                                ? () => Navigator.pop(ctx, borrower)
                                : null,
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
    searchCtrl.dispose();
    if (selected != null && mounted) {
      setState(() {
        _borrower = selected;
        _error = null;
      });
    }
  }

  Future<void> _captureHeldChange() async {
    final nameCtrl = TextEditingController(text: _heldChangeName);
    final phoneCtrl = TextEditingController(text: _heldChangePhone);
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
            left: 20,
            right: 20,
            top: 18,
            bottom: MediaQuery.of(ctx).viewInsets.bottom + 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Hold Change',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
            const SizedBox(height: 6),
            Text(
                '${formatCurrency(_change, _currency)} will stay in the drawer until collection.'),
            const SizedBox(height: 16),
            TextField(
                controller: nameCtrl,
                autofocus: true,
                decoration: const InputDecoration(labelText: 'Customer name')),
            const SizedBox(height: 12),
            TextField(
                controller: phoneCtrl,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'Customer phone')),
            const SizedBox(height: 18),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: () {
                  if (nameCtrl.text.trim().isEmpty ||
                      phoneCtrl.text.trim().isEmpty) {
                    return;
                  }
                  Navigator.pop(ctx, true);
                },
                icon: const Icon(Icons.schedule_rounded),
                label: const Text('Save for collection'),
              ),
            ),
          ],
        ),
      ),
    );
    if (saved == true && mounted) {
      setState(() {
        _holdChange = true;
        _heldChangeName = nameCtrl.text.trim();
        _heldChangePhone = phoneCtrl.text.trim();
      });
    }
    nameCtrl.dispose();
    phoneCtrl.dispose();
  }

  bool get _canConfirm {
    if (_processing) return false;
    if (_method == 'STORE_CREDIT') {
      return _borrower != null &&
          _borrower!.isActive &&
          _borrower!.currency == _currency &&
          _borrower!.availableCredit >= _total;
    }
    if (_method == 'CASH') return _tendered >= _total;
    return true; // card/mobile always allowed
  }

  List<double> _quickAmounts() {
    final t = _total;
    final rounded = [
      (t / 5).ceil() * 5.0,
      (t / 10).ceil() * 10.0,
      (t / 20).ceil() * 20.0,
      (t / 50).ceil() * 50.0,
    ].where((a) => a > t).toSet().toList();
    rounded.sort();
    return [t, ...rounded.take(3)];
  }

  Future<void> _confirm() async {
    final stockMessage = widget.provider.validateCartStock();
    if (stockMessage != null) {
      setState(() => _error = stockMessage);
      return;
    }
    final proceed = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Confirm Sale',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
              const SizedBox(height: 8),
              Text(
                '${widget.provider.cartItemCount} item(s) - $_method - $_currency',
                style: const TextStyle(color: AppColors.textMuted),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  const Expanded(
                      child: Text('Total',
                          style: TextStyle(fontWeight: FontWeight.w800))),
                  Text(formatCurrency(_total, _currency),
                      style: const TextStyle(
                          color: AppColors.primaryBlue,
                          fontSize: 22,
                          fontWeight: FontWeight.w900)),
                ],
              ),
              if (_method == 'CASH') ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    const Expanded(child: Text('Change')),
                    Text(formatCurrency(_change, _currency),
                        style: const TextStyle(
                            color: Color(0xFF1B5E20),
                            fontWeight: FontWeight.w800)),
                  ],
                ),
                if (_holdChange)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      'Change will be held for $_heldChangeName ($_heldChangePhone)',
                      style: const TextStyle(
                          color: AppColors.primaryBlue,
                          fontWeight: FontWeight.w700),
                    ),
                  ),
              ],
              if (_method == 'STORE_CREDIT' && _borrower != null) ...[
                const SizedBox(height: 8),
                Text(
                    'Charge ${_borrower!.fullName} - available ${formatCurrency(_borrower!.availableCredit, _borrower!.currency)}'),
              ],
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(ctx, false),
                      child: const Text('Cancel'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: () => Navigator.pop(ctx, true),
                      icon: const Icon(Icons.check_rounded),
                      label: const Text('Confirm'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.accentYellow,
                        foregroundColor: AppColors.primaryBlue,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
    if (proceed != true || !mounted) return;
    final latestStockMessage = widget.provider.validateCartStock();
    if (latestStockMessage != null) {
      setState(() => _error = latestStockMessage);
      return;
    }
    setState(() {
      _processing = true;
      _error = null;
    });
    final provider = widget.provider;
    final request = {
      'cashSessionId': widget.session.id,
      if (widget.session.branchId != null) 'branchId': widget.session.branchId,
      'currency': _currency,
      'items': provider.cart
          .map((item) => {
                'productId': item.product.id,
                'productName': item.product.name,
                'quantity': item.quantity,
                'unitPrice': item.unitPrice,
                'discountAmount': item.discountAmount,
              })
          .toList(),
      'payments': [
        {
          'method': _method,
          'currency': _currency,
          'amount': _total,
          'exchangeRate': 1,
          'tendered': _tendered,
          'change': _change,
        }
      ],
      if (_borrower != null && _method == 'STORE_CREDIT')
        'borrowerId': _borrower!.id,
      if (_holdChange) 'heldChangeName': _heldChangeName,
      if (_holdChange) 'heldChangePhone': _heldChangePhone,
      if (_holdChange) 'heldChangeAmount': _change,
    };
    try {
      final sale = await widget.api.completeSale(request);
      if (!mounted) return;
      final receiptItems = List.from(provider.cart);

      // ── Fire-and-forget background print ────────────────────────────────
      _autoPrint(sale: sale, cartItems: receiptItems);

      provider.clearCart();

      // Navigate to receipt, replacing payment screen
      final result = await Navigator.of(context).pushReplacement(
        MaterialPageRoute<String>(
          builder: (_) => ReceiptScreen(
            sale: sale,
            change: _holdChange ? 0 : _change,
            currency: _currency,
            cartItems: receiptItems,
          ),
        ),
      );
      // Pop all the way back to POS with result message
      if (mounted) Navigator.of(context).pop(result);
    } catch (e) {
      setState(() {
        _error = e.toString().replaceFirst('Exception: ', '');
        _processing = false;
      });
    }
  }

  // ─── Background print ─────────────────────────────────────────────────────

  /// Sends the receipt to the Bluetooth printer in the background.
  /// Never throws — any failure is surfaced as a dismissible snackbar.
  void _autoPrint({required Sale sale, required List<dynamic> cartItems}) {
    final user = widget.provider.currentUser;
    final companyName = user != null && user.companyName.isNotEmpty
        ? user.companyName
        : 'RetailZW';
    final companyLines = <String>[
      if (user != null && user.companyAddress.isNotEmpty) user.companyAddress,
      if (user != null && user.companyPhone.isNotEmpty) user.companyPhone,
    ];
    final footer = user != null && user.receiptFooter.isNotEmpty
        ? user.receiptFooter
        : 'Thank you for your business!';

    PrinterService()
        .printReceipt(
      sale: sale,
      cartItems: cartItems,
      companyName: companyName,
      companyLines: companyLines,
      footerText: footer,
      change: _holdChange ? 0 : _change,
      currency: _currency,
    )
        .then((result) {
      if (!mounted) return;
      if (!result.success && result.error != null) {
        // Show a non-blocking snackbar — does not block the sale/receipt flow
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            duration: const Duration(seconds: 5),
            backgroundColor: const Color(0xFF37474F),
            content: Row(
              children: [
                const Icon(Icons.print_disabled_rounded,
                    color: Colors.white70, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Print skipped: ${result.error}',
                    style: const TextStyle(color: Colors.white),
                  ),
                ),
              ],
            ),
            action: SnackBarAction(
              label: 'Settings',
              textColor: AppColors.accentYellow,
              onPressed: () {},
            ),
          ),
        );
      }
    });
  }
}

// ─── Payment method tile ───────────────────────────────────────────────────

class _MethodTile extends StatelessWidget {
  final _PayMethod method;
  final bool selected;
  final bool compact;
  final VoidCallback onTap;

  const _MethodTile(
      {required this.method,
      required this.selected,
      this.compact = false,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: EdgeInsets.symmetric(
            horizontal: compact ? 10 : 16, vertical: compact ? 10 : 14),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.primaryBlue.withValues(alpha: 0.06)
              : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: selected ? AppColors.primaryBlue : const Color(0xFFDDE5EF),
            width: selected ? 2 : 1,
          ),
        ),
        child: Row(
          children: [
            Container(
              width: compact ? 34 : 40,
              height: compact ? 34 : 40,
              decoration: BoxDecoration(
                color: selected
                    ? AppColors.primaryBlue.withValues(alpha: 0.12)
                    : const Color(0xFFF0F4F8),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(method.icon,
                  color: selected ? AppColors.primaryBlue : AppColors.textMuted,
                  size: compact ? 18 : 20),
            ),
            SizedBox(width: compact ? 8 : 14),
            Expanded(
              child: Text(
                method.label,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontWeight: FontWeight.w700,
                  fontSize: compact ? 12 : 15,
                  color: selected ? AppColors.primaryBlue : AppColors.textDark,
                ),
              ),
            ),
            if (selected)
              const Icon(Icons.check_circle_rounded,
                  color: AppColors.primaryBlue, size: 18),
          ],
        ),
      ),
    );
  }
}

class _PayMethod {
  final String value;
  final IconData icon;
  final String label;
  const _PayMethod(this.value, this.icon, this.label);
}
