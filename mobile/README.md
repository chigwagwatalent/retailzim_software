# RetailZW POS

RetailZW POS supports Android and Windows from the same Flutter codebase. The Windows version uses SQLite for offline operation and the native Windows print dialog for receipts.

## Build the Windows application

From PowerShell:

```powershell
.\packaging\windows\build_windows.ps1
```

The build creates:

- `dist\RetailZW-POS-Setup-1.2.4.exe` - single-file Windows x64 installer, including the Microsoft Visual C++ x64 runtime and all Flutter assets/plugins. Setup automatically uses the production server for new installations and preserves existing server configuration on upgrades, without displaying a server URL page. Includes the icon-font, branch-name and installer startup fixes. Users upgrading from before 1.2.3 must sign out and sign in online once to fetch their branch name.
- `build\windows\x64\runner\Release\` - full application payload. Do not distribute the application EXE without its adjacent DLLs and data folder.

Requires Windows 10 version 1809 or newer (64-bit), or Windows 11. The installer can install offline; the first cashier login and initial product sync need a working backend connection. Existing local data and saved credentials remain in their original locations during an upgrade.

The desktop login and POS use the refreshed blue/white design, original RetailZim logo, and a generated checkout illustration. Product photographs come from real product image URLs; a neutral placeholder is used when an image is absent or unavailable offline. Prices, tax, stock validation, payment methods and offline queue behavior continue to use the existing services.

Windows HTTPS includes the official ISRG Root X1/X2 public roots for machines missing the server's CA. Hostname, expiry and certificate-chain verification remain enabled. Antivirus/proxy certificates and an incorrect PC clock can still require attention on the affected PC. Run `dart run tool/check_tls.dart` from this directory for a read-only TLS smoke check.

The build checks the Microsoft signature of the bundled runtime and stops on analysis, test, build or installer errors. For trusted publisher signing, pass `-SigningCertificatePath` and `-SigningCertificatePassword` (a SecureString) with your Authenticode certificate. Without it, the installer is unsigned and Windows may show an unknown-publisher/SmartScreen warning.

Visual verification images can be regenerated with `flutter test --dart-define=CAPTURE_UI=true test/windows_redesign_test.dart` on Windows; outputs are in `build/screenshots/` and contain test data only.

To target another backend:

```powershell
.\packaging\windows\build_windows.ps1 -ApiBaseUrl "https://your-server.example.com"
```

## Windows cashier shortcuts

- `F2`: focus product or barcode search.
- `F4`: open payment for the current cart.
- `Esc`: clear search and return focus to barcode entry.

Barcode scanners configured as keyboard input work automatically. Add the scanner suffix `Enter` for the fastest checkout flow.
