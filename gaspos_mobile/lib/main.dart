import 'dart:async';
import 'dart:io';

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
  const GasPosApp({
    super.key,
    this.appState,
    this.minimumSplashDuration = const Duration(milliseconds: 700),
  });

  final GasPosState? appState;
  final Duration minimumSplashDuration;

  @override
  State<GasPosApp> createState() => _GasPosAppState();
}

class _GasPosAppState extends State<GasPosApp> {
  late final GasPosState state;
  bool showingSplash = true;

  @override
  void initState() {
    super.initState();
    state = widget.appState ?? GasPosState();
    _restore();
  }

  Future<void> _restore() async {
    try {
      await Future.wait([
        state.restore(),
        Future<void>.delayed(widget.minimumSplashDuration),
      ]);
    } finally {
      if (mounted) setState(() => showingSplash = false);
    }
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
            cardTheme: const CardThemeData(
                color: Colors.white,
                elevation: 0,
                margin: EdgeInsets.zero,
                shape: RoundedRectangleBorder(
                    side: BorderSide(color: Color(0xFFD9E3F1)),
                    borderRadius: BorderRadius.all(Radius.circular(14)))),
          ),
          home: showingSplash
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
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: Colors.white,
        body: SafeArea(
          child: Semantics(
            label: 'GasPOS RetailZW is starting',
            child: Stack(
              fit: StackFit.expand,
              children: [
                Center(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 34),
                    child: Image.asset(
                      'assets/images/retail_zim_logo.png',
                      width: 310,
                      fit: BoxFit.contain,
                      filterQuality: FilterQuality.high,
                    ),
                  ),
                ),
                const Positioned(
                  left: 24,
                  right: 24,
                  bottom: 28,
                  child: Text(
                    'Powered by CN TECH',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Color(0xFF526985),
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.state});
  final GasPosState state;
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final formKey = GlobalKey<FormState>();
  final username = TextEditingController();
  final password = TextEditingController();
  bool rememberMe = true;
  bool obscurePassword = true;
  String? loginError;

  @override
  void initState() {
    super.initState();
    loginError = widget.state.error;
  }

  Future<void> submit() async {
    FocusManager.instance.primaryFocus?.unfocus();
    if (formKey.currentState?.validate() != true) return;
    setState(() => loginError = null);
    try {
      await widget.state.login(
        username.text,
        password.text,
        rememberMe: rememberMe,
      );
    } catch (e) {
      if (mounted) setState(() => loginError = loginFailureMessage(e));
    }
  }

  void clearLoginError(String _) {
    if (loginError != null) setState(() => loginError = null);
  }

  @override
  void dispose() {
    username.dispose();
    password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: Colors.white,
        body: SafeArea(
          child: LayoutBuilder(
            builder: (context, constraints) => SingleChildScrollView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: const EdgeInsets.symmetric(horizontal: 26, vertical: 20),
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 430),
                  child: SizedBox(
                    height: constraints.maxHeight > 620
                        ? constraints.maxHeight - 40
                        : 620,
                    child: Form(
                      key: formKey,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const Spacer(flex: 2),
                          Image.asset(
                            'assets/images/retail_zim_logo.png',
                            height: 92,
                            fit: BoxFit.contain,
                            semanticLabel: 'Retail Zim',
                          ),
                          const SizedBox(height: 46),
                          if (loginError != null) ...[
                            _LoginStatus(message: loginError!, error: true),
                            const SizedBox(height: 16),
                          ],
                          TextFormField(
                            controller: username,
                            enabled: !widget.state.busy,
                            textInputAction: TextInputAction.next,
                            autofillHints: const [AutofillHints.username],
                            onChanged: clearLoginError,
                            decoration: _loginInputDecoration(
                              label: 'Cashier username',
                              icon: Icons.person_outline_rounded,
                            ),
                            validator: (value) =>
                                value == null || value.trim().isEmpty
                                    ? 'Enter your cashier username'
                                    : null,
                          ),
                          const SizedBox(height: 16),
                          TextFormField(
                            controller: password,
                            enabled: !widget.state.busy,
                            obscureText: obscurePassword,
                            textInputAction: TextInputAction.done,
                            autofillHints: const [AutofillHints.password],
                            onChanged: clearLoginError,
                            onFieldSubmitted: (_) => submit(),
                            decoration: _loginInputDecoration(
                              label: 'Password',
                              icon: Icons.lock_outline_rounded,
                              suffix: IconButton(
                                tooltip: obscurePassword
                                    ? 'Show password'
                                    : 'Hide password',
                                onPressed: () => setState(
                                    () => obscurePassword = !obscurePassword),
                                icon: Icon(
                                  obscurePassword
                                      ? Icons.visibility_outlined
                                      : Icons.visibility_off_outlined,
                                  color: const Color(0xFF567196),
                                ),
                              ),
                            ),
                            validator: (value) => value == null || value.isEmpty
                                ? 'Enter your password'
                                : null,
                          ),
                          const SizedBox(height: 12),
                          const _LoginStatus(
                            message:
                                'Use an active cashier account assigned to this gas branch.',
                          ),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Checkbox(
                                value: rememberMe,
                                onChanged: widget.state.busy
                                    ? null
                                    : (value) => setState(
                                        () => rememberMe = value ?? false),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(4),
                                ),
                              ),
                              const Text(
                                'Keep me signed in',
                                style: TextStyle(color: navy, fontSize: 14),
                              ),
                            ],
                          ),
                          const SizedBox(height: 10),
                          FilledButton(
                            onPressed: widget.state.busy ? null : submit,
                            style: FilledButton.styleFrom(
                              minimumSize: const Size.fromHeight(56),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                            ),
                            child: widget.state.busy
                                ? const SizedBox.square(
                                    dimension: 22,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2.5,
                                      color: Colors.white,
                                    ),
                                  )
                                : const Text(
                                    'Sign in',
                                    style: TextStyle(
                                      fontSize: 17,
                                      fontWeight: FontWeight.w800,
                                    ),
                                  ),
                          ),
                          const Spacer(flex: 3),
                          const Text(
                            'Powered by CN TECH',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              color: Color(0xFF567196),
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 4),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
}

InputDecoration _loginInputDecoration({
  required String label,
  required IconData icon,
  Widget? suffix,
}) =>
    InputDecoration(
      labelText: label,
      labelStyle: const TextStyle(color: Color(0xFF6F7E93)),
      prefixIcon: Icon(icon, color: const Color(0xFF385778)),
      suffixIcon: suffix,
      filled: true,
      fillColor: Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 20),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(15),
        borderSide: const BorderSide(color: Color(0xFF91BCFF), width: 1.2),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(15),
        borderSide: const BorderSide(color: blue, width: 2),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(15),
        borderSide: const BorderSide(color: Colors.redAccent),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(15),
        borderSide: const BorderSide(color: Colors.redAccent, width: 2),
      ),
    );

class _LoginStatus extends StatelessWidget {
  const _LoginStatus({required this.message, this.error = false});

  final String message;
  final bool error;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
        decoration: BoxDecoration(
          color: error ? const Color(0xFFFFF1F1) : const Color(0xFFF2F7FD),
          border: Border.all(
            color: error ? const Color(0xFFF0B7B7) : const Color(0xFFD7E5F5),
          ),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              error ? Icons.error_outline : Icons.info_outline,
              size: 19,
              color: error ? const Color(0xFFB42318) : blue,
            ),
            const SizedBox(width: 9),
            Expanded(
              child: Text(
                message,
                style: TextStyle(
                  color: error ? const Color(0xFF8A1C1C) : navy,
                  fontSize: 12.5,
                  height: 1.35,
                  fontWeight: error ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
            ),
          ],
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
      SellPage(
        state: state,
        onOpenShift: () => setState(() => index = 3),
      ),
      SalesPage(state: state),
      HeldChangePage(state: state),
      ShiftPage(state: state),
    ];
    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 72,
        backgroundColor: Colors.white,
        foregroundColor: navy,
        surfaceTintColor: Colors.white,
        elevation: 0,
        scrolledUnderElevation: 1,
        titleSpacing: 16,
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Image.asset(
            'assets/images/retail_zim_logo.png',
            height: 29,
            width: 120,
            fit: BoxFit.contain,
            alignment: Alignment.centerLeft,
            semanticLabel: 'Retail Zim',
          ),
          const SizedBox(height: 3),
          Text(
            '${state.user!.branchName}  •  ${state.user!.cashierName}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Color(0xFF526985),
              fontSize: 11,
              fontWeight: FontWeight.w600,
            ),
          ),
        ]),
        actions: [
          IconButton(
            tooltip: state.pending > 0
                ? 'Sync ${state.pending} pending sales'
                : state.online
                    ? 'Sales are synced'
                    : 'Retry connection',
            onPressed: state.busy
                ? null
                : state.online
                    ? state.syncPending
                    : state.refresh,
            icon: Badge(
              isLabelVisible: state.pending > 0,
              label: Text('${state.pending}'),
              child: Icon(
                state.online ? Icons.cloud_done_outlined : Icons.cloud_off,
                color: state.online ? const Color(0xFF159C52) : Colors.orange,
              ),
            ),
          ),
          PopupMenuButton<String>(
              tooltip: 'Cashier account: ${state.user!.cashierName}',
              onSelected: (value) {
                if (value == 'logout') state.logout();
              },
              itemBuilder: (_) => [
                    PopupMenuItem<String>(
                      enabled: false,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            state.user!.cashierName,
                            style: const TextStyle(
                              color: navy,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          if (state.user!.username.trim().isNotEmpty)
                            Text(
                              '@${state.user!.username}',
                              style: const TextStyle(
                                color: Color(0xFF526985),
                                fontSize: 12,
                              ),
                            ),
                        ],
                      ),
                    ),
                    const PopupMenuDivider(),
                    const PopupMenuItem(
                      value: 'logout',
                      child: Row(
                        children: [
                          Icon(Icons.logout_rounded, size: 19),
                          SizedBox(width: 10),
                          Text('Sign out'),
                        ],
                      ),
                    ),
                  ])
        ],
      ),
      body: Stack(children: [
        Column(children: [
          if (!state.online || state.pending > 0)
            Container(
              width: double.infinity,
              color: state.online
                  ? const Color(0xFFFFF6E5)
                  : const Color(0xFFFFEED8),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              child: Row(children: [
                Icon(
                  state.online ? Icons.cloud_upload_outlined : Icons.cloud_off,
                  size: 18,
                  color: Colors.orange.shade800,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    state.online
                        ? '${state.pending} sale${state.pending == 1 ? '' : 's'} waiting to sync'
                        : 'Offline mode - sales remain safe on this device',
                    style: TextStyle(
                      color: Colors.orange.shade900,
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ]),
            ),
          Expanded(child: dataGuard(state, pages[index])),
        ]),
        if (state.busy)
          const Positioned(
              left: 0,
              right: 0,
              top: 0,
              child: LinearProgressIndicator(minHeight: 2))
      ]),
      bottomNavigationBar: NavigationBar(
        height: 68,
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
              label: 'Change'),
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
  const SellPage({
    super.key,
    required this.state,
    required this.onOpenShift,
  });
  final GasPosState state;
  final VoidCallback onOpenShift;
  @override
  State<SellPage> createState() => _SellPageState();
}

class _SellPageState extends State<SellPage> {
  final quantity = TextEditingController();
  final selected = <int>{};
  String currency = 'USD';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || sellable.length != 1 || selected.isNotEmpty) return;
      setState(() => selected.add(sellable.single.id));
    });
  }

  @override
  void dispose() {
    quantity.dispose();
    super.dispose();
  }

  List<GasTank> get available => widget.state.data!.assignedShiftTanks;

  List<GasTank> get sellable =>
      available.where((tank) => tank.isActive && tank.hasStock).toList();

  List<String> get currencies => widget.state.data!.prices.entries
      .where((entry) => entry.value > 0)
      .map((entry) => entry.key)
      .toList();

  List<String> get displayCurrencies =>
      currencies.isEmpty ? const ['USD'] : currencies;

  String get activeCurrency =>
      displayCurrencies.contains(currency) ? currency : displayCurrencies.first;

  double get kg => double.tryParse(quantity.text) ?? 0;
  double get price => widget.state.data!.prices[activeCurrency] ?? 0;
  double get total => kg * price;

  Future<void> pay() async {
    if (selected.isEmpty || kg <= 0) {
      message(context, 'Select at least one tank and enter the LPG weight.',
          error: true);
      return;
    }
    if (price <= 0) {
      message(context, 'No active gas price is configured for this branch.',
          error: true);
      return;
    }
    final selectedStock = sellable
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
        builder: (_) => PaymentSheet(total: total, currency: activeCurrency));
    if (payment == null || !mounted) return;
    try {
      final sale = await widget.state.completeSale(
          quantityKg: kg,
          currency: activeCurrency,
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
      if (sellable.length == 1) selected.add(sellable.single.id);
      setState(() {});
    } catch (e) {
      if (mounted) message(context, cleanError(e), error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = widget.state.data!;
    if (!data.hasOpenShift) {
      return ListView(
        padding: const EdgeInsets.all(14),
        children: [
          PageTitle(
            'Open a gas shift to start selling',
            data.tanks.isEmpty
                ? 'No LPG tanks are configured for ${widget.state.user!.branchName}.'
                : '${data.tanks.length} branch tank${data.tanks.length == 1 ? '' : 's'} found. Select the tanks connected to this cashier station.',
          ),
          if (data.tanks.isEmpty)
            const Card(
              child: ListTile(
                leading: Icon(Icons.propane_tank_outlined,
                    color: Colors.orange, size: 34),
                title: Text('No tanks configured',
                    style: TextStyle(fontWeight: FontWeight.w900)),
                subtitle: Text(
                    'Ask the shop administrator to add an LPG tank and opening stock in the RetailZW web dashboard.'),
              ),
            )
          else
            ...data.tanks.map(
              (tank) => Card(
                margin: const EdgeInsets.only(bottom: 10),
                child: ListTile(
                  leading: CircleAvatar(
                    backgroundColor: tank.isActive
                        ? const Color(0xFFEAF3FF)
                        : const Color(0xFFF1F3F6),
                    child: Icon(Icons.propane_tank,
                        color: tank.isActive ? blue : Colors.blueGrey),
                  ),
                  title: Text(tank.name,
                      style: const TextStyle(fontWeight: FontWeight.w900)),
                  subtitle: Text(
                    tank.isActive
                        ? '${tank.currentKg.toStringAsFixed(3)} kg LPG available'
                        : 'Tank is inactive',
                  ),
                  trailing: Chip(
                    label: Text(tank.isActive && tank.hasStock
                        ? 'Ready'
                        : tank.isActive
                            ? 'Empty'
                            : 'Inactive'),
                  ),
                ),
              ),
            ),
          const SizedBox(height: 6),
          FilledButton.icon(
            onPressed:
                data.tanksEligibleForShift.isEmpty ? null : widget.onOpenShift,
            icon: const Icon(Icons.schedule),
            label: const Text('Select tanks and open shift'),
            style:
                FilledButton.styleFrom(minimumSize: const Size.fromHeight(54)),
          ),
        ],
      );
    }
    if (available.isEmpty) {
      return EmptyAction(
        icon: Icons.warning_amber_rounded,
        title: 'This shift has no tanks assigned',
        detail:
            'This can happen with an older shift. Close it from the Shift tab, then open a new shift and select the branch tanks.',
        action: 'Review shift',
        onTap: widget.onOpenShift,
      );
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
            final enabled = tank.isActive && tank.hasStock;
            return InkWell(
              onTap: enabled
                  ? () => setState(() => checked
                      ? selected.remove(tank.id)
                      : selected.add(tank.id))
                  : null,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                width: 142,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                    color: !enabled
                        ? const Color(0xFFF1F3F6)
                        : checked
                            ? const Color(0xFFEDF5FF)
                            : Colors.white,
                    border: Border.all(
                        color: checked ? blue : const Color(0xFFD4DEEC),
                        width: checked ? 2 : 1),
                    borderRadius: BorderRadius.circular(13)),
                child: Column(children: [
                  Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Flexible(
                            child: Text(tank.name,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                    fontWeight: FontWeight.w800))),
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
                  Text(enabled ? 'net remaining' : 'not available',
                      style:
                          const TextStyle(fontSize: 10, color: Colors.blueGrey))
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
                    segments: displayCurrencies
                        .map((code) =>
                            ButtonSegment(value: code, label: Text(code)))
                        .toList(),
                    selected: {activeCurrency},
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
                const SizedBox(height: 10),
                Wrap(
                  spacing: 7,
                  runSpacing: 7,
                  children: [1, 3, 5, 9, 19, 48]
                      .map(
                        (value) => ActionChip(
                          label: Text('$value kg'),
                          onPressed: () {
                            quantity.text = '$value';
                            setState(() {});
                          },
                        ),
                      )
                      .toList(),
                ),
                const SizedBox(height: 13),
                Row(children: [
                  Expanded(
                      child: SummaryValue(
                          label: 'Price / kg',
                          value:
                              '$activeCurrency ${price.toStringAsFixed(2)}')),
                  const SizedBox(width: 10),
                  Expanded(
                      child: SummaryValue(
                          label: 'Total',
                          value: '$activeCurrency ${total.toStringAsFixed(2)}',
                          emphasized: true))
                ])
              ]))),
      const SizedBox(height: 14),
      FilledButton.icon(
          onPressed: pay,
          icon: const Icon(Icons.arrow_forward),
          label: Text(
              'Continue to payment - $activeCurrency ${total.toStringAsFixed(2)}'),
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
    final cashReceived =
        cashDue > 0 ? double.tryParse(received.text) ?? 0 : 0.0;
    if (cashReceived < cashDue) {
      message(context, 'Cash received is below the cash payment amount.',
          error: true);
      return;
    }
    if (hold && (name.text.trim().isEmpty || phone.text.trim().isEmpty)) {
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
        child:
            Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Text('Choose payment method',
              style: TextStyle(
                  color: navy, fontSize: 23, fontWeight: FontWeight.w900)),
          Text(
              '${widget.currency} ${widget.total.toStringAsFixed(2)} total due',
              style: const TextStyle(color: Colors.blueGrey)),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
              initialValue: first,
              decoration: const InputDecoration(labelText: 'Payment method'),
              items: methods
                  .map((method) => DropdownMenuItem(
                      value: method, child: Text(method.replaceAll('_', ' '))))
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
                initialValue: second,
                decoration: InputDecoration(
                    labelText:
                        'Second method - ${widget.currency} ${amount2.toStringAsFixed(2)}'),
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
              style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(54)),
              child: const Text('Complete sale'))
        ]),
      ),
    );
  }
}

class SaleCompleteDialog extends StatelessWidget {
  const SaleCompleteDialog(
      {super.key,
      required this.sale,
      required this.change,
      required this.held});
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
                      value: '${sale['currency']} ${change.toStringAsFixed(2)}')
                ],
                if (sale['offline'] == true) ...[
                  const Divider(),
                  const Row(children: [
                    Icon(Icons.cloud_upload, color: Colors.orange),
                    SizedBox(width: 8),
                    Expanded(
                        child: Text(
                            'Saved offline - sync will run automatically.',
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
                  subtitle: Text(
                      '${sale['quantityKg']} kg - ${sale['paymentMethod']}'),
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
                          Text('${item['phone']} - ${item['referenceNumber']}',
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
      final configured =
          widget.state.data!.tanks.firstWhere((row) => row.id == tank.tankId);
      if (value < configured.tareKg || value > configured.fullGrossKg) {
        message(
            context,
            '${configured.name} gross weight must be between '
            '${configured.tareKg.toStringAsFixed(3)} kg and '
            '${configured.fullGrossKg.toStringAsFixed(3)} kg.',
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
        if (data.tanks.isEmpty)
          const Card(
            child: ListTile(
              leading: Icon(Icons.propane_tank_outlined,
                  color: Colors.orange, size: 34),
              title: Text('No branch tanks found',
                  style: TextStyle(fontWeight: FontWeight.w900)),
              subtitle: Text(
                  'The shop administrator must configure a tank and opening LPG stock before a cashier can open a shift.'),
            ),
          ),
        ...data.tanks.map(
          (tank) {
            final enabled = tank.isActive && tank.hasStock;
            return CheckboxListTile(
              value: selected.contains(tank.id),
              onChanged: enabled
                  ? (checked) => setState(() => checked == true
                      ? selected.add(tank.id)
                      : selected.remove(tank.id))
                  : null,
              secondary: CircleAvatar(
                  backgroundColor: enabled
                      ? const Color(0xFFEAF3FF)
                      : const Color(0xFFF1F3F6),
                  child: Icon(Icons.propane_tank,
                      color: enabled ? blue : Colors.blueGrey)),
              title: Text(tank.name,
                  style: const TextStyle(fontWeight: FontWeight.w800)),
              subtitle: Text(enabled
                  ? '${tank.currentKg.toStringAsFixed(3)} kg net - ${tank.grossKg.toStringAsFixed(3)} kg gross'
                  : tank.isActive
                      ? 'Empty tank - receive LPG stock before selling'
                      : 'Inactive tank'),
            );
          },
        ),
        const SizedBox(height: 12),
        FilledButton.icon(
            onPressed: selected.isEmpty ? null : open,
            icon: const Icon(Icons.play_arrow),
            label: const Text('Open shift with selected tanks'),
            style:
                FilledButton.styleFrom(minimumSize: const Size.fromHeight(54)))
      ]);
    }
    if (data.shiftTanks.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(14),
        children: [
          const PageTitle('Legacy shift needs reopening',
              'This open shift has no tanks assigned to it. Close it safely, then select tanks in a new shift.'),
          const Card(
            color: Color(0xFFFFF6E5),
            child: ListTile(
              leading: Icon(Icons.info_outline, color: Colors.orange),
              title: Text('No tank reconciliation is required',
                  style: TextStyle(fontWeight: FontWeight.w900)),
              subtitle: Text(
                  'Only shifts created before tank assignment was enabled are affected.'),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: widget.state.pending == 0 ? close : null,
            icon: const Icon(Icons.restart_alt),
            label: const Text('Close old shift and reopen'),
            style:
                FilledButton.styleFrom(minimumSize: const Size.fromHeight(56)),
          ),
        ],
      );
    }
    return ListView(padding: const EdgeInsets.all(14), children: [
      PageTitle('Close shift',
          '${widget.state.pending} pending sales - weigh every tank used'),
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
        final invalidGross =
            gross != null && (gross < tank.tareKg || gross > tank.fullGrossKg);
        final variance =
            net == null ? null : net - shiftTank.expectedClosingNetKg;
        return Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
                padding: const EdgeInsets.all(14),
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
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
                          value:
                              '${shiftTank.startingGrossKg.toStringAsFixed(3)} kg'),
                      SummaryValue(
                          label: 'Empty / tare',
                          value: '${tank.tareKg.toStringAsFixed(3)} kg'),
                      const SizedBox(height: 8),
                      TextField(
                          controller: controller,
                          keyboardType: const TextInputType.numberWithOptions(
                              decimal: true),
                          onChanged: (_) => setState(() {}),
                          decoration: InputDecoration(
                              labelText: 'Closing gross weight',
                              suffixText: 'kg',
                              helperText:
                                  '${tank.tareKg.toStringAsFixed(3)}-${tank.fullGrossKg.toStringAsFixed(3)} kg',
                              errorText: invalidGross
                                  ? "Weight is outside this tank's physical range"
                                  : null)),
                      if (net != null && !invalidGross) ...[
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
            child: Text(label, style: const TextStyle(color: Colors.blueGrey))),
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

String loginFailureMessage(Object error) {
  if (error is TimeoutException) {
    return 'The RetailZW server took too long to respond. Check your internet connection and try again.';
  }
  if (error is HandshakeException) {
    return 'A secure connection to RetailZW could not be established. Check the phone date, time, and internet connection.';
  }
  if (error is SocketException) {
    return 'The app cannot reach RetailZW. Turn on mobile data or Wi-Fi, then try again.';
  }

  final message = cleanError(error).trim();
  final normalized = message.toLowerCase();
  if (normalized.contains('clientexception') ||
      normalized.contains('connection closed') ||
      normalized.contains('failed host lookup')) {
    return 'The app cannot reach RetailZW. Turn on mobile data or Wi-Fi, then try again.';
  }
  if (normalized.contains('only cashier')) {
    return 'GasPOS accepts cashier accounts only. Ask the shop administrator to create or update your cashier account.';
  }
  if (normalized.contains('not assigned to a branch')) {
    return 'This cashier is not assigned to a branch. Ask the shop administrator to assign the cashier to the gas branch.';
  }
  if (normalized.contains('not assigned to a gas branch')) {
    return 'This cashier is assigned to a retail branch, not a gas branch. Ask the shop administrator to correct the branch assignment.';
  }
  if (normalized.contains('username exists in more than one shop')) {
    return 'This username is used by more than one shop. Ask the administrator to give this cashier a unique username.';
  }
  if (normalized.contains('shop is not active')) {
    return 'This shop account is inactive. The shop administrator must restore the subscription before cashiers can sign in.';
  }
  if (message.isEmpty) {
    return 'Login could not be completed. Check the cashier account and try again.';
  }
  return message;
}
