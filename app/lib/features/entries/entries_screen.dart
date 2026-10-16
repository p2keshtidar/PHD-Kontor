import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../core/charts.dart';
import '../subaccounts/subaccount_dialogs.dart';
import 'entry_form_screen.dart';
import 'entry_models.dart';
import '../../core/theme.dart';

/// Übersicht der eigenen Einnahmen und Ausgaben: regelmäßige (Gehalt, Miete) und einmalige (Urlaub).
class EntriesScreen extends ConsumerWidget {
  const EntriesScreen({super.key});

  static Future<bool?> openForm(BuildContext context, String kind) => Navigator.of(context)
      .push<bool>(MaterialPageRoute(builder: (_) => EntryFormScreen(kind: kind)));

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(entriesProvider);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Einnahmen & Ausgaben'))),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(entriesProvider);
          await ref.read(entriesProvider.future);
        },
        child: data.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(children: [
            Padding(padding: const EdgeInsets.all(24), child: Text(apiErrorMessage(e))),
          ]),
          data: (s) => ListView(padding: safeListPadding(context), children: [
            _MonthlyCard(summary: s),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(
                child: FilledButton.tonalIcon(
                  key: const Key('entries_add_income'),
                  onPressed: () => openForm(context, 'INCOME'),
                  icon: const Icon(Icons.add),
                  label: Text(tr('Einnahme')),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: FilledButton.tonalIcon(
                  key: const Key('entries_add_expense'),
                  onPressed: () => openForm(context, 'EXPENSE'),
                  icon: const Icon(Icons.remove),
                  label: Text(tr('Ausgabe')),
                ),
              ),
            ]),
            const SizedBox(height: 20),
            if (s.entries.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Text(
                  tr('Noch nichts eingetragen.\nTrag zum Beispiel dein Gehalt und deine Miete als monatlich ein – oder einen Urlaub als einmalige Ausgabe. Der Finanzassistent rechnet dann damit.'),
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium,
                ),
              ),
            if (s.recurring.isNotEmpty) ...[
              Text(tr('Regelmäßig'), style: theme.textTheme.titleMedium),
              const SizedBox(height: 4),
              for (final e in s.recurring) _EntryTile(entry: e),
              const SizedBox(height: 16),
            ],
            if (s.once.isNotEmpty) ...[
              Text(tr('Einmalig'), style: theme.textTheme.titleMedium),
              const SizedBox(height: 4),
              for (final e in s.once) _EntryTile(entry: e),
            ],
          ]),
        ),
      ),
    );
  }
}

class _MonthlyCard extends StatelessWidget {
  const _MonthlyCard({required this.summary});

  final EntriesSummary summary;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    Widget cell(String label, int cents, Color color) => Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(label, style: theme.textTheme.bodySmall),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(formatCents(cents),
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold, color: color)),
            ),
          ]),
        );
    return Card(
      color: scheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(tr('Regelmäßig pro Monat'), style: theme.textTheme.titleSmall),
          const SizedBox(height: 10),
          Row(children: [
            cell(tr('Einnahmen'), summary.monthlyIncomeCents, positiveColor(context)),
            cell(tr('Ausgaben'), -summary.monthlyExpenseCents, scheme.error),
            cell(tr('Übrig'), summary.monthlyBalanceCents, scheme.onPrimaryContainer),
          ]),
        ]),
      ),
    );
  }
}

class _EntryTile extends ConsumerWidget {
  const _EntryTile({required this.entry});

  final Entry entry;

  String _subtitle() {
    final parts = <String>[entry.intervalLabel, entry.categoryLabel];
    if (entry.isOnce) {
      parts.add(formatDate(entry.startDate));
      parts.add(entry.bookedCount > 0 ? tr('gebucht') : (entry.isDone ? tr('nicht gebucht') : tr('geplant')));
    } else if (entry.isPaused) {
      parts.add(tr('pausiert'));
    } else if (entry.nextDate != null) {
      parts.add(tr('nächste: {0}', [formatDate(entry.nextDate!)]));
    } else {
      parts.add(tr('beendet'));
    }
    return parts.where((p) => p.isNotEmpty).join(' · ');
  }

  Future<void> _actions(BuildContext context, WidgetRef ref) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      useSafeArea: true,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            ListTile(
              title: Text(entry.title, style: const TextStyle(fontWeight: FontWeight.bold)),
              subtitle: Text(_subtitle()),
            ),
            if (!entry.isDone)
              ListTile(
                leading: const Icon(Icons.euro),
                title: Text(tr('Betrag ändern')),
                onTap: () => Navigator.pop(ctx, 'amount'),
              ),
            if (!entry.isOnce && !entry.isDone)
              ListTile(
                leading: Icon(entry.isPaused ? Icons.play_arrow : Icons.pause),
                title: Text(entry.isPaused ? tr('Fortsetzen') : tr('Pausieren')),
                onTap: () => Navigator.pop(ctx, 'pause'),
              ),
            ListTile(
              leading: Icon(Icons.delete_outline, color: Theme.of(ctx).colorScheme.error),
              title: Text(tr('Löschen'), style: TextStyle(color: Theme.of(ctx).colorScheme.error)),
              onTap: () => Navigator.pop(ctx, 'delete'),
            ),
          ]),
        ),
      ),
    );
    if (!context.mounted || action == null) return;
    final repo = ref.read(entriesRepositoryProvider);
    try {
      switch (action) {
        case 'amount':
          final cents = await askAmount(context, title: tr('Neuer Betrag'), maxCents: 5000000);
          if (cents == null) return;
          await repo.update(entry.id, {'amountCents': cents});
        case 'pause':
          await repo.update(entry.id, {'paused': !entry.isPaused});
        case 'delete':
          if (!context.mounted) return;
          final ok = await showDialog<bool>(
            context: context,
            builder: (ctx) => AlertDialog(
              title: Text(tr('Eintrag löschen?')),
              content: Text(tr('„{0}“ wird nicht mehr gebucht. Bereits gebuchte Beträge bleiben in deinen Umsätzen.', [entry.title])),
              actions: [
                TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(tr('Abbrechen'))),
                FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(tr('Löschen'))),
              ],
            ),
          );
          if (ok != true) return;
          await repo.delete(entry.id);
      }
      refreshAfterEntryChange(ref);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    final color = entry.isIncome ? positiveColor(context) : scheme.error;
    final dimmed = entry.isPaused || (entry.isDone && entry.bookedCount == 0);
    return Card(
      child: ListTile(
        key: Key('entry_${entry.id}'),
        leading: CircleAvatar(
          backgroundColor: color.withAlpha(30),
          child: Icon(entry.isIncome ? Icons.south_west : Icons.north_east, color: color),
        ),
        title: Text(entry.title, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(_subtitle(), maxLines: 2, overflow: TextOverflow.ellipsis),
        trailing: Text(
          '${entry.isIncome ? '+' : '−'}${formatCents(entry.amountCents)}',
          style: TextStyle(fontWeight: FontWeight.bold, color: dimmed ? scheme.outline : color),
        ),
        onTap: () => _actions(context, ref),
      ),
    );
  }
}
