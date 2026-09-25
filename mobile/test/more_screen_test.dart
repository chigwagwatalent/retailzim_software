import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:retailzw_mobile/models/models.dart';
import 'package:retailzw_mobile/providers/app_provider.dart';
import 'package:retailzw_mobile/screens/more/more_screen.dart';
import 'windows_redesign_test.dart' show capture;

void main() {
  setUpAll(() async {
    if (const bool.fromEnvironment('CAPTURE_UI')) {
      await (FontLoader('Roboto')
            ..addFont(Future.value(ByteData.sublistView(
                await File('C:/Windows/Fonts/segoeui.ttf').readAsBytes()))))
          .load();
      await (FontLoader('MaterialIcons')
            ..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf')))
          .load();
    }
  });
  for (final width in [390.0, 1366.0]) {
    testWidgets('More shows names and preserves settings at $width',
        (tester) async {
      tester.view.physicalSize = Size(width, 1080);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final provider = AppProvider()
        ..setUser(UserInfo(
          id: 30,
          username: 'pos',
          firstName: 'Trymore',
          lastName: 'Hama',
          role: 'CASHIER',
          branchId: 5,
          tenantId: 5,
          branchName: 'City Centre',
          companyName: 'Example Retail',
        ));
      await tester.pumpWidget(ChangeNotifierProvider.value(
          value: provider,
          child: const MaterialApp(home: Scaffold(body: MoreScreen()))));
      expect(find.text('Trymore Hama'), findsOneWidget);
      expect(find.text('City Centre'), findsOneWidget);
      expect(find.text('Example Retail'), findsOneWidget);
      expect(find.text('User ID'), findsNothing);
      expect(find.text('Tenant #5'), findsNothing);
      await capture(tester, 'more-${width.toInt()}');
      await tester.ensureVisible(find.text('ZWG'));
      await tester.tap(find.text('ZWG'));
      await tester.pumpAndSettle();
      expect(provider.currency, 'ZWG');
      await tester.ensureVisible(find.text('Forget this device'));
      await tester.tap(find.text('Forget this device'));
      await tester.pumpAndSettle();
      expect(find.text('Forget this device?'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(provider.currentUser?.fullName, 'Trymore Hama');
      expect(tester.takeException(), isNull);
    });
  }
  testWidgets('legacy profile uses honest placeholders instead of IDs',
      (tester) async {
    final provider = AppProvider()
      ..setUser(UserInfo(
          id: 30,
          username: 'pos',
          firstName: '',
          lastName: '',
          role: 'CASHIER',
          branchId: 5,
          tenantId: 5));
    await tester.pumpWidget(ChangeNotifierProvider.value(
        value: provider,
        child: const MaterialApp(home: Scaffold(body: MoreScreen()))));
    expect(find.text('Shop name unavailable'), findsOneWidget);
    expect(find.text('Branch name unavailable'), findsOneWidget);
    expect(find.text('Tenant #5'), findsNothing);
    expect(find.text('PENDING'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
