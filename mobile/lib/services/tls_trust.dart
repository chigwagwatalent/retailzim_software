import 'dart:io';
import 'package:flutter/services.dart';

/// Supplement Windows' trust store with the public roots used by our server.
/// Hostname, validity and chain verification remain enabled. Never trust a
/// server leaf certificate or accept arbitrary certificates on failure.
class TlsTrust {
  static Future<void> initialize() async {
    if (!Platform.isWindows) return;
    for (final name in ['isrgrootx1.pem', 'isrg-root-x2.pem']) {
      final data = await rootBundle.load('assets/certificates/$name');
      SecurityContext.defaultContext.setTrustedCertificatesBytes(
          data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes));
    }
  }
}
