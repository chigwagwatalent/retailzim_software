import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:retailzw_mobile/services/api_service.dart';
import 'package:retailzw_mobile/services/sync_scheduler.dart';

void main() {
  group('SyncScheduler', () {
    test('never runs overlapping sync operations', () async {
      final api = _FakeApiService(loggedIn: true);
      final scheduler = SyncScheduler(api);

      final first = scheduler.runOnce();
      final second = scheduler.runOnce();
      await second;

      expect(api.syncCalls, 1);
      api.completeSync();
      await first;
    });

    test('does not sync after the local session is logged out', () async {
      final api = _FakeApiService(loggedIn: false);
      final scheduler = SyncScheduler(api);

      await scheduler.runOnce();

      expect(api.syncCalls, 0);
    });

    test('reports the completed sync result', () async {
      final api = _FakeApiService(loggedIn: true);
      final scheduler = SyncScheduler(api);
      SyncResult? reported;

      final operation = scheduler.runOnce(onSynced: (result) {
        reported = result;
      });
      api.completeSync();
      await operation;

      expect(reported?.synced, 2);
      expect(reported?.pending, 0);
      expect(reported?.hasFailures, isFalse);
    });
  });
}

class _FakeApiService extends ApiService {
  _FakeApiService({required this.loggedIn});

  final bool loggedIn;
  final Completer<void> _syncGate = Completer<void>();
  int syncCalls = 0;

  @override
  Future<bool> isLoggedIn() async => loggedIn;

  @override
  Future<SyncResult> syncOfflineSales() async {
    syncCalls++;
    await _syncGate.future;
    return const SyncResult(synced: 2, failed: 0, pending: 0);
  }

  void completeSync() {
    if (!_syncGate.isCompleted) _syncGate.complete();
  }
}
