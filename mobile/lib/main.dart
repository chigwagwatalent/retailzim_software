import 'package:flutter/material.dart';
import 'dart:io';
import 'package:provider/provider.dart';

import 'models/models.dart';
import 'providers/app_provider.dart';
import 'screens/auth/splash_screen.dart';
import 'services/local_database.dart';
import 'services/tls_trust.dart';
import 'widgets/common_widgets.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await TlsTrust.initialize();
  LocalDatabase.ensureInitialized();
  runApp(const RetailZwApp());
}

class RetailZwApp extends StatelessWidget {
  const RetailZwApp({super.key, this.restoreUser});

  final Future<UserInfo?> Function()? restoreUser;

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => AppProvider(),
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'RetailZW Cashier',
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: AppColors.primaryBlue,
            primary: AppColors.primaryBlue,
            secondary: AppColors.accentYellow,
          ),
          useMaterial3: true,
          fontFamily: Platform.isWindows ? 'Segoe UI' : null,
          outlinedButtonTheme: OutlinedButtonThemeData(
              style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.textDark,
                  side: const BorderSide(color: Color(0xFFCCDCEA)),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)))),
          scaffoldBackgroundColor: AppColors.background,
          appBarTheme: const AppBarTheme(
            backgroundColor: AppColors.primaryBlue,
            foregroundColor: Colors.white,
            elevation: 0,
          ),
          cardTheme: CardThemeData(
            color: Colors.white,
            elevation: 0,
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
          ),
          inputDecorationTheme: InputDecorationTheme(
            filled: true,
            fillColor: Colors.white,
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: Color(0xFFE1E8F5)),
            ),
          ),
        ),
        home: SplashScreen(restoreUser: restoreUser),
      ),
    );
  }
}
