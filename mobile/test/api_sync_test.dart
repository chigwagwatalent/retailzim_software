import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:retailzw_mobile/models/models.dart';
import 'package:retailzw_mobile/services/api_service.dart';
import 'package:retailzw_mobile/services/local_database.dart';
import 'package:retailzw_mobile/services/offline_service.dart';

class _SyncApi extends ApiService {
  @override
  Future<CashSession?> getActiveSession({
    bool keepLocalWhenServerHasNoSession = false,
  }) async =>
      CashSession.fromJson({'id': 99, 'branchId': 9});
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  LocalDatabase.ensureInitialized();
  final offline = OfflineService();
  late Directory directory;
  late _SyncApi api;

  setUpAll(() async {
    directory = await Directory.systemTemp.createTemp('retailzw-sync-test-');
    OfflineService.setDatabaseRootForTesting(directory.path);
  });
  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({'auth_token': 'test-token'});
    await offline.clearShiftData();
    api = _SyncApi();
  });
  tearDownAll(() async {
    await offline.closeDatabaseForTesting();
    OfflineService.setDatabaseRootForTesting(null);
    await directory.delete(recursive: true);
  });

  Future<void> queue(String id) =>
      offline.queueSale({'cashSessionId': 77, 'branchId': 7, 'items': []}, id);

  test(
    'preserves queued branch and shift and continues after rejection',
    () async {
      await queue('bad-sale');
      await queue('good-sale');
      final posted = <Map<String, dynamic>>[];
      final result = await http.runWithClient(
        () => api.syncOfflineSales(),
        () => MockClient((request) async {
          if (request.method != 'POST') return http.Response('{}', 200);
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          posted.add(body);
          if (body['offlineReceiptNumber'] == 'bad-sale') {
            return http.Response('{"message":"Invalid product"}', 400);
          }
          return http.Response('{"id":123,"receiptNumber":"SERVER-123"}', 200);
        }),
      );
      expect(posted, hasLength(2));
      expect(posted.last['branchId'], 7);
      expect(posted.last['cashSessionId'], 77);
      expect(result.synced, 1);
      expect(result.failed, 1);
      expect(result.pending, 1);
      expect(
        (await offline.getPendingSales()).single['offline_uuid'],
        'bad-sale',
      );
    },
  );

  test('concurrent sync callers send each queued sale once', () async {
    await queue('one-sale');
    final started = Completer<void>();
    final release = Completer<void>();
    var posts = 0;
    await http.runWithClient(
      () async {
        final first = api.syncOfflineSales();
        await started.future;
        final second = _SyncApi().syncOfflineSales();
        release.complete();
        final results = await Future.wait([first, second]);
        expect(posts, 1);
        expect(results.every((result) => result.synced == 1), isTrue);
      },
      () => MockClient((request) async {
        if (request.method != 'POST') return http.Response('{}', 200);
        posts++;
        started.complete();
        await release.future;
        return http.Response('{"id":123}', 200);
      }),
    );
  });

  test(
    'authentication failure stops sync and retains the whole queue',
    () async {
      await queue('first');
      await queue('second');
      var posts = 0;
      final result = await http.runWithClient(
        () => api.syncOfflineSales(),
        () => MockClient((request) async {
          if (request.method != 'POST') return http.Response('{}', 200);
          posts++;
          return http.Response('{"message":"Expired"}', 401);
        }),
      );
      expect(posts, 1);
      expect(result.pending, 2);
      expect(result.synced, 0);
    },
  );

  test('HTML server error retains queue and stops further requests', () async {
    await queue('first');
    await queue('second');
    var posts = 0;
    final result = await http.runWithClient(
      () => api.syncOfflineSales(),
      () => MockClient((request) async {
        if (request.method != 'POST') return http.Response('{}', 200);
        posts++;
        return http.Response('<html>Bad gateway</html>', 502);
      }),
    );
    expect(posts, 1);
    expect(result.pending, 2);
    expect(result.synced, 0);
  });

  test('missing token counts change actions as pending', () async {
    FlutterSecureStorage.setMockInitialValues({});
    await offline.queueAction('CHANGE_COLLECTION', 'change-1', {'id': 1});
    final result = await api.syncOfflineSales();
    expect(result.pending, 1);
    expect(result.failed, 1);
  });

  test('shift cannot close while records are pending', () async {
    await queue('pending-sale');
    await expectLater(api.closeSession(77, 0, 0), throwsA(isA<ApiException>()));
    expect(await offline.getPendingCount(), 1);
  });
}
