import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'app_state.dart';
import 'models.dart';

const navy = Color(0xFF031B3B);
const blue = Color(0xFF0868F2);
const canvas = Color(0xFFF4F7FC);

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const GasPosApp());
}

class GasPosApp extends StatefulWidget {
  const GasPosApp({super.key});
  @override
  State<GasPosApp> createState() => _GasPosAppState();
}

class _GasPosAppState extends State<GasPosApp> {
  final state = GasPosState();

  @override
  void initState() {
    super.initState();
    state.restore();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: state,
        builder: (context, _) => MaterialApp(
          title: 'GasPOS RetailZW',
          debugShowCheckedModeBanner: false,
          theme: ThemeData(
            useMaterial3: true,
            colorScheme: ColorScheme.fromSeed(
                seedColor: blue, primary: blue, surface: Colors.white),
            scaffoldBackgroundColor: canvas,
            inputDecorationTheme: const InputDecorationTheme(
              filled: true,
              fillColor: Colors.white,
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.all(Radius.circular(12))),
            ),
            cardTheme: const CardTheme(
                color: Colors.white,
                elevation: 0,
                margin: EdgeInsets.zero,
                shape: RoundedRectangleBorder(
                    side: BorderSide(color: Color(0xFFD9E3F1)),
                    borderRadius: BorderRadius.all(Radius.circular(14)))),
          ),
          home: state.busy && state.user == null
              ? const Splash()
              : state.user == null
                  ? LoginScreen(state: state)
                  : GasShell(state: state),
        ),
      );
}

class Splash extends StatelessWidget {
  const Splash({super.key});
  @override
  Widget build(BuildContext context) => const Scaffold(
        backgroundColor: navy,
        body: Center(child: CircularProgressIndicator(color: Colors.white)));
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.state});
  final GasPosState state;
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final username = TextEditingController();
  final password = TextEditingController();

  Future<void> submit() async {
    try {
      await widget.state.login(username.text, password.text);
    } catch (e) {
      if (mounted) message(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: Container(
          decoration: const BoxDecoration(
              gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [navy, Color(0xFF064A91)])),
          child: SafeArea(
            child: Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Container(
                  constraints: const BoxConstraints(maxWidth: 430),
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(24),
                      boxShadow: const [
                        BoxShadow(color: Colors.black26, blurRadius: 30)
                      ]),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const CircleAvatar(
                          radius: 30,
                          backgroundColor: blue,
                          child: Icon(Icons.local_fire_department_rounded,
                              size: 34, color: Colors.white)),
                      const SizedBox(height: 14),
                      const Text('GasPOS RetailZW',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                              color: navy,
                              fontWeight: FontWeight.w900,
                              fontSize: 25)),
                      const Text('Secure LPG cashier workspace',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: Colors.blueGrey)),
                      const SizedBox(height: 28),
                      TextField(
                          controller: username,
                          textInputAction: TextInputAction.next,
                          decoration: const InputDecoration(
                              labelText: 'Cashier username',
                              prefixIcon: Icon(Icons.person_outline))),
                      const SizedBox(height: 12),
                      TextField(
                          controller: password,
                          obscureText: true,
                          onSubmitted: (_) => submit(),
                          decoration: const InputDecoration(
                              labelText: 'Password',
                              prefixIcon: Icon(Icons.lock_outline))),
                      const SizedBox(height: 18),
                      FilledButton(
                          onPressed: widget.state.busy ? null : submit,
                          style: FilledButton.styleFrom(
                              minimumSize: const Size.fromHeight(52)),
                          child: widget.state.busy
                              ? const SizedBox.square(
                                  dimension: 22,
                                  child: CircularProgressIndicator(
                                      strokeWidth: 2, color: Colors.white))
                              : const Text('Sign in to Gas POS')),
                      const SizedBox(height: 15),
                      const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.cloud_done_outlined,
                                size: 16, color: Colors.green),
                            SizedBox(width: 6),
                            Text('Offline sales sync automatically',
                                style: TextStyle(
                                    color: Colors.blueGrey, fontSize: 12))
                          ])
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      );
}

class GasShell extends StatefulWidget {
  const GasShell({super.key, required this.state});
  final GasPosState state;
  @override
  State<GasShell> createState() => _GasShellState();
}

class _GasShellState extends State<GasShell> with WidgetsBindingObserver {
  int index = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState appState) {
    if (appState == AppLifecycleState.resumed) widget.state.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final pages = [
      SellPage(state: state),
      SalesPage(state: state),
      HeldChangePage(state: state),
      ShiftPage(state: state),
    ];
    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 76,
        backgroundColor: navy,
        foregroundColor: Colors.white,
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Row(children: [
            Icon(Icons.local_fire_department_rounded, color: Color(0xFF45A1FF)),
            SizedBox(width: 8),
            Text('GasPOS RetailZW',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w900))
          ]),
          const SizedBox(height: 3),
          Text('${state.user!.branchName}  •  ${state.user!.displayName}',
              style:
                  const TextStyle(fontSize: 11, fontWeight: FontWeight.w400)),
        ]),
        actions: [
          InkWell(
            onTap: state.online ? state.syncPending : state.refresh,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                Icon(state.online ? Icons.cloud_done : Icons.cloud_off,
                    color: state.online ? Colors.greenAccent : Colors.orangeAccent),
                Text(
                    state.pending > 0
                        ? '${state.pending} pending'
                        : state.online
                            ? 'Synced'
                            : 'Offline',
                    style: const TextStyle(fontSize: 9))
              ]),
            ),
          ),
          PopupMenuButton<String>(
              onSelected: (value) {
                if (value == 'logout') state.logout();
              },
              itemBuilder: (_) => const [
                    PopupMenuItem(value: 'logout', child: Text('Sign out'))
                  ])
        ],
      ),
      body: Stack(children: [
        dataGuard(state, pages[index]),
        if (state.busy)
          const Positioned(
              left: 0,
              right: 0,
              top: 0,
              child: LinearProgressIndicator(minHeight: 2))
      ]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: [
          const NavigationDestination(
              icon: Icon(Icons.point_of_sale_outlined),
              selectedIcon: Icon(Icons.point_of_sale),
              label: 'Sell'),
          const NavigationDestination(
              icon: Icon(Icons.receipt_long_outlined), label: 'Sales'),
          NavigationDestination(
              icon: Badge(
                  isLabelVisible: (state.data?.heldChange.length ?? 0) > 0,
                  label: Text('${state.data?.heldChange.length ?? 0}'),
                  child: const Icon(Icons.savings_outlined)),
              label: 'Held Change'),
          const NavigationDestination(
              icon: Icon(Icons.schedule_outlined), label: 'Shift'),
        ],
      ),
    );
  }
}

Widget dataGuard(GasPosState state, Widget child) {
  if (state.data != null) return child;
  return Center(
      child: Padding(
          padding: const EdgeInsets.all(30),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.cloud_off, size: 55, color: Colors.blueGrey),
            const SizedBox(height: 12),
            Text(state.error ?? 'No gas data is available on this device.',
                textAlign: TextAlign.center),
            const SizedBox(height: 15),
            FilledButton.icon(
                onPressed: state.refresh,
                icon: const Icon(Icons.refresh),
                label: const Text('Retry'))
          ])));
}

class SellPage extends StatefulWidget {
  const SellPage({super.key, required this.state});
  final GasPosState state;
  @override
  State<SellPage> createState() => _SellPageState();
}

class _SellPageState extends State<SellPage> {
  final quantity = TextEditingController();
  final selected = <int>{};
  String currency = 'USD';

  List<GasTank> get available {
    final allowed =
        widget.state.data!.shiftTanks.map((item) => item.tankId).toSet();
    return widget.state.data!.tanks
        .where((tank) => allowed.contains(tank.id))
        .toList();
  }

  double get kg => double.tryParse(quantity.text) ?? 0;
  double get price => widget.state.data!.prices[currency] ?? 0;
  double get total => kg * price;

  Future<void> pay() async {
    if (selected.isEmpty || kg <= 0) {
      message(context, 'Select at least one tank and enter the LPG weight.',
          error: true);
      return;
    }
    final selectedStock = available
        .where((t) => selected.contains(t.id))
        .fold<double>(0, (sum, t) => sum + t.currentKg);
    if (kg > selectedStock) {
      message(context,
          'The selected tanks only have ${selectedStock.toStringAsFixed(3)} kg.',
          error: true);
      return;
    }
    final payment = await showModalBottomSheet<PaymentResult>(
        context: context,
        isScrollControlled: true,
        useSafeArea: true,
        builder: (_) => PaymentSheet(total: total, currency: currency));
    if (payment == null || !mounted) return;
    try {
      final sale = await widget.state.completeSale(
          quantityKg: kg,
          currency: currency,
          tankIds: selected.toList(),
          payments: payment.payments,
          amountReceived: payment.received,
          holdChange: payment.holdChange,
          customerName: payment.customerName,
          customerPhone: payment.customerPhone);
      if (!mounted || sale == null) return;
      await showDialog(
          context: context,
          builder: (_) => SaleCompleteDialog(
              sale: sale,
              change: payment.received -
                  payment.payments
                      .where((p) => p['paymentMethod'] == 'CASH')
                      .fold<double>(
                          0, (sum, p) => sum + (p['amount'] as num).toDouble()),
              held: payment.holdChange));
      quantity.clear();
      selected.clear();
      setState(() {});
    } catch (e) {
      if (mounted) message(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = widget.state.data!;
    if (!data.hasOpenShift) {
      return EmptyAction(
          icon: Icons.schedule,
          title: 'Open a gas shift to start selling',
          detail:
              'Select the tanks connected to this selling station from the Shift tab.',
          action: 'Go to Shift',
          onTap: () => message(context, 'Select the Shift tab below.'));
    }
    return ListView(padding: const EdgeInsets.all(14), children: [
      Row(children: [
        const Expanded(
            child: Text('Tanks supplying this sale',
                style: TextStyle(fontWeight: FontWeight.w900, color: navy))),
        Chip(
            avatar: const Icon(Icons.check_circle, size: 16),
            label: Text('${data.shiftTanks.length} in shift'))
      ]),
      const SizedBox(height: 8),
      SizedBox(
        height: 144,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          itemCount: available.length,
          separatorBuilder: (_, __) => const SizedBox(width: 10),
          itemBuilder: (_, index) {
            final tank = available[index];
            final checked = selected.contains(tank.id);
            return InkWell(
              onTap: () => setState(() =>
                  checked ? selected.remove(tank.id) : selected.add(tank.id)),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                width: 142,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                    color: checked ? const Color(0xFFEDF5FF) : Colors.white,
                    border: Border.all(
                        color: checked ? blue : const Color(0xFFD4DEEC),
                        width: checked ? 2 : 1),
                    borderRadius: BorderRadius.circular(13)),
                child: Column(children: [
                  Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                    Flexible(
                        child: Text(tank.name,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontWeight: FontWeight.w800))),
                    Icon(
                        checked
                            ? Icons.check_circle
                            : Icons.radio_button_unchecked,
                        color: checked ? blue : Colors.blueGrey)
                  ]),
                  const Spacer(),
                  const Icon(Icons.propane_tank, color: blue, size: 35),
                  Text('${tank.currentKg.toStringAsFixed(3)} kg',
                      style: const TextStyle(
                          fontSize: 17, fontWeight: FontWeight.w900)),
                  const Text('net remaining',
                      style: TextStyle(fontSize: 10, color: Colors.blueGrey))
                ]),
              ),
            );
          },
        ),
      ),
      const SizedBox(height: 14),
      Card(
          child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(children: [
                Row(children: [
                  Expanded(
                      child: SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(value: 'USD', label: Text('USD')),
                      ButtonSegment(value: 'ZWG', label: Text('ZWG'))
                    ],
                    selected: {currency},
                    onSelectionChanged: (value) =>
                        setState(() => currency = value.first),
                  )),
                ]),
                const SizedBox(height: 14),
                TextField(
                    controller: quantity,
                    keyboardType:
                        const TextInputType.numberWithOptions(decimal: true),
                    onChanged: (_) => setState(() {}),
                    style: const TextStyle(
                        fontSize: 30, fontWeight: FontWeight.w900),
                    decoration: const InputDecoration(
                        labelText: 'Gas weight',
                        suffixText: 'kg',
                        prefixIcon: Icon(Icons.scale))),
                const SizedBox(height: 13),
                Row(children: [
                  Expanded(
                      child: SummaryValue(
                          label: 'Price / kg',
                          value: '$currency ${price.toStringAsFixed(2)}')),
                  const SizedBox(width: 10),
                  Expanded(
                      child: SummaryValue(
                          label: 'Total',
                          value: '$currency ${total.toStringAsFixed(2)}',
                          emphasized: true))
                ])
              ]))),
      const SizedBox(height: 14),
      FilledButton.icon(
          onPressed: pay,
          icon: const Icon(Icons.arrow_forward),
          label: Text('Continue to payment  •  $currency ${total.toStringAsFixed(2)}'),
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(56)))
    ]);
  }
}

class PaymentResult {
  const PaymentResult(this.payments, this.received,
      {this.holdChange = false, this.customerName, this.customerPhone});
  final List<Map<String, dynamic>> payments;
  final double received;
  final bool holdChange;
  final String? customerName;
  final String? customerPhone;
}

class PaymentSheet extends StatefulWidget {
  const PaymentSheet({super.key, required this.total, required this.currency});
  final double total;
  final String currency;
  @override
  State<PaymentSheet> createState() => _PaymentSheetState();
}

class _PaymentSheetState extends State<PaymentSheet> {
  static const methods = [
    'CASH',
    'ECOCASH',
    'ONEMONEY',
    'INNBUCKS',
    'CARD',
    'BANK_TRANSFER'
  ];
  String first = 'CASH';
  String second = 'ECOCASH';
  final firstAmount = TextEditingController();
  final reference = TextEditingController();
  final received = TextEditingController();
  final name = TextEditingController();
  final phone = TextEditingController();
  bool split = false;
  bool hold = false;

  @override
  void initState() {
    super.initState();
    firstAmount.text = widget.total.toStringAsFixed(2);
    received.text = widget.total.toStringAsFixed(2);
  }

  void complete() {
    final amount1 =
        split ? double.tryParse(firstAmount.text) ?? 0 : widget.total;
    final amount2 = widget.total - amount1;
    if (amount1 <= 0 || (split && amount2 <= 0)) {
      message(context, 'Enter valid split payment amounts.', error: true);
      return;
    }
    if ((first != 'CASH' || (split && second != 'CASH')) &&
        reference.text.trim().isEmpty) {
      message(context, 'Enter the mobile, card, or bank reference.',
          error: true);
      return;
    }
    final cashDue = (first == 'CASH' ? amount1 : 0) +
        (split && second == 'CASH' ? amount2 : 0);
    final cashReceived = double.tryParse(received.text) ?? 0;
    if (cashReceived < cashDue) {
      message(context, 'Cash received is below the cash payment amount.',
          error: true);
      return;
    }
    if (hold &&
        (name.text.trim().isEmpty || phone.text.trim().isEmpty)) {
      message(context, 'Customer name and phone are required to hold change.',
          error: true);
      return;
    }
    Navigator.pop(
        context,
        PaymentResult(
          [
            {
              'paymentMethod': first,
              'amount': amount1,
              if (first != 'CASH') 'reference': reference.text.trim()
            },
            if (split)
              {
                'paymentMethod': second,
                'amount': amount2,
                if (second != 'CASH') 'reference': reference.text.trim()
              }
          ],
          cashReceived,
          holdChange: hold,
          customerName: name.text.trim(),
          customerPhone: phone.text.trim(),
        ));
  }

  @override
  Widget build(BuildContext context) {
    final amount1 =
        split ? double.tryParse(firstAmount.text) ?? 0 : widget.total;
    final amount2 = widget.total - amount1;
    final cashDue = (first == 'CASH' ? amount1 : 0) +
        (split && second == 'CASH' ? amount2 : 0);
    final change = (double.tryParse(received.text) ?? 0) - cashDue;
    return Padding(
      padding: EdgeInsets.only(
          left: 18,
          right: 18,
          top: 18,
          bottom: MediaQuery.viewInsetsOf(context).bottom + 18),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('Choose payment method',
              style: TextStyle(
                  color: navy, fontSize: 23, fontWeight: FontWeight.w900)),
          Text(
              '${widget.currency} ${widget.total.toStringAsFixed(2)} total due',
              style: const TextStyle(color: Colors.blueGrey)),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
              value: first,
              decoration: const InputDecoration(labelText: 'Payment method'),
              items: methods
                  .map((method) => DropdownMenuItem(
                      value: method,
                      child: Text(method.replaceAll('_', ' '))))
                  .toList(),
              onChanged: (value) => setState(() => first = value!)),
          const SizedBox(height: 10),
          SwitchListTile(
              value: split,
              title: const Text('Split payment',
                  style: TextStyle(fontWeight: FontWeight.w800)),
              subtitle: const Text('Use two payment methods'),
              onChanged: (value) => setState(() => split = value)),
          if (split) ...[
            TextField(
                controller: firstAmount,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
                onChanged: (_) => setState(() {}),
                decoration:
                    const InputDecoration(labelText: 'First payment amount')),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
                value: second,
                decoration: InputDecoration(
                    labelText:
                        'Second method • ${widget.currency} ${amount2.toStringAsFixed(2)}'),
                items: methods
                    .where((method) => method != first)
                    .map((method) => DropdownMenuItem(
                        value: method,
                        child: Text(method.replaceAll('_', ' '))))
                    .toList(),
                onChanged: (value) => setState(() => second = value!)),
          ],
          if (first != 'CASH' || (split && second != 'CASH')) ...[
            const SizedBox(height: 10),
            TextField(
                controller: reference,
                decoration: const InputDecoration(
                    labelText: 'Payment reference / approval code'))
          ],
          if (first == 'CASH' || (split && second == 'CASH')) ...[
            const SizedBox(height: 10),
            TextField(
                controller: received,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
                onChanged: (_) => setState(() {}),
                decoration: const InputDecoration(labelText: 'Cash received')),
            const SizedBox(height: 10),
            SummaryValue(
                label: 'Change due',
                value:
                    '${widget.currency} ${change.clamp(0, double.infinity).toStringAsFixed(2)}',
                emphasized: true),
            if (change > 0)
              SwitchListTile(
                  value: hold,
                  title: const Text('Hold change for customer'),
                  onChanged: (value) => setState(() => hold = value)),
            if (hold) ...[
              TextField(
                  controller: name,
                  decoration:
                      const InputDecoration(labelText: 'Customer name')),
              const SizedBox(height: 10),
              TextField(
                  controller: phone,
                  keyboardType: TextInputType.phone,
                  decoration:
                      const InputDecoration(labelText: 'Customer phone'))
            ]
          ],
          const SizedBox(height: 16),
          FilledButton(
              onPressed: complete,
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(54)),
              child: const Text('Complete sale'))
        ]),
      ),
    );
  }
}

class SaleCompleteDialog extends StatelessWidget {
  const SaleCompleteDialog(
      {super.key, required this.sale, required this.change, required this.held});
  final Map<String, dynamic> sale;
  final double change;
  final bool held;
  @override
  Widget build(BuildContext context) => AlertDialog(
        backgroundColor: navy,
        icon: const CircleAvatar(
            radius: 30,
            backgroundColor: Color(0xFF17A856),
            child: Icon(Icons.check, size: 38, color: Colors.white)),
        title: const Text('Sale complete',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900)),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('${sale['receiptNumber'] ?? 'Gas sale'}',
              style: const TextStyle(color: Colors.white70)),
          const SizedBox(height: 15),
          Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                  color: Colors.white, borderRadius: BorderRadius.circular(12)),
              child: Column(children: [
                SummaryValue(
                    label: 'Total',
                    value:
                        '${sale['currency']} ${(sale['total'] as num).toStringAsFixed(2)}',
                    emphasized: true),
                if (change > 0) ...[
                  const Divider(),
                  SummaryValue(
                      label: held ? 'Change held' : 'Change due',
                      value:
                          '${sale['currency']} ${change.toStringAsFixed(2)}')
                ],
                if (sale['offline'] == true) ...[
                  const Divider(),
                  const Row(children: [
                    Icon(Icons.cloud_upload, color: Colors.orange),
                    SizedBox(width: 8),
                    Expanded(
                        child: Text('Saved offline — sync will run automatically.',
                            style: TextStyle(fontSize: 12)))
                  ])
                ]
              ]))
        ]),
        actions: [
          FilledButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('New sale'))
        ],
      );
}

class SalesPage extends StatelessWidget {
  const SalesPage({super.key, required this.state});
  final GasPosState state;
  @override
  Widget build(BuildContext context) {
    final sales = state.data!.sales;
    return RefreshIndicator(
      onRefresh: state.refresh,
      child: ListView(
        padding: const EdgeInsets.all(14),
        children: [
          const PageTitle('Shift sales', 'Completed and pending transactions'),
          if (sales.isEmpty)
            const EmptyAction(
                icon: Icons.receipt_long,
                title: 'No sales in this shift',
                detail: 'Completed transactions will appear here.')
          else
            ...sales.map((sale) => Card(
                margin: const EdgeInsets.only(bottom: 9),
                child: ListTile(
                  leading: CircleAvatar(
                      backgroundColor: sale['offline'] == true
                          ? Colors.orange.shade50
                          : Colors.green.shade50,
                      child: Icon(
                          sale['offline'] == true
                              ? Icons.cloud_upload
                              : Icons.check,
                          color: sale['offline'] == true
                              ? Colors.orange
                              : Colors.green)),
                  title: Text('${sale['receiptNumber'] ?? 'Gas sale'}',
                      style: const TextStyle(fontWeight: FontWeight.w800)),
                  subtitle:
                      Text('${sale['quantityKg']} kg • ${sale['paymentMethod']}'),
                  trailing: Text(
                      '${sale['currency']} ${NumberFormat('#,##0.00').format(sale['total'] ?? 0)}',
                      style: const TextStyle(
                          color: navy, fontWeight: FontWeight.w900)),
                )))
        ],
      ),
    );
  }
}

class HeldChangePage extends StatelessWidget {
  const HeldChangePage({super.key, required this.state});
  final GasPosState state;
  @override
  Widget build(BuildContext context) {
    final items = state.data!.heldChange;
    return ListView(padding: const EdgeInsets.all(14), children: [
      const PageTitle(
          'Held change', 'Confirm customer identity before paying change'),
      if (items.isEmpty)
        const EmptyAction(
            icon: Icons.savings_outlined,
            title: 'No open held change',
            detail: 'Change held from gas sales will appear here.')
      else
        ...items.map((item) => Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: Padding(
                padding: const EdgeInsets.all(14),
                child: Column(children: [
                  Row(children: [
                    const CircleAvatar(
                        backgroundColor: Color(0xFFFFF4DE),
                        child: Icon(Icons.savings, color: Colors.orange)),
                    const SizedBox(width: 10),
                    Expanded(
                        child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                          Text('${item['customerName']}',
                              style:
                                  const TextStyle(fontWeight: FontWeight.w900)),
                          Text('${item['phone']} • ${item['referenceNumber']}',
                              style: const TextStyle(
                                  color: Colors.blueGrey, fontSize: 11))
                        ])),
                    Text(
                        '${item['currency']} ${NumberFormat('#,##0.00').format(item['amount'])}',
                        style: const TextStyle(
                            color: navy,
                            fontWeight: FontWeight.w900,
                            fontSize: 16))
                  ]),
                  const SizedBox(height: 10),
                  OutlinedButton.icon(
                      onPressed: () async {
                        try {
                          await state
                              .collectHeldChange((item['id'] as num).toInt());
                          if (context.mounted) {
                            message(context, 'Held change marked as paid.');
                          }
                        } catch (e) {
                          if (context.mounted) {
                            message(context, cleanError(e), error: true);
                          }
                        }
                      },
                      icon: const Icon(Icons.verified_user_outlined),
                      label: const Text('Verify and pay customer'))
                ]))))
    ]);
  }
}

class ShiftPage extends StatefulWidget {
  const ShiftPage({super.key, required this.state});
  final GasPosState state;
  @override
  State<ShiftPage> createState() => _ShiftPageState();
}

class _ShiftPageState extends State<ShiftPage> {
  final selected = <int>{};
  final weights = <int, TextEditingController>{};

  Future<void> open() async {
    if (selected.isEmpty) {
      message(context, 'Select every tank connected to this selling station.',
          error: true);
      return;
    }
    try {
      await widget.state.openShift(selected.toList());
      if (mounted) message(context, 'Gas shift opened.');
    } catch (e) {
      if (mounted) message(context, cleanError(e), error: true);
    }
  }

  Future<void> close() async {
    final values = <int, double>{};
    for (final tank in widget.state.data!.shiftTanks) {
      final value = double.tryParse(weights[tank.tankId]?.text ?? '');
      if (value == null) {
        message(context, 'Enter the closing gross weight for every tank.',
            error: true);
        return;
      }
      values[tank.tankId] = value;
    }
    try {
      await widget.state.closeShift(values);
      if (mounted) message(context, 'Shift reconciled and closed.');
    } catch (e) {
      if (mounted) message(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = widget.state.data!;
    if (!data.hasOpenShift) {
      return ListView(padding: const EdgeInsets.all(14), children: [
        const PageTitle('Open gas shift',
            'Select all tanks connected to the selling machine'),
        ...data.tanks.where((t) => t.status == 'ACTIVE' && t.currentKg > 0).map(
              (tank) => CheckboxListTile(
                value: selected.contains(tank.id),
                onChanged: (checked) => setState(() => checked == true
                    ? selected.add(tank.id)
                    : selected.remove(tank.id)),
                secondary: const CircleAvatar(
                    backgroundColor: Color(0xFFEAF3FF),
                    child: Icon(Icons.propane_tank, color: blue)),
                title: Text(tank.name,
                    style: const TextStyle(fontWeight: FontWeight.w800)),
                subtitle: Text(
                    '${tank.currentKg.toStringAsFixed(3)} kg net • ${tank.grossKg.toStringAsFixed(3)} kg gross'),
              ),
            ),
        const SizedBox(height: 12),
        FilledButton.icon(
            onPressed: open,
            icon: const Icon(Icons.play_arrow),
            label: const Text('Open shift with selected tanks'),
            style:
                FilledButton.styleFrom(minimumSize: const Size.fromHeight(54)))
      ]);
    }
    return ListView(padding: const EdgeInsets.all(14), children: [
      PageTitle('Close shift',
          '${widget.state.pending} pending sales • weigh every tank used'),
      if (widget.state.pending > 0)
        const Card(
            color: Color(0xFFFFF6E5),
            child: ListTile(
                leading: Icon(Icons.cloud_upload, color: Colors.orange),
                title: Text('Sync required before closing'),
                subtitle:
                    Text('This protects the physical stock reconciliation.'))),
      const SizedBox(height: 10),
      ...data.shiftTanks.map((shiftTank) {
        final tank = data.tanks.firstWhere((t) => t.id == shiftTank.tankId);
        final controller =
            weights.putIfAbsent(tank.id, () => TextEditingController());
        final gross = double.tryParse(controller.text);
        final net = gross == null ? null : gross - tank.tareKg;
        final variance =
            net == null ? null : net - shiftTank.expectedClosingNetKg;
        return Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
                padding: const EdgeInsets.all(14),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  Row(children: [
                    const Icon(Icons.propane_tank, color: blue),
                    const SizedBox(width: 8),
                    Text(tank.name,
                        style: const TextStyle(
                            color: navy,
                            fontSize: 17,
                            fontWeight: FontWeight.w900))
                  ]),
                  const SizedBox(height: 10),
                  SummaryValue(
                      label: 'Starting gross',
                      value: '${shiftTank.startingGrossKg.toStringAsFixed(3)} kg'),
                  SummaryValue(
                      label: 'Empty / tare',
                      value: '${tank.tareKg.toStringAsFixed(3)} kg'),
                  const SizedBox(height: 8),
                  TextField(
                      controller: controller,
                      keyboardType:
                          const TextInputType.numberWithOptions(decimal: true),
                      onChanged: (_) => setState(() {}),
                      decoration: const InputDecoration(
                          labelText: 'Closing gross weight', suffixText: 'kg')),
                  if (net != null) ...[
                    const SizedBox(height: 8),
                    SummaryValue(
                        label: 'LPG remaining',
                        value: '${net.toStringAsFixed(3)} kg',
                        emphasized: true),
                    SummaryValue(
                        label: 'Variance',
                        value: '${variance!.toStringAsFixed(3)} kg')
                  ]
                ])));
      }),
      FilledButton.icon(
          onPressed: widget.state.pending == 0 ? close : null,
          icon: const Icon(Icons.fact_check_outlined),
          label: const Text('Review & close shift'),
          style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(56)))
    ]);
  }
}

class PageTitle extends StatelessWidget {
  const PageTitle(this.title, this.subtitle, {super.key});
  final String title;
  final String subtitle;
  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title,
            style: const TextStyle(
                color: navy, fontSize: 23, fontWeight: FontWeight.w900)),
        Text(subtitle, style: const TextStyle(color: Colors.blueGrey))
      ]));
}

class SummaryValue extends StatelessWidget {
  const SummaryValue(
      {super.key,
      required this.label,
      required this.value,
      this.emphasized = false});
  final String label;
  final String value;
  final bool emphasized;
  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(children: [
        Expanded(
            child:
                Text(label, style: const TextStyle(color: Colors.blueGrey))),
        Text(value,
            style: TextStyle(
                color: emphasized ? blue : navy,
                fontSize: emphasized ? 18 : 14,
                fontWeight: FontWeight.w900))
      ]));
}

class EmptyAction extends StatelessWidget {
  const EmptyAction(
      {super.key,
      required this.icon,
      required this.title,
      required this.detail,
      this.action,
      this.onTap});
  final IconData icon;
  final String title;
  final String detail;
  final String? action;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Center(
      child: Padding(
          padding: const EdgeInsets.all(36),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 58, color: Colors.blueGrey.shade300),
            const SizedBox(height: 12),
            Text(title,
                textAlign: TextAlign.center,
                style: const TextStyle(
                    color: navy, fontSize: 18, fontWeight: FontWeight.w900)),
            const SizedBox(height: 5),
            Text(detail,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.blueGrey)),
            if (action != null) ...[
              const SizedBox(height: 14),
              OutlinedButton(onPressed: onTap, child: Text(action!))
            ]
          ])));
}

void message(BuildContext context, String text, {bool error = false}) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      backgroundColor: error ? Colors.red.shade700 : Colors.green.shade700,
      content: Text(text)));
}

String cleanError(Object error) => error
    .toString()
    .replaceFirst('HttpException: ', '')
    .replaceFirst('FormatException: ', '')
    .replaceFirst('Bad state: ', '')
    .replaceFirst('SocketException: ', '');
