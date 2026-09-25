class FuelUser {
  const FuelUser(
      {required this.token,
      required this.tenantId,
      required this.branchId,
      required this.branchName,
      required this.displayName,
      required this.companyName,
      required this.username});
  final String token;
  final int tenantId;
  final int branchId;
  final String branchName;
  final String displayName;
  final String companyName;
  final String username;

  factory FuelUser.fromLogin(Map<String, dynamic> json) {
    if (json['branchModule'] != 'FUEL_MODULE') {
      throw const FormatException(
          'This cashier is not assigned to a fuel-station branch.');
    }
    final token = json['accessToken']?.toString().trim() ?? '';
    final branchId = (json['branchId'] as num?)?.toInt();
    if (token.isEmpty || branchId == null) {
      throw const FormatException(
          'RetailZW returned an incomplete cashier session.');
    }
    final username = json['username']?.toString().trim() ?? '';
    final name = '${json['firstName'] ?? ''} ${json['lastName'] ?? ''}'.trim();
    return FuelUser(
        token: token,
        tenantId: (json['tenantId'] as num).toInt(),
        branchId: branchId,
        branchName: json['branchName']?.toString() ?? 'Fuel station',
        displayName: name.isEmpty ? username : name,
        companyName: json['companyName']?.toString() ?? 'RetailZW',
        username: username);
  }

  factory FuelUser.fromJson(Map<String, dynamic> json) => FuelUser(
      token: json['token'] as String,
      tenantId: (json['tenantId'] as num).toInt(),
      branchId: (json['branchId'] as num).toInt(),
      branchName: json['branchName'] as String,
      displayName: json['displayName'] as String,
      companyName: json['companyName'] as String,
      username: json['username'] as String);
  Map<String, dynamic> toJson() => {
        'token': token,
        'tenantId': tenantId,
        'branchId': branchId,
        'branchName': branchName,
        'displayName': displayName,
        'companyName': companyName,
        'username': username
      };
}

class FuelTank {
  FuelTank(
      {required this.id,
      required this.code,
      required this.grade,
      required this.colour,
      required this.capacity,
      required this.stock,
      required this.dip,
      required this.water});
  final int id;
  final String code;
  final String grade;
  final String colour;
  final double capacity;
  double stock;
  final double dip;
  final double water;
  double get fill => capacity <= 0 ? 0 : (stock / capacity).clamp(0, 1);
  factory FuelTank.fromJson(Map<String, dynamic> j) => FuelTank(
      id: (j['id'] as num).toInt(),
      code: j['code']?.toString() ?? 'Tank',
      grade: j['grade']?.toString() ?? 'Fuel',
      colour: j['colour']?.toString() ?? '#087cf0',
      capacity: (j['capacity'] as num?)?.toDouble() ?? 0,
      stock: (j['stock'] as num?)?.toDouble() ?? 0,
      dip: (j['dip'] as num?)?.toDouble() ?? 0,
      water: (j['water'] as num?)?.toDouble() ?? 0);
  Map<String, dynamic> toJson() => {
        'id': id,
        'code': code,
        'grade': grade,
        'colour': colour,
        'capacity': capacity,
        'stock': stock,
        'dip': dip,
        'water': water
      };
}

class FuelNozzle {
  FuelNozzle(
      {required this.id,
      required this.tankId,
      required this.gradeId,
      required this.pump,
      required this.code,
      required this.grade,
      required this.colour,
      required this.meter,
      required this.status});
  final int id;
  final int tankId;
  final int gradeId;
  final String pump;
  final String code;
  final String grade;
  final String colour;
  double meter;
  final String status;
  bool get available => status != 'OFFLINE';
  factory FuelNozzle.fromJson(Map<String, dynamic> j) => FuelNozzle(
      id: (j['id'] as num).toInt(),
      tankId: (j['tankId'] as num).toInt(),
      gradeId: (j['gradeId'] as num).toInt(),
      pump: j['pump']?.toString() ?? 'Pump',
      code: j['nozzle']?.toString() ?? 'Nozzle',
      grade: j['grade']?.toString() ?? 'Fuel',
      colour: j['colour']?.toString() ?? '#087cf0',
      meter: (j['meter'] as num?)?.toDouble() ?? 0,
      status: j['status']?.toString() ?? 'IDLE');
  Map<String, dynamic> toJson() => {
        'id': id,
        'tankId': tankId,
        'gradeId': gradeId,
        'pump': pump,
        'nozzle': code,
        'grade': grade,
        'colour': colour,
        'meter': meter,
        'status': status
      };
}

class FuelPrice {
  const FuelPrice(
      {required this.gradeId,
      required this.grade,
      required this.currency,
      required this.amount});
  final int gradeId;
  final String grade;
  final String currency;
  final double amount;
  factory FuelPrice.fromJson(Map<String, dynamic> j) => FuelPrice(
      gradeId: (j['gradeId'] as num).toInt(),
      grade: j['grade']?.toString() ?? 'Fuel',
      currency: j['currency']?.toString() ?? 'USD',
      amount: (j['amountPerLitre'] as num?)?.toDouble() ?? 0);
  Map<String, dynamic> toJson() => {
        'gradeId': gradeId,
        'grade': grade,
        'currency': currency,
        'amountPerLitre': amount
      };
}

class FuelBootstrap {
  FuelBootstrap(
      {required this.tanks,
      required this.nozzles,
      required this.prices,
      required this.sales,
      required this.paymentMethods,
      this.shift});
  final List<FuelTank> tanks;
  final List<FuelNozzle> nozzles;
  final List<FuelPrice> prices;
  final List<Map<String, dynamic>> sales;
  final List<String> paymentMethods;
  Map<String, dynamic>? shift;
  bool get hasOpenShift => shift != null;
  double? priceFor(String grade, String currency) {
    for (final price in prices) {
      if (price.grade == grade && price.currency == currency) {
        return price.amount;
      }
    }
    return null;
  }

  factory FuelBootstrap.fromJson(Map<String, dynamic> j) => FuelBootstrap(
      tanks: (j['tanks'] as List? ?? const [])
          .map((e) => FuelTank.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      nozzles: (j['nozzles'] as List? ?? const [])
          .map((e) => FuelNozzle.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      prices: (j['prices'] as List? ?? const [])
          .map((e) => FuelPrice.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      sales: (j['shiftSales'] as List? ?? const [])
          .map((e) => Map<String, dynamic>.from(e as Map))
          .toList(),
      paymentMethods: (j['paymentMethods'] as List? ?? const ['CASH'])
          .map((e) => e.toString())
          .toList(),
      shift: j['currentShift'] == null
          ? null
          : Map<String, dynamic>.from(j['currentShift'] as Map));
  Map<String, dynamic> toJson() => {
        'tanks': tanks.map((e) => e.toJson()).toList(),
        'nozzles': nozzles.map((e) => e.toJson()).toList(),
        'prices': prices.map((e) => e.toJson()).toList(),
        'shiftSales': sales,
        'paymentMethods': paymentMethods,
        'currentShift': shift
      };
}
