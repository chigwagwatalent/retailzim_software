import 'package:flutter_test/flutter_test.dart';
import 'package:fuelpos_retailzw/models.dart';

void main() {
  test('fuel cashier uses the shared RetailZW mobile login response', () {
    final user = FuelUser.fromLogin({
      'accessToken': 'token',
      'tenantId': 7,
      'branchId': 11,
      'branchName': 'Airport Fuel',
      'branchModule': 'FUEL_MODULE',
      'username': 'cashier1',
      'firstName': 'Tariro',
      'lastName': 'Moyo',
      'companyName': 'Demo Energy'
    });
    expect(user.displayName, 'Tariro Moyo');
    expect(user.branchName, 'Airport Fuel');
    expect(FuelUser.fromJson(user.toJson()).username, 'cashier1');
  });
  test('cashier assigned to a non-fuel branch is rejected', () {
    expect(
        () => FuelUser.fromLogin({
              'accessToken': 'token',
              'tenantId': 7,
              'branchId': 11,
              'branchModule': 'SHOP_MODULE'
            }),
        throwsFormatException);
  });
  test('bootstrap maps tanks nozzles and per-grade prices', () {
    final data = FuelBootstrap.fromJson({
      'tanks': [
        {
          'id': 1,
          'code': 'T1',
          'grade': 'Petrol',
          'colour': '#00aa66',
          'capacity': 50000,
          'stock': 25000,
          'dip': 24990,
          'water': 0
        }
      ],
      'nozzles': [
        {
          'id': 2,
          'tankId': 1,
          'gradeId': 3,
          'pump': 'P01',
          'nozzle': '1A',
          'grade': 'Petrol',
          'colour': '#00aa66',
          'meter': 1000,
          'status': 'IDLE'
        }
      ],
      'prices': [
        {
          'gradeId': 3,
          'grade': 'Petrol',
          'currency': 'USD',
          'amountPerLitre': 1.52
        }
      ],
      'shiftSales': [],
      'paymentMethods': ['CASH'],
      'currentShift': {'id': 9}
    });
    expect(data.hasOpenShift, isTrue);
    expect(data.tanks.single.fill, .5);
    expect(data.priceFor('Petrol', 'USD'), 1.52);
    expect(data.nozzles.single.tankId, 1);
  });
}
