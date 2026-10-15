import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import 'app_notification.dart';
import 'notifications_repository.dart';

/// Liste aller Benachrichtigungen, ungelesene hervorgehoben (/F520/).
class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  static IconData iconFor(String type) => switch (type) {
        'TRANSFER_IN' => Icons.call_received,
        'TRANSFER_OUT' => Icons.call_made,
        'CARD_PAYMENT' => Icons.credit_card,
        'CARD_DECLINED' => Icons.credit_card_off,
        'INTERNAL' => Icons.swap_vert,
        'SAVING_RATE' => Icons.savings,
        'GOAL' => Icons.flag,
        'WELCOME' => Icons.celebration,
        _ => Icons.notifications,
      };

  Future<void> _markAll(WidgetRef ref) async {
    await ref.read(notificationsRepositoryProvider).markAllRead();
    ref.invalidate(notificationsProvider);
    ref.invalidate(unreadCountProvider);
  }

  Future<void> _markOne(WidgetRef ref, AppNotification n) async {
    if (n.read) return;
    await ref.read(notificationsRepositoryProvider).markRead(n.id);
    ref.invalidate(notificationsProvider);
    ref.invalidate(unreadCountProvider);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final list = ref.watch(notificationsProvider);
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: Text(tr('Benachrichtigungen')),
        actions: [
          TextButton(
            onPressed: () async {
              try {
                await _markAll(ref);
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context)
                      .showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
                }
              }
            },
            child: Text(tr('Alle gelesen')),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(notificationsProvider.future),
        child: list.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(children: [
            Padding(padding: const EdgeInsets.all(24), child: Text(apiErrorMessage(e))),
          ]),
          data: (items) => items.isEmpty
              ? ListView(children: [
                  Padding(
                    padding: EdgeInsets.all(32),
                    child: Text(tr('Keine Benachrichtigungen.'), textAlign: TextAlign.center),
                  ),
                ])
              : ListView.separated(
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (_, i) {
                    final n = items[i];
                    return ListTile(
                      tileColor: n.read ? null : scheme.primaryContainer.withAlpha(90),
                      leading: CircleAvatar(child: Icon(iconFor(n.type), size: 20)),
                      title: Text(n.title,
                          style: TextStyle(fontWeight: n.read ? FontWeight.normal : FontWeight.bold)),
                      subtitle: Text('${n.body}\n${formatDateTime(n.createdAt)}'),
                      isThreeLine: true,
                      onTap: () => _markOne(ref, n),
                    );
                  },
                ),
        ),
      ),
    );
  }
}
