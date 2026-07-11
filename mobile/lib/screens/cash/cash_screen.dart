import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:printing/printing.dart';
import 'package:provider/provider.dart';

import '../../models/models.dart';
import '../../providers/app_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class CashScreen extends StatefulWidget {
  const CashScreen({super.key});

  @override
  State<CashScreen> createState() => _CashScreenState();
}

class _CashScreenState extends State<CashScreen> {
  final ApiService _api = ApiService();
  bool _busy = false;
  String? _message;
  static const double _thermalPaperWidthMm = 80;
  static const double _thermalLeftMarginMm = 0;
  static const double _thermalRightMarginMm = 18;
  static const double _thermalVerticalMarginMm = 4;

  @override
  void initState() {
    super.initState();
    // Ensure session is visible in this screen even on cold restarts where
    // HomeScreen's _loadInitialData hasn't finished yet.
    WidgetsBinding.instance.addPostFrameCallback((_) => _tryRestoreSession());
  }

  Future<void> _tryRestoreSession() async {
    if (!mounted) return;
    final provider = context.read<AppProvider>();
    if (provider.activeSession != null) return; // already loaded
    try {
      final cached = await _api.loadCachedSession();
      if (!mounted || cached == null) return;
      provider.setSession(cached);
      final products = await _api.loadCachedProducts();
      if (mounted && products.isNotEmpty) provider.setProducts(products);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final rawSession = provider.activeSession;
    final session = rawSession != null &&
            rawSession.id > 0 &&
            rawSession.status.toUpperCase() == 'OPEN'
        ? rawSession
        : null;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('Shift Control',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
        const SizedBox(height: 12),
        if (_message != null)
          Container(
            margin: const EdgeInsets.only(bottom: 12),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
                color: const Color(0xFFFFF7D2),
                borderRadius: BorderRadius.circular(12)),
            child: Text(_message!),
          ),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Icon(session == null ? Icons.lock_open : Icons.verified,
                    color: AppColors.primaryBlue),
                const SizedBox(width: 10),
                Text(
                    session == null
                        ? 'No active shift'
                        : 'Shift #${session.id} open',
                    style: const TextStyle(
                        fontSize: 18, fontWeight: FontWeight.w900)),
              ]),
              const SizedBox(height: 10),
              Text(session == null
                  ? 'Open a shift to download branch products and start selling offline-ready.'
                  : 'Opening float: ${formatCurrency(session.openingFloatUsd, 'USD')} / ${formatCurrency(session.openingFloatZwg, 'ZWG')}'),
              const SizedBox(height: 16),
              if (session == null)
                RetailZWButton(
                    label: 'Open Shift',
                    icon: Icons.play_arrow,
                    loading: _busy,
                    onPressed: _busy ? null : _openShift)
              else
                RetailZWButton(
                  label: 'Close Shift & Print',
                  icon: Icons.print,
                  color: AppColors.primaryBlue,
                  textColor: Colors.white,
                  loading: _busy,
                  onPressed: _busy ? null : () => _closeShift(session),
                ),
            ]),
          ),
        ),
        const SizedBox(height: 12),
        FutureBuilder<int>(
          future: _api.getOfflineQueueCount(),
          builder: (_, snapshot) => Card(
            child: ListTile(
              leading:
                  const Icon(Icons.cloud_queue, color: AppColors.primaryBlue),
              title: const Text('Offline queue'),
              subtitle: Text('${snapshot.data ?? 0} sale(s) waiting to sync'),
              trailing: IconButton(
                icon: const Icon(Icons.sync),
                onPressed: () async {
                  try {
                    final result = await _api.syncOfflineSales();
                    if (mounted) {
                      setState(() => _message = result.hasFailures
                          ? '${result.synced} synced, ${result.pending} still waiting. ${result.message ?? 'They remain safe on this device.'}'
                          : '${result.synced} offline sale(s) synced successfully.');
                    }
                  } catch (e) {
                    if (mounted) setState(() => _message = _friendlyError(e));
                  }
                },
              ),
            ),
          ),
        ),
        if (session != null) ...[
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              leading: const Icon(Icons.monetization_on_rounded,
                  color: AppColors.primaryBlue),
              title: const Text('Change collection'),
              subtitle:
                  const Text('Search customers who left change in the till'),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: _busy ? null : () => _showChangeCollection(session),
            ),
          ),
        ],
      ],
    );
  }

  Future<void> _openShift() async {
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final provider = context.read<AppProvider>();
      provider.clearCart();
      provider.clearSession();
      await _api.clearLocalShiftCache();

      var session = await _api.getActiveSession();
      var openedNew = false;
      if (session == null) {
        final drawers = await _api.getDrawers();
        if (drawers.isEmpty) {
          throw Exception('No cash drawer is configured for this branch.');
        }
        session = await _api.openSession(drawers.first.id, 0, 0);
        openedNew = true;
      }

      final products = await _api.downloadShiftProducts();
      final borrowers = await _api.downloadShiftBorrowers();
      await _api.getOpenChange();
      if (products.isEmpty) {
        throw Exception(
            'Shift is open in the cloud, but no branch products were downloaded. Check stock setup before selling.');
      }
      final cloudSession = session;
      if (!mounted) return;
      provider.setSession(cloudSession);
      provider.setProducts(products);
      provider.setOnline(true);
      setState(() => _message =
          'Shift #${cloudSession.id} ${openedNew ? 'opened' : 'restored'} in the cloud. ${products.length} products and ${borrowers.length} borrowers downloaded.');
    } catch (e) {
      if (mounted) setState(() => _message = _friendlyError(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _closeShift(CashSession session) async {
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final sync = await _api.syncOfflineSales();
      final pending = await _api.getOfflineQueueCount();
      if (pending > 0) {
        throw Exception(
            '${sync.synced} synced, but $pending offline sales are still waiting. ${sync.message ?? 'Sync them before closing this shift.'}');
      }
      final sales = await _api.getShiftSales();
      await _printShiftReport(session, sales);
      final usd = sales.where((s) => s.currency == 'USD').fold<double>(
          session.openingFloatUsd, (sum, sale) => sum + sale.grandTotal);
      final zwg = sales.where((s) => s.currency == 'ZWG').fold<double>(
          session.openingFloatZwg, (sum, sale) => sum + sale.grandTotal);
      await _api.closeSession(session.id, usd, zwg);
      if (!mounted) return;
      final provider = context.read<AppProvider>();
      provider.clearCart();
      provider.clearSession();
      await _api.clearLocalShiftCache();
      setState(() => _message =
          'Shift closed in the cloud. Downloaded products and local shift cache cleared.');
    } catch (e) {
      if (mounted) setState(() => _message = _friendlyError(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _friendlyError(Object error) {
    if (error is ApiException) return error.message;
    if (error is MissingPluginException) {
      return 'Local storage is not ready on this install. Fully stop the app and reinstall it, then open the shift again.';
    }
    if (error.toString().contains('databaseFactory not initialized')) {
      return 'Local storage is starting up. Fully close and reopen RetailZW POS, then open the shift again.';
    }
    if (error is SocketException || error is TimeoutException) {
      return 'Cannot reach the RetailZW server. Check internet or server address, then try again. Existing offline sales remain safe.';
    }
    return error.toString().replaceFirst('Exception: ', '');
  }

  Future<void> _showChangeCollection(CashSession session) async {
    final searchCtrl = TextEditingController();
    var records = await _api.getOpenChange();
    if (!mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setModalState) {
          final needle = searchCtrl.text.trim().toLowerCase();
          final filtered = records
              .where((record) =>
                  needle.isEmpty ||
                  record.customerName.toLowerCase().contains(needle) ||
                  record.phone.toLowerCase().contains(needle) ||
                  record.referenceNumber.toLowerCase().contains(needle))
              .toList();
          return SafeArea(
            child: Padding(
              padding: EdgeInsets.only(
                  left: 18,
                  right: 18,
                  top: 18,
                  bottom: MediaQuery.of(ctx).viewInsets.bottom + 18),
              child: SizedBox(
                height: MediaQuery.of(ctx).size.height * .7,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Collect Held Change',
                        style: TextStyle(
                            fontSize: 20, fontWeight: FontWeight.w900)),
                    const SizedBox(height: 12),
                    TextField(
                      controller: searchCtrl,
                      autofocus: true,
                      onChanged: (_) => setModalState(() {}),
                      decoration: const InputDecoration(
                          prefixIcon: Icon(Icons.search_rounded),
                          hintText: 'Name, phone or reference'),
                    ),
                    const SizedBox(height: 12),
                    Expanded(
                      child: filtered.isEmpty
                          ? const Center(
                              child: Text('No uncollected change found.'))
                          : ListView.separated(
                              itemCount: filtered.length,
                              separatorBuilder: (_, __) =>
                                  const Divider(height: 1),
                              itemBuilder: (_, index) {
                                final record = filtered[index];
                                return ListTile(
                                  title: Text(record.customerName,
                                      style: const TextStyle(
                                          fontWeight: FontWeight.w800)),
                                  subtitle: Text(
                                      '${record.phone}\n${record.referenceNumber}'),
                                  isThreeLine: true,
                                  trailing: Text(
                                      formatCurrency(
                                          record.amount, record.currency),
                                      style: const TextStyle(
                                          color: AppColors.primaryBlue,
                                          fontWeight: FontWeight.w900)),
                                  onTap: () async {
                                    final confirmed = await showDialog<bool>(
                                      context: ctx,
                                      builder: (dialogCtx) => AlertDialog(
                                        title: const Text('Confirm collection'),
                                        content: Text(
                                            'Pay ${formatCurrency(record.amount, record.currency)} to ${record.customerName}?'),
                                        actions: [
                                          TextButton(
                                              onPressed: () => Navigator.pop(
                                                  dialogCtx, false),
                                              child: const Text('Cancel')),
                                          ElevatedButton(
                                              onPressed: () => Navigator.pop(
                                                  dialogCtx, true),
                                              child: const Text('Collect')),
                                        ],
                                      ),
                                    );
                                    if (confirmed != true) return;
                                    await _api.collectHeldChange(
                                        record, session.id);
                                    records = await _api.getOpenChange();
                                    setModalState(() {});
                                  },
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
  }

  Future<void> _printShiftReport(CashSession session, List<Sale> sales) async {
    final pdf = pw.Document();
    final usd = sales
        .where((s) => s.currency == 'USD')
        .fold<double>(0, (sum, sale) => sum + sale.grandTotal);
    final zwg = sales
        .where((s) => s.currency == 'ZWG')
        .fold<double>(0, (sum, sale) => sum + sale.grandTotal);
    final productTotals = <String, double>{};
    for (final sale in sales) {
      for (final item in sale.items) {
        productTotals.update(item.productName, (value) => value + item.quantity,
            ifAbsent: () => item.quantity);
      }
    }
    pdf.addPage(
      pw.Page(
        pageFormat: _shiftReportFormat(productTotals.length),
        margin: pw.EdgeInsets.fromLTRB(
          _thermalLeftMarginMm * PdfPageFormat.mm,
          _thermalVerticalMarginMm * PdfPageFormat.mm,
          _thermalRightMarginMm * PdfPageFormat.mm,
          _thermalVerticalMarginMm * PdfPageFormat.mm,
        ),
        build: (_) => pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            pw.Text('RetailZW Shift Report',
                style:
                    pw.TextStyle(fontSize: 16, fontWeight: pw.FontWeight.bold)),
            pw.Text('Shift #${session.id}',
                style: const pw.TextStyle(fontSize: 10)),
            pw.Text('Printed: ${DateTime.now()}',
                style: const pw.TextStyle(fontSize: 10)),
            pw.Divider(),
            pw.Text('Sales count: ${sales.length}',
                style: const pw.TextStyle(fontSize: 10)),
            pw.Text('USD sales: ${usd.toStringAsFixed(2)}',
                style: const pw.TextStyle(fontSize: 10)),
            pw.Text('ZWG sales: ${zwg.toStringAsFixed(2)}',
                style: const pw.TextStyle(fontSize: 10)),
            pw.Divider(),
            pw.Text('Goods sold',
                style:
                    pw.TextStyle(fontSize: 11, fontWeight: pw.FontWeight.bold)),
            ...productTotals.entries.map((e) => pw.Text(
                '${e.key}: ${e.value.toStringAsFixed(0)}',
                style: const pw.TextStyle(fontSize: 9))),
            pw.Divider(),
            pw.Text('Expected register',
                style:
                    pw.TextStyle(fontSize: 11, fontWeight: pw.FontWeight.bold)),
            pw.Text('USD ${(session.openingFloatUsd + usd).toStringAsFixed(2)}',
                style: const pw.TextStyle(fontSize: 10)),
            pw.Text('ZWG ${(session.openingFloatZwg + zwg).toStringAsFixed(2)}',
                style: const pw.TextStyle(fontSize: 10)),
          ],
        ),
      ),
    );
    await Printing.layoutPdf(
      format: _shiftReportFormat(productTotals.length),
      onLayout: (_) async => pdf.save(),
    );
  }

  PdfPageFormat _shiftReportFormat(int productCount) {
    final heightMm = (140 + (productCount * 6)).clamp(180, 900).toDouble();
    return PdfPageFormat(
      _thermalPaperWidthMm * PdfPageFormat.mm,
      heightMm * PdfPageFormat.mm,
      marginAll: 0,
    );
  }
}
