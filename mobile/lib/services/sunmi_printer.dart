import 'dart:async';
import 'package:flutter/services.dart';

class BuiltInPrinterInfo {
  final bool available;
  final bool detected;
  final bool ready;
  final String model;
  final String message;
  const BuiltInPrinterInfo({
    this.available = false,
    this.detected = false,
    this.ready = false,
    this.model = 'SUNMI',
    this.message =
        'Built-in printer service is unavailable. Tap Refresh to retry.',
  });
  factory BuiltInPrinterInfo.fromMap(Map<Object?, Object?> value) =>
      BuiltInPrinterInfo(
        available: value['available'] == true,
        detected: value['detected'] == true || value['available'] == true,
        ready: value['ready'] == true,
        model: value['model']?.toString() ?? 'SUNMI',
        message: value['message']?.toString() ??
            'Built-in printer service is unavailable. Tap Refresh to retry.',
      );
}

class SunmiPrinter {
  static const channel = MethodChannel('retailzw/sunmi_printer');

  Future<BuiltInPrinterInfo> getInfo() async {
    try {
      final value = await channel
          .invokeMapMethod<Object?, Object?>('getInfo')
          .timeout(const Duration(seconds: 6));
      return BuiltInPrinterInfo.fromMap(value ?? const {});
    } on MissingPluginException {
      return const BuiltInPrinterInfo();
    } on PlatformException catch (error) {
      return BuiltInPrinterInfo(message: error.message ?? error.code);
    } on TimeoutException {
      return const BuiltInPrinterInfo(
          message:
              'Built-in printer connection timed out. Tap Refresh to retry.');
    }
  }

  Future<bool> printRaw(List<int> bytes) async =>
      await channel.invokeMethod<bool>('printRaw', {
        'bytes': Uint8List.fromList(bytes),
      }).timeout(const Duration(seconds: 35)) ==
      true;
}
