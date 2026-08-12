import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gaspos_retailzw/app_state.dart';
import 'package:gaspos_retailzw/main.dart';
import 'package:gaspos_retailzw/models.dart';
import 'package:gaspos_retailzw/services.dart';

class CashierOnlyApi extends GasApi {
  @override
  Future<GasUser> login(String username, String password,
      {bool rememberMe = true}) {
    throw const HttpException(
        'Only cashier accounts can sign in on the mobile app.');
  }
}

class DelayedLoginApi extends GasApi {
  final loginResult = Completer<GasUser>();

  @override
  Future<GasUser?> restoreUser() async => null;

  @override
  Future<GasUser> login(String username, String password,
          {bool rememberMe = true}) =>
      loginResult.future;
}

void main() {
  testWidgets('splash keeps startup branding simple', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Splash()));

    expect(find.byType(Image), findsOneWidget);
    expect(find.text('Powered by CN TECH'), findsOneWidget);
    expect(find.text('GasPOS RetailZW'), findsNothing);
  });

  testWidgets('GasPOS login exposes the approved clean sign-in flow',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: LoginScreen(state: GasPosState())),
    );

    expect(find.byType(Image), findsOneWidget);
    expect(find.text('GasPOS RetailZW'), findsNothing);
    expect(find.text('Cashier username'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
    expect(find.text('Keep me signed in'), findsOneWidget);
    expect(find.text('Sign in'), findsOneWidget);
    expect(
        find.text('Use an active cashier account assigned to this gas branch.'),
        findsOneWidget);
    expect(find.text('Powered by CN TECH'), findsOneWidget);
    expect(find.text('Forgot password?'), findsNothing);
    expect(find.text('Offline sales sync automatically'), findsNothing);

    final checkbox = tester.widget<Checkbox>(find.byType(Checkbox));
    expect(checkbox.value, isTrue);
  });

  testWidgets('login rejection remains visible and explains cashier access',
      (tester) async {
    final state = GasPosState(api: CashierOnlyApi());
    await tester.pumpWidget(MaterialApp(home: LoginScreen(state: state)));

    await tester.enterText(
        find.widgetWithText(TextFormField, 'Cashier username'), 'admin');
    await tester.enterText(
        find.widgetWithText(TextFormField, 'Password'), 'secret');
    await tester.tap(find.text('Sign in'));
    await tester.pump();

    expect(
      find.text(
          'GasPOS accepts cashier accounts only. Ask the shop administrator to create or update your cashier account.'),
      findsOneWidget,
    );
  });

  testWidgets(
      'signing in keeps the login page mounted and shows invalid credentials',
      (tester) async {
    final api = DelayedLoginApi();
    final state = GasPosState(api: api);
    await tester.pumpWidget(
      GasPosApp(
        appState: state,
        minimumSplashDuration: Duration.zero,
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(
        find.widgetWithText(TextFormField, 'Cashier username'), 'cashier');
    await tester.enterText(
        find.widgetWithText(TextFormField, 'Password'), 'wrong-password');
    await tester.tap(find.text('Sign in'));
    await tester.pump();

    expect(find.byType(Splash), findsNothing);
    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    api.loginResult.completeError(
      const GasApiException(400, 'Invalid username or password.'),
    );
    await tester.pumpAndSettle();

    expect(find.text('Invalid username or password.'), findsOneWidget);
    expect(find.byType(LoginScreen), findsOneWidget);
  });
}
