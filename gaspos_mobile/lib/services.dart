import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'models.dart';

class GasApi {
  GasApi({http.Client? client}) : _client = client ?? http.Client();

  static const productionUrl = 'https://admin.retailzw.co.zw';
  static const configuredUrl = String.fromEnvironment('RETAILZW_API_BASE_URL');
  final http.Client _client;
  final _secure = const FlutterSecureStorage();
  String? token;

  String get baseUrl =>
      configuredUrl.trim().isEmpty ? productionUrl : configuredUrl.trim();

  Future<GasUser> login(String username, String password) async {
    final response = await _client
        .post(Uri.parse('$baseUrl/api/auth/mobile-login'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'username': username.trim(), 'password': password}))
        .timeout(const Duration(seconds: 20));
    final user = GasUser.fromLogin(_decode(response));
    token = user.token;
    await _secure.write(key: 'gaspos_token', value: token);
    await _secure.write(key: 'gaspos_user', value: jsonEncode(user.toJson()));
    return user;
  }

  Future<GasUser?> restoreUser() async {
    final raw = await _secure.read(key: 'gaspos_user');
    if (raw == null) return null;
    final user = GasUser.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    token = await _secure.read(key: 'gaspos_token') ?? user.token;
    return user;
  }

  Future<void> logout() async {
    token = null;
    await _secure.delete(key: 'gaspos_token');
    await _secure.delete(key: 'gaspos_user');
  }

  Future<Map<String, dynamic>> bootstrap(int branchId) =>
      _get('/api/gas/pos/bootstrap', {'branchId': '$branchId'});

  Future<Map<String, dynamic>> openShift(int branchId, List<int> tankIds) =>
      _post('/api/gas/pos/shifts/open',
          {'branchId': branchId, 'tankIds': tankIds});

  Future<Map<String, dynamic>> closeShift(
          int branchId, int shiftId, List<Map<String, dynamic>> weights) =>
      _post('/api/gas/pos/shifts/close', {
        'branchId': branchId,
        'shiftId': shiftId,
        'closingWeights': weights,
      });

  Future<Map<String, dynamic>> sale(Map<String, dynamic> request) =>
      _post('/api/gas/pos/sales', request);

  Future<Map<String, dynamic>> collectChange(int branchId, int changeId) =>
      _post('/api/gas/pos/held-change/$changeId/collect', const {},
          query: {'branchId': '$branchId'});

  Future<Map<String, dynamic>> _get(
      String path, Map<String, String> query) async {
    final response = await _client
        .get(Uri.parse('$baseUrl$path').replace(queryParameters: query),
            headers: _headers)
        .timeout(const Duration(seconds: 20));
    return _decode(response);
  }

  Future<Map<String, dynamic>> _post(
      String path, Map<String, dynamic> body,
      {Map<String, String>? query}) async {
    final response = await _client
        .post(Uri.parse('$baseUrl$path').replace(queryParameters: query),
            headers: _headers, body: jsonEncode(body))
        .timeout(const Duration(seconds: 25));
    return _decode(response);
  }

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token',
      };

  Map<String, dynamic> _decode(http.Response response) {
    final envelope = jsonDecode(response.body) as Map<String, dynamic>;
    if (response.statusCode < 200 ||
        response.statusCode >= 300 ||
        envelope['success'] == false) {
      throw HttpException(
          envelope['message'] as String? ?? 'The server rejected the request.');
    }
    final data = envelope['data'];
    if (data == null) return <String, dynamic>{};
    return Map<String, dynamic>.from(data as Map);
  }
}

class OfflineStore {
  Database? _database;

  Future<Database> get db async {
    if (_database != null) return _database!;
    final root = await getDatabasesPath();
    _database = await openDatabase(
      p.join(root, 'gaspos_retailzw.db'),
      version: 1,
      onCreate: (database, version) async {
        await database.execute(
            'CREATE TABLE cache (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at TEXT NOT NULL)');
        await database.execute(
            'CREATE TABLE pending_sales (id TEXT PRIMARY KEY, payload TEXT NOT NULL, created_at TEXT NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT)');
      },
    );
    return _database!;
  }

  Future<void> saveBootstrap(Map<String, dynamic> data) async {
    await (await db).insert(
        'cache',
        {
          'key': 'bootstrap',
          'value': jsonEncode(data),
          'updated_at': DateTime.now().toUtc().toIso8601String(),
        },
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<Map<String, dynamic>?> cachedBootstrap() async {
    final rows = await (await db)
        .query('cache', where: 'key = ?', whereArgs: ['bootstrap'], limit: 1);
    if (rows.isEmpty) return null;
    return jsonDecode(rows.first['value'] as String) as Map<String, dynamic>;
  }

  Future<void> queueSale(String id, Map<String, dynamic> request) async {
    await (await db).insert(
        'pending_sales',
        {
          'id': id,
          'payload': jsonEncode(request),
          'created_at': DateTime.now().toUtc().toIso8601String(),
        },
        conflictAlgorithm: ConflictAlgorithm.ignore);
  }

  Future<List<Map<String, dynamic>>> pendingSales() async =>
      (await (await db).query('pending_sales', orderBy: 'created_at ASC'))
          .map((row) => {
                'id': row['id'],
                'payload': jsonDecode(row['payload'] as String),
              })
          .toList();

  Future<int> pendingCount() async =>
      Sqflite.firstIntValue(
          await (await db).rawQuery('SELECT COUNT(*) FROM pending_sales')) ??
      0;

  Future<void> removePending(String id) async {
    await (await db)
        .delete('pending_sales', where: 'id = ?', whereArgs: [id]);
  }

  Future<void> markFailed(String id, Object error) async {
    await (await db).rawUpdate(
        'UPDATE pending_sales SET attempts = attempts + 1, last_error = ? WHERE id = ?',
        [error.toString(), id]);
  }
}
