import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../auth/auth_controller.dart';
import 'account.dart';
import 'accounts_repository.dart';
import '../insights/insights_models.dart';
import '../insights/insights_repository.dart';
import '../subaccounts/subaccount_dialogs.dart';
import '../subaccounts/subaccount_icons.dart';
import 'booking_tile.dart';
import 'bookings_repository.dart';

/// Startseite "Konto" (/F110/): Gesamtvermögen und Girokonto.
/// Darunter die letzten Umsätze des Girokontos (/F120/). Unterkonten folgen in Phase 3, Sparziele in Phase 4.
class AccountHomeScreen extends ConsumerWidget {
  const AccountHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final accounts = ref.watch(accountsProvider);
    final user = ref.watch(authControllerProvider).valueOrNull;

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(recentBookingsProvider);
        ref.invalidate(netWorthProvider);
        ref.invalidate(accountsProvider);
        await ref.read(accountsProvider.future);
      },
      child: accounts.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(24),
          children: [
            const SizedBox(height: 80),
            const Icon(Icons.cloud_off, size: 48),
            const SizedBox(height: 12),
            Text(apiErrorMessage(e), textAlign: TextAlign.center),
            const SizedBox(height: 12),
            Center(
              child: OutlinedButton(
                onPressed: () => ref.invalidate(accountsProvider),
                child: const Text('Erneut versuchen'),
              ),
            ),
          ],
        ),
        data: (list) {
          final total = list.fold<int>(0, (sum, a) => sum + a.balanceCents);
          final netWorth = ref.watch(netWorthProvider).valueOrNull;
          // Sparplan- und Kreditkonten erscheinen im Bereich "Sparen"
          final visible = list.where((a) => a.isMain || a.isSub).toList();
          final mains = list.where((a) => a.isMain);
          final main = mains.isEmpty ? null : mains.first;
          return ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              if (user != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text('Hallo ${user.firstName}!',
                      style: Theme.of(context).textTheme.titleLarge),
                ),
              _TotalCard(totalCents: netWorth?.totalCents ?? total, netWorth: netWorth),
              const SizedBox(height: 16),
              for (final a in visible)
                _AccountTile(
                  account: a,
                  onTap: () => context.push(a.isSub ? '/unterkonto/${a.id}' : '/umsaetze/${a.id}'),
                ),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: () async {
                    final saved = await showDialog<bool>(
                      context: context,
                      builder: (_) => const SubaccountEditDialog(),
                    );
                    if (saved == true) ref.invalidate(accountsProvider);
                  },
                  icon: const Icon(Icons.add),
                  label: const Text('Neues Unterkonto'),
                ),
              ),
              if (main != null) _RecentBookings(accountId: main.id),
            ],
          );
        },
      ),
    );
  }
}

class _TotalCard extends StatelessWidget {
  const _TotalCard({required this.totalCents, this.netWorth});

  final int totalCents;
  final NetWorth? netWorth;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      color: scheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Gesamtvermögen',
                style: TextStyle(color: scheme.onPrimaryContainer)),
            const SizedBox(height: 6),
            Text(
              formatCents(totalCents),
              style: Theme.of(context)
                  .textTheme
                  .headlineMedium
                  ?.copyWith(color: scheme.onPrimaryContainer, fontWeight: FontWeight.bold),
            ),
            if (netWorth != null && (netWorth!.depotCents != 0 || netWorth!.loansCents != 0)) ...[
              const SizedBox(height: 6),
              Text(
                'Konten ${formatCents(netWorth!.accountsCents)}'
                '${netWorth!.depotCents != 0 ? ' · Depots ${formatCents(netWorth!.depotCents)}' : ''}'
                '${netWorth!.loansCents != 0 ? ' · Kredite ${formatCents(netWorth!.loansCents)}' : ''}',
                style: TextStyle(color: scheme.onPrimaryContainer),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _RecentBookings extends ConsumerWidget {
  const _RecentBookings({required this.accountId});

  final int accountId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final recent = ref.watch(recentBookingsProvider(accountId));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 16),
        Row(children: [
          Expanded(
            child: Text('Letzte Umsätze', style: Theme.of(context).textTheme.titleMedium),
          ),
          TextButton(
            onPressed: () => context.push('/umsaetze/$accountId'),
            child: const Text('Alle anzeigen'),
          ),
        ]),
        Card(
          child: recent.when(
            loading: () => const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: CircularProgressIndicator()),
            ),
            error: (e, _) => Padding(
              padding: const EdgeInsets.all(16),
              child: Text(apiErrorMessage(e)),
            ),
            data: (items) => items.isEmpty
                ? const Padding(
                    padding: EdgeInsets.all(16),
                    child: Text('Noch keine Umsätze.'),
                  )
                : Column(children: [
                    for (final b in items)
                      BookingTile(
                        item: b,
                        onTap: () => context.push('/umsatz', extra: b),
                      ),
                  ]),
          ),
        ),
      ],
    );
  }
}

class _AccountTile extends StatelessWidget {
  const _AccountTile({required this.account, this.onTap});

  final Account account;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final iban = account.iban;
    return Card(
      child: ListTile(
        leading: Icon(account.isMain ? Icons.account_balance : subaccountIcon(account.icon)),
        title: Text(account.name),
        subtitle: iban != null
            ? Text(formatIban(iban))
            : account.shared
                ? Row(children: [
                    const Icon(Icons.group, size: 14),
                    const SizedBox(width: 4),
                    Flexible(
                      child: Text(
                        account.isForeign
                            ? 'Geteilt von ${account.ownerName}'
                            : 'Geteilt mit ${account.memberCount} ${account.memberCount == 1 ? 'Person' : 'Personen'}',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ])
                : null,
        trailing: Text(
          formatCents(account.balanceCents),
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
        ),
        onTap: onTap,
        onLongPress: iban == null
            ? null
            : () {
                Clipboard.setData(ClipboardData(text: iban));
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('IBAN kopiert')),
                );
              },
      ),
    );
  }
}
