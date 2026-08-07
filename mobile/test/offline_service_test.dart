import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:retailzw_mobile/services/local_database.dart';
import 'package:retailzw_mobile/services/offline_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  LocalDatabase.ensureInitialized();

  group('OfflineService sale queue and sync state', () {
    final offline = OfflineService();
    late Directory testDatabaseDirectory;

    setUpAll(() async {
      testDatabaseDirectory =
          await Directory.systemTemp.createTemp('retailzw-pos-test-');
      OfflineService.setDatabaseRootForTesting(testDatabaseDirectory.path);
    });

    setUp(() async {
      await offline.init();
      await offline.clearShiftData();
    });

    tearDown(() async {
      await offline.clearShiftData();
    });

    tearDownAll(() async {
      await offline.closeDatabaseForTesting();
      OfflineService.setDatabaseRootForTesting(null);
      if (await testDatabaseDirectory.exists()) {
        await testDatabaseDirectory.delete(recursive: true);
      }
    });

    test('caches products, queues sale, updates stock, and marks synced',
        () async {
      await offline.cacheProducts([
        {
          'id': 101,
          'name': 'Cerevita 500g',
          'sku': 'GROC-CEREVITA-500',
          'barcode': '263000000002',
          'sellingPriceUsd': 4.25,
          'sellingPriceZwg': 63.75,
          'costPriceUsd': 2.80,
          'taxRate': 15,
          'isTaxable': true,
          'quantityOnHand': 5,
          'wholesaleEnabled': true,
          'wholesaleMinimumQuantity': 2,
          'wholesalePriceUsd': 3.80,
          'wholesalePriceZwg': 57.00,
          'wholesalePricingVersion': 4,
          'pricingProtocolVersion': 2,
        },
        {
          'id': 102,
          'name': 'Mealie Meal 10kg',
          'sku': 'GROC-MEALIE-10KG',
          'barcode': '263000000003',
          'sellingPriceUsd': 7.00,
          'sellingPriceZwg': 105.00,
          'costPriceUsd': 5.20,
          'taxRate': 0,
          'isTaxable': false,
          'quantityOnHand': 8,
        },
      ]);

      final search = await offline.getCachedProducts(search: 'cerevita');
      expect(search, hasLength(1));
      expect(search.first['quantityOnHand'], 5);
      expect(search.first['wholesaleEnabled'], isTrue);
      expect(search.first['wholesalePricingVersion'], 4);
      final barcodeProduct =
          await offline.getCachedProductByBarcode(' 263000000002 ');
      expect(barcodeProduct?['id'], 101);
      expect(await offline.getCachedProductByBarcode('not-a-barcode'), isNull);

      final saleData = {
        'cashSessionId': 77,
        'pricingProtocolVersion': 2,
        'offlinePricingLocked': true,
        'currency': 'USD',
        'items': [
          {
            'productId': 101,
            'productName': 'Cerevita 500g',
            'quantity': 2,
            'unitPrice': 4.25,
            'discountAmount': 0,
            'pricingTier': 'WHOLESALE',
            'pricingVersion': 4,
          }
        ],
        'payments': [
          {
            'method': 'CASH',
            'currency': 'USD',
            'amount': 8.50,
            'exchangeRate': 1,
          }
        ],
      };

      await offline.queueSale(saleData, 'offline-001');
      await offline.saveShiftSale(
        offlineUuid: 'offline-001',
        receiptNumber: 'OFF-offline-001',
        payload: {
          'id': 0,
          'receiptNumber': 'OFF-offline-001',
          'grandTotal': 8.50,
          'currency': 'USD',
          'status': 'PENDING',
          'createdAt': DateTime.now().toIso8601String(),
          'items': saleData['items'],
          'payments': saleData['payments'],
        },
        currency: 'USD',
        total: 8.50,
        synced: false,
      );

      expect(await offline.getPendingCount(), 1);
      final pending = await offline.getPendingSales();
      expect(pending.single['offline_uuid'], 'offline-001');
      final queuedSale = pending.single['sale_data'] as Map<String, dynamic>;
      expect(queuedSale['offlinePricingLocked'], isTrue);
      final queuedItems = queuedSale['items'] as List<dynamic>;
      expect((queuedItems.single as Map<String, dynamic>)['pricingTier'],
          'WHOLESALE');

      final productAfterSale =
          (await offline.getCachedProducts(search: 'cerevita')).single;
      expect(productAfterSale['quantityOnHand'], 3.0);

      await offline.markSynced('offline-001', {
        'id': 500,
        'receiptNumber': 'HRE-000500',
        'grandTotal': 8.50,
        'currency': 'USD',
        'status': 'COMPLETED',
        'createdAt': DateTime.now().toIso8601String(),
        'items': saleData['items'],
        'payments': saleData['payments'],
      });

      expect(await offline.getPendingCount(), 0);
      final shiftSales = await offline.getShiftSales();
      expect(shiftSales.single['receiptNumber'], 'HRE-000500');
      expect(shiftSales.single['status'], 'SYNCED');
    });
  });
}
