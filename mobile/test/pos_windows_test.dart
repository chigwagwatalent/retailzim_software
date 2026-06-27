import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:retailzw_mobile/models/models.dart';
import 'package:retailzw_mobile/providers/app_provider.dart';
import 'package:retailzw_mobile/screens/pos/payment_screen.dart';
import 'package:retailzw_mobile/screens/pos/pos_screen.dart';
import 'package:retailzw_mobile/services/api_service.dart';

void main() {
  testWidgets('Windows POS renders the desktop selling workspace',
      (tester) async {
    tester.view.physicalSize = const Size(1600, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final provider = _provider();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: provider,
        child: const MaterialApp(home: Scaffold(body: PosScreen())),
      ),
    );
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('Categories'), findsOneWidget);
    expect(find.text('Current Sale'), findsOneWidget);
    expect(find.text('Walk-in customer'), findsOneWidget);
    expect(find.text('Discount'), findsOneWidget);
    expect(find.text('Borrower'), findsOneWidget);
    expect(find.text('EcoCash'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('embedded checkout fits a Windows dialog', (tester) async {
    tester.view.physicalSize = const Size(1120, 760);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final provider = _provider();
    await tester.pumpWidget(MaterialApp(
      home: PaymentScreen(
        provider: provider,
        session: provider.activeSession!,
        api: ApiService(),
        embedded: true,
        initialMethod: 'ECOCASH',
      ),
    ));
    await tester.pump();

    expect(find.text('Secure checkout'), findsOneWidget);
    expect(find.text('Payment Method'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

AppProvider _provider() {
  final provider = AppProvider()
    ..setSession(CashSession(
      id: 128,
      status: 'OPEN',
      openingFloatUsd: 20,
      openingFloatZwg: 0,
      expectedCashUsd: 20,
      totalSalesUsd: 0,
    ))
    ..setProducts([
      Product(
        id: 1,
        name: 'White Star Maize Meal 2.5kg',
        sku: 'GROC-001',
        barcode: '263000000001',
        sellingPriceUsd: 2.50,
        sellingPriceZwg: 65,
        costPriceUsd: 1.80,
        taxRate: 15,
        isTaxable: true,
        categoryId: 1,
        quantityOnHand: 34,
      ),
      Product(
        id: 2,
        name: 'Sunfoil Cooking Oil 2L',
        sku: 'GROC-002',
        sellingPriceUsd: 3.80,
        sellingPriceZwg: 98,
        costPriceUsd: 2.90,
        taxRate: 15,
        isTaxable: true,
        categoryId: 1,
        quantityOnHand: 18,
      ),
    ]);
  provider.addToCart(provider.products.first);
  return provider;
}
