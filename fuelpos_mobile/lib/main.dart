import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'app_state.dart';
import 'models.dart';

const navy = Color(0xff071f45),
    blue = Color(0xff087cf0),
    surface = Color(0xfff3f7fc),
    green = Color(0xff0aa66a);
void main() => runApp(const FuelPosApp());

class FuelPosApp extends StatefulWidget {
  const FuelPosApp({super.key, this.state});
  final FuelPosState? state;
  @override
  State<FuelPosApp> createState() => _FuelPosAppState();
}

class _FuelPosAppState extends State<FuelPosApp> {
  late final FuelPosState state;
  @override
  void initState() {
    super.initState();
    state = widget.state ?? FuelPosState();
    state.restore();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'RetailZW FuelPOS',
      theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(
              seedColor: blue, primary: blue, surface: Colors.white),
          scaffoldBackgroundColor: surface,
          fontFamily: 'Roboto',
          inputDecorationTheme: InputDecorationTheme(
              filled: true,
              fillColor: const Color(0xfff8fbff),
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: const BorderSide(color: Color(0xffd7e4f3))),
              enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(14),
                  borderSide: const BorderSide(color: Color(0xffd7e4f3))))),
      home: AnimatedBuilder(
          animation: state,
          builder: (context, _) => state.busy && state.user == null
              ? const Splash()
              : state.user == null
                  ? LoginScreen(state: state)
                  : FuelShell(state: state)));
}

class Splash extends StatelessWidget {
  const Splash({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
          body: Center(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
        Image.asset('assets/images/retail_zim_logo.png', width: 220),
        const SizedBox(height: 28),
        const CircularProgressIndicator(),
        const SizedBox(height: 18),
        const Text('Preparing FuelPOS',
            style: TextStyle(color: navy, fontWeight: FontWeight.w800)),
        const SizedBox(height: 6),
        const Text('Powered by CN Technologies',
            style: TextStyle(color: Colors.blueGrey))
      ])));
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.state});
  final FuelPosState state;
  @override
  State<LoginScreen> createState() => _LoginState();
}

class _LoginState extends State<LoginScreen> {
  final username = TextEditingController(), password = TextEditingController();
  bool remember = true, obscure = true;
  @override
  void dispose() {
    username.dispose();
    password.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    try {
      await widget.state
          .login(username.text, password.text, rememberMe: remember);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) => Scaffold(
      body: SafeArea(
          child: Center(
              child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 440),
                      child: Card(
                          elevation: 0,
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(28),
                              side: const BorderSide(color: Color(0xffdbe7f5))),
                          child: Padding(
                              padding: const EdgeInsets.all(28),
                              child: Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.stretch,
                                  children: [
                                    Image.asset(
                                        'assets/images/retail_zim_logo.png',
                                        height: 70),
                                    const SizedBox(height: 24),
                                    const Text('Fuel cashier sign in',
                                        style: TextStyle(
                                            color: navy,
                                            fontSize: 28,
                                            fontWeight: FontWeight.w900)),
                                    const SizedBox(height: 6),
                                    const Text(
                                        'Use the cashier account created for your fuel-station branch.',
                                        style:
                                            TextStyle(color: Colors.blueGrey)),
                                    if (widget.state.error != null) ...[
                                      const SizedBox(height: 16),
                                      ErrorBox(widget.state.error!)
                                    ],
                                    const SizedBox(height: 20),
                                    TextField(
                                        controller: username,
                                        textInputAction: TextInputAction.next,
                                        decoration: const InputDecoration(
                                            labelText: 'Cashier username',
                                            prefixIcon:
                                                Icon(Icons.person_outline))),
                                    const SizedBox(height: 14),
                                    TextField(
                                        controller: password,
                                        obscureText: obscure,
                                        onSubmitted: (_) => submit(),
                                        decoration: InputDecoration(
                                            labelText: 'Password',
                                            prefixIcon:
                                                const Icon(Icons.lock_outline),
                                            suffixIcon: IconButton(
                                                onPressed: () => setState(
                                                    () => obscure = !obscure),
                                                icon: Icon(obscure
                                                    ? Icons
                                                        .visibility_off_outlined
                                                    : Icons
                                                        .visibility_outlined)))),
                                    CheckboxListTile(
                                        contentPadding: EdgeInsets.zero,
                                        title: const Text('Keep me signed in'),
                                        value: remember,
                                        onChanged: (v) => setState(
                                            () => remember = v ?? true)),
                                    const SizedBox(height: 8),
                                    FilledButton(
                                        onPressed:
                                            widget.state.busy ? null : submit,
                                        style: FilledButton.styleFrom(
                                            minimumSize:
                                                const Size.fromHeight(54),
                                            shape: RoundedRectangleBorder(
                                                borderRadius:
                                                    BorderRadius.circular(14))),
                                        child: widget.state.busy
                                            ? const SizedBox(
                                                width: 22,
                                                height: 22,
                                                child:
                                                    CircularProgressIndicator(
                                                        strokeWidth: 2,
                                                        color: Colors.white))
                                            : const Text('Sign in',
                                                style: TextStyle(
                                                    fontWeight:
                                                        FontWeight.w900))),
                                    const SizedBox(height: 18),
                                    const Text(
                                        'One RetailZW account · Package and branch access are checked automatically',
                                        textAlign: TextAlign.center,
                                        style: TextStyle(
                                            fontSize: 12,
                                            color: Colors.blueGrey))
                                  ]))))))));
}

class FuelShell extends StatefulWidget {
  const FuelShell({super.key, required this.state});
  final FuelPosState state;
  @override
  State<FuelShell> createState() => _FuelShellState();
}

class _FuelShellState extends State<FuelShell> {
  int tab = 0;
  @override
  Widget build(BuildContext context) {
    final user = widget.state.user!;
    final pages = [
      SellPage(state: widget.state),
      ShiftPage(state: widget.state),
      StationPage(state: widget.state)
    ];
    return Scaffold(
        appBar: AppBar(
            toolbarHeight: 72,
            backgroundColor: Colors.white,
            titleSpacing: 18,
            title: Row(children: [
              Image.asset('assets/images/retail_zim_logo.png', height: 42),
              const SizedBox(width: 12),
              Container(width: 1, height: 32, color: const Color(0xffdbe7f5)),
              const SizedBox(width: 12),
              Flexible(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                    Text(user.branchName,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                            color: navy,
                            fontSize: 16,
                            fontWeight: FontWeight.w900)),
                    Text(
                        '${user.displayName} · ${widget.state.online ? 'Online' : 'Offline'}',
                        style: TextStyle(
                            color: widget.state.online ? green : Colors.orange,
                            fontSize: 12))
                  ]))
            ]),
            actions: [
              if (widget.state.pending > 0)
                Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Chip(
                        avatar:
                            const Icon(Icons.cloud_upload_outlined, size: 16),
                        label: Text('${widget.state.pending} queued'))),
              IconButton(
                  onPressed: widget.state.refresh,
                  icon: const Icon(Icons.sync),
                  tooltip: 'Synchronize')
            ]),
        body: Column(children: [
          if (widget.state.error != null)
            Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                child: ErrorBox(widget.state.error!)),
          Expanded(child: IndexedStack(index: tab, children: pages))
        ]),
        bottomNavigationBar: NavigationBar(
            selectedIndex: tab,
            onDestinationSelected: (i) => setState(() => tab = i),
            destinations: const [
              NavigationDestination(
                  icon: Icon(Icons.local_gas_station_outlined),
                  selectedIcon: Icon(Icons.local_gas_station),
                  label: 'Sell fuel'),
              NavigationDestination(
                  icon: Icon(Icons.schedule_outlined),
                  selectedIcon: Icon(Icons.schedule),
                  label: 'Shift'),
              NavigationDestination(
                  icon: Icon(Icons.space_dashboard_outlined),
                  selectedIcon: Icon(Icons.space_dashboard),
                  label: 'Station')
            ]));
  }
}

class SellPage extends StatefulWidget {
  const SellPage({super.key, required this.state});
  final FuelPosState state;
  @override
  State<SellPage> createState() => _SellPageState();
}

class _SellPageState extends State<SellPage> {
  FuelNozzle? nozzle;
  String currency = 'USD', method = 'CASH';
  final litres = TextEditingController();
  @override
  void dispose() {
    litres.dispose();
    super.dispose();
  }

  double get quantity => double.tryParse(litres.text) ?? 0;
  double get price => nozzle == null
      ? 0
      : widget.state.data?.priceFor(nozzle!.grade, currency) ?? 0;
  double get total => quantity * price;
  Future<void> sell() async {
    if (nozzle == null) return;
    try {
      final result = await widget.state.completeSale(
          nozzle: nozzle!,
          litres: quantity,
          currency: currency,
          method: method,
          amount: double.parse(total.toStringAsFixed(2)));
      if (mounted) {
        litres.clear();
        setState(() {});
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            backgroundColor: green,
            content: Text(result?['offline'] == true
                ? 'Sale saved offline · will sync automatically'
                : 'Fuel sale completed')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            backgroundColor: Colors.red.shade700,
            content: Text(cleanError(e))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = widget.state.data;
    if (data == null) {
      return const EmptyState(
          icon: Icons.cloud_off,
          title: 'Station data unavailable',
          detail: 'Connect and synchronize this branch.');
    }
    if (!data.hasOpenShift) {
      return EmptyState(
          icon: Icons.schedule,
          title: 'Open a cashier shift first',
          detail:
              'Go to Shift, enter your opening floats, and start dispensing.',
          action: 'Open shift',
          onTap: () => DefaultTabController.of(context));
    }
    return ListView(padding: const EdgeInsets.all(18), children: [
      const PageTitle(
          title: 'Sell fuel',
          subtitle: 'Select the pump nozzle and enter the litres dispensed.'),
      const SizedBox(height: 16),
      Wrap(
          spacing: 12,
          runSpacing: 12,
          children: data.nozzles
              .where((n) => n.available)
              .map((n) => NozzleCard(
                  nozzle: n,
                  selected: nozzle?.id == n.id,
                  onTap: () => setState(() => nozzle = n)))
              .toList()),
      const SizedBox(height: 18),
      CardPanel(
          child: Column(children: [
        DropdownButtonFormField<FuelNozzle>(
            value: nozzle,
            decoration: const InputDecoration(labelText: 'Pump / nozzle'),
            items: data.nozzles
                .where((n) => n.available)
                .map((n) => DropdownMenuItem(
                    value: n,
                    child: Text('${n.pump} / ${n.code} · ${n.grade}')))
                .toList(),
            onChanged: (v) => setState(() => nozzle = v)),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(
              child: TextField(
                  controller: litres,
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  onChanged: (_) => setState(() {}),
                  decoration: const InputDecoration(
                      labelText: 'Litres dispensed', suffixText: 'L'))),
          const SizedBox(width: 12),
          Expanded(
              child: DropdownButtonFormField(
                  value: currency,
                  decoration: const InputDecoration(labelText: 'Currency'),
                  items: const [
                    DropdownMenuItem(value: 'USD', child: Text('USD')),
                    DropdownMenuItem(value: 'ZWG', child: Text('ZWG'))
                  ],
                  onChanged: (v) => setState(() => currency = v!)))
        ]),
        const SizedBox(height: 12),
        DropdownButtonFormField(
            value: method,
            decoration: const InputDecoration(labelText: 'Payment method'),
            items: data.paymentMethods
                .map((m) => DropdownMenuItem(
                    value: m, child: Text(m.replaceAll('_', ' '))))
                .toList(),
            onChanged: (v) => setState(() => method = v!)),
        const SizedBox(height: 18),
        Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
                color: const Color(0xffedf5ff),
                borderRadius: BorderRadius.circular(14)),
            child: Row(children: [
              Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                    Text(
                        nozzle == null
                            ? 'Select a nozzle'
                            : '${nozzle!.grade} · ${price.toStringAsFixed(4)} / L',
                        style: const TextStyle(color: Colors.blueGrey)),
                    const SizedBox(height: 4),
                    Text('$currency ${total.toStringAsFixed(2)}',
                        style: const TextStyle(
                            color: navy,
                            fontSize: 27,
                            fontWeight: FontWeight.w900))
                  ])),
              FilledButton.icon(
                  onPressed: nozzle != null &&
                          quantity > 0 &&
                          price > 0 &&
                          !widget.state.busy
                      ? sell
                      : null,
                  icon: const Icon(Icons.check_circle_outline),
                  label: const Text('Complete sale'))
            ]))
      ]))
    ]);
  }
}

class ShiftPage extends StatelessWidget {
  const ShiftPage({super.key, required this.state});
  final FuelPosState state;
  @override
  Widget build(BuildContext context) {
    final data = state.data;
    if (data == null) return const SizedBox();
    return ListView(padding: const EdgeInsets.all(18), children: [
      PageTitle(
          title: data.hasOpenShift ? 'Shift open' : 'Start shift',
          subtitle: data.hasOpenShift
              ? 'Meter and wet-stock snapshots are active.'
              : 'Enter opening floats to begin selling.'),
      const SizedBox(height: 16),
      if (!data.hasOpenShift)
        OpenShiftCard(state: state)
      else ...[
        CardPanel(
            child: Column(children: [
          SummaryRow(
              label: 'Shift number',
              value: data.shift!['shiftNumber']?.toString() ?? 'Open'),
          SummaryRow(
              label: 'Opened',
              value: data.shift!['openedAt']?.toString() ?? 'Now'),
          SummaryRow(
              label: 'Sales', value: '${data.sales.length} transactions'),
          SummaryRow(label: 'Queued offline', value: '${state.pending}'),
          const SizedBox(height: 12),
          FilledButton.tonalIcon(
              onPressed: () => showDialog(
                  context: context,
                  builder: (_) => CloseShiftDialog(state: state)),
              icon: const Icon(Icons.lock_clock_outlined),
              label: const Text('Reconcile and close shift'))
        ]))
      ],
      const SizedBox(height: 16),
      CardPanel(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Recent receipts',
            style: TextStyle(
                color: navy, fontWeight: FontWeight.w900, fontSize: 17)),
        const SizedBox(height: 10),
        if (data.sales.isEmpty)
          const Text('No sales in this shift.',
              style: TextStyle(color: Colors.blueGrey)),
        ...data.sales.take(20).map((s) => ListTile(
            contentPadding: EdgeInsets.zero,
            leading:
                const CircleAvatar(child: Icon(Icons.receipt_long_outlined)),
            title: Text(s['receiptNumber']?.toString() ?? 'Fuel sale'),
            subtitle: Text('${s['litres']} L'),
            trailing: Text('${s['currency']} ${s['amount']}',
                style: const TextStyle(fontWeight: FontWeight.w900))))
      ]))
    ]);
  }
}

class OpenShiftCard extends StatefulWidget {
  const OpenShiftCard({super.key, required this.state});
  final FuelPosState state;
  @override
  State<OpenShiftCard> createState() => _OpenShiftCardState();
}

class _OpenShiftCardState extends State<OpenShiftCard> {
  final usd = TextEditingController(text: '0.00'),
      zwg = TextEditingController(text: '0.00');
  @override
  Widget build(BuildContext context) => CardPanel(
          child: Column(children: [
        TextField(
            controller: usd,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Opening USD float')),
        const SizedBox(height: 12),
        TextField(
            controller: zwg,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Opening ZWG float')),
        const SizedBox(height: 18),
        FilledButton.icon(
            onPressed: widget.state.busy
                ? null
                : () async {
                    try {
                      await widget.state.openShift(
                          double.tryParse(usd.text) ?? 0,
                          double.tryParse(zwg.text) ?? 0);
                    } catch (e) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(cleanError(e))));
                      }
                    }
                  },
            icon: const Icon(Icons.play_arrow_rounded),
            label: const Text('Open cashier shift'))
      ]));
}

class CloseShiftDialog extends StatefulWidget {
  const CloseShiftDialog({super.key, required this.state});
  final FuelPosState state;
  @override
  State<CloseShiftDialog> createState() => _CloseShiftDialogState();
}

class _CloseShiftDialogState extends State<CloseShiftDialog> {
  final usd = TextEditingController(text: '0.00'),
      zwg = TextEditingController(text: '0.00');
  late final Map<int, TextEditingController> meters = {
    for (final n in widget.state.data!.nozzles)
      n.id: TextEditingController(text: n.meter.toStringAsFixed(3))
  };
  late final Map<int, TextEditingController> dips = {
    for (final t in widget.state.data!.tanks)
      t.id: TextEditingController(text: t.dip.toStringAsFixed(3))
  };
  late final Map<int, TextEditingController> water = {
    for (final t in widget.state.data!.tanks)
      t.id: TextEditingController(text: t.water.toStringAsFixed(3))
  };
  @override
  Widget build(BuildContext context) => AlertDialog(
          title: const Text('Reconcile shift'),
          content: SizedBox(
              width: 520,
              child: SingleChildScrollView(
                  child: Column(children: [
                Row(children: [
                  Expanded(
                      child: TextField(
                          controller: usd,
                          decoration:
                              const InputDecoration(labelText: 'Counted USD'))),
                  const SizedBox(width: 8),
                  Expanded(
                      child: TextField(
                          controller: zwg,
                          decoration:
                              const InputDecoration(labelText: 'Counted ZWG')))
                ]),
                const SizedBox(height: 16),
                const Align(
                    alignment: Alignment.centerLeft,
                    child: Text('Closing nozzle meters',
                        style: TextStyle(fontWeight: FontWeight.w900))),
                ...widget.state.data!.nozzles.map((n) => Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: TextField(
                        controller: meters[n.id],
                        decoration: InputDecoration(
                            labelText: '${n.pump} / ${n.code} · litres')))),
                const SizedBox(height: 16),
                const Align(
                    alignment: Alignment.centerLeft,
                    child: Text('Closing tank dips',
                        style: TextStyle(fontWeight: FontWeight.w900))),
                ...widget.state.data!.tanks.map((t) => Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Row(children: [
                      Expanded(
                          child: TextField(
                              controller: dips[t.id],
                              decoration: InputDecoration(
                                  labelText: '${t.code} dip L'))),
                      const SizedBox(width: 8),
                      Expanded(
                          child: TextField(
                              controller: water[t.id],
                              decoration:
                                  const InputDecoration(labelText: 'Water mm')))
                    ])))
              ]))),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () async {
                  try {
                    await widget.state.closeShift(
                        usd: double.tryParse(usd.text) ?? 0,
                        zwg: double.tryParse(zwg.text) ?? 0,
                        meters: widget.state.data!.nozzles
                            .map((n) => {
                                  'nozzleId': n.id,
                                  'meterLitres':
                                      double.tryParse(meters[n.id]!.text) ??
                                          n.meter
                                })
                            .toList(),
                        dips: widget.state.data!.tanks
                            .map((t) => {
                                  'tankId': t.id,
                                  'dipLitres':
                                      double.tryParse(dips[t.id]!.text) ??
                                          t.dip,
                                  'waterLevelMm':
                                      double.tryParse(water[t.id]!.text) ??
                                          t.water
                                })
                            .toList());
                    if (context.mounted) Navigator.pop(context);
                  } catch (e) {
                    if (context.mounted) {
                      ScaffoldMessenger.of(context)
                          .showSnackBar(SnackBar(content: Text(cleanError(e))));
                    }
                  }
                },
                child: const Text('Close shift'))
          ]);
}

class StationPage extends StatelessWidget {
  const StationPage({super.key, required this.state});
  final FuelPosState state;

  @override
  Widget build(BuildContext context) {
    final data = state.data;
    return ListView(
      padding: const EdgeInsets.all(18),
      children: [
        const PageTitle(
          title: 'Station position',
          subtitle: 'Live book stock and connected forecourt equipment.',
        ),
        const SizedBox(height: 16),
        if (data == null || data.tanks.isEmpty)
          const EmptyState(
            icon: Icons.storage_outlined,
            title: 'No tanks configured',
            detail: 'Ask your administrator to configure this fuel branch.',
          )
        else
          ...data.tanks.map(
            (tank) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: CardPanel(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          width: 12,
                          height: 38,
                          decoration: BoxDecoration(
                            color: hex(tank.colour),
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${tank.code} · ${tank.grade}',
                                style: const TextStyle(
                                  color: navy,
                                  fontWeight: FontWeight.w900,
                                  fontSize: 17,
                                ),
                              ),
                              Text(
                                '${tank.stock.toStringAsFixed(3)} / ${tank.capacity.toStringAsFixed(3)} L',
                                style: const TextStyle(color: Colors.blueGrey),
                              ),
                            ],
                          ),
                        ),
                        Text(
                          '${(tank.fill * 100).toStringAsFixed(0)}%',
                          style: const TextStyle(
                              color: navy, fontWeight: FontWeight.w900),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    LinearProgressIndicator(
                      value: tank.fill,
                      minHeight: 9,
                      borderRadius: BorderRadius.circular(99),
                      color: hex(tank.colour),
                      backgroundColor: const Color(0xffe8eff8),
                    ),
                    const SizedBox(height: 12),
                    SummaryRow(
                        label: 'Physical dip',
                        value: '${tank.dip.toStringAsFixed(3)} L'),
                    SummaryRow(
                        label: 'Ullage',
                        value:
                            '${(tank.capacity - tank.stock).toStringAsFixed(3)} L'),
                    SummaryRow(
                        label: 'Water level',
                        value: '${tank.water.toStringAsFixed(3)} mm'),
                  ],
                ),
              ),
            ),
          ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: state.logout,
          icon: const Icon(Icons.logout),
          label: const Text('Sign out'),
        ),
      ],
    );
  }
}

class NozzleCard extends StatelessWidget {
  const NozzleCard(
      {super.key,
      required this.nozzle,
      required this.selected,
      required this.onTap});
  final FuelNozzle nozzle;
  final bool selected;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(15),
      child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          width: 178,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: selected ? const Color(0xffeaf4ff) : Colors.white,
              borderRadius: BorderRadius.circular(15),
              border: Border.all(
                  color: selected ? blue : const Color(0xffdbe7f5),
                  width: selected ? 2 : 1)),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Icon(Icons.local_gas_station, color: hex(nozzle.colour)),
              const Spacer(),
              if (selected) const Icon(Icons.check_circle, color: blue)
            ]),
            const SizedBox(height: 10),
            Text('${nozzle.pump} / ${nozzle.code}',
                style:
                    const TextStyle(color: navy, fontWeight: FontWeight.w900)),
            Text(nozzle.grade, style: const TextStyle(color: Colors.blueGrey)),
            const SizedBox(height: 6),
            Text('${nozzle.meter.toStringAsFixed(3)} L',
                style: const TextStyle(fontSize: 11, color: Colors.blueGrey))
          ])));
}

class PageTitle extends StatelessWidget {
  const PageTitle({super.key, required this.title, required this.subtitle});
  final String title, subtitle;
  @override
  Widget build(BuildContext context) =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title,
            style: const TextStyle(
                color: navy, fontSize: 27, fontWeight: FontWeight.w900)),
        const SizedBox(height: 3),
        Text(subtitle, style: const TextStyle(color: Colors.blueGrey))
      ]);
}

class CardPanel extends StatelessWidget {
  const CardPanel({super.key, required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: const Color(0xffdbe7f5)),
          boxShadow: const [
            BoxShadow(
                color: Color(0x0c0a376d), blurRadius: 24, offset: Offset(0, 8))
          ]),
      child: child);
}

class SummaryRow extends StatelessWidget {
  const SummaryRow({super.key, required this.label, required this.value});
  final String label, value;
  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(children: [
        Expanded(
            child: Text(label, style: const TextStyle(color: Colors.blueGrey))),
        Text(value,
            style: const TextStyle(color: navy, fontWeight: FontWeight.w900))
      ]));
}

class ErrorBox extends StatelessWidget {
  const ErrorBox(this.message, {super.key});
  final String message;
  @override
  Widget build(BuildContext context) => Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
          color: const Color(0xffffeeee),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xffffc5c5))),
      child: Row(children: [
        const Icon(Icons.error_outline, color: Colors.red),
        const SizedBox(width: 9),
        Expanded(
            child:
                Text(message, style: const TextStyle(color: Color(0xff9d2020))))
      ]));
}

class EmptyState extends StatelessWidget {
  const EmptyState(
      {super.key,
      required this.icon,
      required this.title,
      required this.detail,
      this.action,
      this.onTap});
  final IconData icon;
  final String title, detail;
  final String? action;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Center(
      child: Padding(
          padding: const EdgeInsets.all(34),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 58, color: Colors.blueGrey.shade300),
            const SizedBox(height: 12),
            Text(title,
                textAlign: TextAlign.center,
                style: const TextStyle(
                    color: navy, fontSize: 19, fontWeight: FontWeight.w900)),
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

Color hex(String value) {
  final clean = value.replaceAll('#', '');
  return Color(int.parse('ff$clean', radix: 16));
}

String money(double value) => NumberFormat('#,##0.00').format(value);
