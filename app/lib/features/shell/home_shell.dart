import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/language_button.dart';
import '../../l10n/l10n.dart';

import '../notifications/notifications_repository.dart';
import '../notifications/realtime_provider.dart';

/// Grundgerüst nach dem Login: obere Leiste + untere Navigation mit vier Bereichen
/// (Pflichtenheft Kap. 11: Konto, Überweisen, Sparen, Karte).
/// Hält außerdem die Live-Verbindung für Benachrichtigungen offen (/F510/).
class HomeShell extends ConsumerWidget {
  const HomeShell({super.key, required this.shell});

  final StatefulNavigationShell shell;

  static const _titles = ['Konto', 'Überweisen', 'Sparen', 'Karte'];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(realtimeProvider);
    final unread = ref.watch(unreadCountProvider).valueOrNull ?? 0;

    return Scaffold(
      appBar: AppBar(
        title: Text(tr(_titles[shell.currentIndex])),
        actions: [
          const LanguageButton(),
          IconButton(
            tooltip: tr('Benachrichtigungen'),
            icon: Badge(
              isLabelVisible: unread > 0,
              label: Text(unread > 99 ? '99+' : '$unread'),
              child: const Icon(Icons.notifications_none),
            ),
            onPressed: () => context.push('/benachrichtigungen'),
          ),
          IconButton(
            tooltip: tr('Profil'),
            icon: const Icon(Icons.account_circle),
            onPressed: () => context.push('/profil'),
          ),
        ],
      ),
      body: shell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: shell.currentIndex,
        onDestinationSelected: (i) =>
            shell.goBranch(i, initialLocation: i == shell.currentIndex),
        destinations: [
          NavigationDestination(
              icon: Icon(Icons.account_balance_outlined),
              selectedIcon: Icon(Icons.account_balance),
              label: tr('Konto')),
          NavigationDestination(
              icon: Icon(Icons.swap_horiz), label: tr('Überweisen')),
          NavigationDestination(
              icon: Icon(Icons.savings_outlined),
              selectedIcon: Icon(Icons.savings),
              label: tr('Sparen')),
          NavigationDestination(
              icon: Icon(Icons.credit_card_outlined),
              selectedIcon: Icon(Icons.credit_card),
              label: tr('Karte')),
        ],
      ),
    );
  }
}
