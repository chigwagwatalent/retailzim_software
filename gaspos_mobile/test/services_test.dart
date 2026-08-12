import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:gaspos_retailzw/services.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('login preserves a useful backend rejection message', () async {
    final api = GasApi(
      client: MockClient(
        (_) async => http.Response(
          '{"success":false,"message":"Only cashier accounts can sign in on the mobile app."}',
          400,
          headers: {'content-type': 'application/json'},
        ),
      ),
    );

    await expectLater(
      api.login('admin', 'secret'),
      throwsA(
        isA<HttpException>().having(
          (error) => error.message,
          'message',
          'Only cashier accounts can sign in on the mobile app.',
        ),
      ),
    );
  });

  test('login translates a non-json server failure', () async {
    final api = GasApi(
      client: MockClient(
        (_) async => http.Response('<html>Bad gateway</html>', 502),
      ),
    );

    await expectLater(
      api.login('cashier', 'secret'),
      throwsA(
        isA<HttpException>().having(
          (error) => error.message,
          'message',
          'RetailZW is temporarily unavailable. Please try again shortly.',
        ),
      ),
    );
  });

  test('API authentication failures retain their HTTP status', () async {
    final api = GasApi(
      client: MockClient(
        (_) async => http.Response(
          '{"success":false,"message":"Authentication token has expired."}',
          401,
          headers: {'content-type': 'application/json'},
        ),
      ),
    );

    await expectLater(
      api.bootstrap(4),
      throwsA(
        isA<GasApiException>()
            .having((error) => error.statusCode, 'statusCode', 401)
            .having(
                (error) => error.isAuthenticationFailure, 'auth failure', true),
      ),
    );
  });
}
