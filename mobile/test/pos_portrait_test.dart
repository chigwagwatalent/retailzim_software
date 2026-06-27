import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:retailzw_mobile/models/models.dart';
import 'package:retailzw_mobile/providers/app_provider.dart';
import 'package:retailzw_mobile/screens/pos/pos_screen.dart';

void main() {
  testWidgets('portrait POS keeps cart compact and opens sale modal',
      (tester) async {
    tester.view.physicalSize = const Size(480, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final provider = AppProvider()
      ..setSession(CashSession(
        id: 1,
        status: 'OPEN',
        openingFloatUsd: 0,
        openingFloatZwg: 0,
        expectedCashUsd: 0,
        totalSalesUsd: 0,
      ))
      ..setProducts([
        Product(
          id: 1,
          name: 'Mazoe Orange Crush 2L',
          sku: 'GROC-MAZOE-2L',
          sellingPriceUsd: 3.50,
          sellingPriceZwg: 52.50,
          costPriceUsd: 2.10,
          taxRate: 15,
          isTaxable: true,
          quantityOnHand: 10,
        ),
      ]);
    provider.addToCart(provider.products.first);

    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: provider,
        child: const MaterialApp(home: Scaffold(body: PosScreen())),
      ),
    );
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.text('1 item(s)'), findsOneWidget);
    expect(find.text('Charge'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.receipt_long_rounded));
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.text('Current Sale'), findsWidgets);
    expect(find.text('Mazoe Orange Crush 2L'), findsWidgets);
    expect(find.text('Clear'), findsOneWidget);
  });
}
