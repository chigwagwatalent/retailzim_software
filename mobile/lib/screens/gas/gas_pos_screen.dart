import 'package:flutter/material.dart';

import '../../models/models.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class GasPosScreen extends StatefulWidget {
  const GasPosScreen({super.key});

  @override
  State<GasPosScreen> createState() => _GasPosScreenState();
}

class _GasPosScreenState extends State<GasPosScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _message;
  UserInfo? _user;
  GasShift? _shift;
  GasDashboard? _dashboard;
  List<GasTank> _tanks = const [];
  List<GasPrice> _prices = const [];
  List<GasSale> _sales = const [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _message = null;
    });
    try {
      final user = await _api.getSavedUser();
      if (user?.branchId == null) {
        throw ApiException('No gas branch assigned to this cashier.',
            statusCode: 400);
      }
      if (user?.isGasBranch != true) {
        throw ApiException(
            'This cashier is assigned to a retail branch, not LPG gas.',
            statusCode: 403);
      }
      final data = await _api.getGasBootstrap(user!.branchId!);
      setState(() {
        _user = user;
        _shift = data['currentShift'] as GasShift?;
        _dashboard = data['dashboard'] as GasDashboard?;
        _tanks = (data['tanks'] as List).cast<GasTank>();
        _prices = (data['prices'] as List).cast<GasPrice>();
        _sales = (data['sales'] as List).cast<GasSale>();
      });
    } catch (e) {
      setState(() => _message = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openShift() async {
    final branchId = _user?.branchId;
    if (branchId == null) return;
    try {
      await _api.openGasShift(branchId);
      await _load();
      setState(() => _message = 'Gas shift opened.');
    } catch (e) {
      setState(() => _message = e.toString());
    }
  }

  Future<void> _closeShift() async {
    final branchId = _user?.branchId;
    final shiftId = _shift?.id;
    if (branchId == null || shiftId == null) return;
    try {
      await _api.closeGasShift(branchId, shiftId);
      await _load();
      setState(() => _message = 'Gas shift closed.');
    } catch (e) {
      setState(() => _message = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _Header(
            shift: _shift,
            onOpen: _openShift,
            onClose: _closeShift,
            onExpense: () => _showExpenseModal(),
          ),
          const SizedBox(height: 12),
          if (_message != null) _Notice(message: _message!),
          const SizedBox(height: 12),
          _AccountingStrip(dashboard: _dashboard),
          const SizedBox(height: 12),
          _ShiftSummary(shift: _shift),
          const SizedBox(height: 16),
          Text('LPG Tanks', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          if (_tanks.isEmpty)
            const Text('No gas tanks configured for this branch.',
                style: TextStyle(color: AppColors.textMuted))
          else
            ..._tanks.map((tank) => _TankCard(
                  tank: tank,
                  onSell: _shift == null ? null : () => _showSaleModal(tank),
                  onRestock: () => _showRestockModal(tank),
                )),
          const SizedBox(height: 16),
          Text('Shift Sales', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          if (_sales.isEmpty)
            const Text('No gas sales in this shift yet.',
                style: TextStyle(color: AppColors.textMuted))
          else
            ..._sales.map((sale) => ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(sale.receiptNumber),
                  subtitle: Text(
                      '${sale.quantityKg.toStringAsFixed(3)} kg - ${sale.paymentMethod}'),
                  trailing: Text(
                      '${sale.currency} ${sale.total.toStringAsFixed(2)}',
                      style: const TextStyle(fontWeight: FontWeight.w800)),
                )),
        ],
      ),
    );
  }

  GasPrice? _priceFor(String currency) {
    for (final price in _prices) {
      if (price.currency == currency) return price;
    }
    return _prices.isEmpty ? null : _prices.first;
  }

  Future<void> _showSaleModal(GasTank tank) async {
    final qty = TextEditingController();
    final name = TextEditingController();
    final phone = TextEditingController();
    final reference = TextEditingController();
    String currency = _prices.isEmpty ? 'USD' : _prices.first.currency;
    String paymentMethod = 'CASH';
    double quantity = 0;
    final presets = _dashboard?.lpgWeightPresetsKg ??
        const [1.0, 2.0, 3.0, 5.0, 9.0, 14.0, 19.0, 48.0];
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) {
          final price = _priceFor(currency)?.pricePerKg ?? 0;
          final total = quantity * price;
          void setQty(double value) {
            qty.text = value
                .toStringAsFixed(value.truncateToDouble() == value ? 0 : 3);
            setModalState(() => quantity = value);
          }

          return Padding(
            padding: EdgeInsets.only(
              left: 16,
              right: 16,
              top: 16,
              bottom: MediaQuery.of(context).viewInsets.bottom + 16,
            ),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Sell ${tank.productName}',
                      style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: presets
                        .map((kg) => ChoiceChip(
                              selected: quantity == kg,
                              label: Text(
                                  '${kg.toStringAsFixed(kg == kg.truncateToDouble() ? 0 : 1)} kg'),
                              onSelected: (_) => setQty(kg),
                            ))
                        .toList(),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: qty,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Quantity kg'),
                    onChanged: (v) =>
                        setModalState(() => quantity = double.tryParse(v) ?? 0),
                  ),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<String>(
                    initialValue: currency,
                    items: _prices
                        .map((p) => DropdownMenuItem(
                            value: p.currency,
                            child: Text('${p.currency} @ ${p.pricePerKg}/kg')))
                        .toList(),
                    onChanged: (v) =>
                        setModalState(() => currency = v ?? currency),
                    decoration: const InputDecoration(labelText: 'Currency'),
                  ),
                  const SizedBox(height: 8),
                  DropdownButtonFormField<String>(
                    initialValue: paymentMethod,
                    items: const [
                      'CASH',
                      'ECOCASH',
                      'ZIPIT',
                      'SWIPE',
                      'BANK_TRANSFER'
                    ]
                        .map((m) => DropdownMenuItem(value: m, child: Text(m)))
                        .toList(),
                    onChanged: (v) =>
                        setModalState(() => paymentMethod = v ?? 'CASH'),
                    decoration:
                        const InputDecoration(labelText: 'Payment method'),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                      controller: reference,
                      decoration: const InputDecoration(
                          labelText: 'Payment reference')),
                  const SizedBox(height: 8),
                  TextField(
                      controller: name,
                      decoration:
                          const InputDecoration(labelText: 'Customer name')),
                  const SizedBox(height: 8),
                  TextField(
                      controller: phone,
                      keyboardType: TextInputType.phone,
                      decoration:
                          const InputDecoration(labelText: 'Customer phone')),
                  const SizedBox(height: 14),
                  Text('$currency ${total.toStringAsFixed(2)}',
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                          fontSize: 24, fontWeight: FontWeight.w900)),
                  const SizedBox(height: 14),
                  ElevatedButton(
                    onPressed: quantity <= 0 || quantity > tank.currentKg
                        ? null
                        : () async {
                            final branchId = _user?.branchId;
                            if (branchId == null) return;
                            Navigator.pop(context);
                            try {
                              await _api.completeGasSale(
                                branchId: branchId,
                                tankId: tank.id,
                                quantityKg: quantity,
                                currency: currency,
                                estimatedTotal: total,
                                paymentMethod: paymentMethod,
                                paymentReference: reference.text,
                                customerName: name.text,
                                customerPhone: phone.text,
                              );
                              await _load();
                            } catch (e) {
                              setState(() => _message = e.toString());
                            }
                          },
                    child: const Text('Complete Gas Sale'),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _showRestockModal(GasTank tank) async {
    final qty = TextEditingController();
    final cost = TextEditingController(text: '0');
    final supplier = TextEditingController();
    final invoice = TextEditingController();
    String currency = 'USD';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 16,
          bottom: MediaQuery.of(context).viewInsets.bottom + 16,
        ),
        child: StatefulBuilder(
          builder: (context, setModalState) => SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Receive ${tank.name}',
                    style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 12),
                TextField(
                    controller: qty,
                    keyboardType: TextInputType.number,
                    decoration:
                        const InputDecoration(labelText: 'Quantity kg')),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  initialValue: currency,
                  items: const ['USD', 'ZWG']
                      .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                      .toList(),
                  onChanged: (v) => setModalState(() => currency = v ?? 'USD'),
                  decoration: const InputDecoration(labelText: 'Currency'),
                ),
                const SizedBox(height: 8),
                TextField(
                    controller: cost,
                    keyboardType: TextInputType.number,
                    decoration:
                        const InputDecoration(labelText: 'Unit cost / kg')),
                const SizedBox(height: 8),
                TextField(
                    controller: supplier,
                    decoration: const InputDecoration(labelText: 'Supplier')),
                const SizedBox(height: 8),
                TextField(
                    controller: invoice,
                    decoration:
                        const InputDecoration(labelText: 'Invoice reference')),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () async {
                    final branchId = _user?.branchId;
                    final quantity = double.tryParse(qty.text.trim()) ?? 0;
                    final unitCost = double.tryParse(cost.text.trim()) ?? 0;
                    if (branchId == null || quantity <= 0) return;
                    Navigator.pop(context);
                    try {
                      await _api.restockGasTank(
                        branchId: branchId,
                        tankId: tank.id,
                        quantityKg: quantity,
                        currency: currency,
                        unitCost: unitCost,
                        supplierName: supplier.text,
                        supplierInvoice: invoice.text,
                      );
                      await _load();
                    } catch (e) {
                      setState(() => _message = e.toString());
                    }
                  },
                  child: const Text('Save Stock Received'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _showExpenseModal() async {
    final category = TextEditingController();
    final description = TextEditingController();
    final amount = TextEditingController();
    final reference = TextEditingController();
    String currency = 'USD';
    String paymentMethod = 'CASH';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 16,
          bottom: MediaQuery.of(context).viewInsets.bottom + 16,
        ),
        child: StatefulBuilder(
          builder: (context, setModalState) => SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Gas Expense',
                    style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 12),
                TextField(
                    controller: category,
                    decoration: const InputDecoration(
                        labelText: 'Category',
                        hintText: 'Transport, ZERA, Maintenance')),
                const SizedBox(height: 8),
                TextField(
                    controller: amount,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Amount')),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  initialValue: currency,
                  items: const ['USD', 'ZWG']
                      .map((c) => DropdownMenuItem(value: c, child: Text(c)))
                      .toList(),
                  onChanged: (v) => setModalState(() => currency = v ?? 'USD'),
                  decoration: const InputDecoration(labelText: 'Currency'),
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  initialValue: paymentMethod,
                  items: const [
                    'CASH',
                    'ECOCASH',
                    'ZIPIT',
                    'SWIPE',
                    'BANK_TRANSFER'
                  ]
                      .map((m) => DropdownMenuItem(value: m, child: Text(m)))
                      .toList(),
                  onChanged: (v) =>
                      setModalState(() => paymentMethod = v ?? 'CASH'),
                  decoration:
                      const InputDecoration(labelText: 'Payment method'),
                ),
                const SizedBox(height: 8),
                TextField(
                    controller: reference,
                    decoration: const InputDecoration(labelText: 'Reference')),
                const SizedBox(height: 8),
                TextField(
                    controller: description,
                    decoration:
                        const InputDecoration(labelText: 'Description')),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () async {
                    final branchId = _user?.branchId;
                    final value = double.tryParse(amount.text.trim()) ?? 0;
                    if (branchId == null || value <= 0) return;
                    Navigator.pop(context);
                    try {
                      await _api.recordGasExpense(
                        branchId: branchId,
                        category: category.text,
                        description: description.text,
                        amount: value,
                        currency: currency,
                        paymentMethod: paymentMethod,
                        reference: reference.text,
                      );
                      await _load();
                    } catch (e) {
                      setState(() => _message = e.toString());
                    }
                  },
                  child: const Text('Save Expense'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  final GasShift? shift;
  final VoidCallback onOpen;
  final VoidCallback onClose;
  final VoidCallback onExpense;

  const _Header({
    required this.shift,
    required this.onOpen,
    required this.onClose,
    required this.onExpense,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Icon(Icons.local_gas_station, color: AppColors.primaryBlue),
        const SizedBox(width: 8),
        const Expanded(
          child: Text('LPG Gas POS',
              style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900)),
        ),
        IconButton(
          onPressed: onExpense,
          icon: const Icon(Icons.receipt_long),
          tooltip: 'Expense',
        ),
        if (shift == null)
          ElevatedButton.icon(
            onPressed: onOpen,
            icon: const Icon(Icons.play_arrow),
            label: const Text('Open'),
          )
        else
          OutlinedButton.icon(
            onPressed: onClose,
            icon: const Icon(Icons.lock_outline),
            label: const Text('Close'),
          ),
      ],
    );
  }
}

class _Notice extends StatelessWidget {
  final String message;
  const _Notice({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF5D6),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(message),
    );
  }
}

class _AccountingStrip extends StatelessWidget {
  final GasDashboard? dashboard;
  const _AccountingStrip({required this.dashboard});

  @override
  Widget build(BuildContext context) {
    final d = dashboard;
    return Row(
      children: [
        Expanded(
            child: _MiniMetric(
                label: 'Kg sold',
                value: '${(d?.soldKgToday ?? 0).toStringAsFixed(3)} kg')),
        const SizedBox(width: 8),
        Expanded(
            child: _MiniMetric(
                label: 'USD margin',
                value: 'USD ${(d?.marginUsdToday ?? 0).toStringAsFixed(2)}')),
        const SizedBox(width: 8),
        Expanded(
            child: _MiniMetric(
                label: 'ZWG margin',
                value: 'ZWG ${(d?.marginZwgToday ?? 0).toStringAsFixed(2)}')),
      ],
    );
  }
}

class _MiniMetric extends StatelessWidget {
  final String label;
  final String value;
  const _MiniMetric({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 11,
                  fontWeight: FontWeight.w700)),
          const SizedBox(height: 4),
          Text(value,
              style:
                  const TextStyle(fontSize: 13, fontWeight: FontWeight.w900)),
        ],
      ),
    );
  }
}

class _ShiftSummary extends StatelessWidget {
  final GasShift? shift;
  const _ShiftSummary({required this.shift});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Row(
        children: [
          Icon(shift == null ? Icons.lock_open : Icons.verified,
              color: AppColors.primaryBlue),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              shift == null
                  ? 'No gas shift open'
                  : '${shift!.shiftNumber}\n${shift!.totalKgSold.toStringAsFixed(3)} kg sold',
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
          Text(shift == null
              ? 'Ready'
              : 'USD ${shift!.totalUsd.toStringAsFixed(2)}'),
        ],
      ),
    );
  }
}

class _TankCard extends StatelessWidget {
  final GasTank tank;
  final VoidCallback? onSell;
  final VoidCallback onRestock;

  const _TankCard({
    required this.tank,
    required this.onSell,
    required this.onRestock,
  });

  @override
  Widget build(BuildContext context) {
    final fill = tank.capacityKg <= 0
        ? 0.0
        : (tank.currentKg / tank.capacityKg).clamp(0.0, 1.0);
    final low =
        tank.reorderLevelKg > 0 && tank.currentKg <= tank.reorderLevelKg;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                    child: Text(tank.name,
                        style: const TextStyle(
                            fontWeight: FontWeight.w900, fontSize: 16))),
                if (low)
                  const Padding(
                    padding: EdgeInsets.only(right: 8),
                    child: Icon(Icons.warning_amber_rounded,
                        color: AppColors.warningOrange, size: 18),
                  ),
                Text('${tank.currentKg.toStringAsFixed(3)} kg',
                    style: const TextStyle(
                        color: AppColors.primaryBlue,
                        fontWeight: FontWeight.w900)),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              low
                  ? 'Reorder at ${tank.reorderLevelKg.toStringAsFixed(3)} kg'
                  : 'Capacity ${tank.capacityKg.toStringAsFixed(3)} kg',
              style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
            ),
            const SizedBox(height: 8),
            LinearProgressIndicator(value: fill),
            const SizedBox(height: 10),
            Row(
              children: [
                OutlinedButton.icon(
                    onPressed: onRestock,
                    icon: const Icon(Icons.add),
                    label: const Text('Restock')),
                const SizedBox(width: 8),
                ElevatedButton.icon(
                    onPressed: onSell,
                    icon: const Icon(Icons.local_gas_station),
                    label: const Text('Sell LPG')),
              ],
            )
          ],
        ),
      ),
    );
  }
}
