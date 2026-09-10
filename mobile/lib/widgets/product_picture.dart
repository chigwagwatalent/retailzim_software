import 'package:flutter/material.dart';
import '../models/models.dart';

/// Show the shop's real product image; missing/offline images stay usable.
class ProductPicture extends StatelessWidget {
  const ProductPicture({super.key, required this.product});
  final Product product;

  @override
  Widget build(BuildContext context) {
    final url = product.imageUrl;
    final fallback = Container(
      decoration: BoxDecoration(
          color: const Color(0xFFEAF4FF),
          borderRadius: BorderRadius.circular(10)),
      alignment: Alignment.center,
      child: const Icon(Icons.inventory_2_outlined,
          size: 42, color: Color(0xFF6A9DCB)),
    );
    if (url == null || !(Uri.tryParse(url)?.hasAuthority ?? false)) {
      return fallback;
    }
    return Image.network(url,
        fit: BoxFit.contain,
        errorBuilder: (_, __, ___) => fallback,
        loadingBuilder: (_, child, progress) =>
            progress == null ? child : fallback);
  }
}
