import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../widgets/common_widgets.dart';
import '../../providers/app_provider.dart';
import 'package:provider/provider.dart';

class PinScreen extends StatefulWidget {
  const PinScreen({super.key});

  @override
  State<PinScreen> createState() => _PinScreenState();
}

class _PinScreenState extends State<PinScreen> {
  final _storage = const FlutterSecureStorage();
  String _entered = '';
  String? _error;
  bool _verifying = false;

  void _append(String digit) {
    if (_entered.length >= 4) return;
    setState(() {
      _entered += digit;
      _error = null;
    });
    if (_entered.length == 4) _verify();
  }

  void _backspace() {
    if (_entered.isEmpty) return;
    setState(() => _entered = _entered.substring(0, _entered.length - 1));
  }

  Future<void> _verify() async {
    setState(() => _verifying = true);
    await Future.delayed(const Duration(milliseconds: 200));
    final stored = await _storage.read(key: 'pos_pin');
    if (stored == null) {
      if (mounted) Navigator.of(context).pop(true);
      return;
    }
    if (_entered == stored) {
      if (mounted) Navigator.of(context).pop(true);
    } else {
      setState(() {
        _error = 'Incorrect PIN. Try again.';
        _entered = '';
        _verifying = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = context.read<AppProvider>().currentUser;
    final initials = user != null
        ? '${user.firstName.isNotEmpty ? user.firstName[0] : ''}${user.lastName.isNotEmpty ? user.lastName[0] : ''}'
        : 'RZ';
    return Scaffold(
      backgroundColor: AppColors.primaryBlue,
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 360),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                CircleAvatar(
                  radius: 40,
                  backgroundColor: AppColors.accentYellow,
                  child: Text(initials,
                      style: const TextStyle(
                          color: AppColors.primaryBlue,
                          fontSize: 26,
                          fontWeight: FontWeight.w900)),
                ),
                const SizedBox(height: 16),
                Text(user?.fullName ?? 'POS Cashier',
                    style: const TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                        fontWeight: FontWeight.w700)),
                const SizedBox(height: 4),
                const Text('Enter your PIN to unlock',
                    style: TextStyle(color: Colors.white70, fontSize: 14)),
                const SizedBox(height: 32),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(4, (i) {
                    final filled = i < _entered.length;
                    return Container(
                      margin: const EdgeInsets.symmetric(horizontal: 10),
                      width: 18,
                      height: 18,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: filled ? AppColors.accentYellow : Colors.transparent,
                        border: Border.all(
                            color: filled ? AppColors.accentYellow : Colors.white54,
                            width: 2),
                      ),
                    );
                  }),
                ),
                const SizedBox(height: 12),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(_error!,
                        style: const TextStyle(color: Color(0xFFFF8A80), fontSize: 13)),
                  ),
                const SizedBox(height: 16),
                _buildPad(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPad() {
    final rows = [
      ['1', '2', '3'],
      ['4', '5', '6'],
      ['7', '8', '9'],
      ['', '0', 'del'],
    ];
    return Column(
      children: rows.map((row) {
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: row.map((key) {
              if (key.isEmpty) return const SizedBox(width: 94, height: 70);
              return GestureDetector(
                onTap: () {
                  if (_verifying) return;
                  if (key == 'del') {
                    _backspace();
                  } else {
                    _append(key);
                  }
                },
                child: Container(
                  margin: const EdgeInsets.symmetric(horizontal: 12),
                  width: 70,
                  height: 70,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.white.withValues(alpha: 0.12),
                    border: Border.all(color: Colors.white.withValues(alpha: 0.2), width: 1),
                  ),
                  alignment: Alignment.center,
                  child: key == 'del'
                      ? const Icon(Icons.backspace_outlined, color: Colors.white70, size: 22)
                      : Text(key,
                          style: const TextStyle(
                              color: Colors.white,
                              fontSize: 26,
                              fontWeight: FontWeight.w500)),
                ),
              );
            }).toList(),
          ),
        );
      }).toList(),
    );
  }
}
