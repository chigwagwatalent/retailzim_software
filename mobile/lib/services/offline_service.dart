import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import 'local_database.dart';

class OfflineService {
  static final OfflineService _instance = OfflineService._internal();
  factory OfflineService() => _instance;
  OfflineService._internal();

  Database? _db;
  Future<Database>? _opening;
  static String? _databaseRootOverride;

  @visibleForTesting
  static void setDatabaseRootForTesting(String? path) {
    _databaseRootOverride = path;
  }

  Future<Database> get database async {
    final existing = _db;
    if (existing != null && existing.isOpen) return existing;

    final opening = _opening;
    if (opening != null) return opening;

    final nextOpening = _initDb();
    _opening = nextOpening;
    try {
      _db = await nextOpening;
      return _db!;
    } finally {
      _opening = null;
    }
  }

  Future<Database> _initDb() async {
    LocalDatabase.ensureInitialized();
    final dbRoot = await _resolveDatabaseRoot();
    final path = p.join(dbRoot, 'retailzw_offline.db');
    return openDatabase(
      path,
      version: 6,
      onConfigure: (db) async {
        await db.rawQuery('PRAGMA journal_mode = WAL');
        await db.rawQuery('PRAGMA synchronous = NORMAL');
        await db.rawQuery('PRAGMA temp_store = MEMORY');
      },
      onCreate: _createSchema,
      onUpgrade: (db, oldVersion, newVersion) async {
        // Drop and recreate all tables on any upgrade — safe for dev/test builds
        await _createSchema(db, newVersion);
      },
    );
  }

  Future<String> _resolveDatabaseRoot() async {
    var dbRoot = _databaseRootOverride;
    if (dbRoot == null || dbRoot.isEmpty) {
      if (Platform.isWindows) {
        final localAppData = Platform.environment['LOCALAPPDATA'];
        final roamingAppData = Platform.environment['APPDATA'];
        final writableRoot = localAppData?.isNotEmpty == true
            ? localAppData!
            : roamingAppData?.isNotEmpty == true
                ? roamingAppData!
                : Directory.systemTemp.path;
        dbRoot = p.join(writableRoot, 'RetailZW', 'POS', 'data');
      } else {
        dbRoot = await getDatabasesPath();
      }
    }

    await Directory(dbRoot).create(recursive: true);
    return dbRoot;
  }

  @visibleForTesting
  Future<void> closeDatabaseForTesting() async {
    final db = _db;
    _db = null;
    _opening = null;
    if (db != null && db.isOpen) await db.close();
  }

  Future<void> _createSchema(Database db, int version) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS offline_sales (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        offline_uuid TEXT NOT NULL UNIQUE,
        sale_data TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'PENDING',
        created_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS cached_products (
        id INTEGER PRIMARY KEY,
        payload TEXT NOT NULL,
        name TEXT NOT NULL,
        sku TEXT,
        barcode TEXT,
        quantity_on_hand REAL NOT NULL DEFAULT 0,
        updated_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS shift_sales (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        offline_uuid TEXT NOT NULL UNIQUE,
        receipt_number TEXT NOT NULL,
        payload TEXT NOT NULL,
        currency TEXT NOT NULL,
        total REAL NOT NULL,
        status TEXT NOT NULL,
        synced INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS app_state (
        key TEXT PRIMARY KEY,
        value TEXT
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS cached_borrowers (
        id INTEGER PRIMARY KEY,
        payload TEXT NOT NULL,
        full_name TEXT NOT NULL,
        phone TEXT NOT NULL,
        account_number TEXT NOT NULL,
        available_credit REAL NOT NULL DEFAULT 0,
        updated_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS cached_change (
        local_key TEXT PRIMARY KEY,
        payload TEXT NOT NULL,
        customer_name TEXT NOT NULL,
        phone TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'OPEN',
        updated_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS offline_actions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        offline_uuid TEXT NOT NULL UNIQUE,
        action_type TEXT NOT NULL,
        payload TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'PENDING',
        created_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS cached_gas_tanks (
        id INTEGER PRIMARY KEY,
        payload TEXT NOT NULL,
        branch_id INTEGER NOT NULL,
        name TEXT NOT NULL,
        current_kg REAL NOT NULL DEFAULT 0,
        updated_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS cached_gas_prices (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        branch_id INTEGER NOT NULL,
        currency TEXT NOT NULL,
        payload TEXT NOT NULL,
        updated_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS gas_shift_state (
        branch_id INTEGER PRIMARY KEY,
        payload TEXT NOT NULL,
        updated_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS offline_gas_sales (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        offline_uuid TEXT NOT NULL UNIQUE,
        sale_data TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'PENDING',
        created_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS gas_shift_sales (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        offline_uuid TEXT NOT NULL UNIQUE,
        receipt_number TEXT NOT NULL,
        payload TEXT NOT NULL,
        currency TEXT NOT NULL,
        total REAL NOT NULL,
        status TEXT NOT NULL,
        synced INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_cached_products_name
      ON cached_products(name)
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_cached_products_sku
      ON cached_products(sku)
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_cached_products_barcode
      ON cached_products(barcode)
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_offline_sales_status_created
      ON offline_sales(status, created_at)
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_shift_sales_created
      ON shift_sales(created_at)
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_cached_borrowers_search
      ON cached_borrowers(full_name, phone, account_number)
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_cached_change_search
      ON cached_change(status, customer_name, phone)
    ''');
    await db.execute('''
      CREATE INDEX IF NOT EXISTS idx_offline_gas_sales_status_created
      ON offline_gas_sales(status, created_at)
    ''');
  }

  Future<void> init() async {
    await database;
  }

  Future<void> setStateValue(String key, String? value) async {
    final db = await database;
    if (value == null) {
      await db.delete('app_state', where: 'key = ?', whereArgs: [key]);
      return;
    }
    await db.insert('app_state', {'key': key, 'value': value},
        conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<String?> getStateValue(String key) async {
    final db = await database;
    final rows = await db.query('app_state',
        where: 'key = ?', whereArgs: [key], limit: 1);
    if (rows.isEmpty) return null;
    return rows.first['value'] as String?;
  }

  Future<void> cacheProducts(List<Map<String, dynamic>> products) async {
    final db = await database;
    final batch = db.batch();
    batch.delete('cached_products');
    final now = DateTime.now().toIso8601String();
    for (final product in products) {
      batch.insert(
        'cached_products',
        {
          'id': (product['id'] as num).toInt(),
          'payload': jsonEncode(product),
          'name': product['name'] as String? ?? '',
          'sku': product['sku'] as String?,
          'barcode': product['barcode'] as String?,
          'quantity_on_hand': _toDouble(product['quantityOnHand']),
          'updated_at': now,
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true, continueOnError: true);
  }

  Future<void> cacheGasBootstrap({
    required int branchId,
    Map<String, dynamic>? currentShift,
    required List<Map<String, dynamic>> tanks,
    required List<Map<String, dynamic>> prices,
    required List<Map<String, dynamic>> sales,
  }) async {
    final db = await database;
    final now = DateTime.now().toIso8601String();
    await db.transaction((txn) async {
      await txn.delete('cached_gas_tanks',
          where: 'branch_id = ?', whereArgs: [branchId]);
      await txn.delete('cached_gas_prices',
          where: 'branch_id = ?', whereArgs: [branchId]);
      await txn.delete('gas_shift_sales', where: 'synced = ?', whereArgs: [1]);
      if (currentShift == null) {
        await txn.delete('gas_shift_state',
            where: 'branch_id = ?', whereArgs: [branchId]);
      } else {
        await txn.insert(
            'gas_shift_state',
            {
              'branch_id': branchId,
              'payload': jsonEncode(currentShift),
              'updated_at': now,
            },
            conflictAlgorithm: ConflictAlgorithm.replace);
      }
      final batch = txn.batch();
      for (final tank in tanks) {
        batch.insert(
            'cached_gas_tanks',
            {
              'id': (tank['id'] as num).toInt(),
              'payload': jsonEncode(tank),
              'branch_id': branchId,
              'name': tank['name']?.toString() ?? '',
              'current_kg': _toDouble(tank['currentKg']),
              'updated_at': now,
            },
            conflictAlgorithm: ConflictAlgorithm.replace);
      }
      for (final price in prices) {
        batch.insert('cached_gas_prices', {
          'branch_id': branchId,
          'currency': price['currency']?.toString() ?? 'USD',
          'payload': jsonEncode(price),
          'updated_at': now,
        });
      }
      for (final sale in sales) {
        final receipt = sale['receiptNumber']?.toString();
        if (receipt == null || receipt.isEmpty) continue;
        batch.insert(
            'gas_shift_sales',
            {
              'offline_uuid':
                  sale['offlineReceiptNumber']?.toString() ?? receipt,
              'receipt_number': receipt,
              'payload': jsonEncode(sale),
              'currency': sale['currency']?.toString() ?? 'USD',
              'total': _toDouble(sale['total']),
              'status': sale['status']?.toString() ?? 'SYNCED',
              'synced': 1,
              'created_at': sale['createdAt']?.toString() ?? now,
            },
            conflictAlgorithm: ConflictAlgorithm.replace);
      }
      await batch.commit(noResult: true, continueOnError: true);
    });
  }

  Future<Map<String, dynamic>> getCachedGasBootstrap(int branchId) async {
    final db = await database;
    final shiftRows = await db.query('gas_shift_state',
        where: 'branch_id = ?', whereArgs: [branchId], limit: 1);
    final tankRows = await db.query('cached_gas_tanks',
        where: 'branch_id = ?', whereArgs: [branchId], orderBy: 'name ASC');
    final priceRows = await db.query('cached_gas_prices',
        where: 'branch_id = ?', whereArgs: [branchId], orderBy: 'currency ASC');
    final saleRows =
        await db.query('gas_shift_sales', orderBy: 'created_at DESC');
    return {
      'currentShift': shiftRows.isEmpty
          ? null
          : jsonDecode(shiftRows.first['payload'] as String)
              as Map<String, dynamic>,
      'tanks': tankRows.map((row) {
        final payload =
            jsonDecode(row['payload'] as String) as Map<String, dynamic>;
        payload['currentKg'] = row['current_kg'];
        return payload;
      }).toList(),
      'prices': priceRows
          .map((row) =>
              jsonDecode(row['payload'] as String) as Map<String, dynamic>)
          .toList(),
      'shiftSales': saleRows
          .map((row) =>
              jsonDecode(row['payload'] as String) as Map<String, dynamic>)
          .toList(),
    };
  }

  Future<void> clearCachedProducts() async {
    final db = await database;
    await db.delete('cached_products');
  }

  Future<void> cacheBorrowers(List<Map<String, dynamic>> borrowers) async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete('cached_borrowers');
      final batch = txn.batch();
      final now = DateTime.now().toIso8601String();
      for (final borrower in borrowers) {
        batch.insert(
          'cached_borrowers',
          {
            'id': (borrower['id'] as num).toInt(),
            'payload': jsonEncode(borrower),
            'full_name': borrower['fullName']?.toString() ?? '',
            'phone': borrower['phone']?.toString() ?? '',
            'account_number': borrower['accountNumber']?.toString() ?? '',
            'available_credit': _toDouble(borrower['availableCredit']),
            'updated_at': now,
          },
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
      await batch.commit(noResult: true);
    });
  }

  Future<List<Map<String, dynamic>>> getCachedBorrowers(
      {String? search}) async {
    final db = await database;
    final needle = search?.trim().toLowerCase();
    final rows = await db.query(
      'cached_borrowers',
      where: needle == null || needle.isEmpty
          ? null
          : '(LOWER(full_name) LIKE ? OR LOWER(phone) LIKE ? OR LOWER(account_number) LIKE ?)',
      whereArgs: needle == null || needle.isEmpty
          ? null
          : ['%$needle%', '%$needle%', '%$needle%'],
      orderBy: 'full_name ASC',
    );
    return rows
        .map((row) =>
            jsonDecode(row['payload'] as String) as Map<String, dynamic>)
        .toList();
  }

  Future<void> cacheOpenChange(List<Map<String, dynamic>> records) async {
    final db = await database;
    await db.transaction((txn) async {
      for (final record in records) {
        final key = record['offlineReference']?.toString() ??
            record['id']?.toString() ??
            record['referenceNumber']?.toString();
        if (key == null || key.isEmpty) continue;
        await txn.insert(
          'cached_change',
          {
            'local_key': key,
            'payload': jsonEncode(record),
            'customer_name': record['customerName']?.toString() ?? '',
            'phone': record['phone']?.toString() ?? '',
            'status': record['status']?.toString() ?? 'OPEN',
            'updated_at': DateTime.now().toIso8601String(),
          },
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
    });
  }

  Future<List<Map<String, dynamic>>> getCachedOpenChange(
      {String? search}) async {
    final db = await database;
    final needle = search?.trim().toLowerCase();
    final rows = await db.query(
      'cached_change',
      where: needle == null || needle.isEmpty
          ? 'status = ?'
          : 'status = ? AND (LOWER(customer_name) LIKE ? OR LOWER(phone) LIKE ?)',
      whereArgs: needle == null || needle.isEmpty
          ? ['OPEN']
          : ['OPEN', '%$needle%', '%$needle%'],
      orderBy: 'updated_at DESC',
    );
    return rows
        .map((row) =>
            jsonDecode(row['payload'] as String) as Map<String, dynamic>)
        .toList();
  }

  Future<void> addLocalHeldChange(Map<String, dynamic> saleData) async {
    final amount = _toDouble(saleData['heldChangeAmount']);
    if (amount <= 0) return;
    final reference = saleData['heldChangeOfflineReference']?.toString();
    if (reference == null || reference.isEmpty) return;
    final record = <String, dynamic>{
      'offlineReference': reference,
      'referenceNumber': reference,
      'customerName': saleData['heldChangeName']?.toString() ?? '',
      'phone': saleData['heldChangePhone']?.toString() ?? '',
      'currency': saleData['currency']?.toString() ?? 'USD',
      'amount': amount,
      'status': 'OPEN',
    };
    final db = await database;
    await db.insert(
      'cached_change',
      {
        'local_key': reference,
        'payload': jsonEncode(record),
        'customer_name': record['customerName'],
        'phone': record['phone'],
        'status': 'OPEN',
        'updated_at': DateTime.now().toIso8601String(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> queueAction(String actionType, String offlineUuid,
      Map<String, dynamic> payload) async {
    final db = await database;
    await db.insert(
      'offline_actions',
      {
        'offline_uuid': offlineUuid,
        'action_type': actionType,
        'payload': jsonEncode(payload),
        'status': 'PENDING',
        'created_at': DateTime.now().toIso8601String(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<Map<String, dynamic>>> getPendingActions() async {
    final db = await database;
    final rows = await db.query('offline_actions',
        where: 'status = ?', whereArgs: ['PENDING'], orderBy: 'created_at ASC');
    return rows
        .map((row) => {
              'offline_uuid': row['offline_uuid'],
              'action_type': row['action_type'],
              'payload':
                  jsonDecode(row['payload'] as String) as Map<String, dynamic>,
            })
        .toList();
  }

  Future<void> markActionSynced(String offlineUuid, {String? changeKey}) async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.update('offline_actions', {'status': 'SYNCED'},
          where: 'offline_uuid = ?', whereArgs: [offlineUuid]);
      if (changeKey != null) {
        await txn.update('cached_change', {'status': 'COLLECTED'},
            where: 'local_key = ?', whereArgs: [changeKey]);
      }
    });
  }

  Future<List<Map<String, dynamic>>> getCachedProducts({String? search}) async {
    final db = await database;
    final needle = search?.trim();
    final rows = await db.query(
      'cached_products',
      where: needle == null || needle.isEmpty
          ? null
          : '(LOWER(name) LIKE ? OR LOWER(sku) LIKE ? OR LOWER(barcode) LIKE ?)',
      whereArgs: needle == null || needle.isEmpty
          ? null
          : [
              '%${needle.toLowerCase()}%',
              '%${needle.toLowerCase()}%',
              '%${needle.toLowerCase()}%'
            ],
      orderBy: 'name ASC',
    );
    return rows.map((row) {
      final payload =
          jsonDecode(row['payload'] as String) as Map<String, dynamic>;
      payload['quantityOnHand'] = row['quantity_on_hand'];
      return payload;
    }).toList();
  }

  Future<Map<String, dynamic>?> getCachedProductByBarcode(
      String barcode) async {
    final db = await database;
    final cleanBarcode = barcode.trim().toLowerCase();
    if (cleanBarcode.isEmpty) return null;

    final rows = await db.query(
      'cached_products',
      where: 'LOWER(TRIM(barcode)) = ?',
      whereArgs: [cleanBarcode],
      limit: 1,
    );
    if (rows.isEmpty) return null;

    final row = rows.first;
    final payload =
        jsonDecode(row['payload'] as String) as Map<String, dynamic>;
    payload['quantityOnHand'] = row['quantity_on_hand'];
    return payload;
  }

  Future<void> queueSale(
      Map<String, dynamic> saleData, String offlineUuid) async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.insert(
        'offline_sales',
        {
          'offline_uuid': offlineUuid,
          'sale_data': jsonEncode(saleData),
          'status': 'PENDING',
          'created_at': DateTime.now().toIso8601String(),
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      await _applyCachedStockChanges(txn, saleData);
    });
    await addLocalHeldChange(saleData);
  }

  Future<void> saveShiftSale({
    required String offlineUuid,
    required String receiptNumber,
    required Map<String, dynamic> payload,
    required String currency,
    required double total,
    required bool synced,
  }) async {
    final db = await database;
    await db.insert(
      'shift_sales',
      {
        'offline_uuid': offlineUuid,
        'receipt_number': receiptNumber,
        'payload': jsonEncode(payload),
        'currency': currency,
        'total': total,
        'status': synced ? 'SYNCED' : 'PENDING',
        'synced': synced ? 1 : 0,
        'created_at': DateTime.now().toIso8601String(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<Map<String, dynamic>>> getShiftSales() async {
    final db = await database;
    final rows = await db.query('shift_sales', orderBy: 'created_at DESC');
    return rows.map((row) {
      final payload =
          jsonDecode(row['payload'] as String) as Map<String, dynamic>;
      payload['offlineUuid'] = row['offline_uuid'];
      payload['receiptNumber'] = row['receipt_number'];
      payload['currency'] = row['currency'];
      payload['grandTotal'] = row['total'];
      payload['status'] = row['status'];
      payload['createdAt'] = row['created_at'];
      return payload;
    }).toList();
  }

  Future<List<Map<String, dynamic>>> getPendingSales() async {
    final db = await database;
    final rows = await db.query(
      'offline_sales',
      where: 'status = ?',
      whereArgs: ['PENDING'],
      orderBy: 'created_at ASC',
    );
    return rows.map((row) {
      final decoded =
          jsonDecode(row['sale_data'] as String) as Map<String, dynamic>;
      return {
        'offline_uuid': row['offline_uuid'] as String,
        'sale_data': decoded,
        'created_at': row['created_at'] as String,
      };
    }).toList();
  }

  Future<void> queueGasSale(
      Map<String, dynamic> saleData, String offlineUuid) async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.insert(
        'offline_gas_sales',
        {
          'offline_uuid': offlineUuid,
          'sale_data': jsonEncode(saleData),
          'status': 'PENDING',
          'created_at': DateTime.now().toIso8601String(),
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      await _applyCachedGasSale(txn, saleData);
      await txn.insert(
        'gas_shift_sales',
        {
          'offline_uuid': offlineUuid,
          'receipt_number':
              saleData['offlineReceiptNumber']?.toString() ?? offlineUuid,
          'payload': jsonEncode(_offlineGasSalePayload(saleData, offlineUuid)),
          'currency': saleData['currency']?.toString() ?? 'USD',
          'total': _toDouble(saleData['estimatedTotal']),
          'status': 'PENDING',
          'synced': 0,
          'created_at': DateTime.now().toIso8601String(),
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    });
  }

  Future<List<Map<String, dynamic>>> getPendingGasSales() async {
    final db = await database;
    final rows = await db.query(
      'offline_gas_sales',
      where: 'status = ?',
      whereArgs: ['PENDING'],
      orderBy: 'created_at ASC',
    );
    return rows.map((row) {
      final decoded =
          jsonDecode(row['sale_data'] as String) as Map<String, dynamic>;
      return {
        'offline_uuid': row['offline_uuid'] as String,
        'sale_data': decoded,
        'created_at': row['created_at'] as String,
      };
    }).toList();
  }

  Future<void> markGasSaleSynced(
      String offlineUuid, Map<String, dynamic>? serverSale) async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.update('offline_gas_sales', {'status': 'SYNCED'},
          where: 'offline_uuid = ?', whereArgs: [offlineUuid]);
      if (serverSale != null) {
        await txn.update(
          'gas_shift_sales',
          {
            'payload': jsonEncode(serverSale),
            'receipt_number':
                serverSale['receiptNumber'] as String? ?? offlineUuid,
            'status': 'SYNCED',
            'synced': 1,
          },
          where: 'offline_uuid = ?',
          whereArgs: [offlineUuid],
        );
      }
    });
  }

  Future<void> markSynced(
      String offlineUuid, Map<String, dynamic>? serverSale) async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.update('offline_sales', {'status': 'SYNCED'},
          where: 'offline_uuid = ?', whereArgs: [offlineUuid]);
      if (serverSale != null) {
        await txn.update(
          'shift_sales',
          {
            'payload': jsonEncode(serverSale),
            'receipt_number':
                serverSale['receiptNumber'] as String? ?? offlineUuid,
            'status': 'SYNCED',
            'synced': 1,
          },
          where: 'offline_uuid = ?',
          whereArgs: [offlineUuid],
        );
      }
    });
  }

  Future<int> getPendingCount() async {
    final db = await database;
    final result = await db.rawQuery('''
      SELECT
        (SELECT COUNT(*) FROM offline_sales WHERE status = 'PENDING') +
        (SELECT COUNT(*) FROM offline_actions WHERE status = 'PENDING') +
        (SELECT COUNT(*) FROM offline_gas_sales WHERE status = 'PENDING') AS count
    ''');
    return (result.first['count'] as int?) ?? 0;
  }

  Future<void> deleteAllSynced() async {
    final db = await database;
    await db
        .delete('offline_sales', where: 'status = ?', whereArgs: ['SYNCED']);
  }

  Future<void> clearShiftData() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete('cached_products');
      await txn.delete('cached_borrowers');
      await txn.delete('cached_change');
      await txn.delete('shift_sales');
      await txn.delete('offline_sales');
      await txn.delete('offline_actions');
      await txn.delete('cached_gas_tanks');
      await txn.delete('cached_gas_prices');
      await txn.delete('gas_shift_state');
      await txn.delete('offline_gas_sales');
      await txn.delete('gas_shift_sales');
      await txn.delete('app_state', where: 'key = ?', whereArgs: ['session']);
    });
  }

  Future<void> clearShiftCache() async {
    final db = await database;
    await db.transaction((txn) async {
      await txn.delete('cached_products');
      await txn.delete('cached_borrowers');
      await txn.delete('shift_sales');
      await txn.delete('cached_gas_tanks');
      await txn.delete('cached_gas_prices');
      await txn.delete('gas_shift_sales');
      await txn.delete('gas_shift_state');
      await txn.delete('app_state', where: 'key = ?', whereArgs: ['session']);
    });
  }

  Future<void> applyCompletedSale(Map<String, dynamic> saleData) async {
    final db = await database;
    await db.transaction((txn) => _applyCachedStockChanges(txn, saleData));
  }

  Future<void> _applyCachedStockChanges(
      Transaction txn, Map<String, dynamic> saleData) async {
    final items = (saleData['items'] as List<dynamic>? ?? []);
    for (final raw in items) {
      if (raw is! Map) continue;
      final productId = (raw['productId'] as num?)?.toInt();
      if (productId == null) continue;
      final quantity = _toDouble(raw['quantity']);
      await txn.rawUpdate(
        '''
        UPDATE cached_products
        SET quantity_on_hand = CASE
          WHEN quantity_on_hand - ? < 0 THEN 0
          ELSE quantity_on_hand - ?
        END
        WHERE id = ?
        ''',
        [quantity, quantity, productId],
      );
      final rows = await txn.query('cached_products',
          columns: ['payload', 'quantity_on_hand'],
          where: 'id = ?',
          whereArgs: [productId],
          limit: 1);
      if (rows.isEmpty) continue;
      final payload =
          jsonDecode(rows.first['payload'] as String) as Map<String, dynamic>;
      payload['quantityOnHand'] = rows.first['quantity_on_hand'];
      await txn.update('cached_products', {'payload': jsonEncode(payload)},
          where: 'id = ?', whereArgs: [productId]);
    }
  }

  Future<void> _applyCachedGasSale(
      Transaction txn, Map<String, dynamic> saleData) async {
    final tankId = (saleData['tankId'] as num?)?.toInt();
    if (tankId == null) return;
    final quantity = _toDouble(saleData['quantityKg']);
    await txn.rawUpdate(
      '''
      UPDATE cached_gas_tanks
      SET current_kg = CASE
        WHEN current_kg - ? < 0 THEN 0
        ELSE current_kg - ?
      END
      WHERE id = ?
      ''',
      [quantity, quantity, tankId],
    );
    final rows = await txn.query('cached_gas_tanks',
        columns: ['payload', 'current_kg'],
        where: 'id = ?',
        whereArgs: [tankId],
        limit: 1);
    if (rows.isEmpty) return;
    final payload =
        jsonDecode(rows.first['payload'] as String) as Map<String, dynamic>;
    payload['currentKg'] = rows.first['current_kg'];
    await txn.update('cached_gas_tanks', {'payload': jsonEncode(payload)},
        where: 'id = ?', whereArgs: [tankId]);
  }

  Map<String, dynamic> _offlineGasSalePayload(
      Map<String, dynamic> saleData, String offlineUuid) {
    final quantity = _toDouble(saleData['quantityKg']);
    final total = _toDouble(saleData['estimatedTotal']);
    final unitPrice = quantity <= 0 ? 0.0 : total / quantity;
    return {
      'id': 0,
      'receiptNumber': saleData['offlineReceiptNumber'] ?? offlineUuid,
      'offlineReceiptNumber': saleData['offlineReceiptNumber'] ?? offlineUuid,
      'quantityKg': quantity,
      'unitPrice': unitPrice,
      'total': total,
      'currency': saleData['currency'] ?? 'USD',
      'paymentMethod': saleData['paymentMethod'] ?? 'CASH',
      'status': 'PENDING',
      'createdAt': DateTime.now().toIso8601String(),
    };
  }

  double _toDouble(dynamic value) => double.tryParse('$value') ?? 0;
}
