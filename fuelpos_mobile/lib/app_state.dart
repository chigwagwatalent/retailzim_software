import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import 'models.dart';
import 'services.dart';

class FuelPosState extends ChangeNotifier {
  FuelPosState({FuelApi? api, FuelOfflineStore? offline})
      : api = api ?? FuelApi(),
        offline = offline ?? FuelOfflineStore();
  final FuelApi api;
  final FuelOfflineStore offline;
  final _uuid = const Uuid();
  FuelUser? user;
  FuelBootstrap? data;
  bool busy = false;
  bool online = true;
  int pending = 0;
  String? error;

  Future<void> restore() async {
    busy = true;
    notifyListeners();
    try {
      user = await api.restoreUser();
      if (user != null) await refresh();
    } catch (e) {
      await api.logout();
      user = null;
      error = 'Your saved sign-in has expired. Please sign in again.';
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<void> login(String username, String password,
          {bool rememberMe = true}) =>
      _run(() async {
        user = await api.login(username, password, rememberMe: rememberMe);
        await refresh();
      });
  Future<void> logout() async {
    await api.logout();
    user = null;
    data = null;
    error = null;
    notifyListeners();
  }

  Future<void> refresh() async {
    if (user == null) return;
    try {
      final payload = await api.bootstrap(user!.branchId);
      data = FuelBootstrap.fromJson(payload);
      await offline.saveBootstrap(user!.tenantId, user!.branchId, payload);
      online = true;
      error = null;
      await syncPending();
    } catch (e) {
      if (e is FuelApiException && e.isAuthenticationFailure) {
        await logout();
        error = 'Your cashier session expired. Sign in again.';
      } else if (_network(e)) {
        online = false;
        final cached =
            await offline.cachedBootstrap(user!.tenantId, user!.branchId);
        if (cached != null) data = FuelBootstrap.fromJson(cached);
        error = cached == null
            ? 'Connect once to download this station setup.'
            : 'Offline mode · queued sales will sync automatically.';
      } else {
        error = cleanError(e);
      }
    }
    pending = await offline.pendingCount();
    notifyListeners();
  }

  Future<void> openShift(double usd, double zwg) => _run(() async {
        await api.openShift(user!.branchId, usd, zwg);
        await refresh();
      });
  Future<Map<String, dynamic>?> completeSale(
      {required FuelNozzle nozzle,
      required double litres,
      required String currency,
      required String method,
      required double amount}) async {
    if (user == null || data?.shift == null) {
      throw StateError('Open your cashier shift before selling fuel.');
    }
    if (litres <= 0) throw ArgumentError('Enter litres greater than zero.');
    final tank = data!.tanks.where((t) => t.id == nozzle.tankId).first;
    if (tank.stock + 0.0001 < litres) {
      throw StateError('Insufficient stock in ${tank.code}.');
    }
    final request = {
      'branchId': user!.branchId,
      'shiftId': data!.shift!['id'],
      'nozzleId': nozzle.id,
      'litres': litres,
      'currency': currency,
      'payments': [
        {'method': method, 'currency': currency, 'amount': amount}
      ],
      'idempotencyKey': _uuid.v4()
    };
    try {
      final result = await api.sale(request);
      await refresh();
      return result;
    } catch (e) {
      if (!_network(e)) rethrow;
      await offline.queueSale(request['idempotencyKey'] as String, request);
      tank.stock -= litres;
      nozzle.meter += litres;
      online = false;
      pending = await offline.pendingCount();
      error = 'Sale saved offline and queued for automatic sync.';
      notifyListeners();
      return {'offline': true};
    }
  }

  Future<void> closeShift(
          {required double usd,
          required double zwg,
          required List<Map<String, dynamic>> meters,
          required List<Map<String, dynamic>> dips,
          String? note}) =>
      _run(() async {
        await api.closeShift({
          'branchId': user!.branchId,
          'shiftId': data!.shift!['id'],
          'countedUsd': usd,
          'countedZwg': zwg,
          'meters': meters,
          'dips': dips,
          'note': note
        });
        await refresh();
      });
  Future<void> syncPending() async {
    if (user == null || !online) return;
    for (final item in await offline.pendingSales()) {
      try {
        await api.sale(Map<String, dynamic>.from(item['payload'] as Map));
        await offline.removePending(item['id'] as String);
      } catch (e) {
        if (_network(e)) {
          online = false;
          break;
        }
        rethrow;
      }
    }
    pending = await offline.pendingCount();
  }

  Future<void> _run(Future<void> Function() operation) async {
    busy = true;
    error = null;
    notifyListeners();
    try {
      await operation();
    } catch (e) {
      error = cleanError(e);
      rethrow;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  bool _network(Object e) =>
      e is SocketException ||
      e.toString().contains('ClientException') ||
      e.toString().contains('TimeoutException');
}

String cleanError(Object error) => error
    .toString()
    .replaceFirst('HttpException: ', '')
    .replaceFirst('FormatException: ', '')
    .replaceFirst('Bad state: ', '')
    .replaceFirst('Invalid argument(s): ', '');
