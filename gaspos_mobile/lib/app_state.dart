import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';

import 'models.dart';
import 'services.dart';

class GasPosState extends ChangeNotifier {
  GasPosState({GasApi? api, OfflineStore? offline})
      : api = api ?? GasApi(),
        offline = offline ?? OfflineStore();

  final GasApi api;
  final OfflineStore offline;
  final _uuid = const Uuid();
  GasUser? user;
  GasBootstrap? data;
  bool busy = false;
  bool online = true;
  int pending = 0;
  String? error;

  Future<void> restore() async {
    busy = true;
    notifyListeners();
    user = await api.restoreUser();
    if (user != null) await refresh();
    busy = false;
    notifyListeners();
  }

  Future<void> login(String username, String password) async {
    await _run(() async {
      user = await api.login(username, password);
      await refresh();
    });
  }

  Future<void> logout() async {
    await api.logout();
    user = null;
    data = null;
    notifyListeners();
  }

  Future<void> refresh() async {
    if (user == null) return;
    try {
      final payload = await api.bootstrap(user!.branchId);
      await offline.saveBootstrap(payload);
      data = GasBootstrap.fromJson(payload);
      online = true;
      error = null;
      await syncPending();
    } catch (_) {
      online = false;
      final cached = await offline.cachedBootstrap();
      if (cached != null) data = GasBootstrap.fromJson(cached);
      error = 'Offline mode: showing the last synchronized gas position.';
    }
    pending = await offline.pendingCount();
    notifyListeners();
  }

  Future<void> syncPending() async {
    if (user == null || !online) return;
    for (final item in await offline.pendingSales()) {
      try {
        await api.sale(Map<String, dynamic>.from(item['payload'] as Map));
        await offline.removePending(item['id'] as String);
      } catch (e) {
        await offline.markFailed(item['id'] as String, e);
        if (_networkError(e)) {
          online = false;
          break;
        }
      }
    }
    pending = await offline.pendingCount();
    if (online) {
      final payload = await api.bootstrap(user!.branchId);
      await offline.saveBootstrap(payload);
      data = GasBootstrap.fromJson(payload);
    }
    notifyListeners();
  }

  Future<Map<String, dynamic>?> completeSale({
    required double quantityKg,
    required String currency,
    required List<int> tankIds,
    required List<Map<String, dynamic>> payments,
    required double amountReceived,
    bool holdChange = false,
    String? customerName,
    String? customerPhone,
  }) async {
    if (user == null || data?.hasOpenShift != true) return null;
    final id = 'GPO-${_uuid.v4()}';
    final request = <String, dynamic>{
      'branchId': user!.branchId,
      'quantityKg': quantityKg,
      'currency': currency,
      'tanks': tankIds.map((id) => {'tankId': id}).toList(),
      'payments': payments,
      'paymentMethod': payments.length > 1
          ? 'SPLIT_PAYMENT'
          : payments.first['paymentMethod'],
      'amountReceived': amountReceived,
      'holdChange': holdChange,
      if (holdChange) 'heldChangeName': customerName,
      if (holdChange) 'heldChangePhone': customerPhone,
      if (holdChange) 'heldChangeOfflineReference': 'HC-$id',
      'offlineReceiptNumber': id,
      'offlineCreatedAt': DateTime.now().toUtc().toIso8601String(),
    };
    try {
      final sale = await api.sale(request);
      online = true;
      await refresh();
      return sale;
    } catch (e) {
      if (!_networkError(e)) rethrow;
      await offline.queueSale(id, request);
      _applyLocalSale(request);
      pending = await offline.pendingCount();
      online = false;
      error = 'Sale saved safely on this device and waiting to sync.';
      notifyListeners();
      return {
        'receiptNumber': id,
        'quantityKg': quantityKg,
        'total': payments.fold<double>(
            0, (sum, row) => sum + (row['amount'] as num).toDouble()),
        'currency': currency,
        'offline': true,
      };
    }
  }

  Future<void> openShift(List<int> tankIds) async {
    if (!online) throw const SocketException('Connect before opening a shift.');
    await _run(() async {
      await api.openShift(user!.branchId, tankIds);
      await refresh();
    });
  }

  Future<void> closeShift(Map<int, double> grossWeights) async {
    if (!online) throw const SocketException('Connect before closing a shift.');
    if (pending > 0) {
      throw StateError('Sync all pending sales before closing this shift.');
    }
    final shiftId = (data!.shift!['id'] as num).toInt();
    await _run(() async {
      await api.closeShift(
          user!.branchId,
          shiftId,
          grossWeights.entries
              .map((e) => {'tankId': e.key, 'closingGrossKg': e.value})
              .toList());
      await refresh();
    });
  }

  Future<void> collectHeldChange(int id) async {
    if (!online) {
      throw const SocketException(
          'Held change payments require an online confirmation.');
    }
    await _run(() async {
      await api.collectChange(user!.branchId, id);
      await refresh();
    });
  }

  void _applyLocalSale(Map<String, dynamic> request) {
    if (data == null) return;
    final selected = (request['tanks'] as List)
        .map((e) => (e['tankId'] as num).toInt())
        .toList();
    var remaining = (request['quantityKg'] as num).toDouble();
    for (var index = 0; index < selected.length; index++) {
      final tank = data!.tanks.firstWhere((t) => t.id == selected[index]);
      final share = index == selected.length - 1
          ? remaining
          : (remaining / (selected.length - index)).clamp(0, tank.currentKg);
      tank.currentKg -= share;
      remaining -= share;
    }
    data!.sales.insert(
        0,
        Map<String, dynamic>.from(request)
          ..['receiptNumber'] = request['offlineReceiptNumber']
          ..['total'] = (request['payments'] as List).fold<double>(
              0, (sum, p) => sum + (p['amount'] as num).toDouble())
          ..['offline'] = true);
  }

  bool _networkError(Object error) =>
      error is SocketException ||
      error is TimeoutException ||
      error is http.ClientException;

  Future<void> _run(Future<void> Function() operation) async {
    busy = true;
    error = null;
    notifyListeners();
    try {
      await operation();
    } catch (e) {
      error = e.toString().replaceFirst('HttpException: ', '');
      rethrow;
    } finally {
      busy = false;
      notifyListeners();
    }
  }
}
