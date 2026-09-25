import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fuelpos_retailzw/app_state.dart';
import 'package:fuelpos_retailzw/main.dart';

void main() {
  testWidgets('login uses RetailZW branding and fuel cashier wording',
      (tester) async {
    await tester
        .pumpWidget(MaterialApp(home: LoginScreen(state: FuelPosState())));
    expect(find.byType(Image), findsOneWidget);
    expect(find.text('Fuel cashier sign in'), findsOneWidget);
    expect(find.text('Cashier username'), findsOneWidget);
    expect(
        find.text(
            'One RetailZW account · Package and branch access are checked automatically'),
        findsOneWidget);
  });
}
