import 'package:flutter/material.dart';

import '../../models/models.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class SalesScreen extends StatefulWidget {
  const SalesScreen({super.key});

  @override
  State<SalesScreen> createState() => _SalesScreenState();
}

class _SalesScreenState extends State<SalesScreen> {
  final ApiService _api = ApiService();
  late Future<List<Sale>> _sales;

  @override
  void initState() {
    super.initState();
    _sales = _load();
  }

  Future<List<Sale>> _load() async {
    await _api.syncOfflineSales();
    return _api.getShiftSales();
  }

  Future<void> _refreshSales() async {
    final next = _load();
    setState(() {
      _sales = next;
    });
    await next;
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<Sale>>(
      future: _sales,
      builder: (context, snapshot) {
        final sales = snapshot.data ?? const <Sale>[];
        final usd = sales
            .where((s) => s.currency == 'USD')
            .fold<double>(0, (sum, sale) => sum + sale.grandTotal);
        final zwg = sales
            .where((s) => s.currency == 'ZWG')
            .fold<double>(0, (sum, sale) => sum + sale.grandTotal);
        return RefreshIndicator(
          onRefresh: _refreshSales,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const Text('Shift Sales',
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
              const SizedBox(height: 12),
              Row(children: [
                Expanded(
                    child: _TotalCard(
                        label: 'USD Sales', value: formatCurrency(usd, 'USD'))),
                const SizedBox(width: 10),
                Expanded(
                    child: _TotalCard(
                        label: 'ZWG Sales', value: formatCurrency(zwg, 'ZWG'))),
              ]),
              const SizedBox(height: 12),
              if (snapshot.connectionState == ConnectionState.waiting)
                const Center(child: CircularProgressIndicator())
              else if (sales.isEmpty)
                const Card(
                    child: Padding(
                        padding: EdgeInsets.all(16),
                        child: Text('No sales recorded in this shift yet.')))
              else
                ...sales.map((sale) => Card(
                      child: ListTile(
                        leading: const Icon(Icons.receipt_long,
                            color: AppColors.primaryBlue),
                        title: Text(sale.receiptNumber),
                        subtitle:
                            Text('${sale.status} • ${timeAgo(sale.createdAt)}'),
                        trailing: Text(
                            formatCurrency(sale.grandTotal, sale.currency),
                            style:
                                const TextStyle(fontWeight: FontWeight.w900)),
                      ),
                    )),
            ],
          ),
        );
      },
    );
  }
}

class _TotalCard extends StatelessWidget {
  const _TotalCard({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(color: AppColors.textMuted)),
          const SizedBox(height: 6),
          Text(value,
              style:
                  const TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
        ]),
      ),
    );
  }
}
