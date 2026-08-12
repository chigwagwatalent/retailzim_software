class GasUser {
  const GasUser({
    required this.token,
    required this.tenantId,
    required this.branchId,
    required this.branchName,
    required this.displayName,
    required this.companyName,
    this.username = '',
  });

  final String token;
  final int tenantId;
  final int branchId;
  final String branchName;
  final String displayName;
  final String companyName;
  final String username;

  String get cashierName {
    final name = displayName.trim();
    if (name.isNotEmpty) return name;
    final login = username.trim();
    return login.isEmpty ? 'Cashier' : login;
  }

  factory GasUser.fromLogin(Map<String, dynamic> json) {
    if (json['branchModule'] != 'GAS_MODULE') {
      throw const FormatException(
          'This cashier is not assigned to a gas branch.');
    }
    final username = json['username']?.toString().trim() ?? '';
    final displayName =
        '${json['firstName'] ?? ''} ${json['lastName'] ?? ''}'.trim();
    final accessToken = json['accessToken']?.toString().trim() ?? '';
    if (accessToken.isEmpty) {
      throw const FormatException(
          'RetailZW did not return a valid cashier session.');
    }
    return GasUser(
      token: accessToken,
      tenantId: (json['tenantId'] as num).toInt(),
      branchId: (json['branchId'] as num).toInt(),
      branchName: json['branchName'] as String? ?? 'Gas branch',
      displayName: displayName.isEmpty ? username : displayName,
      companyName: json['companyName'] as String? ?? 'RetailZW',
      username: username,
    );
  }

  Map<String, dynamic> toJson() => {
        'token': token,
        'tenantId': tenantId,
        'branchId': branchId,
        'branchName': branchName,
        'displayName': displayName,
        'companyName': companyName,
        'username': username,
      };

  factory GasUser.fromJson(Map<String, dynamic> json) => GasUser(
        token: json['token'] as String,
        tenantId: (json['tenantId'] as num?)?.toInt() ?? 0,
        branchId: (json['branchId'] as num).toInt(),
        branchName: json['branchName'] as String,
        displayName: json['displayName'] as String,
        companyName: json['companyName'] as String,
        username: json['username'] as String? ?? '',
      );
}

class GasTank {
  GasTank({
    required this.id,
    required this.name,
    required this.currentKg,
    required this.capacityKg,
    required this.tareKg,
    required this.fullGrossKg,
    required this.status,
  });

  final int id;
  final String name;
  double currentKg;
  final double capacityKg;
  final double tareKg;
  final double fullGrossKg;
  final String status;
  double get grossKg => currentKg + tareKg;
  bool get isActive => status.toUpperCase() == 'ACTIVE';
  bool get hasStock => currentKg > 0;

  factory GasTank.fromJson(Map<String, dynamic> json) => GasTank(
        id: (json['id'] as num).toInt(),
        name: json['name'] as String? ?? 'Gas tank',
        currentKg: (json['currentKg'] as num?)?.toDouble() ?? 0,
        capacityKg: (json['capacityKg'] as num?)?.toDouble() ?? 0,
        tareKg: (json['tareWeightKg'] as num?)?.toDouble() ?? 0,
        fullGrossKg: (json['fullGrossWeightKg'] as num?)?.toDouble() ??
            ((json['capacityKg'] as num?)?.toDouble() ?? 0) +
                ((json['tareWeightKg'] as num?)?.toDouble() ?? 0),
        status: json['status'] as String? ?? 'ACTIVE',
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'currentKg': currentKg,
        'capacityKg': capacityKg,
        'tareWeightKg': tareKg,
        'fullGrossWeightKg': fullGrossKg,
        'status': status,
      };
}

class ShiftTank {
  const ShiftTank({
    required this.tankId,
    required this.startingGrossKg,
    required this.expectedClosingNetKg,
  });
  final int tankId;
  final double startingGrossKg;
  final double expectedClosingNetKg;

  factory ShiftTank.fromJson(Map<String, dynamic> json) => ShiftTank(
        tankId: (json['tankId'] as num).toInt(),
        startingGrossKg: (json['startingGrossKg'] as num?)?.toDouble() ?? 0,
        expectedClosingNetKg:
            (json['expectedClosingNetKg'] as num?)?.toDouble() ?? 0,
      );
}

class GasBootstrap {
  GasBootstrap({
    required this.tanks,
    required this.shiftTanks,
    required this.prices,
    required this.sales,
    required this.heldChange,
    this.shift,
  });

  final List<GasTank> tanks;
  final List<ShiftTank> shiftTanks;
  final Map<String, double> prices;
  final List<Map<String, dynamic>> sales;
  final List<Map<String, dynamic>> heldChange;
  final Map<String, dynamic>? shift;

  bool get hasOpenShift => shift != null;
  List<GasTank> get activeTanks =>
      tanks.where((tank) => tank.isActive).toList();
  List<GasTank> get tanksEligibleForShift =>
      tanks.where((tank) => tank.isActive && tank.hasStock).toList();
  List<GasTank> get assignedShiftTanks {
    final assigned = shiftTanks.map((item) => item.tankId).toSet();
    return tanks.where((tank) => assigned.contains(tank.id)).toList();
  }

  factory GasBootstrap.fromJson(Map<String, dynamic> json) {
    final priceMap = <String, double>{};
    for (final item in (json['prices'] as List? ?? const [])) {
      final row = Map<String, dynamic>.from(item as Map);
      priceMap[row['currency'] as String] =
          (row['pricePerKg'] as num?)?.toDouble() ?? 0;
    }
    return GasBootstrap(
      tanks: (json['tanks'] as List? ?? const [])
          .map((e) => GasTank.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      shiftTanks: (json['shiftTanks'] as List? ?? const [])
          .map((e) => ShiftTank.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      prices: priceMap,
      sales: (json['shiftSales'] as List? ?? const [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList(),
      heldChange: (json['heldChange'] as List? ?? const [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList(),
      shift: json['currentShift'] == null
          ? null
          : Map<String, dynamic>.from(json['currentShift'] as Map),
    );
  }

  Map<String, dynamic> toJson() => {
        'tanks': tanks.map((e) => e.toJson()).toList(),
        'shiftTanks': shiftTanks
            .map((e) => {
                  'tankId': e.tankId,
                  'startingGrossKg': e.startingGrossKg,
                  'expectedClosingNetKg': e.expectedClosingNetKg,
                })
            .toList(),
        'prices': prices.entries
            .map((e) => {'currency': e.key, 'pricePerKg': e.value})
            .toList(),
        'shiftSales': sales,
        'heldChange': heldChange,
        'currentShift': shift,
      };
}
