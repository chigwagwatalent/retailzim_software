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
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('Profile',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w900)),
        const SizedBox(height: 12),
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
                          style: const TextStyle(color: AppColors.textMuted)),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          StatusBadge(user?.role ?? 'CASHIER'),
                          StatusBadge(provider.isOnline ? 'SYNCED' : 'PENDING'),
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
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.badge_outlined,
                    color: AppColors.primaryBlue),
                title: const Text('User ID'),
                subtitle: Text('${user?.id ?? '-'}'),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.storefront_outlined,
                    color: AppColors.primaryBlue),
                title: const Text('Branch'),
                subtitle: Text(user?.branchId == null
                    ? 'No branch assigned'
                    : 'Branch #${user!.branchId}'),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.business_outlined,
                    color: AppColors.primaryBlue),
                title: const Text('Shop'),
                subtitle: Text(user?.tenantId == null
                    ? 'Unknown shop'
                    : 'Tenant #${user!.tenantId}'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        // ── Printer settings ─────────────────────────────────────────────
        Card(
          child: ListTile(
            leading:
                const Icon(Icons.print_rounded, color: AppColors.primaryBlue),
            title: Text(
                Platform.isWindows ? 'Windows Printer' : 'Bluetooth Printer'),
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
                          builder: (_) => const PrinterSettingsScreen()),
                    ),
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.currency_exchange,
                    color: AppColors.primaryBlue),
                title: const Text('Currency'),
                subtitle: Text(provider.currency),
                trailing: SegmentedButton<String>(
                  segments: const [
                    ButtonSegment(value: 'USD', label: Text('USD')),
                    ButtonSegment(value: 'ZWG', label: Text('ZWG')),
                  ],
                  selected: {provider.currency},
                  onSelectionChanged: (value) =>
                      provider.setCurrency(value.first),
                ),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.store, color: AppColors.primaryBlue),
                title: const Text('Zimbabwe retail mode'),
                subtitle:
                    const Text('Multi-currency, branch-aware POS workflows'),
                trailing: Icon(
                    provider.isOnline ? Icons.cloud_done : Icons.cloud_off),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.logout, color: AppColors.errorRed),
                title: const Text('Logout'),
                subtitle: const Text(
                    'Return to sign in. Device offline login remains available.'),
                onTap: () => _confirmLogout(context, forgetDevice: false),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.delete_forever_outlined,
                    color: AppColors.errorRed),
                title: const Text('Forget this device'),
                subtitle: const Text(
                    'Remove saved offline login and tokens from this phone.'),
                onTap: () => _confirmLogout(context, forgetDevice: true),
              ),
            ],
          ),
        ),
      ],
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
              ? 'This removes the saved offline login from this phone. You will need internet to sign in again.'
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
