# GasPOS RetailZW

Standalone Android cashier application for RetailZW LPG branches. It is deliberately separate from the existing RetailZW retail POS application.

## Cashier scope

- Open a shift and select every tank connected to the selling machine.
- Sell from one or several tanks at the same time.
- Accept Cash, EcoCash, OneMoney, InnBucks, Card, Bank Transfer, or split payments.
- Give change or hold it against a verified customer name and phone number.
- Queue completed sales in the local SQLite database while offline and synchronize them idempotently when connectivity returns.
- Enter the closing gross weight for every shift tank. The server subtracts the configured tare weight, compares physical and expected LPG, audits the variance, and closes the shift.

Pricing, stock receipts, stock adjustments, tank setup, and expenses are intentionally absent from this app. Those operations remain in the authenticated Gas Operations web module.

## API target

Release builds use `https://admin.retailzw.co.zw`.

For local testing:

```powershell
flutter run --dart-define=RETAILZW_API_BASE_URL=http://10.0.2.2:8080
```

The Android emulator uses `10.0.2.2` to reach the host machine.

## Build

```powershell
flutter analyze
flutter test
flutter build apk --release
```

The production APK is generated at `build/app/outputs/flutter-apk/app-release.apk`.
