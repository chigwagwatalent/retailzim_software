import 'dart:convert';
import 'dart:io';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import 'models.dart';

class FuelApiException extends HttpException {
  const FuelApiException(this.statusCode, String message) : super(message);
  final int statusCode;
  bool get isAuthenticationFailure => statusCode == 401 || statusCode == 403;
}

class FuelApi {
  FuelApi({http.Client? client}) : _client = client ?? http.Client();
  static const productionUrl = 'https://admin.retailzw.co.zw';
  static const configuredUrl = String.fromEnvironment('RETAILZW_API_BASE_URL');
  final http.Client _client;
  final _secure = const FlutterSecureStorage();
  String? token;
  String get baseUrl =>
      configuredUrl.trim().isEmpty ? productionUrl : configuredUrl.trim();

  Future<FuelUser> login(String username, String password,
      {bool rememberMe = true}) async {
    final response = await _client
        .post(Uri.parse('$baseUrl/api/auth/mobile-login'),
            headers: {'Content-Type': 'application/json'},
            body:
                jsonEncode({'username': username.trim(), 'password': password}))
        .timeout(const Duration(seconds: 20));
    final user = FuelUser.fromLogin(_decode(response));
    token = user.token;
    if (rememberMe) {
      await _secure.write(key: 'fuelpos_token', value: token);
      await _secure.write(
          key: 'fuelpos_user', value: jsonEncode(user.toJson()));
    } else {
      await _secure.delete(key: 'fuelpos_token');
      await _secure.delete(key: 'fuelpos_user');
    }
    return user;
  }

  Future<FuelUser?> restoreUser() async {
    final raw = await _secure.read(key: 'fuelpos_user');
    if (raw == null) return null;
    final user = FuelUser.fromJson(jsonDecode(raw));
    token = await _secure.read(key: 'fuelpos_token') ?? user.token;
    return user;
  }

  Future<void> logout() async {
    token = null;
    await _secure.delete(key: 'fuelpos_token');
    await _secure.delete(key: 'fuelpos_user');
  }

  Future<Map<String, dynamic>> bootstrap(int branchId) =>
      _get('/api/fuel/pos/bootstrap', {'branchId': '$branchId'});
  Future<Map<String, dynamic>> openShift(
          int branchId, double openingUsd, double openingZwg) =>
      _post('/api/fuel/pos/shifts/open', {
        'branchId': branchId,
        'openingUsd': openingUsd,
        'openingZwg': openingZwg
      });
  Future<Map<String, dynamic>> sale(Map<String, dynamic> request) =>
      _post('/api/fuel/pos/sales', request);
  Future<Map<String, dynamic>> closeShift(Map<String, dynamic> request) =>
      _post('/api/fuel/pos/shifts/close', request);
  Future<Map<String, dynamic>> _get(
          String path, Map<String, String> query) async =>
      _decode(await _client
          .get(Uri.parse('$baseUrl$path').replace(queryParameters: query),
              headers: _headers)
          .timeout(const Duration(seconds: 20)));
  Future<Map<String, dynamic>> _post(
          String path, Map<String, dynamic> body) async =>
      _decode(await _client
          .post(Uri.parse('$baseUrl$path'),
              headers: _headers, body: jsonEncode(body))
          .timeout(const Duration(seconds: 25)));
  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token'
      };
  Map<String, dynamic> _decode(http.Response response) {
    Map<String, dynamic> envelope;
    try {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      if (decoded is! Map) throw const FormatException();
      envelope = Map<String, dynamic>.from(decoded);
    } catch (_) {
      throw FuelApiException(response.statusCode, _status(response.statusCode));
    }
    if (response.statusCode < 200 ||
        response.statusCode >= 300 ||
        envelope['success'] == false) {
      throw FuelApiException(response.statusCode,
          envelope['message']?.toString() ?? _status(response.statusCode));
    }
    final data = envelope['data'];
    return data is Map ? Map<String, dynamic>.from(data) : <String, dynamic>{};
  }

  String _status(int status) {
    if (status == 401) return 'Invalid username or password.';
    if (status == 403) return 'This account cannot access FuelPOS.';
    if (status >= 500) return 'RetailZW is temporarily unavailable.';
    return 'RetailZW could not complete this request (error $status).';
  }
}

class FuelOfflineStore {
  Database? _db;
  Future<Database> get db async => _db ??= await openDatabase(
          p.join(await getDatabasesPath(), 'fuelpos_retailzw.db'),
          version: 1, onCreate: (d, v) async {
        await d.execute(
            'CREATE TABLE cache (key TEXT PRIMARY KEY,value TEXT NOT NULL,updated_at TEXT NOT NULL)');
        await d.execute(
            'CREATE TABLE pending_sales (id TEXT PRIMARY KEY,payload TEXT NOT NULL,created_at TEXT NOT NULL,attempts INTEGER NOT NULL DEFAULT 0,last_error TEXT)');
      });
  String _key(int tenant, int branch) => 'bootstrap:$tenant:$branch';
  Future<void> saveBootstrap(
          int tenant, int branch, Map<String, dynamic> value) async =>
      (await db).insert(
          'cache',
          {
            'key': _key(tenant, branch),
            'value': jsonEncode(value),
            'updated_at': DateTime.now().toUtc().toIso8601String()
          },
          conflictAlgorithm: ConflictAlgorithm.replace);
  Future<Map<String, dynamic>?> cachedBootstrap(int tenant, int branch) async {
    final rows = await (await db).query('cache',
        where: 'key=?', whereArgs: [_key(tenant, branch)], limit: 1);
    return rows.isEmpty
        ? null
        : Map<String, dynamic>.from(jsonDecode(rows.first['value'] as String));
  }

  Future<void> queueSale(String id, Map<String, dynamic> payload) async =>
      (await db).insert(
          'pending_sales',
          {
            'id': id,
            'payload': jsonEncode(payload),
            'created_at': DateTime.now().toUtc().toIso8601String()
          },
          conflictAlgorithm: ConflictAlgorithm.ignore);
  Future<List<Map<String, dynamic>>> pendingSales() async => (await (await db)
          .query('pending_sales', orderBy: 'created_at'))
      .map(
          (r) => {'id': r['id'], 'payload': jsonDecode(r['payload'] as String)})
      .toList();
  Future<int> pendingCount() async =>
      Sqflite.firstIntValue(
          await (await db).rawQuery('SELECT COUNT(*) FROM pending_sales')) ??
      0;
  Future<void> removePending(String id) async =>
      (await db).delete('pending_sales', where: 'id=?', whereArgs: [id]);
}
