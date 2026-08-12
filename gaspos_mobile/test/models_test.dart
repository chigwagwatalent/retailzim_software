import 'package:flutter_test/flutter_test.dart';
import 'package:gaspos_retailzw/models.dart';

void main() {
  test('cashier identity captures username and survives local persistence', () {
    final user = GasUser.fromLogin({
      'accessToken': 'signed-token',
      'tenantId': 7,
      'branchId': 11,
      'branchName': 'Harare Gas',
      'branchModule': 'GAS_MODULE',
      'username': 'tendai',
      'firstName': 'Tendai',
      'lastName': 'Moyo',
      'companyName': 'Retail Zim',
    });

    expect(user.username, 'tendai');
    expect(user.cashierName, 'Tendai Moyo');

    final restored = GasUser.fromJson(user.toJson());
    expect(restored.username, 'tendai');
    expect(restored.cashierName, 'Tendai Moyo');
  });

  test('cashier username is used when the profile name is blank', () {
    final user = GasUser.fromLogin({
      'accessToken': 'signed-token',
      'tenantId': 7,
      'branchId': 11,
      'branchName': 'Harare Gas',
      'branchModule': 'GAS_MODULE',
      'username': 'cashier-one',
      'firstName': '',
      'lastName': '',
    });

    expect(user.displayName, 'cashier-one');
    expect(user.cashierName, 'cashier-one');
  });

  test('gas bootstrap preserves tare, net and gross stock evidence', () {
    final bootstrap = GasBootstrap.fromJson({
      'tanks': [
        {
          'id': 1,
          'name': 'Tank A',
          'currentKg': 612.0,
          'capacityKg': 1000.0,
          'tareWeightKg': 200.0,
          'status': 'ACTIVE'
        }
      ],
      'shiftTanks': [
        {'tankId': 1, 'startingGrossKg': 1020.0, 'expectedClosingNetKg': 612.0}
      ],
      'prices': [
        {'currency': 'USD', 'pricePerKg': 2.0}
      ],
      'shiftSales': [],
      'heldChange': [],
      'currentShift': {'id': 20, 'status': 'OPEN'}
    });

    expect(bootstrap.hasOpenShift, isTrue);
    expect(bootstrap.tanks.single.grossKg, 812.0);
    expect(bootstrap.assignedShiftTanks.single.name, 'Tank A');
    expect(bootstrap.tanksEligibleForShift.single.name, 'Tank A');
    expect(bootstrap.prices['USD'], 2.0);
  });

  test('branch tanks remain available before a cashier opens a shift', () {
    final bootstrap = GasBootstrap.fromJson({
      'tanks': [
        {
          'id': 1,
          'name': 'Ready Tank',
          'currentKg': 80.0,
          'capacityKg': 100.0,
          'status': 'ACTIVE'
        },
        {
          'id': 2,
          'name': 'Empty Tank',
          'currentKg': 0.0,
          'capacityKg': 100.0,
          'status': 'ACTIVE'
        }
      ],
      'shiftTanks': [],
      'prices': [],
      'shiftSales': [],
      'heldChange': [],
      'currentShift': null,
    });

    expect(bootstrap.tanks, hasLength(2));
    expect(bootstrap.assignedShiftTanks, isEmpty);
    expect(bootstrap.tanksEligibleForShift.single.name, 'Ready Tank');
  });

  test('a retail cashier cannot enter GasPOS', () {
    expect(
        () => GasUser.fromLogin({
              'accessToken': 'token',
              'branchId': 2,
              'branchModule': 'RETAIL_MODULE'
            }),
        throwsFormatException);
  });
}
