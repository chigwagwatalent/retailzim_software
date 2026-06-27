import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../models/models.dart';
import '../../providers/app_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';
import 'payment_screen.dart';

class PosScreen extends StatefulWidget {
  const PosScreen({super.key});

  @override
  State<PosScreen> createState() => _PosScreenState();
}

class _PosScreenState extends State<PosScreen> {
  final ApiService _api = ApiService();
  final TextEditingController _search = TextEditingController();
  final FocusNode _searchFocus = FocusNode();
  Timer? _barcodeDebounce;
  String? _notice;
  bool _loading = false;
  bool _barcodeLookupRunning = false;
  String? _lastScannedBarcode;
  DateTime? _lastScanTime;
  List<Category> _categories = const [];
  int? _selectedCategoryId;

  @override
  void initState() {
    super.initState();
    _loadProducts();
  }

  @override
  void dispose() {
    _barcodeDebounce?.cancel();
    _search.dispose();
    _searchFocus.dispose();
    super.dispose();
  }

  Future<void> _loadProducts({bool preferCache = false}) async {
    setState(() => _loading = true);
    try {
      final products = await _api.getProducts(
          search: _search.text, preferCache: preferCache);
      if (!mounted) return;
      context.read<AppProvider>().setProducts(products);
      context.read<AppProvider>().setOnline(true);
      if (!preferCache && _categories.isEmpty) {
        try {
          final categories = await _api.getCategories();
          if (mounted) setState(() => _categories = categories);
        } catch (_) {
          // Product selling remains available when category metadata is offline.
        }
      }
    } catch (_) {
      if (mounted) context.read<AppProvider>().setOnline(false);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final session = provider.activeSession;

    if (session == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: AppColors.accentYellow.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                child: const Icon(Icons.lock_clock,
                    color: AppColors.accentYellow, size: 40),
              ),
              const SizedBox(height: 20),
              const Text('No Active Shift',
                  style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w900,
                      color: AppColors.textDark)),
              const SizedBox(height: 8),
              const Text(
                'Open a cash shift before making sales.\nProducts will sync to this device.',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.textMuted, fontSize: 14),
              ),
            ],
          ),
        ),
      );
    }

    return CallbackShortcuts(
      bindings: <ShortcutActivator, VoidCallback>{
        const SingleActivator(LogicalKeyboardKey.f2): () {
          _searchFocus.requestFocus();
          _search.selection = TextSelection(
            baseOffset: 0,
            extentOffset: _search.text.length,
          );
        },
        const SingleActivator(LogicalKeyboardKey.f4): () {
          if (provider.cart.isNotEmpty) _openPayment(provider);
        },
        const SingleActivator(LogicalKeyboardKey.escape): () {
          _barcodeDebounce?.cancel();
          _search.clear();
          setState(() => _notice = null);
          _searchFocus.requestFocus();
        },
      },
      child: Focus(
        autofocus: true,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final desktop = constraints.maxWidth >= 1180;
            final wide = constraints.maxWidth >= 840;
            if (desktop) {
              return Row(
                children: [
                  SizedBox(width: 182, child: _categoryPane(provider)),
                  Expanded(child: _productPane(provider, desktop: true)),
                  Container(
                    width: 500,
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      border: Border(
                        left: BorderSide(color: Color(0xFFDDE5EF)),
                      ),
                    ),
                    child: _cartPane(provider),
                  ),
                ],
              );
            }
            if (wide) {
              return Row(
                children: [
                  Expanded(
                      flex: 3, child: _productPane(provider, desktop: desktop)),
                  Container(
                    width: desktop ? 460 : 380,
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      boxShadow: [
                        BoxShadow(
                          color: Color(0x10000000),
                          blurRadius: 12,
                          offset: Offset(-4, 0),
                        ),
                      ],
                    ),
                    child: _cartPane(provider),
                  ),
                ],
              );
            }
            return Column(
              children: [
                Expanded(child: _productPane(provider)),
                _cartPane(provider, compact: true),
              ],
            );
          },
        ),
      ),
    );
  }

  // ─── Product Pane ─────────────────────────────────────────────────────────

  Widget _categoryPane(AppProvider provider) {
    final products = provider.products;
    return Container(
      color: const Color(0xFFF8FAFC),
      padding: const EdgeInsets.fromLTRB(14, 18, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 8),
            child: Text('Categories',
                style: TextStyle(
                    color: AppColors.textDark,
                    fontSize: 14,
                    fontWeight: FontWeight.w900)),
          ),
          const SizedBox(height: 12),
          _CategoryButton(
            label: 'All products',
            count: products.length,
            selected: _selectedCategoryId == null,
            onTap: () => setState(() => _selectedCategoryId = null),
          ),
          ..._categories.map((category) => _CategoryButton(
                label: category.name,
                count: products
                    .where((product) => product.categoryId == category.id)
                    .length,
                selected: _selectedCategoryId == category.id,
                onTap: () => setState(() => _selectedCategoryId = category.id),
              )),
          if (_categories.isEmpty) ...[
            const SizedBox(height: 10),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 8),
              child: Text('Categories appear after the next online sync.',
                  style: TextStyle(
                      color: AppColors.textMuted, fontSize: 11, height: 1.4)),
            ),
          ],
          const Spacer(),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: const Color(0xFFDDE5EF)),
            ),
            child: const Row(
              children: [
                Icon(Icons.keyboard_rounded,
                    size: 18, color: AppColors.primaryBlue),
                SizedBox(width: 8),
                Expanded(
                  child: Text('F2 search\nF4 checkout',
                      style: TextStyle(
                          color: AppColors.textMuted,
                          fontSize: 10,
                          height: 1.45)),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _productPane(AppProvider provider, {bool desktop = false}) {
    final allProducts = provider.products;
    final query = _search.text.trim().toLowerCase();
    final categoryProducts = _selectedCategoryId == null
        ? allProducts
        : allProducts
            .where((product) => product.categoryId == _selectedCategoryId)
            .toList();
    final searched = query.isEmpty
        ? categoryProducts
        : categoryProducts
            .where((p) =>
                p.name.toLowerCase().contains(query) ||
                p.sku.toLowerCase().contains(query) ||
                (p.barcode?.toLowerCase().contains(query) ?? false))
            .toList();

    return Column(
      children: [
        // Search + sync bar
        Container(
          color: AppColors.background,
          padding:
              EdgeInsets.fromLTRB(desktop ? 18 : 14, 16, desktop ? 18 : 14, 0),
          child: Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _search,
                  focusNode: _searchFocus,
                  autofocus: true,
                  textInputAction: TextInputAction.search,
                  onChanged: _onSearchChanged,
                  onSubmitted: (_) => _processBarcode(forceLookup: true),
                  decoration: InputDecoration(
                    prefixIcon: const Icon(Icons.barcode_reader,
                        color: AppColors.textDark, size: 20),
                    hintText: 'Search products, SKU, barcode…',
                    hintStyle: const TextStyle(
                        color: AppColors.textMuted, fontSize: 14),
                    filled: true,
                    fillColor: Colors.white,
                    contentPadding: const EdgeInsets.symmetric(
                        vertical: 12, horizontal: 16),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide.none,
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(color: Color(0xFFE0E7EF)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: const BorderSide(
                          color: AppColors.primaryBlue, width: 2),
                    ),
                    suffixIcon: _search.text.isNotEmpty
                        ? IconButton(
                            icon: const Icon(Icons.clear_rounded, size: 18),
                            onPressed: () {
                              _barcodeDebounce?.cancel();
                              _search.clear();
                              setState(() {});
                              _searchFocus.requestFocus();
                            },
                          )
                        : null,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Tooltip(
                message: 'Sync products from server',
                child: InkWell(
                  borderRadius: BorderRadius.circular(8),
                  onTap: _loading ? null : () => _loadProducts(),
                  child: Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: AppColors.primaryBlue,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: _loading
                        ? const Padding(
                            padding: EdgeInsets.all(12),
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white),
                          )
                        : const Icon(Icons.cloud_sync_rounded,
                            color: Colors.white, size: 20),
                  ),
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Notice banner
        if (_notice != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
            child: _NoticeBanner(message: _notice!),
          ),

        // Product grid
        Expanded(
          child: searched.isEmpty
              ? Center(
                  child: EmptyState(
                    _search.text.isNotEmpty
                        ? 'No products match "${_search.text}"'
                        : 'No products in this category.',
                    Icons.inventory_2_outlined,
                  ),
                )
              : RefreshIndicator(
                  onRefresh: () => _loadProducts(),
                  child: GridView.builder(
                    padding: EdgeInsets.fromLTRB(
                        desktop ? 18 : 14, 0, desktop ? 18 : 14, 14),
                    gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                      maxCrossAxisExtent: desktop ? 168 : 200,
                      childAspectRatio: desktop ? 0.88 : 0.78,
                      crossAxisSpacing: 10,
                      mainAxisSpacing: 10,
                    ),
                    itemCount: searched.length,
                    itemBuilder: (context, index) {
                      final product = searched[index];
                      return _ProductTile(
                        product: product,
                        currency: provider.currency,
                        onTap: () => _addProduct(product),
                      );
                    },
                  ),
                ),
        ),
      ],
    );
  }

  // ─── Cart Pane ────────────────────────────────────────────────────────────

  Widget _cartPane(AppProvider provider, {bool compact = false}) {
    final total = provider.cartTotal();
    final itemCount =
        provider.cart.fold<int>(0, (sum, item) => sum + item.quantity.toInt());

    if (compact) {
      return _compactCartBar(provider, total, itemCount);
    }

    return Container(
      color: Colors.white,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.max,
        children: [
          // Cart header
          Container(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
            decoration: const BoxDecoration(
              border: Border(bottom: BorderSide(color: Color(0xFFEDF2F7))),
            ),
            child: Row(
              children: [
                const Icon(Icons.shopping_cart_rounded,
                    color: AppColors.primaryBlue, size: 20),
                const SizedBox(width: 8),
                const Text('Current Sale',
                    style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w900,
                        color: AppColors.textDark)),
                const SizedBox(width: 6),
                if (itemCount > 0)
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.primaryBlue,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text('$itemCount',
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.w700)),
                  ),
                const Spacer(),
                // Currency toggle
                Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFFF0F4F8),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: ['USD', 'ZWG'].map((c) {
                      final selected = provider.currency == c;
                      return GestureDetector(
                        onTap: () => provider.setCurrency(c),
                        child: AnimatedContainer(
                          duration: const Duration(milliseconds: 180),
                          padding: const EdgeInsets.symmetric(
                              horizontal: 12, vertical: 6),
                          decoration: BoxDecoration(
                            color: selected
                                ? AppColors.primaryBlue
                                : Colors.transparent,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(c,
                              style: TextStyle(
                                color: selected
                                    ? Colors.white
                                    : AppColors.textMuted,
                                fontWeight: FontWeight.w700,
                                fontSize: 12,
                              )),
                        ),
                      );
                    }).toList(),
                  ),
                ),
                if (provider.cart.isNotEmpty) ...[
                  const SizedBox(width: 8),
                  IconButton(
                    icon: const Icon(Icons.delete_sweep_rounded,
                        color: AppColors.errorRed, size: 20),
                    tooltip: 'Clear cart',
                    onPressed: () => _confirmClearCart(provider),
                    padding: EdgeInsets.zero,
                    constraints:
                        const BoxConstraints(minWidth: 32, minHeight: 32),
                  ),
                ],
              ],
            ),
          ),

          InkWell(
            onTap: () => _showCustomerDialog(provider),
            child: Container(
              margin: const EdgeInsets.fromLTRB(14, 12, 14, 6),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: const Color(0xFFF8FAFC),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFFDDE5EF)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.person_outline_rounded,
                      size: 20, color: AppColors.textMuted),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      provider.attachedCustomer?.fullName ?? 'Walk-in customer',
                      style: const TextStyle(
                          fontSize: 13, fontWeight: FontWeight.w700),
                    ),
                  ),
                  if (provider.attachedCustomer != null)
                    IconButton(
                      tooltip: 'Remove customer',
                      onPressed: provider.detachCustomer,
                      icon: const Icon(Icons.close_rounded, size: 17),
                      constraints:
                          const BoxConstraints(minWidth: 28, minHeight: 28),
                      padding: EdgeInsets.zero,
                    )
                  else
                    const Icon(Icons.add_rounded,
                        size: 19, color: AppColors.primaryBlue),
                ],
              ),
            ),
          ),

          // Cart items
          Flexible(
            child: provider.cart.isEmpty
                ? const Padding(
                    padding: EdgeInsets.symmetric(vertical: 28, horizontal: 16),
                    child: Center(
                      child: Text(
                        'Tap a product to add it to the sale.',
                        textAlign: TextAlign.center,
                        style:
                            TextStyle(color: AppColors.textMuted, fontSize: 13),
                      ),
                    ),
                  )
                : ListView.separated(
                    shrinkWrap: compact,
                    padding:
                        const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                    itemCount: provider.cart.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final item = provider.cart[index];
                      return _CartItem(
                        item: item,
                        onDecrement: () {
                          if (!provider.updateQty(index, item.quantity - 1)) {
                            _showStockNotice(provider, item.product);
                          }
                        },
                        onIncrement: () {
                          if (!provider.updateQty(index, item.quantity + 1)) {
                            _showStockNotice(provider, item.product);
                          }
                        },
                        onRemove: () => provider.updateQty(index, 0),
                      );
                    },
                  ),
          ),

          // Total + checkout
          Container(
            padding: const EdgeInsets.all(16),
            decoration: const BoxDecoration(
              border: Border(top: BorderSide(color: Color(0xFFEDF2F7))),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: _CartActionButton(
                        icon: Icons.sell_outlined,
                        label: 'Discount',
                        onTap: provider.cart.isEmpty
                            ? null
                            : () => _showDiscountDialog(provider),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _CartActionButton(
                        icon: Icons.handshake_outlined,
                        label: 'Borrower',
                        onTap: provider.cart.isEmpty
                            ? null
                            : () => _openPayment(provider,
                                initialMethod: 'STORE_CREDIT'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: _CartActionButton(
                        icon: Icons.schedule_outlined,
                        label: 'Leave change',
                        onTap: provider.cart.isEmpty
                            ? null
                            : () => _openPayment(provider),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    const Text('Subtotal',
                        style: TextStyle(color: AppColors.textMuted)),
                    const Spacer(),
                    Text(
                        formatCurrency(
                            provider.cartSubtotal(), provider.currency),
                        style: const TextStyle(color: AppColors.textMuted)),
                  ],
                ),
                const SizedBox(height: 4),
                Row(
                  children: [
                    const Text('TOTAL',
                        style: TextStyle(
                            fontWeight: FontWeight.w900,
                            fontSize: 15,
                            color: AppColors.textDark)),
                    const Spacer(),
                    Text(
                      formatCurrency(total, provider.currency),
                      style: const TextStyle(
                          fontSize: 26,
                          fontWeight: FontWeight.w900,
                          color: AppColors.primaryBlue),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: provider.cart.isEmpty
                        ? null
                        : () => _openPayment(provider),
                    icon: const Icon(Icons.payments_rounded, size: 20),
                    label: Text(
                      'Pay  ${provider.cart.isEmpty ? '' : formatCurrency(total, provider.currency)}',
                      style: const TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w800),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.accentYellow,
                      foregroundColor: AppColors.primaryBlue,
                      disabledBackgroundColor: const Color(0xFFE8ECF0),
                      disabledForegroundColor: AppColors.textMuted,
                      elevation: 0,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8)),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: provider.cart.isEmpty
                            ? null
                            : () => _openPayment(provider),
                        icon: const Icon(Icons.payments_outlined, size: 17),
                        label: const Text('Cash'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: provider.cart.isEmpty
                            ? null
                            : () => _openPayment(provider,
                                initialMethod: 'ECOCASH'),
                        icon: const Icon(Icons.phone_android_rounded, size: 17),
                        label: const Text('EcoCash'),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: provider.cart.isEmpty
                            ? null
                            : () =>
                                _openPayment(provider, initialMethod: 'CARD'),
                        icon: const Icon(Icons.credit_card_rounded, size: 17),
                        label: const Text('Card'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _compactCartBar(AppProvider provider, double total, int itemCount) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 12),
      decoration: const BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
              color: Color(0x16000000), blurRadius: 14, offset: Offset(0, -4)),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: [
            Expanded(
              child: InkWell(
                onTap: provider.cart.isEmpty
                    ? null
                    : () => _showCartSheet(provider),
                borderRadius: BorderRadius.circular(12),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('$itemCount item(s)',
                          style: const TextStyle(
                              color: AppColors.textMuted,
                              fontSize: 12,
                              fontWeight: FontWeight.w700)),
                      Text(formatCurrency(total, provider.currency),
                          style: const TextStyle(
                              color: AppColors.primaryBlue,
                              fontSize: 22,
                              fontWeight: FontWeight.w900)),
                    ],
                  ),
                ),
              ),
            ),
            IconButton(
              tooltip: 'View sale',
              onPressed:
                  provider.cart.isEmpty ? null : () => _showCartSheet(provider),
              icon: const Icon(Icons.receipt_long_rounded),
            ),
            const SizedBox(width: 8),
            ElevatedButton.icon(
              onPressed:
                  provider.cart.isEmpty ? null : () => _openPayment(provider),
              icon: const Icon(Icons.payments_rounded, size: 18),
              label: const Text('Charge',
                  style: TextStyle(fontWeight: FontWeight.w900)),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.accentYellow,
                foregroundColor: AppColors.primaryBlue,
                disabledBackgroundColor: const Color(0xFFE8ECF0),
                disabledForegroundColor: AppColors.textMuted,
                elevation: 0,
                padding:
                    const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ─── Actions ──────────────────────────────────────────────────────────────

  void _onSearchChanged(String value) {
    setState(() {});
    _barcodeDebounce?.cancel();
    final barcode = _cleanBarcode(value);
    if (!_looksLikeBarcode(barcode)) return;

    _barcodeDebounce = Timer(
      const Duration(milliseconds: 180),
      () => _processBarcode(),
    );
  }

  String _cleanBarcode(String value) =>
      value.replaceAll(RegExp(r'[\r\n\t]'), '').trim();

  bool _looksLikeBarcode(String value) {
    if (value.length < 4 || value.contains(' ')) return false;
    return RegExp(r'^[A-Za-z0-9._/-]+$').hasMatch(value);
  }

  Future<void> _processBarcode({bool forceLookup = false}) async {
    if (_barcodeLookupRunning || !mounted) return;
    _barcodeDebounce?.cancel();

    final barcode = _cleanBarcode(_search.text);
    if (!_looksLikeBarcode(barcode)) return;

    final now = DateTime.now();
    if (_lastScannedBarcode == barcode &&
        _lastScanTime != null &&
        now.difference(_lastScanTime!) < const Duration(milliseconds: 300)) {
      return;
    }

    final provider = context.read<AppProvider>();
    Product? product;
    for (final candidate in provider.products) {
      if ((candidate.barcode ?? '').trim().toLowerCase() ==
          barcode.toLowerCase()) {
        product = candidate;
        break;
      }
    }

    // Automatic scanner input uses the downloaded/offline product list.
    // Enter can force a server/cache lookup when the product is absent.
    if (product == null && !forceLookup) return;

    if (product == null) {
      _barcodeLookupRunning = true;
      try {
        product = await _api.getProductByBarcode(barcode);
      } finally {
        _barcodeLookupRunning = false;
      }
      if (!mounted) return;
    }

    if (product == null) {
      if (forceLookup) {
        setState(() => _notice = 'No product found for barcode $barcode.');
        _clearNoticeLater();
      }
      return;
    }

    final scannedProduct = product;
    _lastScannedBarcode = barcode;
    _lastScanTime = now;
    _addProduct(scannedProduct);
    if (!mounted) return;

    _search.clear();
    setState(() {
      _notice ??= '${scannedProduct.name} added to cart.';
    });
    _clearNoticeLater();
    _searchFocus.requestFocus();
  }

  void _clearNoticeLater() {
    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) setState(() => _notice = null);
    });
  }

  void _addProduct(Product product) {
    final provider = context.read<AppProvider>();
    final available = provider.availableStockFor(product);
    final inCart = provider.cartQuantityFor(product);
    if (available <= 0) {
      setState(() => _notice = '${product.name} is out of stock.');
      Future.delayed(const Duration(seconds: 3),
          () => mounted ? setState(() => _notice = null) : null);
      return;
    }
    if (inCart + 1 > available || !provider.addToCart(product)) {
      setState(() => _notice =
          '${product.name} has only ${available.toStringAsFixed(0)} left.');
      Future.delayed(const Duration(seconds: 3),
          () => mounted ? setState(() => _notice = null) : null);
      return;
    }
    setState(() => _notice = null);
  }

  void _showStockNotice(AppProvider provider, Product product) {
    final available = provider.availableStockFor(product);
    final message = available <= 0
        ? '${product.name} is out of stock.'
        : '${product.name} has only ${available.toStringAsFixed(0)} left.';
    setState(() => _notice = message);
    Future.delayed(const Duration(seconds: 3),
        () => mounted ? setState(() => _notice = null) : null);
  }

  Future<void> _openPayment(AppProvider provider,
      {String initialMethod = 'CASH'}) async {
    final stockMessage = provider.validateCartStock();
    if (stockMessage != null) {
      setState(() => _notice = stockMessage);
      Future.delayed(const Duration(seconds: 4),
          () => mounted ? setState(() => _notice = null) : null);
      return;
    }
    var session = provider.activeSession;
    setState(() {
      _loading = true;
      _notice = null;
    });
    try {
      final latest = await _api.getActiveSession();
      if (latest != null) {
        provider.setSession(latest);
        provider.setOnline(true);
        session = latest;
      } else if (session != null) {
        provider.setOnline(false);
      }
    } catch (_) {
      provider.setOnline(false);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
    if (!mounted) return;
    await _refreshProductsForStock(provider);
    if (!mounted) return;
    final latestStockMessage = provider.validateCartStock();
    if (latestStockMessage != null) {
      setState(() => _notice = latestStockMessage);
      return;
    }
    if (session == null) {
      setState(() => _notice = 'Open a shift before confirming sales.');
      return;
    }
    final result = await _presentPayment(
      provider: provider,
      session: session,
      initialMethod: initialMethod,
    );
    if (!mounted) return;
    if (result != null) {
      provider.clearCart();
      await _loadProducts();
      if (!mounted) return;
      setState(() => _notice = result);
      Future.delayed(const Duration(seconds: 5),
          () => mounted ? setState(() => _notice = null) : null);
    }
  }

  Future<String?> _presentPayment({
    required AppProvider provider,
    required CashSession session,
    required String initialMethod,
  }) async {
    final desktop = MediaQuery.sizeOf(context).width >= 1180;
    final payment = PaymentScreen(
      provider: provider,
      session: session,
      api: _api,
      embedded: desktop,
      initialMethod: initialMethod,
    );
    if (desktop) {
      return showDialog<String>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) {
          final viewport = MediaQuery.sizeOf(dialogContext);
          return Dialog(
            insetPadding: const EdgeInsets.all(12),
            clipBehavior: Clip.antiAlias,
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            child: SizedBox(
              width: math.min(1120, viewport.width - 24),
              height: math.min(720, viewport.height - 24),
              child: payment,
            ),
          );
        },
      );
    }
    return Navigator.of(context).push<String>(
      MaterialPageRoute(builder: (_) => payment),
    );
  }

  Future<void> _showCustomerDialog(AppProvider provider) async {
    final controller = TextEditingController();
    Customer? match;
    String? error;
    var searching = false;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          title: const Text('Attach customer',
              style: TextStyle(fontWeight: FontWeight.w900)),
          content: SizedBox(
            width: 440,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: controller,
                  autofocus: true,
                  decoration: const InputDecoration(
                    labelText: 'Name or phone number',
                    prefixIcon: Icon(Icons.person_search_rounded),
                  ),
                  onSubmitted: (_) async {
                    if (controller.text.trim().isEmpty) return;
                    setDialogState(() {
                      searching = true;
                      error = null;
                    });
                    try {
                      final result = await _api.searchCustomer(controller.text);
                      setDialogState(() {
                        match = result;
                        searching = false;
                        if (result == null) error = 'No customer found.';
                      });
                    } catch (_) {
                      setDialogState(() {
                        searching = false;
                        error = 'Customer search needs an internet connection.';
                      });
                    }
                  },
                ),
                if (searching)
                  const Padding(
                    padding: EdgeInsets.only(top: 18),
                    child: LinearProgressIndicator(),
                  ),
                if (error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 16),
                    child: Text(error!,
                        style: const TextStyle(color: AppColors.errorRed)),
                  ),
                if (match != null)
                  ListTile(
                    contentPadding: const EdgeInsets.only(top: 12),
                    leading:
                        const CircleAvatar(child: Icon(Icons.person_rounded)),
                    title: Text(match!.fullName,
                        style: const TextStyle(fontWeight: FontWeight.w800)),
                    subtitle: Text(match!.phone ?? ''),
                  ),
              ],
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(dialogContext),
                child: const Text('Cancel')),
            FilledButton(
              onPressed: match == null
                  ? null
                  : () {
                      provider.attachCustomer(match!);
                      Navigator.pop(dialogContext);
                    },
              child: const Text('Attach'),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
  }

  Future<void> _showDiscountDialog(AppProvider provider) async {
    final controllers = provider.cart
        .map((item) =>
            TextEditingController(text: item.discountAmount.toStringAsFixed(2)))
        .toList();
    final applied = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        title: const Text('Sale discounts',
            style: TextStyle(fontWeight: FontWeight.w900)),
        content: SizedBox(
          width: 520,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(dialogContext).height * .55,
            ),
            child: ListView.separated(
              shrinkWrap: true,
              itemCount: provider.cart.length,
              separatorBuilder: (_, __) => const Divider(height: 20),
              itemBuilder: (_, index) {
                final item = provider.cart[index];
                return Row(
                  children: [
                    Expanded(
                      child: Text(item.product.name,
                          style: const TextStyle(fontWeight: FontWeight.w700)),
                    ),
                    const SizedBox(width: 16),
                    SizedBox(
                      width: 140,
                      child: TextField(
                        controller: controllers[index],
                        keyboardType: const TextInputType.numberWithOptions(
                            decimal: true),
                        decoration: InputDecoration(
                            labelText: 'Discount ${provider.currency}'),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Apply discounts')),
        ],
      ),
    );
    if (applied == true) {
      for (var i = 0; i < controllers.length; i++) {
        final line = provider.cart[i].quantity * provider.cart[i].unitPrice;
        final value = double.tryParse(controllers[i].text) ?? 0;
        provider.updateItemDiscount(i, value.clamp(0, line).toDouble());
      }
    }
    for (final controller in controllers) {
      controller.dispose();
    }
  }

  Future<void> _refreshProductsForStock(AppProvider provider) async {
    try {
      final products = await _api.getProducts();
      if (!mounted) return;
      provider.setProducts(products);
      provider.setOnline(true);
    } catch (_) {
      provider.setOnline(false);
    }
  }

  void _showCartSheet(AppProvider provider) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (_) => StatefulBuilder(
        builder: (context, setSheetState) => SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 18),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    const Text('Current Sale',
                        style: TextStyle(
                            fontSize: 18, fontWeight: FontWeight.w900)),
                    const Spacer(),
                    Text(
                        formatCurrency(provider.cartTotal(), provider.currency),
                        style: const TextStyle(
                            color: AppColors.primaryBlue,
                            fontWeight: FontWeight.w900)),
                  ],
                ),
                const SizedBox(height: 10),
                ConstrainedBox(
                  constraints: BoxConstraints(
                      maxHeight: MediaQuery.of(context).size.height * 0.52),
                  child: ListView.separated(
                    shrinkWrap: true,
                    itemCount: provider.cart.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final item = provider.cart[index];
                      return _CartItem(
                        item: item,
                        onDecrement: () {
                          if (!provider.updateQty(index, item.quantity - 1)) {
                            _showStockNotice(provider, item.product);
                          }
                          setSheetState(() {});
                        },
                        onIncrement: () {
                          if (!provider.updateQty(index, item.quantity + 1)) {
                            _showStockNotice(provider, item.product);
                          }
                          setSheetState(() {});
                        },
                        onRemove: () {
                          provider.updateQty(index, 0);
                          setSheetState(() {});
                        },
                      );
                    },
                  ),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () {
                          Navigator.pop(context);
                          _confirmClearCart(provider);
                        },
                        icon: const Icon(Icons.delete_sweep_rounded),
                        label: const Text('Clear'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: provider.cart.isEmpty
                            ? null
                            : () {
                                Navigator.pop(context);
                                _openPayment(provider);
                              },
                        icon: const Icon(Icons.payments_rounded),
                        label: const Text('Charge'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.accentYellow,
                          foregroundColor: AppColors.primaryBlue,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _confirmClearCart(AppProvider provider) {
    showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text('Clear Sale?',
            style: TextStyle(fontWeight: FontWeight.w900)),
        content:
            const Text('This will remove all items from the current sale.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(ctx);
              provider.clearCart();
            },
            style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.errorRed,
                foregroundColor: Colors.white),
            child: const Text('Clear'),
          ),
        ],
      ),
    );
  }
}

// ─── Product Tile ──────────────────────────────────────────────────────────

class _CategoryButton extends StatelessWidget {
  const _CategoryButton({
    required this.label,
    required this.count,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final int count;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        borderRadius: BorderRadius.circular(8),
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 12),
          decoration: BoxDecoration(
            color: selected ? Colors.white : Colors.transparent,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: selected ? AppColors.primaryBlue : const Color(0xFFDDE5EF),
            ),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color:
                          selected ? AppColors.primaryBlue : AppColors.textDark,
                      fontSize: 12,
                      fontWeight: selected ? FontWeight.w800 : FontWeight.w600,
                    )),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                decoration: BoxDecoration(
                  color: selected
                      ? AppColors.primaryBlue.withValues(alpha: 0.1)
                      : const Color(0xFFEFF3F8),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Text('$count',
                    style: const TextStyle(
                        fontSize: 10, fontWeight: FontWeight.w700)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CartActionButton extends StatelessWidget {
  const _CartActionButton(
      {required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onTap,
      icon: Icon(icon, size: 16),
      label: Text(label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)),
      style: OutlinedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 11),
        side: const BorderSide(color: Color(0xFFDDE5EF)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
    );
  }
}

class _ProductTile extends StatelessWidget {
  final Product product;
  final String currency;
  final VoidCallback onTap;

  const _ProductTile({
    required this.product,
    required this.currency,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final noStock = product.quantityOnHand <= 0;
    final price = product.priceForCurrency(currency);

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(
          color: noStock
              ? AppColors.errorRed.withValues(alpha: 0.2)
              : const Color(0xFFE8EDF5),
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: noStock ? null : onTap,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Icon + stock badge
              Row(
                children: [
                  Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: noStock
                          ? AppColors.errorRed.withValues(alpha: 0.08)
                          : AppColors.primaryBlue.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(
                      Icons.inventory_2_rounded,
                      color:
                          noStock ? AppColors.errorRed : AppColors.primaryBlue,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 6, vertical: 3),
                          decoration: BoxDecoration(
                            color: noStock
                                ? AppColors.errorRed.withValues(alpha: 0.1)
                                : const Color(0xFFE8F5E9),
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            noStock
                                ? 'Out'
                                : '${product.quantityOnHand.toStringAsFixed(0)} left',
                            style: TextStyle(
                              fontSize: 9,
                              fontWeight: FontWeight.w700,
                              color: noStock
                                  ? AppColors.errorRed
                                  : const Color(0xFF2E7D32),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),

              // Name
              Text(
                product.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontWeight: FontWeight.w800,
                  fontSize: 13,
                  color: noStock ? AppColors.textMuted : AppColors.textDark,
                ),
              ),
              if (product.sku.isNotEmpty) ...[
                const SizedBox(height: 2),
                Text(
                  product.sku,
                  style: const TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10,
                      fontFamily: 'monospace'),
                ),
              ],

              const SizedBox(height: 6),

              // Price
              Text(
                formatCurrency(price, currency),
                style: TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w900,
                  color: noStock ? AppColors.textMuted : AppColors.primaryBlue,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ─── Cart Item Row ─────────────────────────────────────────────────────────

class _CartItem extends StatelessWidget {
  final dynamic item;
  final VoidCallback onDecrement;
  final VoidCallback onIncrement;
  final VoidCallback onRemove;

  const _CartItem({
    required this.item,
    required this.onDecrement,
    required this.onIncrement,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.product.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      fontWeight: FontWeight.w700, fontSize: 13),
                ),
                Text(
                  formatCurrency(item.unitPrice, item.currency),
                  style:
                      const TextStyle(color: AppColors.textMuted, fontSize: 11),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          // Qty control
          Row(
            children: [
              _QtyBtn(
                icon: Icons.remove_rounded,
                onTap: onDecrement,
                color: AppColors.errorRed,
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10),
                child: Text(
                  item.quantity.toStringAsFixed(0),
                  style: const TextStyle(
                      fontWeight: FontWeight.w900, fontSize: 15),
                ),
              ),
              _QtyBtn(
                icon: Icons.add_rounded,
                onTap: onIncrement,
                color: AppColors.primaryBlue,
              ),
            ],
          ),
          const SizedBox(width: 10),
          Text(
            formatCurrency(item.lineTotal + item.lineTax, item.currency),
            style: const TextStyle(
                fontWeight: FontWeight.w900,
                fontSize: 13,
                color: AppColors.textDark),
          ),
          const SizedBox(width: 4),
          GestureDetector(
            onTap: onRemove,
            child: const Icon(Icons.close_rounded,
                color: AppColors.textMuted, size: 16),
          ),
        ],
      ),
    );
  }
}

class _QtyBtn extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  final Color color;

  const _QtyBtn({required this.icon, required this.onTap, required this.color});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 28,
        height: 28,
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.1),
          shape: BoxShape.circle,
        ),
        child: Icon(icon, size: 16, color: color),
      ),
    );
  }
}

// ─── Notice Banner ─────────────────────────────────────────────────────────

class _NoticeBanner extends StatelessWidget {
  final String message;
  const _NoticeBanner({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF8E1),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.accentYellow),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline_rounded,
              color: AppColors.warningOrange, size: 16),
          const SizedBox(width: 8),
          Expanded(
            child: Text(message,
                style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 13,
                    color: AppColors.textDark)),
          ),
        ],
      ),
    );
  }
}
