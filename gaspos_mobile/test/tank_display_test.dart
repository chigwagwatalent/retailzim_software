import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gaspos_retailzw/app_state.dart';
import 'package:gaspos_retailzw/main.dart';
import 'package:gaspos_retailzw/models.dart';

void main() {
  testWidgets('signed-in shell displays the captured cashier name',
      (tester) async {
    final state = GasPosState()
      ..user = const GasUser(
        token: 'token',
        tenantId: 7,
        branchId: 11,
        branchName: 'Harare Gas',
        displayName: 'Tendai Moyo',
        companyName: 'Retail Zim',
        username: 'tendai',
      )
      ..data = GasBootstrap(
        tanks: [],
        shiftTanks: const [],
        prices: const {'USD': 2},
        sales: [],
        heldChange: [],
      );

    await tester.pumpWidget(MaterialApp(home: GasShell(state: state)));

    expect(find.text('Harare Gas  •  Tendai Moyo'), findsOneWidget);
    expect(find.byTooltip('Cashier account: Tendai Moyo'), findsOneWidget);
  });

  testWidgets('configured branch tanks are visible before opening a shift',
      (tester) async {
    var openedShiftSetup = false;
    final state = GasPosState()
      ..user = const GasUser(
        token: 'token',
        tenantId: 7,
        branchId: 11,
        branchName: 'Harare Gas',
        displayName: 'Cashier',
        companyName: 'Retail Zim',
      )
      ..data = GasBootstrap(
        tanks: [
          GasTank(
            id: 1,
            name: 'Main Tank',
            currentKg: 450,
            capacityKg: 1000,
            tareKg: 200,
            fullGrossKg: 1200,
            status: 'ACTIVE',
          ),
        ],
        shiftTanks: const [],
        prices: const {'USD': 2},
        sales: [],
        heldChange: [],
      );

    await tester.pumpWidget(
      MaterialApp(
        home: SellPage(
          state: state,
          onOpenShift: () => openedShiftSetup = true,
        ),
      ),
    );

    expect(find.text('Main Tank'), findsOneWidget);
    expect(find.text('450.000 kg LPG available'), findsOneWidget);
    await tester.tap(find.text('Select tanks and open shift'));
    expect(openedShiftSetup, isTrue);
  });
}
