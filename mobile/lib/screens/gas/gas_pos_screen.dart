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
      final branchId = user?.branchId;
      if (branchId == null) {
        throw ApiException('No branch assigned.', statusCode: 400);
      }
      final data = await _api.getGasBootstrap(branchId);
      setState(() {
        _user = user;
        _shift = data['currentShift'] as GasShift?;
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
      final shift = await _api.openGasShift(branchId);
      setState(() {
        _shift = shift;
        _message = 'Gas shift opened.';
      });
      await _load();
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
      setState(() {
        _shift = null;
        _message = 'Gas shift closed.';
      });
      await _load();
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
          Row(
            children: [
              const Icon(Icons.local_gas_station, color: AppColors.primaryBlue),
              const SizedBox(width: 8),
              const Expanded(
                child: Text('Gas POS',
                    style:
                        TextStyle(fontSize: 24, fontWeight: FontWeight.w900)),
              ),
              if (_shift == null)
                ElevatedButton.icon(
                  onPressed: _openShift,
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('Open Shift'),
                )
              else
                OutlinedButton.icon(
                  onPressed: _closeShift,
                  icon: const Icon(Icons.lock_outline),
                  label: const Text('Close'),
                ),
            ],
          ),
          const SizedBox(height: 12),
          if (_message != null)
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: const Color(0xFFFFF5D6),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(_message!),
            ),
          const SizedBox(height: 12),
          _ShiftSummary(shift: _shift),
          const SizedBox(height: 16),
          Text('Tanks', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
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
                  subtitle: Text('${sale.quantityKg.toStringAsFixed(3)} kg'),
                  trailing: Text(
                      '${sale.currency} ${sale.total.toStringAsFixed(2)}',
                      style: const TextStyle(fontWeight: FontWeight.w800)),
                )),
        ],
      ),
    );
  }

  Future<void> _showSaleModal(GasTank tank) async {
    final qty = TextEditingController();
    final name = TextEditingController();
    final phone = TextEditingController();
    String currency = 'USD';
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (context) => StatefulBuilder(
        builder: (context, setModalState) => Padding(
          padding: EdgeInsets.only(
            left: 16,
            right: 16,
            top: 16,
            bottom: MediaQuery.of(context).viewInsets.bottom + 16,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Sell ${tank.productName}',
                  style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 12),
              TextField(
                  controller: qty,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Quantity kg')),
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                value: currency,
                items: _prices
                    .map((p) => DropdownMenuItem(
                        value: p.currency,
                        child: Text('${p.currency} @ ${p.pricePerKg}/kg')))
                    .toList(),
                onChanged: (v) => setModalState(() => currency = v ?? 'USD'),
                decoration: const InputDecoration(labelText: 'Currency'),
              ),
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
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () async {
                  final branchId = _user?.branchId;
                  final quantity = double.tryParse(qty.text.trim()) ?? 0;
                  if (branchId == null || quantity <= 0) return;
                  Navigator.pop(context);
                  try {
                    await _api.completeGasSale(
                      branchId: branchId,
                      tankId: tank.id,
                      quantityKg: quantity,
                      currency: currency,
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
      ),
    );
  }

  Future<void> _showRestockModal(GasTank tank) async {
    final qty = TextEditingController();
    final supplier = TextEditingController();
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
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Restock ${tank.name}',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 12),
            TextField(
                controller: qty,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Quantity kg')),
            const SizedBox(height: 8),
            TextField(
                controller: supplier,
                decoration: const InputDecoration(labelText: 'Supplier')),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () async {
                final branchId = _user?.branchId;
                final quantity = double.tryParse(qty.text.trim()) ?? 0;
                if (branchId == null || quantity <= 0) return;
                Navigator.pop(context);
                try {
                  await _api.restockGasTank(
                    branchId: branchId,
                    tankId: tank.id,
                    quantityKg: quantity,
                    supplierName: supplier.text,
                  );
                  await _load();
                } catch (e) {
                  setState(() => _message = e.toString());
                }
              },
              child: const Text('Save Restock'),
            ),
          ],
        ),
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

  const _TankCard(
      {required this.tank, required this.onSell, required this.onRestock});

  @override
  Widget build(BuildContext context) {
    final fill = tank.capacityKg <= 0
        ? 0.0
        : (tank.currentKg / tank.capacityKg).clamp(0.0, 1.0);
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
                Text('${tank.currentKg.toStringAsFixed(3)} kg',
                    style: const TextStyle(
                        color: AppColors.primaryBlue,
                        fontWeight: FontWeight.w900)),
              ],
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
                    label: const Text('Sell')),
              ],
            )
          ],
        ),
      ),
    );
  }
}
