# RetailZW POS

RetailZW POS supports Android and Windows from the same Flutter codebase. The Windows version uses SQLite for offline operation and the native Windows print dialog for receipts.

## Build the Windows application

From PowerShell:

```powershell
.\packaging\windows\build_windows.ps1
```

The build creates:

- `dist\RetailZW-POS-Portable-1.0.0.zip` - standalone portable application.
- `dist\RetailZW-POS-Setup-1.0.0.exe` - installer when Inno Setup 6 is available.

To target another backend:

```powershell
.\packaging\windows\build_windows.ps1 -ApiBaseUrl "https://your-server.example.com"
```

## Windows cashier shortcuts

- `F2`: focus product or barcode search.
- `F4`: open payment for the current cart.
- `Esc`: clear search and return focus to barcode entry.

Barcode scanners configured as keyboard input work automatically. Add the scanner suffix `Enter` for the fastest checkout flow.
