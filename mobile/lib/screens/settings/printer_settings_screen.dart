import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:print_bluetooth_thermal/print_bluetooth_thermal.dart';
import 'package:printing/printing.dart';

import '../../models/models.dart';
import '../../services/printer_service.dart';
import '../../widgets/common_widgets.dart';

/// Screen for pairing and managing a Bluetooth receipt printer.
///
/// The user scans for paired Bluetooth devices, taps one to connect,
/// and the chosen address is persisted via [PrinterService].
class PrinterSettingsScreen extends StatefulWidget {
  const PrinterSettingsScreen({super.key});

  @override
  State<PrinterSettingsScreen> createState() => _PrinterSettingsScreenState();
}

class _PrinterSettingsScreenState extends State<PrinterSettingsScreen> {
  final _printer = PrinterService();

  List<BluetoothInfo> _devices = [];
  List<Printer> _systemPrinters = [];
  bool _loadingDevices = false;
  String? _connectingTo;
  bool _connected = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    await _printer.init();
    if (_printer.usesSystemPrinter) {
      await _loadSystemPrinters();
      return;
    }
    await _ensureBluetoothPermissions();
    final connected = await _printer.isConnected;
    if (mounted) setState(() => _connected = connected);
    await _loadDevices();
  }

  Future<void> _loadSystemPrinters() async {
    if (!mounted) return;
    setState(() {
      _loadingDevices = true;
      _error = null;
    });
    final printers = await _printer.getSystemPrinters();
    if (!mounted) return;
    setState(() {
      _systemPrinters = printers;
      _loadingDevices = false;
      _connected = _printer.savedSystemPrinterUrl != null &&
          printers.any((p) => p.url == _printer.savedSystemPrinterUrl);
      if (printers.isEmpty) {
        _error =
            'No printers are installed. Add a printer in Windows Settings.';
      }
    });
  }

  Future<void> _selectSystemPrinter(Printer printer) async {
    setState(() {
      _connectingTo = printer.url;
      _error = null;
    });
    await _printer.selectSystemPrinter(printer);
    if (!mounted) return;
    setState(() {
      _connectingTo = null;
      _connected = true;
    });
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      backgroundColor: const Color(0xFF2E7D32),
      content: Text('${printer.name} selected for receipts.'),
    ));
  }

  Future<void> _setWindowsPrinterMode(bool raw) async {
    await _printer.setWindowsPrinterMode(raw: raw);
    if (mounted) setState(() {});
  }

  Future<void> _setCutterCommand(String? command) async {
    if (command == null) return;
    await _printer.setCutterCommand(command);
    if (mounted) setState(() {});
  }

  Future<void> _testCutter() async {
    final result = await _printer.testWindowsCutter();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      backgroundColor:
          result.success ? const Color(0xFF2E7D32) : AppColors.errorRed,
      content: Text(
        result.success
            ? 'Cutter command sent. Check the printer.'
            : result.error ?? 'Cutter test failed.',
        style:
            const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
      ),
    ));
  }

  Future<void> _loadDevices() async {
    if (!mounted) return;
    setState(() {
      _loadingDevices = true;
      _error = null;
    });
    try {
      final permissionsGranted = await _ensureBluetoothPermissions();
      if (!permissionsGranted) {
        if (mounted) {
          setState(() {
            _loadingDevices = false;
            _error =
                'Bluetooth permission is required to find and connect printers.';
          });
        }
        return;
      }
      final enabled = await _printer.isBluetoothEnabled;
      if (!enabled) {
        if (mounted) {
          setState(() {
            _loadingDevices = false;
            _error = 'Bluetooth is off. Please turn it on in device settings.';
          });
        }
        return;
      }
      final devices = await _printer.getPairedDevices();
      if (mounted) {
        setState(() {
          _devices = devices;
          _loadingDevices = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _loadingDevices = false;
          _error = 'Could not load devices: $e';
        });
      }
    }
  }

  Future<void> _connectTo(BluetoothInfo device) async {
    if (!await _ensureBluetoothPermissions()) {
      if (mounted) {
        setState(() => _error =
            'Allow Bluetooth permission in phone settings, then try again.');
      }
      return;
    }
    setState(() {
      _connectingTo = device.macAdress;
      _error = null;
    });
    final ok = await _printer.connect(device.macAdress);
    final connected = ok || await _printer.isConnected;
    if (mounted) {
      setState(() {
        _connectingTo = null;
        _connected = connected;
      });
      if (connected) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: const Color(0xFF2E7D32),
            content: Row(
              children: [
                const Icon(Icons.check_circle_rounded,
                    color: Colors.white, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Connected to ${device.name}',
                    style: const TextStyle(
                        color: Colors.white, fontWeight: FontWeight.w600),
                  ),
                ),
              ],
            ),
          ),
        );
      } else {
        setState(() => _error =
            'Failed to connect to ${device.name}. Is it on and in range?');
      }
    }
  }

  Future<void> _disconnect() async {
    if (_printer.usesSystemPrinter) {
      await _printer.forgetSystemPrinter();
      if (mounted) setState(() => _connected = false);
      return;
    }
    await _printer.disconnect();
    if (mounted) setState(() => _connected = false);
  }

  Future<void> _forget() async {
    await _printer.forgetPrinter();
    await _disconnect();
    if (mounted) setState(() {});
  }

  Future<void> _testPrint() async {
    final result = await _printer.printReceipt(
      sale: _dummySale(),
      cartItems: _dummyCart(),
      companyName: 'RetailZW',
      companyLines: const ['Test Receipt', 'Printer connectivity check'],
      footerText: 'Printer test - OK!',
      change: 0,
      currency: 'USD',
    );
    if (!mounted) return;
    if (result.success) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        backgroundColor: Color(0xFF2E7D32),
        content: Text('Test receipt printed!',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
      ));
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        backgroundColor: AppColors.errorRed,
        content: Text(result.error ?? 'Print failed',
            style: const TextStyle(
                color: Colors.white, fontWeight: FontWeight.w600)),
      ));
    }
  }

  Future<bool> _ensureBluetoothPermissions() async {
    final statuses = await [
      Permission.bluetoothConnect,
      Permission.bluetoothScan,
    ].request();
    return statuses.values.every((status) => status.isGranted);
  }

  @override
  Widget build(BuildContext context) {
    if (_printer.usesSystemPrinter) return _buildSystemPrinterScreen();
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.primaryBlue,
        foregroundColor: Colors.white,
        elevation: 0,
        title: const Text('Bluetooth Printer',
            style: TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          IconButton(
            onPressed: _loadDevices,
            icon: _loadingDevices
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                        strokeWidth: 2.5, color: Colors.white))
                : const Icon(Icons.refresh_rounded),
            tooltip: 'Refresh devices',
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // ── Status card ────────────────────────────────────────────────
            _statusCard(),
            const SizedBox(height: 20),

            // ── Error banner ───────────────────────────────────────────────
            if (_error != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.errorRed.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                      color: AppColors.errorRed.withValues(alpha: 0.3)),
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
              const SizedBox(height: 16),
            ],

            // ── Paired devices ─────────────────────────────────────────────
            const Text('Paired Bluetooth Devices',
                style: TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 15,
                    color: AppColors.textDark)),
            const SizedBox(height: 4),
            const Text(
              'Go to your phone\'s Bluetooth settings to pair a new printer first.',
              style: TextStyle(color: AppColors.textMuted, fontSize: 12),
            ),
            const SizedBox(height: 12),

            if (_loadingDevices)
              const Center(
                  child: Padding(
                padding: EdgeInsets.all(32),
                child: CircularProgressIndicator(),
              ))
            else if (_devices.isEmpty)
              _emptyState()
            else
              ...(_devices.map((d) => _deviceTile(d))),

            const SizedBox(height: 28),

            // ── Tips ───────────────────────────────────────────────────────
            _tipsCard(),
          ],
        ),
      ),
    );
  }

  Widget _buildSystemPrinterScreen() {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: AppColors.textDark,
        elevation: 0,
        title: const Text('Windows Printers',
            style: TextStyle(fontWeight: FontWeight.w900)),
        actions: [
          IconButton(
            onPressed: _loadingDevices ? null : _loadSystemPrinters,
            tooltip: 'Refresh installed printers',
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          _statusCard(),
          const SizedBox(height: 20),
          const Text('Receipt output',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900)),
          const SizedBox(height: 8),
          SegmentedButton<bool>(
            segments: const [
              ButtonSegment<bool>(
                value: true,
                icon: Icon(Icons.receipt_long_rounded),
                label: Text('Thermal ESC/POS'),
              ),
              ButtonSegment<bool>(
                value: false,
                icon: Icon(Icons.description_outlined),
                label: Text('Windows PDF'),
              ),
            ],
            selected: {_printer.useRawWindowsPrinting},
            onSelectionChanged: (selection) =>
                _setWindowsPrinterMode(selection.first),
          ),
          const SizedBox(height: 6),
          Text(
            _printer.useRawWindowsPrinting
                ? 'Use for 58 mm and 80 mm POS thermal printers. Receipt data is sent directly to the Windows print queue.'
                : 'Use for office printers or thermal drivers that explicitly support PDF documents.',
            style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
          ),
          const SizedBox(height: 16),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  value: _printer.cutterCommand,
                  decoration: const InputDecoration(
                    labelText: 'Paper cutter profile',
                    prefixIcon: Icon(Icons.content_cut_rounded),
                  ),
                  items: PrinterService.cutterCommands.entries
                      .map((entry) => DropdownMenuItem<String>(
                            value: entry.key,
                            child: Text(entry.value),
                          ))
                      .toList(),
                  onChanged: _setCutterCommand,
                ),
              ),
              const SizedBox(width: 10),
              OutlinedButton.icon(
                onPressed: _connected ? _testCutter : null,
                icon: const Icon(Icons.content_cut_rounded),
                label: const Text('Test cut'),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size(120, 56),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          const Text(
            'If the recommended profile only feeds paper, try Feed + full cut, then the legacy profiles.',
            style: TextStyle(color: AppColors.textMuted, fontSize: 12),
          ),
          const SizedBox(height: 20),
          if (_error != null)
            Container(
              margin: const EdgeInsets.only(bottom: 16),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.errorRed.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(_error!,
                  style: const TextStyle(color: AppColors.errorRed)),
            ),
          const Text('Printers installed on this computer',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900)),
          const SizedBox(height: 4),
          const Text(
            'RetailZW supports receipt, office, USB, network and shared printers through their Windows driver.',
            style: TextStyle(color: AppColors.textMuted, fontSize: 12),
          ),
          const SizedBox(height: 14),
          if (_loadingDevices)
            const Center(
              child: Padding(
                padding: EdgeInsets.all(32),
                child: CircularProgressIndicator(),
              ),
            )
          else
            ..._systemPrinters.map(_systemPrinterTile),
          const SizedBox(height: 20),
          Card(
            elevation: 0,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
              side: const BorderSide(color: Color(0xFFDDE5EF)),
            ),
            child: const Padding(
              padding: EdgeInsets.all(16),
              child: Text(
                'Add or configure printers in Windows Settings > Bluetooth & devices > Printers & scanners, then refresh this page.',
                style: TextStyle(
                    color: AppColors.textMuted, fontSize: 12, height: 1.5),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _systemPrinterTile(Printer printer) {
    final selected = printer.url == _printer.savedSystemPrinterUrl;
    final selecting = _connectingTo == printer.url;
    return Card(
      elevation: 0,
      margin: const EdgeInsets.only(bottom: 8),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(
          color: selected ? AppColors.primaryBlue : const Color(0xFFDDE5EF),
          width: selected ? 2 : 1,
        ),
      ),
      child: ListTile(
        leading: Icon(
          printer.isAvailable ? Icons.print_rounded : Icons.print_disabled,
          color:
              printer.isAvailable ? AppColors.primaryBlue : AppColors.textMuted,
        ),
        title: Text(printer.name,
            style: const TextStyle(fontWeight: FontWeight.w800)),
        subtitle: Text([
          if (printer.isDefault) 'Windows default',
          if (printer.model?.isNotEmpty == true) printer.model!,
          printer.isAvailable ? 'Available' : 'Unavailable',
        ].join(' - ')),
        trailing: selecting
            ? const SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(strokeWidth: 2))
            : selected
                ? const Icon(Icons.check_circle_rounded,
                    color: AppColors.successGreen)
                : FilledButton(
                    onPressed: printer.isAvailable
                        ? () => _selectSystemPrinter(printer)
                        : null,
                    child: const Text('Use printer'),
                  ),
      ),
    );
  }

  // ─── Widgets ──────────────────────────────────────────────────────────────

  Widget _statusCard() {
    final saved = _printer.usesSystemPrinter
        ? _printer.savedSystemPrinterName
        : _printer.savedAddress;
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(
          color: _connected ? const Color(0xFFA5D6A7) : const Color(0xFFE0E7EF),
        ),
      ),
      color: _connected ? const Color(0xFFE8F5E9) : AppColors.background,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color:
                    _connected ? const Color(0xFF2E7D32) : AppColors.textMuted,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                _connected
                    ? Icons.print_rounded
                    : Icons.bluetooth_disabled_rounded,
                color: Colors.white,
                size: 22,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _connected ? 'Printer Ready' : 'No Printer Selected',
                    style: TextStyle(
                      fontWeight: FontWeight.w800,
                      fontSize: 15,
                      color: _connected
                          ? const Color(0xFF1B5E20)
                          : AppColors.textDark,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    saved ?? 'Select a printer below',
                    style: TextStyle(
                        color: _connected
                            ? const Color(0xFF388E3C)
                            : AppColors.textMuted,
                        fontSize: 12),
                  ),
                ],
              ),
            ),
            if (_connected) ...[
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextButton(
                    onPressed: _testPrint,
                    child: const Text('Test',
                        style: TextStyle(fontWeight: FontWeight.w700)),
                  ),
                  TextButton(
                    onPressed: _forget,
                    style: TextButton.styleFrom(
                        foregroundColor: AppColors.errorRed),
                    child: const Text('Forget',
                        style: TextStyle(fontWeight: FontWeight.w700)),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _deviceTile(BluetoothInfo device) {
    final isSaved = _printer.savedAddress == device.macAdress;
    final isConnecting = _connectingTo == device.macAdress;

    return Card(
      elevation: 0,
      margin: const EdgeInsets.only(bottom: 8),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: isSaved ? AppColors.primaryBlue : const Color(0xFFE0E7EF),
          width: isSaved ? 2 : 1,
        ),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        leading: Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: isSaved
                ? AppColors.primaryBlue.withValues(alpha: 0.1)
                : const Color(0xFFF0F4F8),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(
            Icons.print_rounded,
            color: isSaved ? AppColors.primaryBlue : AppColors.textMuted,
            size: 20,
          ),
        ),
        title: Text(
          device.name,
          style: TextStyle(
            fontWeight: FontWeight.w700,
            color: isSaved ? AppColors.primaryBlue : AppColors.textDark,
          ),
        ),
        subtitle: Text(
          device.macAdress,
          style: const TextStyle(color: AppColors.textMuted, fontSize: 11),
        ),
        trailing: isConnecting
            ? const SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(strokeWidth: 2.5))
            : isSaved && _connected
                ? const Icon(Icons.check_circle_rounded,
                    color: Color(0xFF2E7D32))
                : ElevatedButton(
                    onPressed: () => _connectTo(device),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primaryBlue,
                      foregroundColor: Colors.white,
                      elevation: 0,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 8),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8)),
                    ),
                    child: const Text('Connect',
                        style: TextStyle(
                            fontSize: 12, fontWeight: FontWeight.w700)),
                  ),
      ),
    );
  }

  Widget _emptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 32),
        child: Column(
          children: [
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                color: const Color(0xFFF0F4F8),
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Icon(Icons.bluetooth_searching_rounded,
                  size: 32, color: AppColors.textMuted),
            ),
            const SizedBox(height: 16),
            const Text('No paired devices found',
                style: TextStyle(
                    fontWeight: FontWeight.w700, color: AppColors.textDark)),
            const SizedBox(height: 8),
            const Text(
              'Pair your thermal printer in phone Settings → Bluetooth,\nthen come back here.',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppColors.textMuted, fontSize: 13),
            ),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: _loadDevices,
              icon: const Icon(Icons.refresh_rounded, size: 18),
              label: const Text('Refresh',
                  style: TextStyle(fontWeight: FontWeight.w700)),
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.primaryBlue,
                side: const BorderSide(color: AppColors.primaryBlue),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _tipsCard() {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: const BorderSide(color: Color(0xFFE0E7EF)),
      ),
      child: const Padding(
        padding: EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Tips',
                style: TextStyle(fontWeight: FontWeight.w800, fontSize: 14)),
            SizedBox(height: 8),
            Text(
              '• Supported printers: ESC/POS thermal printers (58 mm or 80 mm paper)\n'
              '• Common models: MUNBYN, Xprinter, Rongta, EPSON TM-series\n'
              '• Make sure the printer is on and charged before connecting\n'
              '• If connection fails, try turning the printer off and on again',
              style: TextStyle(
                  color: AppColors.textMuted, fontSize: 12, height: 1.6),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Dummy sale for test print ─────────────────────────────────────────────

  Sale _dummySale() => Sale(
        id: 0,
        receiptNumber: 'TEST-001',
        grandTotal: 5.00,
        currency: 'USD',
        status: 'COMPLETED',
        createdAt: DateTime.now(),
        items: [],
        payments: [],
      );

  List<_DummyCartItem> _dummyCart() => [
        _DummyCartItem('Test Item A', 2, 2.00),
        _DummyCartItem('Test Item B', 1, 1.00),
      ];
}

// Simple wrapper to satisfy PrinterService's duck-typed cartItems access
class _DummyCartItem {
  final String name;
  final double qty;
  final double price;

  _DummyCartItem(this.name, this.qty, this.price);

  _DummyProduct get product => _DummyProduct(name);
  double get quantity => qty;
  double get lineTotal => qty * price;
  double get lineTax => 0;
}

class _DummyProduct {
  final String name;
  const _DummyProduct(this.name);
}
