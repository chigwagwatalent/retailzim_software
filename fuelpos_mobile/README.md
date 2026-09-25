# RetailZW FuelPOS

Android cashier application for tenants whose RetailZW package and assigned branch use `FUEL_MODULE`.

It uses the existing RetailZW mobile login, tenant, branch, user and role records. Cashiers sign in with the same credentials created in RetailZW **Users & Roles**. The app does not create a second shop account.

## Development

```powershell
flutter pub get
flutter test
flutter build apk --debug --dart-define=RETAILZW_API_BASE_URL=http://10.0.2.2:8080
```

For production, omit the define to use `https://admin.retailzw.co.zw`, then build with the configured Android signing key.
