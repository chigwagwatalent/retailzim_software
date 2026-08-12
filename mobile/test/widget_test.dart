// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:retailzw_mobile/main.dart';
import 'package:retailzw_mobile/models/models.dart';
import 'package:retailzw_mobile/providers/app_provider.dart';
import 'package:retailzw_mobile/screens/auth/splash_screen.dart';
import 'package:retailzw_mobile/screens/home_screen.dart';

void main() {
  testWidgets('RetailZW app renders login screen', (WidgetTester tester) async {
    await tester.pumpWidget(RetailZwApp(restoreUser: () async => null));
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.text('Sign In'), findsOneWidget);
    expect(find.bySemanticsLabel('RetailZW'), findsOneWidget);
  });

  testWidgets('valid saved session resumes directly into the POS',
      (WidgetTester tester) async {
    final provider = AppProvider();
    final user = UserInfo(
      id: 17,
      username: 'cashier.one',
      firstName: 'Tariro',
      lastName: 'Moyo',
      role: 'CASHIER',
      branchId: 4,
      tenantId: 2,
    );

    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: provider,
        child: MaterialApp(
          home: SplashScreen(restoreUser: () async => user),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 1500));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.byType(HomeScreen), findsOneWidget);
    expect(provider.currentUser?.username, 'cashier.one');

    await tester.pumpWidget(const SizedBox.shrink());
  });
}
