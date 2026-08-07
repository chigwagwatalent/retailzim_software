import 'dart:async';
import 'dart:io';

import 'package:esc_pos_utils_plus/esc_pos_utils_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:print_bluetooth_thermal/print_bluetooth_thermal.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:printing/printing.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/models.dart';

/// Bluetooth ESC/POS thermal receipt printer service.
///
/// Windows/Linux/macOS use system printer/PDF printing.
/// Android/iOS use Bluetooth ESC/POS printing.
class PrinterService {
  static const double _thermalPaperWidthMm = 80;
  static const double _thermalLeftMarginMm = 0;
  static const double _thermalRightMarginMm = 18;
  static const double _thermalVerticalMarginMm = 4;

  // ─── Singleton ────────────────────────────────────────────────────────────
  static final PrinterService _instance = PrinterService._internal();
  factory PrinterService() => _instance;
  PrinterService._internal();

  // ─── State ────────────────────────────────────────────────────────────────
  static const _prefKey = 'printer_mac_address';
  static const _systemPrinterUrlKey = 'system_printer_url';
  static const _systemPrinterNameKey = 'system_printer_name';
  static const _systemPrinterRawKey = 'system_printer_raw';
  static const _cutterCommandKey = 'printer_cutter_command';

  /// Optional native Windows raw print channel.
  ///
  /// If you have not implemented this in windows/runner native code, Flutter
  /// will throw MissingPluginException. This service now catches that and falls
  /// back to PDF/system printing.
  static const MethodChannel _windowsPrinterChannel =
      MethodChannel('retailzw/windows_printer');

  String? _savedAddress;
  String? _savedSystemPrinterUrl;
  String? _savedSystemPrinterName;

  /// IMPORTANT:
  /// Default is false so Windows uses normal system/PDF printing.
  /// Set true only if you have implemented native Windows printRaw support.
  bool _useRawWindowsPrinting = false;
  String _cutterCommand = 'feed_partial';

  bool _initialized = false;

  // ─── Init ─────────────────────────────────────────────────────────────────

  Future<void> init() async {
    if (_initialized) return;
    _initialized = true;

    final prefs = await SharedPreferences.getInstance();
    _savedAddress = prefs.getString(_prefKey);
    _savedSystemPrinterUrl = prefs.getString(_systemPrinterUrlKey);
    _savedSystemPrinterName = prefs.getString(_systemPrinterNameKey);

    // Default false to prevent MissingPluginException on Windows.
    _useRawWindowsPrinting = prefs.getBool(_systemPrinterRawKey) ?? false;
    _cutterCommand = prefs.getString(_cutterCommandKey) ?? 'feed_partial';
  }

  // ─── Getters ──────────────────────────────────────────────────────────────

  String? get savedAddress => _savedAddress;
  String? get savedSystemPrinterUrl => _savedSystemPrinterUrl;
  String? get savedSystemPrinterName => _savedSystemPrinterName;
  bool get useRawWindowsPrinting => _useRawWindowsPrinting;
  String get cutterCommand => _cutterCommand;

  static const Map<String, String> cutterCommands = {
    'feed_partial': 'Feed + partial cut (recommended)',
    'feed_full': 'Feed + full cut',
    'partial': 'Partial cut',
    'full': 'Full cut',
    'legacy_partial': 'Legacy partial cut (ESC m)',
    'legacy_full': 'Legacy full cut (ESC i)',
  };

  bool get usesSystemPrinter =>
      Platform.isWindows || Platform.isLinux || Platform.isMacOS;

  bool get hasSavedPrinter => usesSystemPrinter
      ? _savedSystemPrinterUrl != null || _savedSystemPrinterName != null
      : _savedAddress != null;

  Future<bool> get isBluetoothEnabled => usesSystemPrinter
      ? Future<bool>.value(true)
      : PrintBluetoothThermal.bluetoothEnabled;

  Future<bool> get isConnected => usesSystemPrinter
      ? Future<bool>.value(
          _savedSystemPrinterUrl != null || _savedSystemPrinterName != null,
        )
      : PrintBluetoothThermal.connectionStatus;

  Future<List<Printer>> getSystemPrinters() async {
    if (!usesSystemPrinter) return const [];

    try {
      final printers = await Printing.listPrinters();
      printers.sort((a, b) {
        if (a.isDefault != b.isDefault) return a.isDefault ? -1 : 1;
        if (a.isAvailable != b.isAvailable) return a.isAvailable ? -1 : 1;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });
      return printers;
    } catch (_) {
      return const [];
    }
  }

  Future<void> selectSystemPrinter(Printer printer) async {
    _savedSystemPrinterUrl = printer.url;
    _savedSystemPrinterName = printer.name;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_systemPrinterUrlKey, printer.url);
    await prefs.setString(_systemPrinterNameKey, printer.name);
  }

  Future<void> setWindowsPrinterMode({required bool raw}) async {
    _useRawWindowsPrinting = raw;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_systemPrinterRawKey, raw);
  }

  Future<void> setCutterCommand(String command) async {
    if (!cutterCommands.containsKey(command)) return;
    _cutterCommand = command;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_cutterCommandKey, command);
  }

  Future<PrintResult> testWindowsCutter() async {
    await init();
    if (!Platform.isWindows || _savedSystemPrinterName == null) {
      return PrintResult.failure('Select a Windows thermal printer first.');
    }
    try {
      final accepted = await _windowsPrinterChannel.invokeMethod<bool>(
        'printRaw',
        <String, Object>{
          'printerName': _savedSystemPrinterName!,
          'documentName': 'RetailZW cutter test',
          'bytes': Uint8List.fromList(<int>[
            0x1B,
            0x40,
            0x0A,
            0x0A,
            0x0A,
            0x0A,
            0x0A,
            ..._cutterBytes(),
          ]),
        },
      );
      return accepted == true
          ? PrintResult.success()
          : PrintResult.failure('Windows rejected the cutter command.');
    } on MissingPluginException {
      return PrintResult.failure(
          'This app build does not include the Windows cutter bridge.');
    } on PlatformException catch (error) {
      return PrintResult.failure(
          'Cutter test failed: ${error.message ?? error.code}');
    }
  }

  Future<void> _cutAfterPdf(Printer selected) async {
    if (!Platform.isWindows) return;
    await _windowsPrinterChannel.invokeMethod<bool>(
      'printRaw',
      <String, Object>{
        'printerName': selected.name,
        'documentName': 'RetailZW paper cut',
        'bytes': Uint8List.fromList(<int>[
          0x0A,
          0x0A,
          0x0A,
          ..._cutterBytes(),
        ]),
      },
    );
  }

  Future<void> forgetSystemPrinter() async {
    _savedSystemPrinterUrl = null;
    _savedSystemPrinterName = null;

    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_systemPrinterUrlKey);
    await prefs.remove(_systemPrinterNameKey);
  }

  /// Returns paired Bluetooth devices. Returns an empty list on error.
  Future<List<BluetoothInfo>> getPairedDevices() async {
    if (usesSystemPrinter) return const [];

    try {
      return await PrintBluetoothThermal.pairedBluetooths;
    } catch (_) {
      return [];
    }
  }

  // ─── Connect / disconnect ─────────────────────────────────────────────────

  /// Connects to [macAddress]. Persists the address on success.
  Future<bool> connect(String macAddress) async {
    if (usesSystemPrinter) return true;

    final normalizedAddress =
        macAddress.trim().toUpperCase().replaceAll('-', ':');

    if (!RegExp(r'^([0-9A-F]{2}:){5}[0-9A-F]{2}$')
        .hasMatch(normalizedAddress)) {
      return false;
    }

    try {
      final ok = await PrintBluetoothThermal.connect(
        macPrinterAddress: normalizedAddress,
      );

      if (ok) {
        _savedAddress = normalizedAddress;
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString(_prefKey, normalizedAddress);
      }

      return ok;
    } catch (_) {
      return false;
    }
  }

  /// Disconnects from the current printer.
  Future<void> disconnect() async {
    if (usesSystemPrinter) return;

    try {
      await PrintBluetoothThermal.disconnect;
    } catch (_) {}
  }

  /// Clears the saved printer address without disconnecting.
  Future<void> forgetPrinter() async {
    if (usesSystemPrinter) {
      await forgetSystemPrinter();
      return;
    }

    _savedAddress = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_prefKey);
  }

  // ─── Auto-connect ─────────────────────────────────────────────────────────

  /// Tries to connect to the saved printer address silently.
  Future<bool> connectToSaved() async {
    await init();

    if (usesSystemPrinter) {
      return _savedSystemPrinterUrl != null || _savedSystemPrinterName != null;
    }

    if (_savedAddress == null) return false;

    final connected = await isConnected;
    if (connected) return true;

    return connect(_savedAddress!);
  }

  // ─── Print receipt ────────────────────────────────────────────────────────

  Future<PrintResult> printReceipt({
    required Sale sale,
    required List<dynamic> cartItems,
    required String companyName,
    required List<String> companyLines,
    required String footerText,
    required double change,
    required String currency,
    PaperSize paperSize = PaperSize.mm58,
  }) async {
    try {
      await init();

      if (usesSystemPrinter) {
        return _printWithSystemPrinter(
          sale: sale,
          cartItems: cartItems,
          companyName: companyName,
          companyLines: companyLines,
          footerText: footerText,
          change: change,
          currency: currency,
          paperSize: paperSize,
        );
      }

      final btEnabled = await isBluetoothEnabled;
      if (!btEnabled) {
        return PrintResult.failure(
          'Bluetooth is off. Turn on Bluetooth and try again.',
        );
      }

      var connected = await isConnected;
      if (!connected) {
        if (_savedAddress == null) {
          return PrintResult.failure(
            'No printer selected. Go to Settings → Bluetooth Printer to pair one.',
          );
        }

        connected = await connect(_savedAddress!);
        if (!connected) {
          return PrintResult.failure(
            'Could not connect to printer. Make sure it is on and in range.',
          );
        }
      }

      final bytes = await _buildReceipt(
        sale: sale,
        cartItems: cartItems,
        companyName: companyName,
        companyLines: companyLines,
        footerText: footerText,
        change: change,
        currency: currency,
        paperSize: paperSize,
      );

      var ok = await _writeBytesCompat(bytes);

      if (!ok) {
        ok = await _writeBytesCompat(
          _buildFallbackReceipt(
            sale: sale,
            cartItems: cartItems,
            companyName: companyName,
            footerText: footerText,
            change: change,
            currency: currency,
          ),
        );
      }

      return ok
          ? PrintResult.success()
          : PrintResult.failure(
              'Printer rejected the receipt. Confirm it supports ESC/POS mode.',
            );
    } catch (e) {
      return PrintResult.failure('Print error: $e');
    }
  }

  // ─── Windows / desktop printing ───────────────────────────────────────────

  Future<PrintResult> _printWithSystemPrinter({
    required Sale sale,
    required List<dynamic> cartItems,
    required String companyName,
    required List<String> companyLines,
    required String footerText,
    required double change,
    required String currency,
    required PaperSize paperSize,
  }) async {
    try {
      final printers = await getSystemPrinters();

      if (printers.isEmpty) {
        return PrintResult.failure(
          'No system printers found. Install a Windows printer first.',
        );
      }

      final selected = _resolveSelectedPrinter(printers);

      if (selected == null) {
        return PrintResult.failure(
          'The selected Windows printer is no longer installed. Select another printer in Settings.',
        );
      }

      // Keep selected/default printer saved.
      if (_savedSystemPrinterUrl == null && _savedSystemPrinterName == null) {
        await selectSystemPrinter(selected);
      }

      // Optional raw ESC/POS printing for Windows.
      // This will only work if you implemented retailzw/windows_printer
      // in native Windows code. Otherwise it falls back to PDF printing.
      if (Platform.isWindows && _useRawWindowsPrinting) {
        final rawResult = await _tryWindowsRawPrint(
          selected: selected,
          sale: sale,
          cartItems: cartItems,
          companyName: companyName,
          companyLines: companyLines,
          footerText: footerText,
          change: change,
          currency: currency,
          paperSize: paperSize,
        );

        if (rawResult.success) {
          return rawResult;
        }

        // Do not fail here. Fall back to PDF/system printing.
      }

      return _printPdfReceipt(
        selected: selected,
        sale: sale,
        cartItems: cartItems,
        companyName: companyName,
        companyLines: companyLines,
        footerText: footerText,
        change: change,
        currency: currency,
      );
    } catch (error) {
      return PrintResult.failure('Windows print error: $error');
    }
  }

  Printer? _resolveSelectedPrinter(List<Printer> printers) {
    if (printers.isEmpty) return null;

    if (_savedSystemPrinterUrl != null || _savedSystemPrinterName != null) {
      for (final printer in printers) {
        if (printer.url == _savedSystemPrinterUrl ||
            printer.name == _savedSystemPrinterName) {
          return printer;
        }
      }

      // Saved printer no longer exists.
      return null;
    }

    for (final printer in printers) {
      if (printer.isDefault) return printer;
    }

    for (final printer in printers) {
      if (printer.isAvailable) return printer;
    }

    return printers.first;
  }

  Future<PrintResult> _tryWindowsRawPrint({
    required Printer selected,
    required Sale sale,
    required List<dynamic> cartItems,
    required String companyName,
    required List<String> companyLines,
    required String footerText,
    required double change,
    required String currency,
    required PaperSize paperSize,
  }) async {
    try {
      final receipt = await _buildReceipt(
        sale: sale,
        cartItems: cartItems,
        companyName: companyName,
        companyLines: companyLines,
        footerText: footerText,
        change: change,
        currency: currency,
        paperSize: paperSize,
      );

      final printed = await _windowsPrinterChannel.invokeMethod<bool>(
        'printRaw',
        <String, Object>{
          'printerName': selected.name,
          'documentName': 'RetailZW-${sale.receiptNumber}',
          'bytes': Uint8List.fromList(receipt),
        },
      );

      return printed == true
          ? PrintResult.success()
          : PrintResult.failure('Windows did not accept the thermal receipt.');
    } on MissingPluginException {
      // This is the error you are seeing:
      // No implementation found for method printRaw.
      // It means native Windows raw printing was not implemented.
      return PrintResult.failure(
        'Raw Windows printing is not implemented. Falling back to PDF printing.',
      );
    } on PlatformException catch (error) {
      return PrintResult.failure(
        'Thermal printer error: ${error.message ?? error.code}. Falling back to PDF printing.',
      );
    } catch (error) {
      return PrintResult.failure(
        'Raw Windows printing failed: $error. Falling back to PDF printing.',
      );
    }
  }

  Future<PrintResult> _printPdfReceipt({
    required Printer selected,
    required Sale sale,
    required List<dynamic> cartItems,
    required String companyName,
    required List<String> companyLines,
    required String footerText,
    required double change,
    required String currency,
  }) async {
    final bytes = await _buildPdfReceipt(
      sale: sale,
      cartItems: cartItems,
      companyName: companyName,
      companyLines: companyLines,
      footerText: footerText,
      change: change,
      currency: currency,
    );

    var printed = false;

    if (Platform.isWindows) {
      try {
        printed = await Printing.layoutPdf(
          name: 'RetailZW-${sale.receiptNumber}',
          format: _receiptPdfFormat(cartItems.length),
          onLayout: (_) async => bytes,
        );
      } catch (error) {
        return PrintResult.failure('PDF print error: $error');
      }
    } else {
      try {
        printed = await Printing.directPrintPdf(
          printer: selected,
          name: 'RetailZW-${sale.receiptNumber}',
          format: _receiptPdfFormat(cartItems.length),
          usePrinterSettings: true,
          onLayout: (_) async => bytes,
        );
      } catch (_) {
        printed = false;
      }

      if (!printed) {
        try {
          printed = await Printing.layoutPdf(
            name: 'RetailZW-${sale.receiptNumber}',
            format: _receiptPdfFormat(cartItems.length),
            onLayout: (_) async => bytes,
          );
        } catch (error) {
          return PrintResult.failure('PDF print error: $error');
        }
      }
    }

    if (printed && Platform.isWindows) {
      try {
        await _cutAfterPdf(selected);
      } on PlatformException catch (error) {
        return PrintResult.failure(
          'Receipt printed, but the cutter command failed: ${error.message ?? error.code}. Try another cutter profile in Printer Settings.',
        );
      } on MissingPluginException {
        return PrintResult.failure(
          'Receipt printed, but this Windows build cannot send a cutter command.',
        );
      }
    }

    return printed
        ? PrintResult.success()
        : PrintResult.failure('Printing was cancelled.');
  }

  PdfPageFormat _receiptPdfFormat(int itemCount) {
    final heightMm = (130 + (itemCount * 12)).clamp(180, 900).toDouble();
    return PdfPageFormat(
      _thermalPaperWidthMm * PdfPageFormat.mm,
      heightMm * PdfPageFormat.mm,
      marginAll: 0,
    );
  }

  Future<Uint8List> _buildPdfReceipt({
    required Sale sale,
    required List<dynamic> cartItems,
    required String companyName,
    required List<String> companyLines,
    required String footerText,
    required double change,
    required String currency,
  }) async {
    final document = pw.Document(
      title: 'Receipt ${sale.receiptNumber}',
      author: companyName,
    );

    final tendered = sale.grandTotal + change;

    document.addPage(
      pw.Page(
        pageFormat: _receiptPdfFormat(cartItems.length),
        margin: const pw.EdgeInsets.fromLTRB(
          _thermalLeftMarginMm * PdfPageFormat.mm,
          _thermalVerticalMarginMm * PdfPageFormat.mm,
          _thermalRightMarginMm * PdfPageFormat.mm,
          _thermalVerticalMarginMm * PdfPageFormat.mm,
        ),
        build: (_) => pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.stretch,
          children: [
            pw.Center(
              child: pw.Text(
                _safeText(companyName).toUpperCase(),
                textAlign: pw.TextAlign.center,
                style: pw.TextStyle(
                  fontSize: 15,
                  fontWeight: pw.FontWeight.bold,
                ),
              ),
            ),
            ...companyLines.where((line) => line.trim().isNotEmpty).take(4).map(
                  (line) => pw.Center(
                    child: pw.Text(
                      _safeText(line),
                      textAlign: pw.TextAlign.center,
                      style: const pw.TextStyle(fontSize: 8),
                    ),
                  ),
                ),
            pw.SizedBox(height: 8),
            pw.Divider(),
            pw.Center(
              child: pw.Text(
                'RECEIPT #${_safeText(sale.receiptNumber)}',
                textAlign: pw.TextAlign.center,
                style: pw.TextStyle(
                  fontSize: 10,
                  fontWeight: pw.FontWeight.bold,
                ),
              ),
            ),
            pw.Center(
              child: pw.Text(
                _formatDate(sale.createdAt),
                style: const pw.TextStyle(fontSize: 8),
              ),
            ),
            pw.Divider(),
            ...cartItems.map(
              (item) {
                final qty = _itemQuantity(item);
                final name = _itemProductName(item);
                final amount = _itemLineTotal(item) + _itemLineTax(item);

                return pw.Padding(
                  padding: const pw.EdgeInsets.symmetric(vertical: 3),
                  child: pw.Row(
                    crossAxisAlignment: pw.CrossAxisAlignment.start,
                    children: [
                      pw.Expanded(
                        child: pw.Text(
                          '${qty.toStringAsFixed(0)} x $name',
                          style: const pw.TextStyle(fontSize: 8),
                        ),
                      ),
                      pw.SizedBox(width: 3 * PdfPageFormat.mm),
                      pw.SizedBox(
                        width: 22 * PdfPageFormat.mm,
                        child: pw.Text(
                          '$currency ${_fmt(amount)}',
                          textAlign: pw.TextAlign.right,
                          style: pw.TextStyle(
                            fontSize: 8,
                            fontWeight: pw.FontWeight.bold,
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
            pw.Divider(),
            _pdfTotalRow('TOTAL', '$currency ${_fmt(sale.grandTotal)}', true),
            _pdfTotalRow('Tendered', '$currency ${_fmt(tendered)}', false),
            if (change > 0)
              _pdfTotalRow('Change', '$currency ${_fmt(change)}', true),
            pw.SizedBox(height: 10),
            pw.Center(
              child: pw.Text(
                _safeText(footerText),
                textAlign: pw.TextAlign.center,
                style: pw.TextStyle(
                  fontSize: 8,
                  fontWeight: pw.FontWeight.bold,
                ),
              ),
            ),
            pw.SizedBox(height: 4),
            pw.Center(
              child: pw.Text(
                'Powered by RetailZW',
                style: const pw.TextStyle(fontSize: 7),
              ),
            ),
            pw.SizedBox(height: 20),
          ],
        ),
      ),
    );

    return document.save();
  }

  pw.Widget _pdfTotalRow(String label, String value, bool bold) {
    final style = pw.TextStyle(
      fontSize: bold ? 10 : 8,
      fontWeight: bold ? pw.FontWeight.bold : pw.FontWeight.normal,
    );

    return pw.Padding(
      padding: const pw.EdgeInsets.symmetric(vertical: 2),
      child: pw.Row(
        mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
        children: [
          pw.Expanded(child: pw.Text(label, style: style)),
          pw.SizedBox(width: 3 * PdfPageFormat.mm),
          pw.SizedBox(
            width: 26 * PdfPageFormat.mm,
            child: pw.Text(
              value,
              textAlign: pw.TextAlign.right,
              style: style,
            ),
          ),
        ],
      ),
    );
  }

  // ─── ESC/POS receipt builder ──────────────────────────────────────────────

  Future<List<int>> _buildReceipt({
    required Sale sale,
    required List<dynamic> cartItems,
    required String companyName,
    required List<String> companyLines,
    required String footerText,
    required double change,
    required String currency,
    required PaperSize paperSize,
  }) async {
    final profile = await CapabilityProfile.load();
    final gen = Generator(paperSize, profile);
    final bytes = <int>[];

    bytes.addAll(gen.reset());

    bytes.addAll(
      gen.text(
        _safeText(companyName).toUpperCase(),
        styles: const PosStyles(
          bold: true,
          align: PosAlign.center,
          height: PosTextSize.size2,
          width: PosTextSize.size2,
        ),
      ),
    );

    bytes.addAll(gen.feed(1));

    for (final line in companyLines.take(4)) {
      bytes.addAll(
        gen.text(
          _safeText(line),
          styles: const PosStyles(align: PosAlign.center),
        ),
      );
    }

    bytes.addAll(_separator(gen, paperSize));

    bytes.addAll(
      gen.text(
        'RECEIPT',
        styles: const PosStyles(bold: true, align: PosAlign.center),
      ),
    );

    if (sale.receiptNumber.isNotEmpty) {
      bytes.addAll(
        gen.text(
          '#${_safeText(sale.receiptNumber)}',
          styles: const PosStyles(align: PosAlign.center),
        ),
      );
    }

    bytes.addAll(_separator(gen, paperSize));

    bytes.addAll(
      gen.text(
        _formatDate(sale.createdAt),
        styles: const PosStyles(align: PosAlign.center),
      ),
    );

    bytes.addAll(_separator(gen, paperSize));

    bytes.addAll(gen.feed(1));

    bytes.addAll(
      gen.row([
        PosColumn(
          text: 'ITEM',
          width: 6,
          styles: const PosStyles(bold: true),
        ),
        PosColumn(
          text: 'QTY',
          width: 2,
          styles: const PosStyles(
            bold: true,
            align: PosAlign.center,
          ),
        ),
        PosColumn(
          text: 'AMT',
          width: 4,
          styles: const PosStyles(
            bold: true,
            align: PosAlign.right,
          ),
        ),
      ]),
    );

    for (final item in cartItems) {
      final name = _itemProductName(item);
      final qty = _itemQuantity(item).toStringAsFixed(0);
      final lineAmt = _fmt(_itemLineTotal(item) + _itemLineTax(item));
      final truncated = _fitText(name, paperSize == PaperSize.mm58 ? 14 : 22);

      bytes.addAll(
        gen.row([
          PosColumn(text: truncated, width: 6),
          PosColumn(
            text: qty,
            width: 2,
            styles: const PosStyles(align: PosAlign.center),
          ),
          PosColumn(
            text: _fitText(lineAmt, paperSize == PaperSize.mm58 ? 8 : 10),
            width: 4,
            styles: const PosStyles(align: PosAlign.right),
          ),
        ]),
      );
    }

    bytes.addAll(_separator(gen, paperSize));

    final tendered = sale.grandTotal + change;

    bytes.addAll(
      gen.row([
        PosColumn(
          text: 'TOTAL',
          width: 6,
          styles: const PosStyles(
            bold: true,
            height: PosTextSize.size2,
          ),
        ),
        PosColumn(
          text: _fitText('$currency ${_fmt(sale.grandTotal)}', 14),
          width: 6,
          styles: const PosStyles(
            bold: true,
            align: PosAlign.right,
            height: PosTextSize.size2,
          ),
        ),
      ]),
    );

    bytes.addAll(gen.feed(1));

    bytes.addAll(
      gen.row([
        PosColumn(text: 'Tendered', width: 6),
        PosColumn(
          text: _fitText('$currency ${_fmt(tendered)}', 14),
          width: 6,
          styles: const PosStyles(align: PosAlign.right),
        ),
      ]),
    );

    if (change > 0) {
      bytes.addAll(
        gen.row([
          PosColumn(
            text: 'Change',
            width: 6,
            styles: const PosStyles(bold: true),
          ),
          PosColumn(
            text: _fitText('$currency ${_fmt(change)}', 14),
            width: 6,
            styles: const PosStyles(
              bold: true,
              align: PosAlign.right,
            ),
          ),
        ]),
      );
    }

    bytes.addAll(_separator(gen, paperSize));

    bytes.addAll(gen.feed(1));

    bytes.addAll(
      gen.text(
        _safeText(footerText),
        styles: const PosStyles(
          align: PosAlign.center,
          bold: true,
        ),
      ),
    );

    bytes.addAll(gen.feed(1));

    bytes.addAll(
      gen.text(
        'Powered by RetailZW',
        styles: const PosStyles(align: PosAlign.center),
      ),
    );

    // The cut command must begin on a fresh line after the paper is fed.
    bytes.addAll(gen.feed(3));
    bytes.addAll(_cutterBytes());

    return bytes;
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  String _formatDate(DateTime dt) {
    final d = dt.day.toString().padLeft(2, '0');
    final m = dt.month.toString().padLeft(2, '0');
    final y = dt.year.toString();
    final h = dt.hour.toString().padLeft(2, '0');
    final min = dt.minute.toString().padLeft(2, '0');
    return '$d/$m/$y  $h:$min';
  }

  String _fmt(double value) => value.toStringAsFixed(2);

  List<int> _separator(Generator gen, PaperSize paperSize) {
    return gen.text(
      paperSize == PaperSize.mm58
          ? '------------------------------'
          : '------------------------------------------',
      styles: const PosStyles(align: PosAlign.left),
    );
  }

  String _fitText(String value, int maxChars) {
    final text = _safeText(value);
    if (text.length <= maxChars) return text;
    if (maxChars <= 1) return text.substring(0, maxChars);
    return '${text.substring(0, maxChars - 1)}.';
  }

  double _toDouble(Object? value) {
    if (value == null) return 0.0;
    if (value is num) return value.toDouble();
    return double.tryParse(value.toString()) ?? 0.0;
  }

  String _itemProductName(dynamic item) {
    try {
      return _safeText(item.product.name);
    } catch (_) {
      return 'Item';
    }
  }

  double _itemQuantity(dynamic item) {
    try {
      return _toDouble(item.quantity);
    } catch (_) {
      return 0.0;
    }
  }

  double _itemLineTotal(dynamic item) {
    try {
      return _toDouble(item.lineTotal);
    } catch (_) {
      return 0.0;
    }
  }

  double _itemLineTax(dynamic item) {
    try {
      return _toDouble(item.lineTax);
    } catch (_) {
      return 0.0;
    }
  }

  Future<bool> _writeBytesCompat(List<int> bytes) async {
    const chunkSize = 512;

    try {
      for (var offset = 0; offset < bytes.length; offset += chunkSize) {
        final end = (offset + chunkSize < bytes.length)
            ? offset + chunkSize
            : bytes.length;

        final ok = await PrintBluetoothThermal.writeBytes(
          bytes.sublist(offset, end),
        );

        if (!ok) return false;

        if (end < bytes.length) {
          await Future<void>.delayed(const Duration(milliseconds: 30));
        }
      }

      return true;
    } catch (_) {
      return false;
    }
  }

  String _safeText(Object? value) {
    var text = (value ?? '').toString();

    const replacements = <String, String>{
      '\u2010': '-',
      '\u2011': '-',
      '\u2012': '-',
      '\u2013': '-',
      '\u2014': '-',
      '\u2018': "'",
      '\u2019': "'",
      '\u201c': '"',
      '\u201d': '"',
      '\u2022': '*',
      '\u2026': '...',
      '\u00a0': ' ',
      '\u2192': '>',
      '\u00a9': '(c)',
      '\u00ae': '(R)',
    };

    replacements.forEach((source, replacement) {
      text = text.replaceAll(source, replacement);
    });

    return text
        .replaceAll(RegExp(r'[\r\n\t]+'), ' ')
        .replaceAll(RegExp(r'[^\x20-\x7E]'), '?')
        .replaceAll(RegExp(r' {2,}'), ' ')
        .trim();
  }

  List<int> _buildFallbackReceipt({
    required Sale sale,
    required List<dynamic> cartItems,
    required String companyName,
    required String footerText,
    required double change,
    required String currency,
  }) {
    final lines = <String>[
      _safeText(companyName).toUpperCase(),
      'RECEIPT ${_safeText(sale.receiptNumber)}',
      _formatDate(sale.createdAt),
      '--------------------------------',
      ...cartItems.map((item) {
        final name = _itemProductName(item);
        final qty = _itemQuantity(item).toStringAsFixed(0);
        final total = _fmt(_itemLineTotal(item) + _itemLineTax(item));
        final shortName = name.length > 18 ? name.substring(0, 18) : name;
        return '$shortName x$qty $currency $total';
      }),
      '--------------------------------',
      'TOTAL $currency ${_fmt(sale.grandTotal)}',
      if (change > 0) 'CHANGE $currency ${_fmt(change)}',
      _safeText(footerText),
      'Powered by RetailZW',
      '',
      '',
      '',
    ];

    return <int>[
      0x1B,
      0x40,
      ...lines
          .join('\n')
          .codeUnits
          .where((unit) => unit == 0x0A || (unit >= 0x20 && unit <= 0x7E)),
      ..._cutterBytes(),
    ];
  }

  List<int> _cutterBytes() {
    switch (_cutterCommand) {
      case 'feed_full':
        // GS V 65 n: feed to cutter plus n units, then full cut.
        return const [0x1D, 0x56, 0x41, 0x03];
      case 'partial':
        return const [0x1D, 0x56, 0x01];
      case 'full':
        return const [0x1D, 0x56, 0x00];
      case 'legacy_partial':
        return const [0x1B, 0x6D];
      case 'legacy_full':
        return const [0x1B, 0x69];
      case 'feed_partial':
      default:
        // GS V 66 n: common on generic 58/80 mm ESC/POS printers.
        return const [0x1D, 0x56, 0x42, 0x03];
    }
  }
}

// ─── Print result ──────────────────────────────────────────────────────────

@immutable
class PrintResult {
  final bool success;
  final String? error;

  const PrintResult._({
    required this.success,
    this.error,
  });

  factory PrintResult.success() => const PrintResult._(success: true);

  factory PrintResult.failure(String message) =>
      PrintResult._(success: false, error: message);

  @override
  String toString() =>
      success ? 'PrintResult.success' : 'PrintResult.failure($error)';
}
