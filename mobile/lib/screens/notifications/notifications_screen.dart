import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../models/models.dart';
import '../../providers/app_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class NotificationsScreen extends StatefulWidget {
  const NotificationsScreen({super.key});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  final ApiService _api = ApiService();
  late Future<List<AppNotification>> _notifications;

  @override
  void initState() {
    super.initState();
    _notifications = _api.getNotifications();
  }

  Future<void> _markAllRead() async {
    await _api.markAllRead();
    if (!mounted) return;
    context.read<AppProvider>().setNotificationCount(0);
    setState(() => _notifications = _api.getNotifications());
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications')),
      body: FutureBuilder<List<AppNotification>>(
        future: _notifications,
        builder: (context, snapshot) {
          final items = snapshot.data ?? const <AppNotification>[];
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              RetailZWButton(label: 'Mark all read', icon: Icons.done_all, onPressed: _markAllRead),
              const SizedBox(height: 12),
              if (snapshot.connectionState == ConnectionState.waiting)
                const Center(child: CircularProgressIndicator())
              else if (items.isEmpty)
                const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('No notifications yet.')))
              else
                ...items.map((n) => Card(
                      child: ListTile(
                        leading: Icon(n.isRead ? Icons.notifications_none : Icons.notifications_active,
                            color: n.isRead ? AppColors.textMuted : AppColors.primaryBlue),
                        title: Text(n.title),
                        subtitle: Text('${n.message}\n${timeAgo(n.createdAt)}'),
                        isThreeLine: true,
                      ),
                    )),
            ],
          );
        },
      ),
    );
  }
}
