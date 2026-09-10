import 'dart:io';

// Run from mobile/: dart run tool/check_tls.dart
// No credentials are sent and certificate validation is never disabled.
Future<void> main() async {
  final context = SecurityContext(withTrustedRoots: false);
  for (final name in ['isrgrootx1.pem', 'isrg-root-x2.pem']) {
    context.setTrustedCertificates('assets/certificates/$name');
  }
  final client = HttpClient(context: context)
    ..connectionTimeout = const Duration(seconds: 15);
  try {
    final request =
        await client.getUrl(Uri.parse('https://admin.retailzw.co.zw/'));
    request.followRedirects = false;
    final response = await request.close();
    await response.drain<void>();
    if (response.statusCode < 200 || response.statusCode >= 400) {
      throw StateError('Unexpected server status: ${response.statusCode}');
    }
    stdout.writeln(
        'PASS: production HTTPS verifies using only the bundled roots (${response.statusCode}).');
  } finally {
    client.close(force: true);
  }
  final strict = HttpClient()..connectionTimeout = const Duration(seconds: 15);
  try {
    for (final host in [
      'expired.badssl.com',
      'wrong.host.badssl.com',
      'self-signed.badssl.com'
    ]) {
      try {
        final request = await strict.getUrl(Uri.https(host, '/'));
        await request.close();
        throw StateError('Invalid certificate accepted: $host');
      } on HandshakeException {
        stdout.writeln('PASS: invalid certificate rejected ($host).');
      }
    }
  } finally {
    strict.close(force: true);
  }
}
