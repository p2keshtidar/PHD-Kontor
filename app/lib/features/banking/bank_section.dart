import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/money.dart';
import '../../l10n/l10n.dart';
import 'bank_accounts_screen.dart';
import 'bank_repository.dart';

/// Bereich "Bankkonten" auf der Startseite: [leading] (Girokonto der Projektbank, Simulation) und
/// verbundene Konten anderer Banken (nur Lesezugriff).
class BankAccountsSection extends ConsumerWidget {
  const BankAccountsSection({super.key, this.leading = const []});

  final List<Widget> leading;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final overview = ref.watch(bankOverviewProvider);
    final theme = Theme.of(context);
    final accounts = overview.valueOrNull?.accounts ?? const [];
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Padding(
        padding: const EdgeInsets.only(top: 12),
        child: Row(children: [
          Expanded(child: Text(tr('Bankkonten'), style: theme.textTheme.titleMedium)),
          TextButton(
            key: const Key('home_banks'),
            onPressed: () => context.push('/banken'),
            child: Text(accounts.isEmpty ? tr('Mehr erfahren') : tr('Verwalten')),
          ),
        ]),
      ),
      ...leading,
      if (overview.isLoading && !overview.hasValue)
        const SizedBox.shrink()
      else if (accounts.isEmpty)
        Card(
          child: ListTile(
            key: const Key('home_bank_connect'),
            leading: const Icon(Icons.add_link),
            title: Text(tr('Konten anderer Banken verbinden')),
            subtitle: Text(tr('Nur Lesezugriff – Kontostand und Umsätze an einem Ort')),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/banken/verbinden'),
          ),
        )
      else ...[
        for (final a in accounts)
          Card(
            child: ListTile(
              key: Key('home_bank_account_${a.id}'),
              leading: Icon(a.sandbox ? Icons.science : Icons.account_balance),
              title: Text(a.name),
              subtitle: Wrap(spacing: 6, runSpacing: 2, crossAxisAlignment: WrapCrossAlignment.center, children: [
                Text(
                  [a.bankName, if (a.ibanMasked != null) a.ibanMasked!].join(' · '),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                if (!a.syncOk) BankChip(tr('nicht aktuell'), warning: true),
              ]),
              trailing: Text(
                a.balanceCents == null ? '–' : formatCents(a.balanceCents!),
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              onTap: () => context.push('/banken/umsaetze?konto=${a.id}&name=${Uri.encodeComponent(a.name)}'),
            ),
          ),
        if (overview.valueOrNull?.anySandbox ?? false)
          Padding(
            padding: const EdgeInsets.only(left: 4, top: 2),
            child: Text(tr('Sandbox: simulierte Banken mit Testdaten – keine echte Bank, kein echtes Geld.'),
                style: theme.textTheme.bodySmall),
          ),
      ],
    ]);
  }
}
