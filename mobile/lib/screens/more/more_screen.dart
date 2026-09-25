import 'dart:io';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../providers/app_provider.dart';
import '../../screens/auth/login_screen.dart';
import '../../screens/settings/printer_settings_screen.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class MoreScreen extends StatelessWidget {
  const MoreScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = context.watch<AppProvider>();
    final user = provider.currentUser;
    final initials = user == null
        ? 'RZ'
        : '${user.firstName.isNotEmpty ? user.firstName[0] : ''}${user.lastName.isNotEmpty ? user.lastName[0] : ''}'
            .toUpperCase();
    return Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 1120),
            child: ListView(
              padding: const EdgeInsets.all(24),
              children: [
                const Text('Your workspace',
                    style:
                        TextStyle(fontSize: 28, fontWeight: FontWeight.w900)),
                const SizedBox(height: 6),
                const Text(
                    'Your profile, store and device preferences — all in one place.',
                    style: TextStyle(color: AppColors.textMuted)),
                const SizedBox(height: 20),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(18),
                    child: Row(
                      children: [
                        CircleAvatar(
                          radius: 32,
                          backgroundColor: AppColors.primaryBlue,
                          child: Text(
                            initials.isEmpty ? 'RZ' : initials,
                            style: const TextStyle(
                              color: AppColors.accentYellow,
                              fontWeight: FontWeight.w900,
                              fontSize: 20,
                            ),
                          ),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                user?.fullName.isNotEmpty == true
                                    ? user!.fullName
                                    : user?.username ?? 'Cashier',
                                style: const TextStyle(
                                    fontSize: 18, fontWeight: FontWeight.w900),
                              ),
                              const SizedBox(height: 3),
                              Text('@${user?.username ?? 'cashier'}',
                                  style: const TextStyle(
                                      color: AppColors.textMuted)),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: [
                                  StatusBadge(user?.role ?? 'CASHIER'),
                                  StatusBadge(
                                      provider.isOnline ? 'ONLINE' : 'OFFLINE'),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                LayoutBuilder(builder: (context, constraints) {
                  final width = constraints.maxWidth >= 640
                      ? (constraints.maxWidth - 16) / 2
                      : constraints.maxWidth;
                  return Wrap(spacing: 16, runSpacing: 12, children: [
                    SizedBox(
                        width: width,
                        child: _workspaceCard(
                          Icons.business_outlined,
                          'Shop',
                          user?.companyName.trim().isNotEmpty == true
                              ? user!.companyName.trim()
                              : 'Shop name unavailable',
                          user?.companyName.trim().isNotEmpty == true
                              ? 'Your retail business'
                              : 'Sign in online once to save your shop name.',
                        )),
                    SizedBox(
                        width: width,
                        child: _workspaceCard(
                          Icons.storefront_outlined,
                          'Branch',
                          user?.branchName.trim().isNotEmpty == true
                              ? user!.branchName.trim()
                              : user?.branchId == null
                                  ? 'No branch assigned'
                                  : 'Branch name unavailable',
                          user?.branchName.trim().isNotEmpty == true
                              ? 'Your assigned selling location'
                              : 'Sign in online once to save your branch name.',
                        )),
                  ]);
                }),
                const SizedBox(height: 24),
                const Text('Device & preferences',
                    style:
                        TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                const SizedBox(height: 10),
                // ── Printer settings ─────────────────────────────────────────────
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.print_rounded,
                        color: AppColors.primaryBlue),
                    title: Text(Platform.isWindows
                        ? 'Windows Printer'
                        : 'Bluetooth Printer'),
                    subtitle: Text(Platform.isWindows
                        ? 'Use any printer installed in Windows'
                        : 'Connect a thermal receipt printer'),
                    trailing: const Icon(Icons.chevron_right_rounded,
                        color: AppColors.textMuted),
                    onTap: Platform.isWindows
                        ? () => showDialog<void>(
                              context: context,
                              builder: (context) => AlertDialog(
                                title: const Text('Windows printing'),
                                content: const Text(
                                  'RetailZW uses the Windows print dialog. Install your receipt or office printer in Windows Settings, then select it when printing a receipt.',
                                ),
                                actions: [
                                  TextButton(
                                    onPressed: () => Navigator.pop(context),
                                    child: const Text('Close'),
                                  ),
                                ],
                              ),
                            )
                        : () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                  builder: (_) =>
                                      const PrinterSettingsScreen()),
                            ),
                  ),
                ),
                const SizedBox(height: 12),
                Card(
                  child: Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.all(16),
                        child: Wrap(
                          spacing: 24,
                          runSpacing: 12,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            const Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(Icons.currency_exchange,
                                      color: AppColors.primaryBlue),
                                  SizedBox(width: 16),
                                  Text('Sales currency'),
                                ]),
                            SegmentedButton<String>(
                              segments: const [
                                ButtonSegment(value: 'USD', label: Text('USD')),
                                ButtonSegment(value: 'ZWG', label: Text('ZWG')),
                              ],
                              selected: {provider.currency},
                              onSelectionChanged: (value) =>
                                  provider.setCurrency(value.first),
                            ),
                          ],
                        ),
                      ),
                      const Divider(height: 1),
                      ListTile(
                        leading: Icon(
                            user?.isGasBranch == true
                                ? Icons.local_gas_station
                                : Icons.store,
                            color: AppColors.primaryBlue),
                        title: Text(user?.isGasBranch == true
                            ? 'Zimbabwe LPG gas mode'
                            : 'Zimbabwe retail mode'),
                        subtitle: Text(user?.isGasBranch == true
                            ? 'Gas shifts, kilogram pricing and tank-aware sales'
                            : 'Multi-currency, branch-aware POS workflows'),
                        trailing: Icon(provider.isOnline
                            ? Icons.cloud_done
                            : Icons.cloud_off),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),
                const Text('Account & security',
                    style:
                        TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                const SizedBox(height: 10),
                Card(
                  child: Column(
                    children: [
                      ListTile(
                        leading:
                            const Icon(Icons.logout, color: AppColors.errorRed),
                        title: const Text('Logout'),
                        subtitle: const Text(
                            'Return to sign in. Device offline login remains available.'),
                        onTap: () =>
                            _confirmLogout(context, forgetDevice: false),
                      ),
                      const Divider(height: 1),
                      ListTile(
                        leading: const Icon(Icons.delete_forever_outlined,
                            color: AppColors.errorRed),
                        title: const Text('Forget this device'),
                        subtitle: const Text(
                            'Remove saved offline login and tokens from this device.'),
                        onTap: () =>
                            _confirmLogout(context, forgetDevice: true),
                      ),
                    ],
                  ),
                ),
              ],
            )));
  }

  Widget _workspaceCard(IconData icon, String label, String name, String hint) {
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(icon, color: AppColors.primaryBlue),
            const SizedBox(width: 10),
            Text(label, style: const TextStyle(color: AppColors.textMuted)),
          ]),
          const SizedBox(height: 16),
          Text(name,
              style:
                  const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text(hint,
              style: const TextStyle(color: AppColors.textMuted, fontSize: 12)),
        ]),
      ),
    );
  }

  Future<void> _confirmLogout(BuildContext context,
      {required bool forgetDevice}) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(forgetDevice ? 'Forget this device?' : 'Logout?'),
        content: Text(
          forgetDevice
              ? 'This removes the saved offline login from this device. You will need internet to sign in again.'
              : 'You will return to the sign-in page. Offline login will still work for this cashier on this device.',
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel')),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(forgetDevice ? 'Forget' : 'Logout'),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    final provider = context.read<AppProvider>();
    provider.clearCart();
    provider.clearSession();
    provider.clearUser();
    await ApiService().logout(forgetDevice: forgetDevice);
    if (!context.mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }
}
