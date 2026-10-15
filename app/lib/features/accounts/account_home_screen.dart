import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../auth/auth_controller.dart';
import '../banking/bank_repository.dart';
import '../banking/bank_section.dart';
import 'account.dart';
import 'accounts_repository.dart';
import '../insights/insights_models.dart';
import '../insights/insights_repository.dart';
import '../subaccounts/subaccount_dialogs.dart';
import '../subaccounts/subaccount_icons.dart';
import '../subaccounts/subaccount_screen.dart' show digitalAccountsExplanation;
import 'booking_tile.dart';
import 'bookings_repository.dart';

/// Startseite "Konto" (/F110/): Gesamtvermögen, zwei getrennte Bereiche – Bankkonten (Girokonto der Projektbank
/// als Simulation und verbundene echte Banken) und digitale Konten (Töpfe, keine eigenen Bankkonten) –,
/// darunter die letzten Umsätze des Girokontos (/F120/).
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
        ref.invalidate(bankOverviewProvider);
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
                child: Text(tr('Erneut versuchen')),
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
                  child: Text(tr('Hallo {0}!', [user.firstName]),
                      style: Theme.of(context).textTheme.titleLarge),
                ),
              _TotalCard(totalCents: netWorth?.totalCents ?? total, netWorth: netWorth),
              const SizedBox(height: 12),
              // Eigene Einnahmen und Ausgaben eintragen (Gehalt, Miete, Urlaub …)
              Row(children: [
                Expanded(
                  child: FilledButton.tonalIcon(
                    key: const Key('home_add_income'),
                    onPressed: () => context.push('/eintraege/neu?art=einnahme'),
                    icon: const Icon(Icons.add),
                    label: Text(tr('Einnahme')),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: FilledButton.tonalIcon(
                    key: const Key('home_add_expense'),
                    onPressed: () => context.push('/eintraege/neu?art=ausgabe'),
                    icon: const Icon(Icons.remove),
                    label: Text(tr('Ausgabe')),
                  ),
                ),
              ]),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  key: const Key('home_entries'),
                  onPressed: () => context.push('/eintraege'),
                  child: Text(tr('Einnahmen & Ausgaben verwalten')),
                ),
              ),
              const SizedBox(height: 4),
              // Bereich A: Bankkonten – Girokonto der Projektbank (Simulation) und verbundene echte Banken
              BankAccountsSection(leading: [
                for (final a in visible.where((a) => a.isMain))
                  _AccountTile(account: a, onTap: () => context.push('/umsaetze/${a.id}')),
              ]),
              // Bereich B: Digitale Konten – Töpfe im Projektbank-Konto, keine eigenen Bankkonten
              const _DigitalHeader(),
              for (final a in visible.where((a) => a.isSub))
                _DigitalTile(account: a, onTap: () => context.push('/unterkonto/${a.id}')),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  key: const Key('home_new_digital'),
                  onPressed: () async {
                    final saved = await showDialog<bool>(
                      context: context,
                      builder: (_) => const SubaccountEditDialog(),
                    );
                    if (saved == true) ref.invalidate(accountsProvider);
                  },
                  icon: const Icon(Icons.add),
                  label: Text(tr('Neues digitales Konto')),
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
            Text(tr('Gesamtvermögen'),
                style: TextStyle(color: scheme.onPrimaryContainer)),
            const SizedBox(height: 6),
            Text(
              formatCents(totalCents),
              style: Theme.of(context)
                  .textTheme
                  .headlineMedium
                  ?.copyWith(color: scheme.onPrimaryContainer, fontWeight: FontWeight.bold),
            ),
            if (netWorth != null &&
                (netWorth!.depotCents != 0 || netWorth!.loansCents != 0 || netWorth!.externalCents != 0)) ...[
              const SizedBox(height: 6),
              Text(
                tr('Konten {0}{1}{2}', [formatCents(netWorth!.accountsCents), netWorth!.depotCents != 0 ? tr(' · Depots {0}', [formatCents(netWorth!.depotCents)]) : '', netWorth!.loansCents != 0 ? tr(' · Kredite {0}', [formatCents(netWorth!.loansCents)]) : '']),
                style: TextStyle(color: scheme.onPrimaryContainer),
              ),
              if (netWorth!.externalCents != 0)
                Text(
                  tr('Andere Banken {0}', [formatCents(netWorth!.externalCents)]),
                  key: const Key('home_external_total'),
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
            child: Text(tr('Letzte Umsätze'), style: Theme.of(context).textTheme.titleMedium),
          ),
          TextButton(
            onPressed: () => context.push('/umsaetze/$accountId'),
            child: Text(tr('Alle anzeigen')),
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
                ? Padding(
                    padding: EdgeInsets.all(16),
                    child: Text(tr('Noch keine Umsätze.')),
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
            ? Text(tr('Projektbank (Simulation) · {0}', [formatIban(iban)]))
            : account.shared
                ? Row(children: [
                    const Icon(Icons.group, size: 14),
                    const SizedBox(width: 4),
                    Flexible(
                      child: Text(
                        account.isForeign
                            ? tr('Geteilt von {0}', [account.ownerName])
                            : tr('Geteilt mit {0} {1}', [account.memberCount, account.memberCount == 1 ? tr('Person') : tr('Personen')]),
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
                  SnackBar(content: Text(tr('IBAN kopiert'))),
                );
              },
      ),
    );
  }
}

/// Überschrift "Digitale Konten" mit Erklärung, was sie sind (und was nicht).
class _DigitalHeader extends StatelessWidget {
  const _DigitalHeader();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(child: Text(tr('Digitale Konten'), style: theme.textTheme.titleMedium)),
          IconButton(
            key: const Key('home_digital_info'),
            tooltip: tr('Was sind digitale Konten?'),
            icon: const Icon(Icons.info_outline),
            onPressed: () => showDialog<void>(
              context: context,
              builder: (ctx) => AlertDialog(
                title: Text(tr('Was sind digitale Konten?')),
                content: Text(digitalAccountsExplanation),
                actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: Text(tr('OK')))],
              ),
            ),
          ),
        ]),
        Text(tr('Töpfe für Ziele – umgebucht innerhalb der Projektbank, keine eigenen Bankkonten.'),
            style: theme.textTheme.bodySmall),
        const SizedBox(height: 4),
      ]),
    );
  }
}

/// Digitales Konto mit Zweck, Fortschritt zum Ziel und Restbetrag.
class _DigitalTile extends StatelessWidget {
  const _DigitalTile({required this.account, this.onTap});

  final Account account;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final d = account.digital;
    final theme = Theme.of(context);
    final sharing = account.isForeign
        ? tr('Geteilt von {0}', [account.ownerName])
        : account.shared
            ? tr('Geteilt mit {0} {1}', [account.memberCount, account.memberCount == 1 ? tr('Person') : tr('Personen')])
            : null;
    return Card(
      child: InkWell(
        key: Key('digital_tile_${account.id}'),
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Icon(subaccountIcon(account.icon)),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(account.name, style: theme.textTheme.titleMedium, maxLines: 2, overflow: TextOverflow.ellipsis),
                if (d != null) Text(d.purposeLabel, style: theme.textTheme.bodySmall),
                if (sharing != null)
                  Row(children: [
                    const Icon(Icons.group, size: 14),
                    const SizedBox(width: 4),
                    Flexible(child: Text(sharing, style: theme.textTheme.bodySmall, overflow: TextOverflow.ellipsis)),
                  ]),
                if (d != null && d.hasTarget) ...[
                  const SizedBox(height: 6),
                  LinearProgressIndicator(
                    value: ((d.progressPercent ?? 0) / 100).clamp(0, 1).toDouble(),
                    minHeight: 6,
                    borderRadius: BorderRadius.circular(3),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    d.goalReached
                        ? tr('Ziel erreicht')
                        : tr('noch {0} bis {1}', [formatCents(d.remainingCents ?? 0), formatCents(d.targetCents!)]),
                    style: theme.textTheme.bodySmall,
                  ),
                ],
              ]),
            ),
            const SizedBox(width: 8),
            Text(formatCents(account.balanceCents), style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          ]),
        ),
      ),
    );
  }
}
