import 'package:flutter_test/flutter_test.dart';
import 'package:retailzw_mobile/models/models.dart';
import 'package:retailzw_mobile/providers/app_provider.dart';

void main() {
  group('AppProvider cart operations', () {
    test('adds products, switches currency, and clears sale cleanly', () {
      final provider = AppProvider();
      final product = Product(
        id: 1,
        name: 'Mazoe Orange Crush 2L',
        sku: 'GROC-MAZOE-2L',
        sellingPriceUsd: 3.50,
        sellingPriceZwg: 52.50,
        costPriceUsd: 2.10,
        taxRate: 15,
        isTaxable: true,
        quantityOnHand: 5,
      );

      provider.addToCart(product);
      provider.addToCart(product);

      expect(provider.cartItemCount, 2);
      expect(provider.cartSubtotal(), 7.00);
      expect(provider.cartTax(), 1.05);
      expect(provider.cartTotal(), 8.05);

      provider.setCurrency('ZWG');

      expect(provider.currency, 'ZWG');
      expect(provider.cart.first.unitPrice, 52.50);
      expect(provider.cartSubtotal(), 105.00);

      provider.clearCart();

      expect(provider.cart, isEmpty);
      expect(provider.cartTotal(), 0);
    });

    test('blocks out-of-stock products and caps cart quantity at stock on hand',
        () {
      final provider = AppProvider();
      final emptyProduct = Product(
        id: 1,
        name: 'Empty Shelf Item',
        sku: 'EMPTY',
        sellingPriceUsd: 1.00,
        sellingPriceZwg: 15.00,
        costPriceUsd: 0.50,
        taxRate: 0,
        isTaxable: false,
        quantityOnHand: 0,
      );
      final limitedProduct = Product(
        id: 2,
        name: 'Limited Item',
        sku: 'LIMITED',
        sellingPriceUsd: 2.00,
        sellingPriceZwg: 30.00,
        costPriceUsd: 1.00,
        taxRate: 0,
        isTaxable: false,
        quantityOnHand: 2,
      );

      provider.setProducts([emptyProduct, limitedProduct]);

      expect(provider.addToCart(emptyProduct), isFalse);
      expect(provider.cart, isEmpty);

      expect(provider.addToCart(limitedProduct), isTrue);
      expect(provider.addToCart(limitedProduct), isTrue);
      expect(provider.addToCart(limitedProduct), isFalse);
      expect(provider.cart.first.quantity, 2);
      expect(provider.updateQty(0, 3), isFalse);
      expect(provider.cart.first.quantity, 2);
      expect(provider.validateCartStock(), isNull);
    });
  });
}
