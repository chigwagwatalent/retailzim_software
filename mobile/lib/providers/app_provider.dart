import 'package:flutter/foundation.dart';
import '../models/models.dart';

class AppProvider extends ChangeNotifier {
  UserInfo? _currentUser;
  CashSession? _activeSession;
  List<CartItem> _cart = [];
  List<Product> _products = [];
  Customer? _attachedCustomer;
  String _currency = 'USD';
  bool _isOnline = true;
  int _notificationCount = 0;
  String? _selectedBranchId;

  // ─── Getters ──────────────────────────────────────────────────────────────

  UserInfo? get currentUser => _currentUser;
  CashSession? get activeSession => _activeSession;
  List<CartItem> get cart => List.unmodifiable(_cart);
  List<Product> get products => List.unmodifiable(_products);
  Customer? get attachedCustomer => _attachedCustomer;
  String get currency => _currency;
  bool get isOnline => _isOnline;
  int get notificationCount => _notificationCount;
  String? get selectedBranchId => _selectedBranchId;

  // ─── User ─────────────────────────────────────────────────────────────────

  void setUser(UserInfo user) {
    _currentUser = user;
    notifyListeners();
  }

  void clearUser() {
    _currentUser = null;
    notifyListeners();
  }

  // ─── Session ──────────────────────────────────────────────────────────────

  void setSession(CashSession s) {
    _activeSession = s;
    notifyListeners();
  }

  void clearSession() {
    _activeSession = null;
    _products = [];
    notifyListeners();
  }

  void setProducts(List<Product> products) {
    _products = products;
    notifyListeners();
  }

  void clearProducts() {
    _products = [];
    notifyListeners();
  }

  // ─── Cart ─────────────────────────────────────────────────────────────────

  double availableStockFor(Product p) {
    final latest = _products.where((product) => product.id == p.id);
    return latest.isEmpty ? p.quantityOnHand : latest.first.quantityOnHand;
  }

  double cartQuantityFor(Product p) {
    return _cart
        .where((item) => item.product.id == p.id)
        .fold(0.0, (sum, item) => sum + item.quantity);
  }

  String? validateCartStock() {
    for (final item in _cart) {
      final available = availableStockFor(item.product);
      if (available <= 0) {
        return '${item.product.name} is out of stock. Remove it from the sale.';
      }
      if (item.quantity > available) {
        return '${item.product.name} has only ${available.toStringAsFixed(0)} left.';
      }
    }
    return null;
  }

  bool addToCart(Product p) {
    final stock = availableStockFor(p);
    if (stock <= 0) return false;
    final idx = _cart.indexWhere((item) => item.product.id == p.id);
    if (idx >= 0) {
      if (_cart[idx].quantity + 1 > stock) return false;
      _cart[idx].quantity += 1;
    } else {
      _cart.add(CartItem(
        product: p,
        quantity: 1,
        unitPrice: p.priceForCurrency(_currency),
        currency: _currency,
      ));
    }
    notifyListeners();
    return true;
  }

  bool updateQty(int index, double qty) {
    if (index < 0 || index >= _cart.length) return false;
    if (qty <= 0) {
      _cart.removeAt(index);
    } else {
      final stock = availableStockFor(_cart[index].product);
      if (stock <= 0 || qty > stock) return false;
      _cart[index].quantity = qty;
    }
    notifyListeners();
    return true;
  }

  void removeFromCart(int index) {
    if (index < 0 || index >= _cart.length) return;
    _cart.removeAt(index);
    notifyListeners();
  }

  void clearCart() {
    _cart = [];
    _attachedCustomer = null;
    notifyListeners();
  }

  void updateItemDiscount(int index, double discount) {
    if (index < 0 || index >= _cart.length) return;
    _cart[index].discountAmount = discount;
    notifyListeners();
  }

  // ─── Customer ─────────────────────────────────────────────────────────────

  void attachCustomer(Customer c) {
    _attachedCustomer = c;
    notifyListeners();
  }

  void detachCustomer() {
    _attachedCustomer = null;
    notifyListeners();
  }

  // ─── Currency ─────────────────────────────────────────────────────────────

  void setCurrency(String c) {
    if (_currency == c) return;
    _currency = c;
    // Recompute unit prices for all cart items when currency changes
    for (final item in _cart) {
      item.unitPrice = item.product.priceForCurrency(c);
      item.currency = c;
    }
    notifyListeners();
  }

  // ─── Online status ────────────────────────────────────────────────────────

  void setOnline(bool online) {
    if (_isOnline == online) return;
    _isOnline = online;
    notifyListeners();
  }

  // ─── Notifications ────────────────────────────────────────────────────────

  void setNotificationCount(int count) {
    _notificationCount = count;
    notifyListeners();
  }

  void decrementNotificationCount() {
    if (_notificationCount > 0) {
      _notificationCount--;
      notifyListeners();
    }
  }

  // ─── Branch ───────────────────────────────────────────────────────────────

  void setBranch(String branchId) {
    _selectedBranchId = branchId;
    notifyListeners();
  }

  // ─── Cart totals ──────────────────────────────────────────────────────────

  double cartSubtotal() {
    return _cart.fold(0.0, (sum, item) => sum + item.lineTotal);
  }

  double cartTax() {
    return _cart.fold(0.0, (sum, item) => sum + item.lineTax);
  }

  double cartTotal() {
    return cartSubtotal() + cartTax();
  }

  double cartDiscount() {
    return _cart.fold(0.0, (sum, item) => sum + item.discountAmount);
  }

  int get cartItemCount =>
      _cart.fold(0, (sum, item) => sum + item.quantity.toInt());
}
