import 'dart:convert';
import 'dart:async';
import 'dart:io';
import 'dart:math';
import 'package:crypto/crypto.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';
import '../models/models.dart';
import 'offline_service.dart';
import 'server_config.dart';

class ApiService {
  static const Duration _timeout = Duration(seconds: 15);

  final FlutterSecureStorage _storage = const FlutterSecureStorage();
  final OfflineService _offline = OfflineService();
  String? _token;
  bool lastLoginUsedOfflineCache = false;

  // Build a full URL by looking up the configured server at runtime.
  Future<Uri> _uri(String path, {Map<String, String>? queryParameters}) async {
    final base = await ServerConfig.getBaseUrl();
    final uri = Uri.parse('$base$path');
    return queryParameters != null
        ? uri.replace(queryParameters: queryParameters)
        : uri;
  }

  // ─── Auth ────────────────────────────────────────────────────────────────

  Future<LoginResponse> login(String username, String password) async {
    final cleanUsername = username.trim();
    try {
      final response = await http
          .post(
            await _uri('/api/auth/mobile-login'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'username': cleanUsername,
              'password': password,
            }),
          )
          .timeout(_timeout);
      final data = _decode(response);
      final loginResp = LoginResponse.fromJson(data);
      await _storeOnlineLogin(loginResp, cleanUsername, password);
      lastLoginUsedOfflineCache = false;
      return loginResp;
    } on SocketException {
      return _loginFromDeviceCache(cleanUsername, password);
    } on TimeoutException {
      return _loginFromDeviceCache(cleanUsername, password);
    }
  }

  Future<void> logout({bool forgetDevice = false}) async {
    _token = null;
    if (forgetDevice) {
      await _storage.deleteAll();
    }
  }

  Future<void> saveToken(String token) async {
    _token = token;
    await _storage.write(key: 'auth_token', value: token);
  }

  Future<String?> getToken() async {
    _token ??= await _storage.read(key: 'auth_token');
    return _token;
  }

  Future<UserInfo?> getSavedUser() async {
    final raw = await _storage.read(key: 'user_info');
    if (raw == null) return null;
    return UserInfo.fromJson(jsonDecode(raw) as Map<String, dynamic>);
  }

  Future<String?> getLastUsername() =>
      _storage.read(key: 'last_login_username');

  Future<bool> isLoggedIn() async {
    final t = await getToken();
    return t != null && t.isNotEmpty;
  }

  Future<void> _storeOnlineLogin(
      LoginResponse loginResp, String username, String password) async {
    final previousUsername = await _storage.read(key: 'last_login_username');
    _token = loginResp.accessToken;
    await _storage.write(key: 'auth_token', value: _token);
    await _storage.write(key: 'refresh_token', value: loginResp.refreshToken);
    await _storage.write(
        key: 'user_info', value: jsonEncode(loginResp.user.toJson()));
    await _storage.write(key: 'last_login_username', value: username);
    final cached = await _cachedSession();
    final switchedCashier = previousUsername != null &&
        previousUsername.toLowerCase() != username.toLowerCase();
    if (cached != null &&
        (switchedCashier ||
            (cached.cashierId != null &&
                cached.cashierId != loginResp.user.id))) {
      await clearCachedSession();
    }
    final salt = _newSalt();
    await _storage.write(key: 'offline_login_salt', value: salt);
    await _storage.write(
        key: 'offline_login_hash',
        value: _passwordDigest(username, password, salt));
  }

  Future<LoginResponse> _loginFromDeviceCache(
      String username, String password) async {
    final savedUser = await getSavedUser();
    final savedUsername = await _storage.read(key: 'last_login_username');
    final salt = await _storage.read(key: 'offline_login_salt');
    final storedHash = await _storage.read(key: 'offline_login_hash');
    final accessToken = await _storage.read(key: 'auth_token') ?? '';
    final refreshToken = await _storage.read(key: 'refresh_token') ?? '';
    if (savedUser == null ||
        savedUsername == null ||
        salt == null ||
        storedHash == null) {
      throw ApiException(
          'No offline login saved on this device. Connect to the server once to sign in.',
          statusCode: 0);
    }
    if (savedUsername.toLowerCase() != username.toLowerCase()) {
      throw ApiException(
          'This device is cached for $savedUsername. Connect to server to switch cashier.',
          statusCode: 0);
    }
    if (_passwordDigest(username, password, salt) != storedHash) {
      throw ApiException('Invalid username or password.', statusCode: 401);
    }
    _token = accessToken;
    lastLoginUsedOfflineCache = true;
    return LoginResponse(
        accessToken: accessToken, refreshToken: refreshToken, user: savedUser);
  }

  String _newSalt() {
    final random = Random.secure();
    final bytes = List<int>.generate(24, (_) => random.nextInt(256));
    return base64UrlEncode(bytes);
  }

  String _passwordDigest(String username, String password, String salt) {
    List<int> bytes =
        utf8.encode('$salt:${username.trim().toLowerCase()}:$password');
    for (var i = 0; i < 12000; i++) {
      bytes = sha256.convert(bytes).bytes;
    }
    return base64UrlEncode(bytes);
  }

  // ─── Products ────────────────────────────────────────────────────────────

  Future<List<Product>> getProducts(
      {String? search,
      int? categoryId,
      int page = 0,
      bool preferCache = false}) async {
    await _ensureToken();
    if (preferCache) {
      final cached = await _offline.getCachedProducts(search: search);
      if (cached.isNotEmpty) {
        return cached.map(Product.fromJson).toList();
      }
    }
    final params = <String, String>{'page': '$page', 'size': '50'};
    if (search != null && search.isNotEmpty) params['search'] = search;
    if (categoryId != null) params['categoryId'] = '$categoryId';
    try {
      final response = await http
          .get(
            await _uri('/api/products/branch-stock', queryParameters: params),
            headers: _headers(),
          )
          .timeout(_timeout);
      final data = _decode(response);
      final list = _extractList(data).cast<Map<String, dynamic>>();
      await _offline.cacheProducts(list);
      return list.map(Product.fromJson).toList();
    } catch (_) {
      final cached = await _offline.getCachedProducts(search: search);
      return cached.map(Product.fromJson).toList();
    }
  }

  Future<List<Product>> refreshBranchProducts() => getProducts(page: 0);

  Future<Map<String, dynamic>> importProductsExcel(String filePath) async {
    await _ensureToken();
    final request = http.MultipartRequest(
      'POST',
      await _uri('/api/products/import'),
    );
    request.headers['Authorization'] = 'Bearer $_token';
    request.files.add(await http.MultipartFile.fromPath('file', filePath));
    final streamed = await request.send().timeout(_timeout);
    final response = await http.Response.fromStream(streamed);
    return _decode(response);
  }

  Future<List<Product>> refreshOpenShiftProducts() async {
    final session =
        await getActiveSession(keepLocalWhenServerHasNoSession: true);
    if (session == null) return const <Product>[];
    return downloadShiftProducts();
  }

  Future<List<Product>> downloadShiftProducts() async {
    await _ensureToken();
    // Fetch first — only replace cache on success (avoids empty cache on network failure)
    final response = await http
        .get(
          await _uri('/api/products/branch-stock',
              queryParameters: {'page': '0', 'size': '500'}),
          headers: _headers(),
        )
        .timeout(_timeout);
    final data = _decode(response);
    final list = _extractList(data).cast<Map<String, dynamic>>();
    await _offline.cacheProducts(
        list); // cacheProducts already clears+replaces atomically
    return list.map(Product.fromJson).toList();
  }

  Future<List<Borrower>> downloadShiftBorrowers() async {
    await _ensureToken();
    final response = await http
        .get(await _uri('/api/borrowers'), headers: _headers())
        .timeout(_timeout);
    final data = _decode(response);
    final list = _extractList(data).cast<Map<String, dynamic>>();
    await _offline.cacheBorrowers(list);
    return list.map(Borrower.fromJson).toList();
  }

  Future<List<Borrower>> loadCachedBorrowers({String? search}) async {
    final rows = await _offline.getCachedBorrowers(search: search);
    return rows.map(Borrower.fromJson).toList();
  }

  Future<List<HeldChangeRecord>> getOpenChange({String? search}) async {
    await _ensureToken();
    try {
      final response = await http
          .get(
            await _uri('/api/change/open',
                queryParameters: search == null || search.trim().isEmpty
                    ? {'size': '100'}
                    : {'size': '100', 'search': search.trim()}),
            headers: _headers(),
          )
          .timeout(_timeout);
      final data = _decode(response);
      final list = _extractList(data).cast<Map<String, dynamic>>();
      await _offline.cacheOpenChange(list);
      return list.map(HeldChangeRecord.fromJson).toList();
    } catch (_) {
      final cached = await _offline.getCachedOpenChange(search: search);
      return cached.map(HeldChangeRecord.fromJson).toList();
    }
  }

  Future<void> collectHeldChange(
      HeldChangeRecord record, int cashSessionId) async {
    await _ensureToken();
    final payload = <String, dynamic>{
      'cashSessionId': cashSessionId,
      if (record.offlineReference != null)
        'offlineReference': record.offlineReference,
    };
    try {
      final path = record.id == null
          ? '/api/change/collect'
          : '/api/change/${record.id}/collect';
      final response = await http
          .post(await _uri(path),
              headers: _headers(), body: jsonEncode(payload))
          .timeout(_timeout);
      _decode(response);
      final key = record.offlineReference ?? record.id?.toString();
      if (key != null) {
        await _offline.markActionSynced('instant-change-$key', changeKey: key);
      }
    } on SocketException {
      await _queueChangeCollection(record, cashSessionId);
    } on TimeoutException {
      await _queueChangeCollection(record, cashSessionId);
    }
  }

  Future<void> _queueChangeCollection(
      HeldChangeRecord record, int cashSessionId) async {
    final key = record.offlineReference ?? record.id?.toString();
    if (key == null) throw Exception('Change reference is missing.');
    await _offline.queueAction('CHANGE_COLLECTION', 'collect-$key', {
      'id': record.id,
      'offlineReference': record.offlineReference,
      'cashSessionId': cashSessionId,
    });
    await _offline.markActionSynced('local-placeholder', changeKey: key);
  }

  Future<List<Category>> getCategories() async {
    await _ensureToken();
    final response = await http
        .get(await _uri('/api/categories'), headers: _headers())
        .timeout(_timeout);
    final data = _decode(response);
    final list = _extractList(data);
    return list
        .map((e) => Category.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<Product?> getProductByBarcode(String barcode) async {
    await _ensureToken();
    final cleanBarcode = barcode.trim();
    if (cleanBarcode.isEmpty) return null;
    try {
      final response = await http
          .get(
            await _uri(
                '/api/products/barcode/${Uri.encodeComponent(cleanBarcode)}'),
            headers: _headers(),
          )
          .timeout(_timeout);
      final data = _decode(response);
      return Product.fromJson(data);
    } catch (_) {
      final cached = await _offline.getCachedProductByBarcode(cleanBarcode);
      return cached == null ? null : Product.fromJson(cached);
    }
  }

  // ─── Sales ───────────────────────────────────────────────────────────────

  Future<Sale> completeSale(Map<String, dynamic> saleRequest) async {
    await _ensureToken();
    final uuid =
        saleRequest['offlineReceiptNumber'] as String? ?? const Uuid().v4();
    saleRequest = _saleRequestWithOfflineIdentity(saleRequest, uuid);
    try {
      final session =
          await getActiveSession(keepLocalWhenServerHasNoSession: true);
      if (session != null) {
        saleRequest = {
          ...saleRequest,
          'cashSessionId': session.id,
          if (session.branchId != null) 'branchId': session.branchId,
        };
      }
      saleRequest = await _withResolvedItemPrices(saleRequest);
      final response = await http
          .post(
            await _uri('/api/sales'),
            headers: _headers(),
            body: jsonEncode(saleRequest),
          )
          .timeout(_timeout);
      final data = _decode(response);
      final sale = Sale.fromJson(data);
      await _offline.applyCompletedSale(saleRequest);
      await _offline.addLocalHeldChange(saleRequest);
      await _offline.saveShiftSale(
        offlineUuid: uuid,
        receiptNumber: sale.receiptNumber,
        payload: sale.toJson(),
        currency: sale.currency,
        total: sale.grandTotal,
        synced: true,
      );
      return sale;
    } on SocketException {
      return _saveOffline(saleRequest, uuid);
    } on TimeoutException {
      return _saveOffline(saleRequest, uuid);
    } on ApiException catch (e) {
      if (_canSafelyQueueAfterApiError(e)) {
        return _saveOffline(saleRequest, uuid);
      }
      rethrow;
    }
  }

  Future<Sale> _saveOffline(
      Map<String, dynamic> saleRequest, String uuid) async {
    final request = await _withResolvedItemPrices(await _withCachedSaleContext(
        _saleRequestWithOfflineIdentity(saleRequest, uuid)));
    final payload = _offlineSalePayload(request, uuid);
    await _offline.queueSale(request, uuid);
    await _offline.saveShiftSale(
      offlineUuid: uuid,
      receiptNumber: payload['receiptNumber'] as String,
      payload: payload,
      currency: payload['currency'] as String,
      total: _toDouble(payload['grandTotal']),
      synced: false,
    );
    return Sale.fromJson(payload);
  }

  Future<Sale> getSaleByReceipt(String receiptNumber) async {
    await _ensureToken();
    final response = await http
        .get(await _uri('/api/sales/receipt/$receiptNumber'),
            headers: _headers())
        .timeout(_timeout);
    final data = _decode(response);
    return Sale.fromJson(data);
  }

  Future<void> voidSale(int saleId, String reason) async {
    await _ensureToken();
    await http
        .post(
          await _uri('/api/sales/$saleId/void'),
          headers: _headers(),
          body: jsonEncode({'reason': reason}),
        )
        .timeout(_timeout);
  }

  Future<List<Sale>> getRecentSales({String? date}) async {
    await _ensureToken();
    final params = <String, String>{};
    if (date != null) params['date'] = date;
    final response = await http
        .get(
          await _uri('/api/sales/recent',
              queryParameters: params.isEmpty ? null : params),
          headers: _headers(),
        )
        .timeout(_timeout);
    final data = _decode(response);
    final list = _extractList(data);
    return list.map((e) => Sale.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<Sale>> getShiftSales() async {
    await _ensureToken();
    final local = await _offline.getShiftSales();
    try {
      final cached = await _cachedSession();
      final branchId = cached?.branchId ?? (await getSavedUser())?.branchId;
      final params = <String, String>{};
      if (branchId != null) params['branchId'] = '$branchId';
      if (cached != null) params['sessionId'] = '${cached.id}';
      final response = await http
          .get(
            await _uri(
              '/api/sales/shift',
              queryParameters: params.isEmpty ? null : params,
            ),
            headers: _headers(),
          )
          .timeout(_timeout);
      final data = _decode(response);
      final remote = _extractList(data)
          .map((e) => Sale.fromJson(e as Map<String, dynamic>))
          .toList();
      final pending = local
          .where((row) => row['status'] == 'PENDING')
          .map(Sale.fromJson)
          .toList();
      return [...pending, ...remote];
    } catch (_) {
      return local.map(Sale.fromJson).toList();
    }
  }

  // ─── Cash Session ────────────────────────────────────────────────────────

  Future<CashSession?> getActiveSession({
    bool keepLocalWhenServerHasNoSession = false,
  }) async {
    await _ensureToken();
    try {
      final cached = await _cachedSession();
      final branchId = cached?.branchId ?? (await getSavedUser())?.branchId;
      final response = await http
          .get(
            await _uri(
              '/api/cash/session',
              queryParameters:
                  branchId == null ? null : {'branchId': '$branchId'},
            ),
            headers: _headers(),
          )
          .timeout(_timeout);
      if (response.statusCode == 404) {
        return keepLocalWhenServerHasNoSession ? _cachedSession() : null;
      }
      final raw = jsonDecode(response.body);
      if (raw is Map<String, dynamic> &&
          raw.containsKey('data') &&
          raw['data'] == null) {
        if (keepLocalWhenServerHasNoSession) {
          return _cachedSession();
        }
        await _offline.setStateValue('session', null);
        return null;
      }
      final data = _decode(response);
      final session = _cashSessionFromPayload(data);
      if (session == null) {
        await _offline.setStateValue('session', null);
        return null;
      }
      await _offline.setStateValue('session', jsonEncode(session.toJson()));
      return session;
    } on SocketException {
      if (keepLocalWhenServerHasNoSession) return _cachedSession();
      rethrow;
    } on TimeoutException {
      if (keepLocalWhenServerHasNoSession) return _cachedSession();
      rethrow;
    }
  }

  Future<CashSession> openSession(
      int drawerId, double floatUsd, double floatZwg) async {
    await _ensureToken();
    try {
      final response = await http
          .post(
            await _uri('/api/cash/open'),
            headers: _headers(),
            body: jsonEncode({
              'drawerId': drawerId,
              'openingFloatUsd': floatUsd,
              'openingFloatZwg': floatZwg,
            }),
          )
          .timeout(_timeout);
      final data = _decode(response);
      final session = _cashSessionFromPayload(data);
      if (session == null) {
        throw ApiException(
          'The server opened/restored a shift but did not return a valid shift ID. Please try again.',
          statusCode: response.statusCode,
        );
      }
      await _offline.setStateValue('session', jsonEncode(session.toJson()));
      return session;
    } on ApiException catch (e) {
      if (e.message.toLowerCase().contains('already has an open session')) {
        final existing = await getActiveSession();
        if (existing != null) {
          return existing;
        }
      }
      rethrow;
    }
  }

  Future<CashSession> closeSession(
      int sessionId, double actualUsd, double actualZwg) async {
    await _ensureToken();
    final response = await http
        .post(
          await _uri('/api/cash/close'),
          headers: _headers(),
          body: jsonEncode({
            'sessionId': sessionId,
            'actualUsd': actualUsd,
            'actualZwg': actualZwg,
          }),
        )
        .timeout(_timeout);
    final data = _decode(response);
    final session = _cashSessionFromPayload(data) ?? CashSession.fromJson(data);
    await _offline.clearShiftData();
    return session;
  }

  Future<List<CashDrawer>> getDrawers() async {
    await _ensureToken();
    final response = await http
        .get(await _uri('/api/cash/drawers'), headers: _headers())
        .timeout(_timeout);
    final data = _decode(response);
    final list = _extractList(data);
    return list
        .map((e) => CashDrawer.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> recordCashMovement(int sessionId, String type, String currency,
      double amount, String reason) async {
    await _ensureToken();
    await http
        .post(
          await _uri('/api/cash/movement'),
          headers: _headers(),
          body: jsonEncode({
            'sessionId': sessionId,
            'type': type,
            'currency': currency,
            'amount': amount,
            'reason': reason,
          }),
        )
        .timeout(_timeout);
  }

  // ─── Customers ───────────────────────────────────────────────────────────

  Future<Customer?> searchCustomer(String query) async {
    await _ensureToken();
    final response = await http
        .get(
          await _uri('/api/customers/search', queryParameters: {'q': query}),
          headers: _headers(),
        )
        .timeout(_timeout);
    if (response.statusCode == 404) return null;
    if (response.body.isEmpty) return null;
    final dynamic raw = jsonDecode(response.body);
    // API may return a list or a wrapped object
    if (raw is List) {
      if (raw.isEmpty) return null;
      return Customer.fromJson(raw.first as Map<String, dynamic>);
    }
    if (raw is Map<String, dynamic>) {
      final inner = raw['data'] ?? raw['content'];
      if (inner is List) {
        if (inner.isEmpty) return null;
        return Customer.fromJson(inner.first as Map<String, dynamic>);
      }
      return Customer.fromJson(raw);
    }
    return null;
  }

  Future<Customer> registerCustomer(Map<String, dynamic> data) async {
    await _ensureToken();
    final response = await http
        .post(
          await _uri('/api/customers'),
          headers: _headers(),
          body: jsonEncode(data),
        )
        .timeout(_timeout);
    final respData = _decode(response);
    return Customer.fromJson(respData);
  }

  // ─── Notifications ───────────────────────────────────────────────────────

  Future<List<AppNotification>> getNotifications() async {
    await _ensureToken();
    final response = await http
        .get(await _uri('/api/notifications'), headers: _headers())
        .timeout(_timeout);
    final data = _decode(response);
    final list = _extractList(data);
    return list
        .map((e) => AppNotification.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<int> getUnreadCount() async {
    await _ensureToken();
    try {
      final response = await http
          .get(
            await _uri('/api/notifications/unread-count'),
            headers: _headers(),
          )
          .timeout(_timeout);
      final data = _decode(response);
      return (data['count'] as num?)?.toInt() ?? 0;
    } catch (_) {
      return 0;
    }
  }

  Future<void> markAllRead() async {
    await _ensureToken();
    await http
        .post(
          await _uri('/api/notifications/mark-all-read'),
          headers: _headers(),
        )
        .timeout(_timeout);
  }

  // ─── HR / Attendance ─────────────────────────────────────────────────────

  Future<void> clockIn() async {
    await _ensureToken();
    await http
        .post(await _uri('/api/attendance/clock-in'), headers: _headers())
        .timeout(_timeout);
  }

  Future<void> clockOut() async {
    await _ensureToken();
    await http
        .post(await _uri('/api/attendance/clock-out'), headers: _headers())
        .timeout(_timeout);
  }

  Future<AttendanceRecord?> getTodayAttendance() async {
    await _ensureToken();
    try {
      final response = await http
          .get(await _uri('/api/attendance/today'), headers: _headers())
          .timeout(_timeout);
      final data = _decode(response);
      return AttendanceRecord.fromJson(data);
    } catch (_) {
      return null;
    }
  }

  // ─── Returns ─────────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> lookupSaleForReturn(String receiptNumber) async {
    await _ensureToken();
    final response = await http
        .get(
          await _uri('/api/sales/receipt/$receiptNumber'),
          headers: _headers(),
        )
        .timeout(_timeout);
    return _decode(response);
  }

  Future<void> processReturn(Map<String, dynamic> returnData) async {
    await _ensureToken();
    await http
        .post(
          await _uri('/api/returns'),
          headers: _headers(),
          body: jsonEncode(returnData),
        )
        .timeout(_timeout);
  }

  // ─── Offline queue ───────────────────────────────────────────────────────

  Future<void> queueOfflineSale(Map<String, dynamic> saleData) async {
    final uuid =
        saleData['offlineReceiptNumber'] as String? ?? const Uuid().v4();
    final request = await _withResolvedItemPrices(await _withCachedSaleContext(
        _saleRequestWithOfflineIdentity(saleData, uuid)));
    await _offline.queueSale(request, uuid);
  }

  Future<SyncResult> syncOfflineSales() async {
    await _ensureToken();
    final pending = await _offline.getPendingSales();
    final pendingGas = await _offline.getPendingGasSales();
    var synced = 0;
    var failed = 0;
    if (_token == null || _token!.isEmpty) {
      return SyncResult(
        synced: 0,
        failed: pending.length + pendingGas.length,
        pending: pending.length + pendingGas.length,
        message:
            'Sign in online once before syncing offline sales. The device has no server session token.',
      );
    }
    CashSession? session;
    try {
      session = await getActiveSession(keepLocalWhenServerHasNoSession: true);
    } on ApiException catch (e) {
      if (e.statusCode == 401 || e.statusCode == 403) {
        return SyncResult(
          synced: 0,
          failed: pending.length + pendingGas.length,
          pending: pending.length + pendingGas.length,
          message: _syncErrorMessage(e),
        );
      }
      session = await _cachedSession();
    }
    String? lastError;
    for (final entry in pending) {
      try {
        var saleData = entry['sale_data'] as Map<String, dynamic>;
        final uuid = entry['offline_uuid'] as String;
        final queuedSessionId = (saleData['cashSessionId'] as num?)?.toInt();
        final queuedBranchId = (saleData['branchId'] as num?)?.toInt();
        final sessionId = queuedSessionId ?? session?.id;
        saleData = await _withCachedSaleContext(
            _saleRequestWithOfflineIdentity(saleData, uuid));
        final fallbackBranchId = session?.branchId ??
            queuedBranchId ??
            (await getSavedUser())?.branchId;
        saleData = {
          ...saleData,
          if (sessionId != null && sessionId > 0) 'cashSessionId': sessionId,
          if (session?.branchId != null) 'branchId': session!.branchId,
          if (session?.branchId == null && queuedBranchId != null)
            'branchId': queuedBranchId,
          if (session?.branchId == null &&
              queuedBranchId == null &&
              fallbackBranchId != null)
            'branchId': fallbackBranchId,
        };
        saleData = await _withResolvedItemPrices(saleData);
        final response = await http
            .post(
              await _uri('/api/sales'),
              headers: _headers(),
              body: jsonEncode(saleData),
            )
            .timeout(_timeout);
        final payload = _decode(response);
        await _offline.markSynced(entry['offline_uuid'] as String, payload);
        synced++;
      } catch (e) {
        failed++;
        lastError = _syncErrorMessage(e);
        if (e is SocketException ||
            e is TimeoutException ||
            e is ApiException) {
          break;
        }
      }
    }
    for (final entry in pendingGas) {
      try {
        final saleData = entry['sale_data'] as Map<String, dynamic>;
        final response = await http
            .post(
              await _uri('/api/gas/sales'),
              headers: _headers(),
              body: jsonEncode(saleData),
            )
            .timeout(_timeout);
        final payload = _decode(response);
        await _offline.markGasSaleSynced(
            entry['offline_uuid'] as String, payload);
        synced++;
      } catch (e) {
        failed++;
        lastError = _syncErrorMessage(e);
        if (e is SocketException ||
            e is TimeoutException ||
            e is ApiException) {
          break;
        }
      }
    }
    final pendingActions = await _offline.getPendingActions();
    for (final entry in pendingActions) {
      try {
        final payload = entry['payload'] as Map<String, dynamic>;
        if (entry['action_type'] == 'CHANGE_COLLECTION') {
          final id = (payload['id'] as num?)?.toInt();
          final path =
              id == null ? '/api/change/collect' : '/api/change/$id/collect';
          final response = await http
              .post(await _uri(path),
                  headers: _headers(), body: jsonEncode(payload))
              .timeout(_timeout);
          _decode(response);
          await _offline.markActionSynced(entry['offline_uuid'] as String,
              changeKey:
                  payload['offlineReference']?.toString() ?? id?.toString());
          synced++;
        }
      } catch (e) {
        failed++;
        lastError = _syncErrorMessage(e);
        break;
      }
    }
    final remaining = await _offline.getPendingCount();
    return SyncResult(
      synced: synced,
      failed: failed,
      pending: remaining,
      message: lastError,
    );
  }

  Future<int> getOfflineQueueCount() => _offline.getPendingCount();

  Future<void> clearLocalShiftData() => _offline.clearShiftData();

  Future<void> clearLocalShiftCache() => _offline.clearShiftCache();

  Future<void> clearCachedSession() => _offline.setStateValue('session', null);

  /// Reads the active session from local SQLite without any network call.
  /// Use this to restore state on app startup before the server responds.
  Future<CashSession?> loadCachedSession() => _cachedSession();

  /// Reads all cached products from local SQLite without any network call.
  Future<List<Product>> loadCachedProducts() async {
    final rows = await _offline.getCachedProducts();
    return rows.map(Product.fromJson).toList();
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────

  Future<Map<String, dynamic>> getGasBootstrap(int branchId) async {
    await _ensureToken();
    try {
      final response = await http
          .get(
            await _uri('/api/gas/bootstrap',
                queryParameters: {'branchId': '$branchId'}),
            headers: _headers(),
          )
          .timeout(_timeout);
      final data = _decode(response);
      await _offline.cacheGasBootstrap(
        branchId: branchId,
        currentShift: data['currentShift'] as Map<String, dynamic>?,
        tanks: (data['tanks'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>(),
        prices: (data['prices'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>(),
        sales: (data['shiftSales'] as List<dynamic>? ?? [])
            .cast<Map<String, dynamic>>(),
      );
      return _gasBootstrapFromJson(data);
    } catch (_) {
      final cached = await _offline.getCachedGasBootstrap(branchId);
      return _gasBootstrapFromJson(cached);
    }
  }

  Future<GasShift> openGasShift(int branchId) async {
    await _ensureToken();
    final response = await http
        .post(
          await _uri('/api/gas/shift/open'),
          headers: _headers(),
          body: jsonEncode({'branchId': branchId}),
        )
        .timeout(_timeout);
    return GasShift.fromJson(_decode(response));
  }

  Future<GasShift> closeGasShift(int branchId, int shiftId) async {
    await _ensureToken();
    final response = await http
        .post(
          await _uri('/api/gas/shift/close'),
          headers: _headers(),
          body: jsonEncode({'branchId': branchId, 'shiftId': shiftId}),
        )
        .timeout(_timeout);
    return GasShift.fromJson(_decode(response));
  }

  Future<GasSale> completeGasSale({
    required int branchId,
    required int tankId,
    required double quantityKg,
    required String currency,
    double? estimatedTotal,
    String? customerName,
    String? customerPhone,
    String paymentMethod = 'CASH',
    String? paymentReference,
  }) async {
    await _ensureToken();
    final uuid = const Uuid().v4();
    final payload = {
      'branchId': branchId,
      'tankId': tankId,
      'quantityKg': quantityKg,
      'currency': currency,
      'offlineReceiptNumber': 'GAS-OFF-$uuid',
      'offlineCreatedAt': DateTime.now().toIso8601String(),
      'estimatedTotal': estimatedTotal ?? 0,
      'paymentMethod': paymentMethod,
      if (paymentReference != null && paymentReference.trim().isNotEmpty)
        'paymentReference': paymentReference.trim(),
      if (customerName != null && customerName.trim().isNotEmpty)
        'customerName': customerName.trim(),
      if (customerPhone != null && customerPhone.trim().isNotEmpty)
        'customerPhone': customerPhone.trim(),
    };
    try {
      final response = await http
          .post(
            await _uri('/api/gas/sales'),
            headers: _headers(),
            body: jsonEncode(payload),
          )
          .timeout(_timeout);
      return GasSale.fromJson(_decode(response));
    } on SocketException {
      return _saveOfflineGasSale(payload, uuid);
    } on TimeoutException {
      return _saveOfflineGasSale(payload, uuid);
    } on ApiException catch (e) {
      if (_canSafelyQueueAfterApiError(e)) {
        return _saveOfflineGasSale(payload, uuid);
      }
      rethrow;
    }
  }

  Future<void> restockGasTank({
    required int branchId,
    required int tankId,
    required double quantityKg,
    String? supplierName,
    String currency = 'USD',
    double unitCost = 0,
    String? supplierInvoice,
  }) async {
    await _ensureToken();
    final response = await http
        .post(
          await _uri('/api/gas/restocks'),
          headers: _headers(),
          body: jsonEncode({
            'branchId': branchId,
            'tankId': tankId,
            'quantityKg': quantityKg,
            'currency': currency,
            'unitCost': unitCost,
            if (supplierName != null && supplierName.trim().isNotEmpty)
              'supplierName': supplierName.trim(),
            if (supplierInvoice != null && supplierInvoice.trim().isNotEmpty)
              'supplierInvoice': supplierInvoice.trim(),
          }),
        )
        .timeout(_timeout);
    _decode(response);
  }

  Future<void> recordGasExpense({
    required int branchId,
    required String category,
    required String description,
    required double amount,
    required String currency,
    String paymentMethod = 'CASH',
    String? reference,
  }) async {
    await _ensureToken();
    final response = await http
        .post(
          await _uri('/api/gas/expenses'),
          headers: _headers(),
          body: jsonEncode({
            'branchId': branchId,
            'category': category,
            'description': description,
            'amount': amount,
            'currency': currency,
            'paymentMethod': paymentMethod,
            if (reference != null && reference.trim().isNotEmpty)
              'reference': reference.trim(),
          }),
        )
        .timeout(_timeout);
    _decode(response);
  }

  Map<String, String> _headers() => {
        'Authorization': 'Bearer $_token',
        'Content-Type': 'application/json',
      };

  Future<void> _ensureToken() async {
    _token ??= await _storage.read(key: 'auth_token');
  }

  Map<String, dynamic> _decode(http.Response response) {
    final payload = jsonDecode(response.body) as Map<String, dynamic>;
    if (response.statusCode >= 400 || payload['success'] == false) {
      throw ApiException(
        payload['message'] as String? ?? 'Request failed',
        statusCode: response.statusCode,
      );
    }
    if (payload.containsKey('data') && payload['data'] != null) {
      final d = payload['data'];
      if (d is Map<String, dynamic>) return d;
      return payload;
    }
    return payload;
  }

  List<dynamic> _extractList(Map<String, dynamic> payload) {
    if (payload.containsKey('data')) {
      final d = payload['data'];
      if (d is List) return d;
      if (d is Map && d.containsKey('content')) {
        return d['content'] as List;
      }
    }
    if (payload.containsKey('content')) {
      return payload['content'] as List;
    }
    return [];
  }

  Map<String, dynamic> _offlineSalePayload(
      Map<String, dynamic> saleRequest, String uuid) {
    final currency = saleRequest['currency'] as String? ?? 'USD';
    final items = (saleRequest['items'] as List<dynamic>? ?? [])
        .cast<Map<String, dynamic>>();
    final total = items.fold<double>(
      0,
      (sum, item) =>
          sum + _toDouble(item['quantity']) * _toDouble(item['unitPrice']),
    );
    return {
      'id': 0,
      'receiptNumber': 'OFF-$uuid',
      'grandTotal': total,
      'currency': currency,
      'status': 'PENDING',
      'createdAt': DateTime.now().toIso8601String(),
      'items': items
          .map((item) => {
                'productId': item['productId'],
                'productName':
                    item['productName'] ?? 'Product #${item['productId']}',
                'quantity': item['quantity'],
                'unitPrice': item['unitPrice'],
                'lineTotal':
                    _toDouble(item['quantity']) * _toDouble(item['unitPrice']),
              })
          .toList(),
      'payments': saleRequest['payments'] ?? [],
    };
  }

  Future<GasSale> _saveOfflineGasSale(
      Map<String, dynamic> saleRequest, String uuid) async {
    await _offline.queueGasSale(saleRequest, uuid);
    final quantity = _toDouble(saleRequest['quantityKg']);
    final total = _toDouble(saleRequest['estimatedTotal']);
    return GasSale(
      id: 0,
      receiptNumber: saleRequest['offlineReceiptNumber']?.toString() ?? uuid,
      quantityKg: quantity,
      unitPrice: quantity <= 0 ? 0 : total / quantity,
      total: total,
      currency: saleRequest['currency']?.toString() ?? 'USD',
      paymentMethod: saleRequest['paymentMethod']?.toString() ?? 'CASH',
    );
  }

  Map<String, dynamic> _gasBootstrapFromJson(Map<String, dynamic> data) {
    return {
      'currentShift': data['currentShift'] == null
          ? null
          : GasShift.fromJson(data['currentShift'] as Map<String, dynamic>),
      'tanks': (data['tanks'] as List<dynamic>? ?? [])
          .map((e) => GasTank.fromJson(e as Map<String, dynamic>))
          .toList(),
      'prices': (data['prices'] as List<dynamic>? ?? [])
          .map((e) => GasPrice.fromJson(e as Map<String, dynamic>))
          .toList(),
      'sales': (data['shiftSales'] as List<dynamic>? ?? [])
          .map((e) => GasSale.fromJson(e as Map<String, dynamic>))
          .toList(),
      'dashboard': data['dashboard'] == null
          ? null
          : GasDashboard.fromJson(data['dashboard'] as Map<String, dynamic>),
    };
  }

  double _toDouble(dynamic value) => double.tryParse('$value') ?? 0;

  Map<String, dynamic> _saleRequestWithOfflineIdentity(
      Map<String, dynamic> saleRequest, String uuid) {
    return {
      ...saleRequest,
      'offlineReceiptNumber': saleRequest['offlineReceiptNumber'] ?? uuid,
      'offlineCreatedAt':
          saleRequest['offlineCreatedAt'] ?? DateTime.now().toIso8601String(),
      if (saleRequest['borrowerId'] != null)
        'borrowerOfflineReference':
            saleRequest['borrowerOfflineReference'] ?? '$uuid-BORROW',
      if (_toDouble(saleRequest['heldChangeAmount']) > 0)
        'heldChangeOfflineReference':
            saleRequest['heldChangeOfflineReference'] ?? '$uuid-CHANGE',
    };
  }

  Future<Map<String, dynamic>> _withCachedSaleContext(
      Map<String, dynamic> saleRequest) async {
    final cached = await _cachedSession();
    final savedUser = await getSavedUser();
    final hasSession = (saleRequest['cashSessionId'] as num?)?.toInt() != null;
    final hasBranch = (saleRequest['branchId'] as num?)?.toInt() != null;
    return {
      ...saleRequest,
      if (!hasSession && cached != null) 'cashSessionId': cached.id,
      if (!hasBranch && cached?.branchId != null) 'branchId': cached!.branchId,
      if (!hasBranch && cached?.branchId == null && savedUser?.branchId != null)
        'branchId': savedUser!.branchId,
    };
  }

  Future<Map<String, dynamic>> _withResolvedItemPrices(
      Map<String, dynamic> saleRequest) async {
    final rawItems = saleRequest['items'] as List<dynamic>? ?? const [];
    if (rawItems.isEmpty) return saleRequest;

    final cachedProducts = await _offline.getCachedProducts();
    final productsById = <int, Map<String, dynamic>>{
      for (final product in cachedProducts)
        if (product['id'] is num) (product['id'] as num).toInt(): product,
    };
    final currency = saleRequest['currency']?.toString() ?? 'USD';

    final items = rawItems.map((raw) {
      if (raw is! Map) return raw;
      final item = Map<String, dynamic>.from(raw);
      final productId = (item['productId'] as num?)?.toInt();
      final hasPrice =
          item.containsKey('unitPrice') && item['unitPrice'] != null;
      final quantity = _toDouble(item['quantity']);

      if (!hasPrice) {
        final product = productId == null ? null : productsById[productId];
        final productPrice = product == null
            ? null
            : currency == 'ZWG'
                ? product['sellingPriceZwg']
                : product['sellingPriceUsd'];
        final lineTotal = _toDouble(item['lineTotal']);
        item['unitPrice'] = productPrice != null
            ? _toDouble(productPrice)
            : quantity > 0
                ? lineTotal / quantity
                : 0.0;
      }

      if (!item.containsKey('lineTotal') || item['lineTotal'] == null) {
        item['lineTotal'] = quantity * _toDouble(item['unitPrice']) -
            _toDouble(item['discountAmount']);
      }
      return item;
    }).toList();

    return {
      ...saleRequest,
      'items': items,
    };
  }

  bool _canSafelyQueueAfterApiError(ApiException error) {
    final msg = error.message.toLowerCase();
    return msg.contains('no value present') ||
        msg.contains('open a shift') ||
        msg.contains('cash session') ||
        msg.contains('session is not open') ||
        msg.contains('timeout') ||
        msg.contains('connection') ||
        msg.contains('server unavailable');
  }

  String _syncErrorMessage(Object error) {
    if (error is ApiException) {
      if (error.statusCode == 401 || error.statusCode == 403) {
        return 'Your login session expired. Sign in online again, then sync the offline sales.';
      }
      return error.message;
    }
    if (error is SocketException) {
      return 'Cannot reach the RetailZW server. Tried ${ServerConfig.candidates.join(', ')}. Internet may be available, but the backend is not reachable from this device.';
    }
    if (error is TimeoutException) {
      return 'The RetailZW server did not respond in time. Tried ${ServerConfig.candidates.join(', ')}.';
    }
    return error.toString().replaceFirst('Exception: ', '');
  }

  Future<CashSession?> _cachedSession() async {
    final raw = await _offline.getStateValue('session');
    if (raw == null || raw.isEmpty) return null;
    final session =
        _cashSessionFromPayload(jsonDecode(raw) as Map<String, dynamic>);
    if (session == null) {
      await clearCachedSession();
      return null;
    }
    if (session.id <= 0 || session.status.toUpperCase() != 'OPEN') {
      await clearCachedSession();
      return null;
    }
    final user = await getSavedUser();
    if (user != null &&
        session.cashierId != null &&
        session.cashierId != user.id) {
      await clearCachedSession();
      return null;
    }
    return session;
  }

  CashSession? _cashSessionFromPayload(Map<String, dynamic> payload) {
    Map<String, dynamic>? candidate = payload;
    for (final key in const [
      'session',
      'cashSession',
      'cashSessionDto',
      'activeSession',
    ]) {
      final nested = candidate?[key];
      if (nested is Map<String, dynamic>) {
        candidate = nested;
        break;
      }
    }
    if (candidate == null) return null;
    final session = CashSession.fromJson(candidate);
    if (session.id <= 0) return null;
    return session;
  }
}

class SyncResult {
  final int synced;
  final int failed;
  final int pending;
  final String? message;
  const SyncResult(
      {required this.synced,
      required this.failed,
      required this.pending,
      this.message});
  bool get hasFailures => failed > 0 || pending > 0;
}

class ApiException implements Exception {
  final String message;
  final int statusCode;
  ApiException(this.message, {required this.statusCode});
  @override
  String toString() => message;
}

class OfflineException implements Exception {
  final String offlineUuid;
  OfflineException({required this.offlineUuid});
  @override
  String toString() =>
      'Sale saved offline (uuid: $offlineUuid). Will sync when online.';
}
