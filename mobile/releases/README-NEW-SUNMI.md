# RetailZW Shop — new SUNMI installation

APK: RetailZW-Shop-NEW-v1.0.0-SUNMI.apk
Version: 1.0.0 (1)
Android package: zw.co.retailzw.pos.sunmi
Display name: RetailZW Shop

This installs as a separate app alongside RetailZW POS. It starts with fresh local app storage; it does not erase the old app, its offline records, or server records. It connects to https://admin.retailzw.co.zw using your shop account.

Install the APK, sign in, then open More → Receipt Printer. On a SUNMI terminal, the built-in printer is selected automatically when no external printer has been selected. Load paper, close the cover and tap Test. Bluetooth pairing is not required for built-in printing. If the printer service is starting, tap Refresh; if it remains unavailable, restart the terminal and check its SUNMI printer service.

Uses the manufacturer's printer SDK and Android service integration: https://github.com/shangmisunmi/SunmiPrinterDemo

Verification: release APK signature, package/name/version, launch activity and native SUNMI bridge verified. Shared app tests: 39 passed; static analysis clean. Printing on the physical SUNMI terminal remains to be confirmed using Test.
