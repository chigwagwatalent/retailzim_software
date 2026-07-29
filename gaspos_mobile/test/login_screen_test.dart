import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gaspos_retailzw/app_state.dart';
import 'package:gaspos_retailzw/main.dart';

void main() {
  testWidgets('GasPOS login exposes the approved clean sign-in flow',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: LoginScreen(state: GasPosState())),
    );

    expect(find.text('GasPOS RetailZW'), findsOneWidget);
    expect(find.text('Username or employee ID'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
    expect(find.text('Remember me'), findsOneWidget);
    expect(find.text('Forgot password?'), findsOneWidget);
    expect(find.text('Sign in'), findsOneWidget);
    expect(find.text('Offline sales sync automatically'), findsOneWidget);

    final checkbox = tester.widget<Checkbox>(find.byType(Checkbox));
    expect(checkbox.value, isTrue);
  });
}
