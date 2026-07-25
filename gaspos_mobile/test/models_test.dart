import 'package:flutter_test/flutter_test.dart';
import 'package:gaspos_retailzw/models.dart';

void main() {
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
        {
          'tankId': 1,
          'startingGrossKg': 1020.0,
          'expectedClosingNetKg': 612.0
        }
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
    expect(bootstrap.prices['USD'], 2.0);
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
