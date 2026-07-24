import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../models/models.dart';
import '../providers/app_provider.dart';
import '../services/api_service.dart';
import '../services/sync_scheduler.dart';
import '../widgets/common_widgets.dart';
import 'cash/cash_screen.dart';
import 'gas/gas_pos_screen.dart';
import 'more/more_screen.dart';
import 'notifications/notifications_screen.dart';
import 'pos/pos_screen.dart';
import 'sales/sales_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  int _currentIndex = 0;
  DateTime? _backgroundedAt;
  static const Duration _pinTimeout = Duration(minutes: 5);
  final ApiService _api = ApiService();
  late final SyncScheduler _syncScheduler = SyncScheduler(_api);

  static const _retailTabs = [
    _TabItem(
      label: 'POS',
      icon: Icons.point_of_sale_outlined,
      activeIcon: Icons.point_of_sale,
    ),
    _TabItem(
      label: 'Shift Sales',
      icon: Icons.receipt_long_outlined,
      activeIcon: Icons.receipt_long,
    ),
    _TabItem(
      label: 'Cash',
      icon: Icons.account_balance_wallet_outlined,
      activeIcon: Icons.account_balance_wallet,
    ),
    _TabItem(
      label: 'More',
      icon: Icons.grid_view_outlined,
      activeIcon: Icons.grid_view,
    ),
  ];

  static const _gasTabs = [
    _TabItem(
      label: 'Gas POS',
      icon: Icons.local_gas_station_outlined,
      activeIcon: Icons.local_gas_station,
    ),
    _TabItem(
      label: 'More',
      icon: Icons.grid_view_outlined,
      activeIcon: Icons.grid_view,
    ),
  ];

  List<_TabItem> _tabsFor(AppProvider provider) =>
      provider.currentUser?.isGasBranch == true ? _gasTabs : _retailTabs;

  List<Widget> _screensFor(AppProvider provider) {
    final isGas = provider.currentUser?.isGasBranch == true;
    if (isGas) {
      return const [GasPosScreen(), MoreScreen()];
    }
    return [
      const PosScreen(),
      const SalesScreen(),
      const CashScreen(),
      const MoreScreen(),
    ];
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadInitialData();
  }

  @override
  void dispose() {
    _syncScheduler.stop();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive) {
      _backgroundedAt = DateTime.now();
    } else if (state == AppLifecycleState.resumed) {
      if (_backgroundedAt != null) {
        final elapsed = DateTime.now().difference(_backgroundedAt!);
        if (elapsed >= _pinTimeout) _showPinLock();
        _backgroundedAt = null;
      }
      _runQuietSync();
    }
  }

  Future<void> _showPinLock() async {
    if (!mounted) return;
    await Navigator.of(context).push(PageRouteBuilder(
      pageBuilder: (_, __, ___) => const _InlinePinScreen(),
      opaque: true,
      barrierColor: Colors.black,
    ));
  }

  Future<void> _loadInitialData() async {
    final provider = context.read<AppProvider>();
    if (provider.currentUser?.isGasBranch == true) {
      provider.clearSession();
      try {
        provider.setNotificationCount(await _api.getUnreadCount());
        provider.setOnline(true);
      } catch (_) {
        provider.setOnline(false);
      }
      return;
    }

    // ── Step 1: Restore from local SQLite immediately ─────────────────────────
    // This makes the app fully usable offline before the server even responds.
    // Without this, a cashier who restarts the app mid-shift loses their session.
    try {
      final cached = await _api.loadCachedSession();
      if (cached != null && mounted) {
        final p = context.read<AppProvider>();
        p.setSession(cached);
        final products = await _api.loadCachedProducts();
        if (mounted && products.isNotEmpty) p.setProducts(products);
      }
    } catch (_) {
      // Non-fatal — Step 2 will update state from the server
    }

    // ── Step 2: Sync with server and override local state with server truth ───
    try {
      final session =
          await _api.getActiveSession(keepLocalWhenServerHasNoSession: false);
      final unread = await _api.getUnreadCount();
      final products = (session != null && session.id > 0)
          ? await _api.getProducts(preferCache: true)
          : const <Product>[];
      await _runQuietSync(refreshProductsAfterSync: false);
      if (mounted) {
        final p = context.read<AppProvider>();
        if (session != null && session.id > 0) {
          p.setSession(session);
        } else {
          p.clearSession();
        }
        if (products.isNotEmpty) p.setProducts(products);
        p.setNotificationCount(unread);
        p.setOnline(true);
      }
      _syncScheduler.start(
        onSynced: _handleAutoSyncResult,
        onError: (_) {
          if (mounted) context.read<AppProvider>().setOnline(false);
        },
      );
    } catch (_) {
      // Server unavailable — Step 1 already restored from local cache above.
      // The cashier can sell offline; sales queue until connectivity returns.
      if (mounted) context.read<AppProvider>().setOnline(false);
      _syncScheduler.start(
        onSynced: _handleAutoSyncResult,
        onError: (_) {
          if (mounted) context.read<AppProvider>().setOnline(false);
        },
      );
    }
  }

  Future<void> _runQuietSync({bool refreshProductsAfterSync = true}) async {
    if (context.read<AppProvider>().currentUser?.isGasBranch == true) return;
    try {
      await _api.syncOfflineSales();
      if (!mounted) return;
      context.read<AppProvider>().setOnline(true);
      if (refreshProductsAfterSync &&
          context.read<AppProvider>().activeSession != null) {
        final products = await _api.refreshOpenShiftProducts();
        if (mounted) context.read<AppProvider>().setProducts(products);
      }
    } catch (_) {
      if (mounted) context.read<AppProvider>().setOnline(false);
    }
  }

  Future<void> _handleAutoSyncResult(SyncResult result) async {
    if (!mounted) return;
    context.read<AppProvider>().setOnline(!result.hasFailures);
    try {
      if (context.read<AppProvider>().activeSession != null) {
        final products = await _api.refreshOpenShiftProducts();
        if (mounted) context.read<AppProvider>().setProducts(products);
      }
    } catch (_) {
      if (mounted) context.read<AppProvider>().setOnline(false);
    }
  }

  // ─── Build ──────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final w = MediaQuery.of(context).size.width;
    final isTablet = w >= 700;
    final isDesktop = w >= 1100;
    final provider = context.watch<AppProvider>();
    final session = provider.activeSession;

    if (isTablet) {
      return _tabletLayout(provider, session, isDesktop: isDesktop);
    }
    return _phoneLayout(provider, session);
  }

  // ─── Tablet layout: side NavigationRail ─────────────────────────────────────

  Widget _tabletLayout(AppProvider provider, dynamic session,
      {required bool isDesktop}) {
    final tabs = _tabsFor(provider);
    final isGas = provider.currentUser?.isGasBranch == true;
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Column(
        children: [
          // Top bar
          _TopBar(
            provider: provider,
            onNotificationsTap: _openNotifications,
          ),
          // Session banner
          if (session != null && !isGas)
            _SessionBanner(
              session: session,
              onTap: () => setState(() => _currentIndex = 2),
            ),
          // Main area
          Expanded(
            child: Row(
              children: [
                // Navigation Rail
                _SideRail(
                  currentIndex: _currentIndex,
                  tabs: tabs,
                  extended: false,
                  onTap: (i) => setState(() => _currentIndex = i),
                ),
                // Content
                Expanded(
                  child: IndexedStack(
                    index: _currentIndex,
                    children: _screensFor(provider),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ─── Phone layout: bottom navigation bar ───────────────────────────────────

  Widget _phoneLayout(AppProvider provider, dynamic session) {
    final tabs = _tabsFor(provider);
    final isGas = provider.currentUser?.isGasBranch == true;
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.primaryBlue,
        foregroundColor: Colors.white,
        elevation: 0,
        titleSpacing: 16,
        title: _logoAndUser(provider),
        actions: _appBarActions(provider),
        bottom: session != null && !isGas
            ? PreferredSize(
                preferredSize: const Size.fromHeight(32),
                child: _SessionBanner(
                  session: session,
                  onTap: () => setState(() => _currentIndex = 2),
                ),
              )
            : null,
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: _screensFor(provider),
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (i) => setState(() => _currentIndex = i),
        type: BottomNavigationBarType.fixed,
        selectedItemColor: AppColors.primaryBlue,
        unselectedItemColor: AppColors.textMuted,
        backgroundColor: Colors.white,
        elevation: 10,
        selectedLabelStyle:
            const TextStyle(fontWeight: FontWeight.w700, fontSize: 11),
        items: tabs
            .map((t) => BottomNavigationBarItem(
                  icon: Icon(t.icon),
                  activeIcon: Icon(t.activeIcon),
                  label: t.label,
                ))
            .toList(),
      ),
    );
  }

  // ─── Shared pieces ──────────────────────────────────────────────────────────

  Widget _logoAndUser(AppProvider provider) {
    return Row(
      children: [
        Container(
          width: 30,
          height: 30,
          decoration: BoxDecoration(
            color: AppColors.accentYellow,
            borderRadius: BorderRadius.circular(6),
          ),
          alignment: Alignment.center,
          child: const Text('RZ',
              style: TextStyle(
                  color: AppColors.primaryBlue,
                  fontWeight: FontWeight.w900,
                  fontSize: 13)),
        ),
        const SizedBox(width: 8),
        Text(provider.currentUser?.isGasBranch == true ? 'RetailZW Gas POS' : 'RetailZW POS',
            style: const TextStyle(
                fontWeight: FontWeight.w800,
                fontSize: 16,
                color: Colors.white)),
        const SizedBox(width: 10),
        if (provider.currentUser?.firstName != null)
          Text(
            provider.currentUser!.firstName,
            style: const TextStyle(color: Colors.white60, fontSize: 12),
          ),
      ],
    );
  }

  List<Widget> _appBarActions(AppProvider provider) {
    return [
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: provider.isOnline
                    ? const Color(0xFF4CAF50)
                    : const Color(0xFFEF5350),
              ),
            ),
            const SizedBox(width: 4),
            Text(provider.isOnline ? 'Online' : 'Offline',
                style: const TextStyle(color: Colors.white70, fontSize: 11)),
          ],
        ),
      ),
      Stack(
        alignment: Alignment.center,
        children: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined, color: Colors.white),
            onPressed: _openNotifications,
          ),
          if (provider.notificationCount > 0)
            Positioned(
              top: 8,
              right: 8,
              child: Container(
                padding: const EdgeInsets.all(2),
                decoration: BoxDecoration(
                  color: AppColors.errorRed,
                  borderRadius: BorderRadius.circular(10),
                ),
                constraints: const BoxConstraints(minWidth: 16, minHeight: 16),
                child: Text(
                  '${provider.notificationCount > 99 ? '99+' : provider.notificationCount}',
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 9,
                      fontWeight: FontWeight.w700),
                  textAlign: TextAlign.center,
                ),
              ),
            ),
        ],
      ),
    ];
  }

  void _openNotifications() {
    Navigator.of(context).push(
      MaterialPageRoute(builder: (_) => const NotificationsScreen()),
    );
  }
}

// ─── Top Bar widget (tablet only) ────────────────────────────────────────────

class _TopBar extends StatelessWidget {
  final AppProvider provider;
  final VoidCallback onNotificationsTap;

  const _TopBar({required this.provider, required this.onNotificationsTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.white,
      padding: EdgeInsets.only(
        top: MediaQuery.of(context).padding.top,
        left: 16,
        right: 8,
        bottom: 0,
      ),
      child: SizedBox(
        height: 56,
        child: Row(
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: AppColors.accentYellow,
                borderRadius: BorderRadius.circular(8),
              ),
              alignment: Alignment.center,
              child: const Text('RZ',
                  style: TextStyle(
                      color: AppColors.primaryBlue,
                      fontWeight: FontWeight.w900,
                      fontSize: 14)),
            ),
            const SizedBox(width: 10),
            Text(provider.currentUser?.isGasBranch == true ? 'RetailZW Gas POS' : 'RetailZW POS',
                style: const TextStyle(
                    color: AppColors.textDark,
                    fontWeight: FontWeight.w900,
                    fontSize: 18)),
            const SizedBox(width: 16),
            if (provider.currentUser != null)
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                decoration: BoxDecoration(
                  color: const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.person_rounded,
                        size: 14, color: AppColors.textMuted),
                    const SizedBox(width: 6),
                    Text(
                      '${provider.currentUser!.firstName} ${provider.currentUser!.lastName}',
                      style: const TextStyle(
                          color: AppColors.textDark,
                          fontSize: 13,
                          fontWeight: FontWeight.w600),
                    ),
                  ],
                ),
              ),
            const Spacer(),
            // Online status
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: provider.isOnline
                        ? const Color(0xFF4CAF50)
                        : const Color(0xFFEF5350),
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  provider.isOnline ? 'Online' : 'Offline',
                  style:
                      const TextStyle(color: AppColors.textMuted, fontSize: 12),
                ),
              ],
            ),
            const SizedBox(width: 8),
            // Notification bell
            Stack(
              alignment: Alignment.center,
              children: [
                IconButton(
                  icon: const Icon(Icons.notifications_outlined,
                      color: AppColors.textDark),
                  onPressed: onNotificationsTap,
                ),
                if (provider.notificationCount > 0)
                  Positioned(
                    top: 8,
                    right: 8,
                    child: Container(
                      padding: const EdgeInsets.all(2),
                      decoration: BoxDecoration(
                        color: AppColors.errorRed,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      constraints:
                          const BoxConstraints(minWidth: 16, minHeight: 16),
                      child: Text(
                        '${provider.notificationCount}',
                        style: const TextStyle(
                            color: Colors.white,
                            fontSize: 9,
                            fontWeight: FontWeight.w700),
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Side Rail (tablet nav) ───────────────────────────────────────────────────

class _SideRail extends StatelessWidget {
  final int currentIndex;
  final List<_TabItem> tabs;
  final ValueChanged<int> onTap;
  final bool extended;

  const _SideRail({
    required this.currentIndex,
    required this.tabs,
    required this.onTap,
    this.extended = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: extended ? 210 : 88,
      decoration: BoxDecoration(
        color: const Color(0xFF071B3A),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.12),
            blurRadius: 8,
            offset: const Offset(2, 0),
          ),
        ],
      ),
      child: Column(
        children: [
          const SizedBox(height: 12),
          ...tabs.asMap().entries.map((e) {
            final i = e.key;
            final tab = e.value;
            final selected = i == currentIndex;
            return _RailItem(
              icon: selected ? tab.activeIcon : tab.icon,
              label: tab.label,
              selected: selected,
              extended: extended,
              onTap: () => onTap(i),
            );
          }),
          const Spacer(),
          const SizedBox(height: 12),
        ],
      ),
    );
  }
}

class _RailItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final bool extended;

  const _RailItem({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
    required this.extended,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        padding: EdgeInsets.symmetric(
          vertical: 10,
          horizontal: extended ? 14 : 0,
        ),
        decoration: BoxDecoration(
          color: selected ? AppColors.primaryBlue : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
        ),
        child: extended
            ? Row(
                children: [
                  Icon(icon,
                      color: selected ? Colors.white : Colors.white70,
                      size: 22),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(label,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight:
                              selected ? FontWeight.w800 : FontWeight.w600,
                          color: selected ? Colors.white : Colors.white70,
                        )),
                  ),
                  if (selected)
                    const Icon(Icons.chevron_right_rounded,
                        size: 18, color: Colors.white),
                ],
              )
            : Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    icon,
                    color: selected ? Colors.white : Colors.white70,
                    size: 24,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    label,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                      color: selected ? Colors.white : Colors.white70,
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}

// ─── Session banner ───────────────────────────────────────────────────────────

class _SessionBanner extends StatelessWidget {
  final dynamic session;
  final VoidCallback onTap;

  const _SessionBanner({required this.session, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: double.infinity,
        height: 32,
        color: AppColors.accentYellow,
        alignment: Alignment.center,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.check_circle,
                size: 14, color: AppColors.primaryBlue),
            const SizedBox(width: 6),
            Text(
              'Session Open  •  Float: \$${session.openingFloatUsd?.toStringAsFixed(2) ?? '0.00'} USD',
              style: const TextStyle(
                color: AppColors.primaryBlue,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(width: 6),
            const Icon(Icons.arrow_forward_ios,
                size: 10, color: AppColors.primaryBlue),
          ],
        ),
      ),
    );
  }
}

// ─── Tab item model ───────────────────────────────────────────────────────────

class _TabItem {
  final String label;
  final IconData icon;
  final IconData activeIcon;

  const _TabItem({
    required this.label,
    required this.icon,
    required this.activeIcon,
  });
}

// ─── PIN Lock Screen ──────────────────────────────────────────────────────────

class _InlinePinScreen extends StatelessWidget {
  const _InlinePinScreen();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.primaryBlue,
      body: SafeArea(
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 60,
                height: 60,
                decoration: BoxDecoration(
                  color: AppColors.accentYellow,
                  borderRadius: BorderRadius.circular(14),
                ),
                alignment: Alignment.center,
                child: const Text('RZ',
                    style: TextStyle(
                        color: AppColors.primaryBlue,
                        fontWeight: FontWeight.w900,
                        fontSize: 22)),
              ),
              const SizedBox(height: 24),
              const Text('Session Locked',
                  style: TextStyle(
                      color: Colors.white,
                      fontSize: 22,
                      fontWeight: FontWeight.w800)),
              const SizedBox(height: 8),
              Text('You were away for too long',
                  style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.65),
                      fontSize: 14)),
              const SizedBox(height: 40),
              ElevatedButton.icon(
                onPressed: () => Navigator.of(context).pop(),
                icon: const Icon(Icons.lock_open_rounded),
                label: const Text('Unlock',
                    style: TextStyle(fontWeight: FontWeight.w700)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accentYellow,
                  foregroundColor: AppColors.primaryBlue,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 32, vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
