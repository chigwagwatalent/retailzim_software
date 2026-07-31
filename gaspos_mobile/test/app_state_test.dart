import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:gaspos_retailzw/app_state.dart';
import 'package:gaspos_retailzw/models.dart';
import 'package:gaspos_retailzw/services.dart';

class OfflineGasApi extends GasApi {
  @override
  Future<Map<String, dynamic>> sale(Map<String, dynamic> request) {
    throw const SocketException('offline');
  }
}

class MemoryOfflineStore extends OfflineStore {
  final queued = <Map<String, dynamic>>[];

  @override
  Future<void> queueSale(String id, Map<String, dynamic> request) async {
    queued.add(Map<String, dynamic>.from(request));
  }

  @override
  Future<int> pendingCount() async => queued.length;
}

void main() {
  test('offline sale never makes a selected tank negative', () async {
    final offline = MemoryOfflineStore();
    final state = GasPosState(api: OfflineGasApi(), offline: offline)
      ..user = const GasUser(
        token: 'token',
        tenantId: 7,
        branchId: 1,
        branchName: 'Gas Branch',
        displayName: 'Cashier',
        companyName: 'Retail Zim',
      )
      ..data = GasBootstrap(
        tanks: [
          GasTank(
            id: 1,
            name: 'Tank A',
            currentKg: 1,
            capacityKg: 100,
            tareKg: 10,
            fullGrossKg: 110,
            status: 'ACTIVE',
          ),
          GasTank(
            id: 2,
            name: 'Tank B',
            currentKg: 100,
            capacityKg: 100,
            tareKg: 10,
            fullGrossKg: 110,
            status: 'ACTIVE',
          ),
          GasTank(
            id: 3,
            name: 'Tank C',
            currentKg: 1,
            capacityKg: 100,
            tareKg: 10,
            fullGrossKg: 110,
            status: 'ACTIVE',
          ),
        ],
        shiftTanks: const [
          ShiftTank(tankId: 1, startingGrossKg: 11, expectedClosingNetKg: 1),
          ShiftTank(tankId: 2, startingGrossKg: 110, expectedClosingNetKg: 100),
          ShiftTank(tankId: 3, startingGrossKg: 11, expectedClosingNetKg: 1),
        ],
        prices: const {'USD': 2},
        sales: [],
        heldChange: [],
        shift: const {'id': 10, 'status': 'OPEN'},
      );

    final result = await state.completeSale(
      quantityKg: 50,
      currency: 'USD',
      tankIds: const [1, 2, 3],
      payments: const [
        {'paymentMethod': 'CASH', 'amount': 100.0}
      ],
      amountReceived: 100,
    );

    expect(result?['offline'], isTrue);
    expect(state.data!.tanks.map((tank) => tank.currentKg), [0, 51, 1]);
    expect(state.data!.tanks.every((tank) => tank.currentKg >= 0), isTrue);
    expect(offline.queued, hasLength(1));
  });
}
