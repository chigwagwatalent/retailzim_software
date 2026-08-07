class LoginResponse {
  final String accessToken;
  final String refreshToken;
  final UserInfo user;

  LoginResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.user,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      accessToken: json['accessToken'] as String? ?? '',
      refreshToken: json['refreshToken'] as String? ?? '',
      user: UserInfo.fromJson(json['user'] as Map<String, dynamic>? ?? json),
    );
  }

  Map<String, dynamic> toJson() => {
        'accessToken': accessToken,
        'refreshToken': refreshToken,
        'user': user.toJson(),
      };
}

class UserInfo {
  final int id;
  final String username;
  final String firstName;
  final String lastName;
  final String role;
  final int? branchId;
  final int? tenantId;
  final String tenantCode;
  final String branchModule;
  final String companyName;
  final String companyEmail;
  final String companyPhone;
  final String companyAddress;
  final String companyCity;
  final String companyCountry;
  final String companyLogoUrl;
  final String companyWebsite;
  final String companyRegistrationNumber;
  final String companyVatNumber;
  final String receiptFooter;
  final String defaultCurrency;
  final String secondaryCurrency;

  UserInfo({
    required this.id,
    required this.username,
    required this.firstName,
    required this.lastName,
    required this.role,
    this.branchId,
    this.tenantId,
    this.tenantCode = '',
    this.branchModule = 'SHOP_MODULE',
    this.companyName = '',
    this.companyEmail = '',
    this.companyPhone = '',
    this.companyAddress = '',
    this.companyCity = '',
    this.companyCountry = '',
    this.companyLogoUrl = '',
    this.companyWebsite = '',
    this.companyRegistrationNumber = '',
    this.companyVatNumber = '',
    this.receiptFooter = '',
    this.defaultCurrency = 'USD',
    this.secondaryCurrency = 'ZWG',
  });

  factory UserInfo.fromJson(Map<String, dynamic> json) {
    return UserInfo(
      id: (json['id'] as num?)?.toInt() ??
          (json['userId'] as num?)?.toInt() ??
          0,
      username: json['username'] as String? ?? '',
      firstName: json['firstName'] as String? ?? '',
      lastName: json['lastName'] as String? ?? '',
      role: json['role'] as String? ?? '',
      branchId: (json['branchId'] as num?)?.toInt(),
      tenantId: (json['tenantId'] as num?)?.toInt(),
      tenantCode: json['tenantCode'] as String? ?? '',
      branchModule: json['branchModule'] as String? ?? 'SHOP_MODULE',
      companyName: json['companyName'] as String? ?? '',
      companyEmail: json['companyEmail'] as String? ?? '',
      companyPhone: json['companyPhone'] as String? ?? '',
      companyAddress: json['companyAddress'] as String? ?? '',
      companyCity: json['companyCity'] as String? ?? '',
      companyCountry: json['companyCountry'] as String? ?? '',
      companyLogoUrl: json['companyLogoUrl'] as String? ?? '',
      companyWebsite: json['companyWebsite'] as String? ?? '',
      companyRegistrationNumber:
          json['companyRegistrationNumber'] as String? ?? '',
      companyVatNumber: json['companyVatNumber'] as String? ?? '',
      receiptFooter: json['receiptFooter'] as String? ?? '',
      defaultCurrency: json['defaultCurrency'] as String? ?? 'USD',
      secondaryCurrency: json['secondaryCurrency'] as String? ?? 'ZWG',
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'username': username,
        'firstName': firstName,
        'lastName': lastName,
        'role': role,
        'branchId': branchId,
        'tenantId': tenantId,
        'tenantCode': tenantCode,
        'branchModule': branchModule,
        'companyName': companyName,
        'companyEmail': companyEmail,
        'companyPhone': companyPhone,
        'companyAddress': companyAddress,
        'companyCity': companyCity,
        'companyCountry': companyCountry,
        'companyLogoUrl': companyLogoUrl,
        'companyWebsite': companyWebsite,
        'companyRegistrationNumber': companyRegistrationNumber,
        'companyVatNumber': companyVatNumber,
        'receiptFooter': receiptFooter,
        'defaultCurrency': defaultCurrency,
        'secondaryCurrency': secondaryCurrency,
      };

  String get fullName => '$firstName $lastName'.trim();
  bool get isGasBranch => branchModule == 'GAS_MODULE';
}

class GasTank {
  final int id;
  final String name;
  final String productName;
  final double currentKg;
  final double capacityKg;
  final double reorderLevelKg;
  final String status;

  GasTank({
    required this.id,
    required this.name,
    required this.productName,
    required this.currentKg,
    required this.capacityKg,
    required this.reorderLevelKg,
    required this.status,
  });

  factory GasTank.fromJson(Map<String, dynamic> json) => GasTank(
        id: (json['id'] as num?)?.toInt() ?? 0,
        name: json['name'] as String? ?? 'Tank',
        productName: json['productName'] as String? ?? 'LPG Gas',
        currentKg: _double(json['currentKg']),
        capacityKg: _double(json['capacityKg']),
        reorderLevelKg: _double(json['reorderLevelKg']),
        status: json['status'] as String? ?? 'ACTIVE',
      );
}

class GasPrice {
  final String currency;
  final double pricePerKg;

  GasPrice({required this.currency, required this.pricePerKg});

  factory GasPrice.fromJson(Map<String, dynamic> json) => GasPrice(
        currency: json['currency'] as String? ?? 'USD',
        pricePerKg: _double(json['pricePerKg']),
      );
}

class GasShift {
  final int id;
  final int? branchId;
  final int? cashierId;
  final String shiftNumber;
  final String status;
  final double totalKgSold;
  final double totalUsd;
  final double totalZwg;

  GasShift({
    required this.id,
    this.branchId,
    this.cashierId,
    required this.shiftNumber,
    required this.status,
    required this.totalKgSold,
    required this.totalUsd,
    required this.totalZwg,
  });

  factory GasShift.fromJson(Map<String, dynamic> json) => GasShift(
        id: (json['id'] as num?)?.toInt() ?? 0,
        branchId: (json['branchId'] as num?)?.toInt(),
        cashierId: (json['cashierId'] as num?)?.toInt(),
        shiftNumber: json['shiftNumber'] as String? ?? '',
        status: json['status'] as String? ?? 'OPEN',
        totalKgSold: _double(json['totalKgSold']),
        totalUsd: _double(json['totalUsd']),
        totalZwg: _double(json['totalZwg']),
      );
}

class GasSale {
  final int id;
  final String receiptNumber;
  final double quantityKg;
  final double unitPrice;
  final double total;
  final String currency;
  final String paymentMethod;

  GasSale({
    required this.id,
    required this.receiptNumber,
    required this.quantityKg,
    required this.unitPrice,
    required this.total,
    required this.currency,
    this.paymentMethod = 'CASH',
  });

  factory GasSale.fromJson(Map<String, dynamic> json) => GasSale(
        id: (json['id'] as num?)?.toInt() ?? 0,
        receiptNumber: json['receiptNumber'] as String? ?? '',
        quantityKg: _double(json['quantityKg']),
        unitPrice: _double(json['unitPrice']),
        total: _double(json['total']),
        currency: json['currency'] as String? ?? 'USD',
        paymentMethod: json['paymentMethod']?.toString() ?? 'CASH',
      );
}

class GasDashboard {
  final double soldKgToday;
  final double revenueUsdToday;
  final double revenueZwgToday;
  final double expensesUsdToday;
  final double expensesZwgToday;
  final double marginUsdToday;
  final double marginZwgToday;
  final List<double> lpgWeightPresetsKg;

  GasDashboard({
    required this.soldKgToday,
    required this.revenueUsdToday,
    required this.revenueZwgToday,
    required this.expensesUsdToday,
    required this.expensesZwgToday,
    required this.marginUsdToday,
    required this.marginZwgToday,
    required this.lpgWeightPresetsKg,
  });

  factory GasDashboard.fromJson(Map<String, dynamic> json) => GasDashboard(
        soldKgToday: _double(json['soldKgToday']),
        revenueUsdToday: _double(json['revenueUsdToday']),
        revenueZwgToday: _double(json['revenueZwgToday']),
        expensesUsdToday: _double(json['expensesUsdToday']),
        expensesZwgToday: _double(json['expensesZwgToday']),
        marginUsdToday: _double(json['marginUsdToday']),
        marginZwgToday: _double(json['marginZwgToday']),
        lpgWeightPresetsKg: (json['lpgWeightPresetsKg'] as List<dynamic>? ??
                const [1, 2, 3, 5, 9, 14, 19, 48])
            .map(_double)
            .where((v) => v > 0)
            .toList(),
      );
}

double _double(dynamic value) {
  if (value is num) return value.toDouble();
  return double.tryParse('$value') ?? 0;
}

class Product {
  final int id;
  final String name;
  final String sku;
  final String? barcode;
  final double sellingPriceUsd;
  final double sellingPriceZwg;
  final double costPriceUsd;
  final double taxRate;
  final bool isTaxable;
  final int? categoryId;
  final String? imageUrl;
  final double quantityOnHand;
  final bool wholesaleEnabled;
  final double? wholesaleMinimumQuantity;
  final double? wholesalePriceUsd;
  final double? wholesalePriceZwg;
  final int? wholesalePricingVersion;
  final int pricingProtocolVersion;

  Product({
    required this.id,
    required this.name,
    required this.sku,
    this.barcode,
    required this.sellingPriceUsd,
    required this.sellingPriceZwg,
    required this.costPriceUsd,
    required this.taxRate,
    required this.isTaxable,
    this.categoryId,
    this.imageUrl,
    required this.quantityOnHand,
    this.wholesaleEnabled = false,
    this.wholesaleMinimumQuantity,
    this.wholesalePriceUsd,
    this.wholesalePriceZwg,
    this.wholesalePricingVersion,
    this.pricingProtocolVersion = 1,
  });

  factory Product.fromJson(Map<String, dynamic> json) {
    return Product(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name'] as String? ?? '',
      sku: json['sku'] as String? ?? '',
      barcode: json['barcode'] as String?,
      sellingPriceUsd: _toDouble(json['sellingPriceUsd']),
      sellingPriceZwg: _toDouble(json['sellingPriceZwg']),
      costPriceUsd: _toDouble(json['costPriceUsd']),
      taxRate: _toDouble(json['taxRate']),
      isTaxable: json['isTaxable'] as bool? ?? false,
      categoryId: (json['categoryId'] as num?)?.toInt(),
      imageUrl: json['imageUrl'] as String?,
      quantityOnHand: _toDouble(json['quantityOnHand']),
      wholesaleEnabled: json['wholesaleEnabled'] as bool? ?? false,
      wholesaleMinimumQuantity:
          json['wholesaleMinimumQuantity'] == null
              ? null
              : _toDouble(json['wholesaleMinimumQuantity']),
      wholesalePriceUsd: json['wholesalePriceUsd'] == null
          ? null
          : _toDouble(json['wholesalePriceUsd']),
      wholesalePriceZwg: json['wholesalePriceZwg'] == null
          ? null
          : _toDouble(json['wholesalePriceZwg']),
      wholesalePricingVersion:
          (json['wholesalePricingVersion'] as num?)?.toInt(),
      pricingProtocolVersion:
          (json['pricingProtocolVersion'] as num?)?.toInt() ?? 1,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'sku': sku,
        'barcode': barcode,
        'sellingPriceUsd': sellingPriceUsd,
        'sellingPriceZwg': sellingPriceZwg,
        'costPriceUsd': costPriceUsd,
        'taxRate': taxRate,
        'isTaxable': isTaxable,
        'categoryId': categoryId,
        'imageUrl': imageUrl,
        'quantityOnHand': quantityOnHand,
        'wholesaleEnabled': wholesaleEnabled,
        'wholesaleMinimumQuantity': wholesaleMinimumQuantity,
        'wholesalePriceUsd': wholesalePriceUsd,
        'wholesalePriceZwg': wholesalePriceZwg,
        'wholesalePricingVersion': wholesalePricingVersion,
        'pricingProtocolVersion': pricingProtocolVersion,
      };

  double priceForCurrency(String currency) =>
      currency == 'USD' ? sellingPriceUsd : sellingPriceZwg;

  bool qualifiesForWholesale(double quantity) =>
      wholesaleEnabled &&
      wholesaleMinimumQuantity != null &&
      wholesaleMinimumQuantity! > 1 &&
      quantity >= wholesaleMinimumQuantity! &&
      wholesalePriceUsd != null &&
      wholesalePriceZwg != null &&
      wholesalePriceUsd! > 0 &&
      wholesalePriceZwg! > 0;

  double priceForQuantity(String currency, double quantity) {
    if (!qualifiesForWholesale(quantity)) {
      return priceForCurrency(currency);
    }
    return currency == 'USD' ? wholesalePriceUsd! : wholesalePriceZwg!;
  }
}

class Category {
  final int id;
  final String name;
  final String code;

  Category({required this.id, required this.name, required this.code});

  factory Category.fromJson(Map<String, dynamic> json) {
    return Category(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name'] as String? ?? '',
      code: json['code'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'code': code};
}

class CartItem {
  final Product product;
  double quantity;
  double unitPrice;
  double discountAmount;
  String currency;
  String pricingTier;
  int? pricingVersion;

  CartItem({
    required this.product,
    this.quantity = 1,
    required this.unitPrice,
    this.discountAmount = 0,
    required this.currency,
    this.pricingTier = 'RETAIL',
    this.pricingVersion,
  });

  double get lineTotal => (quantity * unitPrice) - discountAmount;
  double get lineTax =>
      product.isTaxable ? lineTotal * (product.taxRate / 100) : 0;
}

class Sale {
  final int id;
  final String receiptNumber;
  final double grandTotal;
  final String currency;
  final String status;
  final DateTime createdAt;
  final List<SaleItem> items;
  final List<SalePayment> payments;

  Sale({
    required this.id,
    required this.receiptNumber,
    required this.grandTotal,
    required this.currency,
    required this.status,
    required this.createdAt,
    required this.items,
    required this.payments,
  });

  factory Sale.fromJson(Map<String, dynamic> json) {
    return Sale(
      id: (json['id'] as num?)?.toInt() ?? 0,
      receiptNumber: json['receiptNumber'] as String? ?? '',
      grandTotal: _toDouble(json['grandTotal']),
      currency: json['currency'] as String? ?? 'USD',
      status: json['status'] as String? ?? '',
      createdAt: _parseDateTime(json['createdAt']) ?? DateTime.now(),
      items: (json['items'] as List<dynamic>? ?? [])
          .map((e) => SaleItem.fromJson(e as Map<String, dynamic>))
          .toList(),
      payments: (json['payments'] as List<dynamic>? ?? [])
          .map((e) => SalePayment.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'receiptNumber': receiptNumber,
        'grandTotal': grandTotal,
        'currency': currency,
        'status': status,
        'createdAt': createdAt.toIso8601String(),
        'items': items.map((e) => e.toJson()).toList(),
        'payments': payments.map((e) => e.toJson()).toList(),
      };
}

class SaleItem {
  final int productId;
  final String productName;
  final double quantity;
  final double unitPrice;
  final double lineTotal;

  SaleItem({
    required this.productId,
    required this.productName,
    required this.quantity,
    required this.unitPrice,
    required this.lineTotal,
  });

  factory SaleItem.fromJson(Map<String, dynamic> json) {
    return SaleItem(
      productId: (json['productId'] as num?)?.toInt() ?? 0,
      productName: json['productName'] as String? ?? '',
      quantity: _toDouble(json['quantity']),
      unitPrice: _toDouble(json['unitPrice']),
      lineTotal: _toDouble(json['lineTotal']),
    );
  }

  Map<String, dynamic> toJson() => {
        'productId': productId,
        'productName': productName,
        'quantity': quantity,
        'unitPrice': unitPrice,
        'lineTotal': lineTotal,
      };
}

class SalePayment {
  final String method;
  final String currency;
  final double amount;
  final String? reference;

  SalePayment({
    required this.method,
    required this.currency,
    required this.amount,
    this.reference,
  });

  factory SalePayment.fromJson(Map<String, dynamic> json) {
    return SalePayment(
      method: json['method'] as String? ?? '',
      currency: json['currency'] as String? ?? 'USD',
      amount: _toDouble(json['amount']),
      reference: json['reference'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'method': method,
        'currency': currency,
        'amount': amount,
        'reference': reference,
      };
}

class Customer {
  final int id;
  final String firstName;
  final String lastName;
  final String? phone;
  final String? email;
  final String? loyaltyCardNumber;
  final String? loyaltyTier;
  final double loyaltyPointsBalance;

  Customer({
    required this.id,
    required this.firstName,
    required this.lastName,
    this.phone,
    this.email,
    this.loyaltyCardNumber,
    this.loyaltyTier,
    this.loyaltyPointsBalance = 0,
  });

  String get fullName => '$firstName $lastName'.trim();

  factory Customer.fromJson(Map<String, dynamic> json) {
    return Customer(
      id: (json['id'] as num?)?.toInt() ?? 0,
      firstName: json['firstName'] as String? ?? '',
      lastName: json['lastName'] as String? ?? '',
      phone: json['phone'] as String?,
      email: json['email'] as String?,
      loyaltyCardNumber: json['loyaltyCardNumber'] as String?,
      loyaltyTier: json['loyaltyTier'] as String?,
      loyaltyPointsBalance: _toDouble(json['loyaltyPointsBalance']),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'firstName': firstName,
        'lastName': lastName,
        'phone': phone,
        'email': email,
        'loyaltyCardNumber': loyaltyCardNumber,
        'loyaltyTier': loyaltyTier,
        'loyaltyPointsBalance': loyaltyPointsBalance,
      };
}

class Borrower {
  final int id;
  final String accountNumber;
  final String fullName;
  final String phone;
  final String currency;
  final double creditLimit;
  final double currentBalance;
  final bool isActive;

  Borrower({
    required this.id,
    required this.accountNumber,
    required this.fullName,
    required this.phone,
    required this.currency,
    required this.creditLimit,
    required this.currentBalance,
    required this.isActive,
  });

  double get availableCredit =>
      (creditLimit - currentBalance).clamp(0, double.infinity);

  factory Borrower.fromJson(Map<String, dynamic> json) => Borrower(
        id: (json['id'] as num?)?.toInt() ?? 0,
        accountNumber: json['accountNumber']?.toString() ?? '',
        fullName: json['fullName']?.toString() ?? '',
        phone: json['phone']?.toString() ?? '',
        currency: json['currency']?.toString() ?? 'USD',
        creditLimit: _toDouble(json['creditLimit']),
        currentBalance: _toDouble(json['currentBalance']),
        isActive: json['isActive'] as bool? ?? false,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'accountNumber': accountNumber,
        'fullName': fullName,
        'phone': phone,
        'currency': currency,
        'creditLimit': creditLimit,
        'currentBalance': currentBalance,
        'availableCredit': availableCredit,
        'isActive': isActive,
      };
}

class HeldChangeRecord {
  final int? id;
  final String referenceNumber;
  final String? offlineReference;
  final String customerName;
  final String phone;
  final String currency;
  final double amount;
  final String status;

  HeldChangeRecord({
    this.id,
    required this.referenceNumber,
    this.offlineReference,
    required this.customerName,
    required this.phone,
    required this.currency,
    required this.amount,
    required this.status,
  });

  factory HeldChangeRecord.fromJson(Map<String, dynamic> json) =>
      HeldChangeRecord(
        id: (json['id'] as num?)?.toInt(),
        referenceNumber: json['referenceNumber']?.toString() ??
            json['offlineReference']?.toString() ??
            '',
        offlineReference: json['offlineReference']?.toString(),
        customerName: json['customerName']?.toString() ?? '',
        phone: json['phone']?.toString() ?? '',
        currency: json['currency']?.toString() ?? 'USD',
        amount: _toDouble(json['amount']),
        status: json['status']?.toString() ?? 'OPEN',
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'referenceNumber': referenceNumber,
        'offlineReference': offlineReference,
        'customerName': customerName,
        'phone': phone,
        'currency': currency,
        'amount': amount,
        'status': status,
      };
}

class CashSession {
  final int id;
  final int? branchId;
  final int? cashierId;
  final String status;
  final double openingFloatUsd;
  final double openingFloatZwg;
  final double expectedCashUsd;
  final double totalSalesUsd;
  final String? drawerName;
  final DateTime? openedAt;
  final String? cashierName;

  CashSession({
    required this.id,
    this.branchId,
    this.cashierId,
    required this.status,
    required this.openingFloatUsd,
    required this.openingFloatZwg,
    required this.expectedCashUsd,
    required this.totalSalesUsd,
    this.drawerName,
    this.openedAt,
    this.cashierName,
  });

  factory CashSession.fromJson(Map<String, dynamic> json) {
    return CashSession(
      id: (json['id'] as num?)?.toInt() ?? 0,
      branchId: (json['branchId'] as num?)?.toInt(),
      cashierId: (json['cashierId'] as num?)?.toInt(),
      status: json['status']?.toString() ?? '',
      openingFloatUsd: _toDouble(json['openingFloatUsd']),
      openingFloatZwg: _toDouble(json['openingFloatZwg']),
      expectedCashUsd: _toDouble(json['expectedCashUsd']),
      totalSalesUsd: _toDouble(json['totalSalesUsd']),
      drawerName: json['drawerName']?.toString(),
      openedAt: _parseDateTime(json['openedAt']),
      cashierName: json['cashierName']?.toString(),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'branchId': branchId,
        'cashierId': cashierId,
        'status': status,
        'openingFloatUsd': openingFloatUsd,
        'openingFloatZwg': openingFloatZwg,
        'expectedCashUsd': expectedCashUsd,
        'totalSalesUsd': totalSalesUsd,
        'drawerName': drawerName,
        'openedAt': openedAt?.toIso8601String(),
        'cashierName': cashierName,
      };
}

class CashDrawer {
  final int id;
  final String name;

  CashDrawer({required this.id, required this.name});

  factory CashDrawer.fromJson(Map<String, dynamic> json) {
    return CashDrawer(
      id: (json['id'] as num?)?.toInt() ?? 0,
      name: json['name'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() => {'id': id, 'name': name};
}

class AppNotification {
  final int id;
  final String type;
  final String title;
  final String message;
  final bool isRead;
  final DateTime createdAt;

  AppNotification({
    required this.id,
    required this.type,
    required this.title,
    required this.message,
    required this.isRead,
    required this.createdAt,
  });

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    return AppNotification(
      id: (json['id'] as num?)?.toInt() ?? 0,
      type: json['type'] as String? ?? '',
      title: json['title'] as String? ?? '',
      message: json['message'] as String? ?? '',
      isRead: json['isRead'] as bool? ?? false,
      createdAt: _parseDateTime(json['createdAt']) ?? DateTime.now(),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'type': type,
        'title': title,
        'message': message,
        'isRead': isRead,
        'createdAt': createdAt.toIso8601String(),
      };
}

class ReturnRequest {
  final String originalReceiptNumber;
  final List<ReturnItem> items;
  final String reason;
  final String refundMethod;
  final String? notes;

  ReturnRequest({
    required this.originalReceiptNumber,
    required this.items,
    required this.reason,
    required this.refundMethod,
    this.notes,
  });

  Map<String, dynamic> toJson() => {
        'originalReceiptNumber': originalReceiptNumber,
        'items': items.map((e) => e.toJson()).toList(),
        'reason': reason,
        'refundMethod': refundMethod,
        'notes': notes,
      };
}

class ReturnItem {
  final int productId;
  final double quantity;

  ReturnItem({required this.productId, required this.quantity});

  Map<String, dynamic> toJson() => {
        'productId': productId,
        'quantity': quantity,
      };
}

class AttendanceRecord {
  final int id;
  final String date;
  final String? clockInTime;
  final String? clockOutTime;

  AttendanceRecord({
    required this.id,
    required this.date,
    this.clockInTime,
    this.clockOutTime,
  });

  factory AttendanceRecord.fromJson(Map<String, dynamic> json) {
    return AttendanceRecord(
      id: (json['id'] as num?)?.toInt() ?? 0,
      date: json['date'] as String? ?? '',
      clockInTime: json['clockInTime'] as String?,
      clockOutTime: json['clockOutTime'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'date': date,
        'clockInTime': clockInTime,
        'clockOutTime': clockOutTime,
      };
}

double _toDouble(dynamic value) => double.tryParse('$value') ?? 0.0;

/// Safely parse a date value that may arrive as:
/// - ISO-8601 string: "2024-01-15T08:30:00"          (with write-dates-as-timestamps=false)
/// - Timestamp array: [2024, 1, 15, 8, 30, 0, 0]     (Jackson default without config)
/// - null / missing
DateTime? _parseDateTime(dynamic value) {
  if (value == null) return null;
  if (value is String) return DateTime.tryParse(value);
  if (value is List && value.length >= 3) {
    try {
      return DateTime(
        (value[0] as num).toInt(),
        (value[1] as num).toInt(),
        (value[2] as num).toInt(),
        value.length > 3 ? (value[3] as num).toInt() : 0,
        value.length > 4 ? (value[4] as num).toInt() : 0,
        value.length > 5 ? (value[5] as num).toInt() : 0,
      );
    } catch (_) {
      return null;
    }
  }
  // Epoch milliseconds (rare but possible)
  if (value is num) {
    return DateTime.fromMillisecondsSinceEpoch(value.toInt());
  }
  return null;
}
