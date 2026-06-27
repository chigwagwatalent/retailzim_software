import 'dart:async';

import 'api_service.dart';

class SyncScheduler {
  SyncScheduler(this._api);

  static const Duration interval = Duration(seconds: 10);

  final ApiService _api;
  Timer? _timer;
  bool _running = false;

  bool get isActive => _timer?.isActive ?? false;

  void start({
    void Function(SyncResult result)? onSynced,
    void Function(Object error)? onError,
  }) {
    if (isActive) return;
    unawaited(runOnce(onSynced: onSynced, onError: onError));
    _timer = Timer.periodic(interval, (_) {
      unawaited(runOnce(onSynced: onSynced, onError: onError));
    });
  }

  Future<void> runOnce({
    void Function(SyncResult result)? onSynced,
    void Function(Object error)? onError,
  }) async {
    if (_running) return;
    _running = true;
    try {
      if (!await _api.isLoggedIn()) return;
      final result = await _api.syncOfflineSales();
      onSynced?.call(result);
    } catch (error) {
      onError?.call(error);
    } finally {
      _running = false;
    }
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
  }
}
