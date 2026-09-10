import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:provider/provider.dart';
import 'package:retailzw_mobile/models/models.dart';
import 'package:retailzw_mobile/providers/app_provider.dart';
import 'package:retailzw_mobile/screens/auth/login_screen.dart';
import 'package:retailzw_mobile/screens/home_screen.dart';
import 'package:retailzw_mobile/screens/pos/pos_screen.dart';
import 'package:retailzw_mobile/screens/pos/payment_screen.dart';
import 'package:retailzw_mobile/services/api_service.dart';

void main() {
  setUp(() => FlutterSecureStorage.setMockInitialValues({}));
  setUpAll(() async {
    if (const bool.fromEnvironment('CAPTURE_UI')) {
      final font = FontLoader('Roboto')
        ..addFont(Future.value(ByteData.sublistView(
            await File('C:/Windows/Fonts/segoeui.ttf').readAsBytes())));
      await font.load();
      await (FontLoader('MaterialIcons')
            ..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf')))
          .load();
    }
  });

  for (final size in [
    const Size(1100, 700),
    const Size(1366, 768),
    const Size(1920, 1080)
  ]) {
    testWidgets('desktop login fits $size and validates empty fields',
        (tester) async {
      setSize(tester, size);
      await tester.pumpWidget(const MaterialApp(home: LoginScreen()));
      await tester.pumpAndSettle();
      expect(find.text('Welcome back'), findsOneWidget);
      await capture(tester, 'login-${size.width.toInt()}');
      await tester.tap(find.text('Sign In'));
      await tester.pump();
      expect(find.text('Enter username'), findsOneWidget);
      expect(find.text('Enter password'), findsOneWidget);
    });

    testWidgets(
        'desktop POS fits $size and preserves search, quantity, currency, checkout',
        (tester) async {
      setSize(tester, size);
      final provider = fixture();
      await tester.pumpWidget(ChangeNotifierProvider.value(
          value: provider,
          child: MaterialApp(
              theme: ThemeData(
                  colorScheme:
                      ColorScheme.fromSeed(seedColor: const Color(0xFF087DDF)),
                  scaffoldBackgroundColor: const Color(0xFFF4F8FD)),
              home: Scaffold(body: PosScreen(api: FixtureApi(provider))))));
      await tester.pump(const Duration(milliseconds: 400));
      await capture(tester, 'pos-${size.width.toInt()}');
      await tester.sendKeyEvent(LogicalKeyboardKey.f2);
      await tester.enterText(find.byType(TextField).first, 'Cooking oil');
      await tester.pump();
      await tester.tap(find.text('Cooking oil 2 L').first);
      await tester.pump();
      expect(provider.cart.length, 2);
      expect(provider.cartTotal(), closeTo(9.80, 0.001));
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pump();
      expect(find.text('Sugar 2 kg'), findsOneWidget);
      await tester.tap(find.text('ZWG'));
      await tester.pump();
      expect(provider.currency, 'ZWG');
      expect(provider.cartTotal(), closeTo(254.80, 0.001));
      await tester.sendKeyEvent(LogicalKeyboardKey.f4);
      await tester.pumpAndSettle();
      expect(find.byType(PaymentScreen), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  testWidgets('desktop cashier can reach Cash before opening a shift',
      (tester) async {
    setSize(tester, const Size(1366, 768));
    await tester.pumpWidget(ChangeNotifierProvider.value(
        value: AppProvider()
          ..setUser(UserInfo(
              id: 1,
              username: 'test',
              firstName: 'Test',
              lastName: 'Cashier',
              role: 'CASHIER',
              branchId: 5,
              branchName: 'City Centre')),
        child: const MaterialApp(home: HomeScreen())));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('No Active Shift'), findsOneWidget);
    expect(find.text('City Centre'), findsOneWidget);
    expect(find.text('Branch 5'), findsNothing);
    expect(find.text('Cash'), findsOneWidget);
    await tester.tap(find.text('Cash'));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('No Active Shift'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

class FixtureApi extends ApiService {
  FixtureApi(this.provider);
  final AppProvider provider;
  @override
  Future<List<Product>> getProducts(
          {String? search,
          int? categoryId,
          int page = 0,
          bool preferCache = false}) async =>
      provider.products;
  @override
  Future<List<Category>> getCategories() async =>
      [Category(id: 1, name: 'Groceries', code: 'GROC')];
  @override
  Future<CashSession?> getActiveSession(
          {bool keepLocalWhenServerHasNoSession = false}) async =>
      provider.activeSession;
}

void setSize(WidgetTester tester, Size size) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

AppProvider fixture() {
  final provider = AppProvider()
    ..setSession(CashSession(
        id: 128,
        status: 'OPEN',
        openingFloatUsd: 50,
        openingFloatZwg: 0,
        expectedCashUsd: 50,
        totalSalesUsd: 0));
  final names = [
    'Maize meal 2 kg',
    'Cooking oil 2 L',
    'Sugar 2 kg',
    'Bread loaf',
    'Milk 1 L',
    'Orange juice 1 L',
    'Rice 2 kg',
    'Laundry soap',
    'Bottled water 500 ml'
  ];
  final prices = [2.50, 4.80, 2.20, 1.20, 1.50, 1.80, 3.40, 0.90, 0.50];
  provider.setProducts(List.generate(
      names.length,
      (i) => Product(
          id: i + 1,
          name: names[i],
          sku: 'TEST-$i',
          sellingPriceUsd: prices[i],
          sellingPriceZwg: prices[i] * 26,
          costPriceUsd: 0,
          taxRate: 0,
          isTaxable: false,
          quantityOnHand: 100)));
  provider.addToCart(provider.products.first);
  provider.addToCart(provider.products.first);
  return provider;
}

Future<void> capture(WidgetTester tester, String name) async {
  if (!const bool.fromEnvironment('CAPTURE_UI')) return;
  final boundary = tester.firstRenderObject<RenderRepaintBoundary>(
      find.byType(RepaintBoundary).first);
  await tester.runAsync(() async {
    final image = await boundary.toImage();
    final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
    await Directory('build/screenshots').create(recursive: true);
    await File('build/screenshots/$name.png')
        .writeAsBytes(bytes!.buffer.asUint8List());
    image.dispose();
  });
}
