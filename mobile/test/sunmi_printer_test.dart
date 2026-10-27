import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:retailzw_mobile/models/models.dart';
import 'package:retailzw_mobile/services/printer_service.dart';
import 'package:retailzw_mobile/services/sunmi_printer.dart';
import 'package:retailzw_mobile/screens/settings/printer_settings_screen.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const bluetooth = MethodChannel('groons.web.app/print');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  var bluetoothCalls = 0;
  var printCalls = 0;
  var available = true;
  var ready = true;
  var failPrint = false;
  Uint8List? lastReceipt;

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    bluetoothCalls = 0;
    printCalls = 0;
    available = true;
    ready = true;
    failPrint = false;
    lastReceipt = null;
    messenger.setMockMethodCallHandler(bluetooth, (call) async {
      bluetoothCalls++;
      return false;
    });
    messenger.setMockMethodCallHandler(SunmiPrinter.channel, (call) async {
      if (call.method == 'getInfo') {
        return {
          'available': available,
          'ready': ready,
          'model': 'V2 Pro',
          'message':
              ready ? 'Ready · 58 mm receipt paper' : 'Load the receipt roll.'
        };
      }
      if (call.method == 'printRaw') {
        printCalls++;
        lastReceipt = (call.arguments as Map)['bytes'] as Uint8List;
        if (failPrint) {
          throw PlatformException(
              code: 'SUNMI_PRINT', message: 'Load the receipt roll.');
        }
        return true;
      }
      throw MissingPluginException();
    });
  });
  tearDown(() {
    messenger.setMockMethodCallHandler(bluetooth, null);
    messenger.setMockMethodCallHandler(SunmiPrinter.channel, null);
  });

  Future<dynamic> printReceipt(PrinterService printer) => printer.printReceipt(
        sale: Sale(
            id: 1,
            receiptNumber: 'SUNMI-TEST-1',
            grandTotal: 4,
            currency: 'USD',
            status: 'COMPLETED',
            createdAt: DateTime(2026, 10, 26),
            items: [],
            payments: []),
        cartItems: [],
        companyName: 'RetailZW',
        companyLines: const ['Printer test'],
        footerText: 'Thank you',
        change: 0,
        currency: 'USD',
      );

  test('automatically selects the built-in printer without Bluetooth',
      () async {
    final printer = PrinterService.forTesting(isAndroid: true);
    await printer.init();
    expect(printer.usesBuiltInPrinter, isTrue);
    expect(printer.hasSavedPrinter, isTrue);
    expect(await printer.connectToSaved(), isTrue);
    expect(bluetoothCalls, 0);
    expect(
        (await SharedPreferences.getInstance())
            .getString('receipt_printer_transport'),
        'sunmi');
  });

  test('sends a real receipt through SUNMI while Bluetooth is off', () async {
    final printer = PrinterService.forTesting(isAndroid: true);
    final result = await printReceipt(printer);
    expect(result.success, isTrue);
    expect(printCalls, 1);
    expect(lastReceipt, isNotEmpty);
    expect(String.fromCharCodes(lastReceipt!), contains('SUNMI-TEST-1'));
    expect(bluetoothCalls, 0);
  });

  test('paper errors are shown without resending or falling back to Bluetooth',
      () async {
    failPrint = true;
    final result =
        await printReceipt(PrinterService.forTesting(isAndroid: true));
    expect(result.success, isFalse);
    expect(result.error, contains('receipt roll'));
    expect(printCalls, 1);
    expect(bluetoothCalls, 0);
  });

  test('saved built-in choice survives a temporarily unavailable service',
      () async {
    SharedPreferences.setMockInitialValues(
        {'receipt_printer_transport': 'sunmi'});
    available = false;
    ready = false;
    final printer = PrinterService.forTesting(isAndroid: true);
    await printer.init();
    expect(printer.usesBuiltInPrinter, isTrue);
    expect(await printer.connectToSaved(), isFalse);
    expect(bluetoothCalls, 0);
  });

  test('recognizes a SUNMI terminal even while its printer service starts',
      () async {
    messenger.setMockMethodCallHandler(
        SunmiPrinter.channel,
        (call) async => {
              'detected': true,
              'available': false,
              'ready': false,
              'model': 'V2 Pro',
              'message': 'SUNMI printer service is unavailable. Tap Refresh.',
            });
    final printer = PrinterService.forTesting(isAndroid: true);
    await printer.init();
    expect(printer.usesBuiltInPrinter, isTrue);
    expect(await printer.connectToSaved(), isFalse);
    expect(bluetoothCalls, 0);
  });

  test('preserves an existing external Bluetooth selection', () async {
    SharedPreferences.setMockInitialValues(
        {'printer_mac_address': '00:11:22:33:44:55'});
    final printer = PrinterService.forTesting(isAndroid: true);
    await printer.init();
    expect(printer.usesBuiltInPrinter, isFalse);
    await printer.selectBuiltInPrinter();
    expect(printer.usesBuiltInPrinter, isTrue);
    await printer.selectBluetoothPrinter();
    expect(printer.usesBuiltInPrinter, isFalse);
    expect(printer.savedAddress, '00:11:22:33:44:55');
  });

  test('non-SUNMI Android devices retain Bluetooth printing', () async {
    available = false;
    final printer = PrinterService.forTesting(isAndroid: true);
    await printer.init();
    expect(printer.usesBuiltInPrinter, isFalse);
    expect(printer.hasSavedPrinter, isFalse);
    final result = await printReceipt(printer);
    expect(result.success, isFalse);
    expect(result.error, contains('Bluetooth is off'));
    expect(printCalls, 0);
  });

  test('missing native service is treated as unavailable', () async {
    messenger.setMockMethodCallHandler(SunmiPrinter.channel, null);
    final printer = PrinterService.forTesting(isAndroid: true);
    await printer.init();
    expect(printer.builtInInfo.available, isFalse);
    expect(printer.usesBuiltInPrinter, isFalse);
  });

  testWidgets('SUNMI printer settings skip Bluetooth discovery and permissions',
      (tester) async {
    final printer = PrinterService.forTesting(isAndroid: true);
    await tester
        .pumpWidget(MaterialApp(home: PrinterSettingsScreen(printer: printer)));
    await tester.pumpAndSettle();
    expect(find.text('Receipt Printer'), findsOneWidget);
    expect(find.text('SUNMI built-in printer'), findsOneWidget);
    expect(find.text('Paired Bluetooth Devices'), findsNothing);
    expect(find.textContaining('Bluetooth is off'), findsNothing);
    expect(bluetoothCalls, 0);
    await tester.tap(find.text('Test'));
    await tester.pumpAndSettle();
    expect(find.text('Test receipt sent. Check the printer.'), findsOneWidget);
    expect(printCalls, 1);
  });
}
