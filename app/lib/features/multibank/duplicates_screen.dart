import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import 'multibank_models.dart';
import 'multibank_repository.dart';

/// Mögliche doppelte Umsätze: prüfen, als „kein Duplikat“ markieren oder bei einer verbundenen Bank nicht mitzählen.
class DuplicatesScreen extends ConsumerWidget {
  const DuplicatesScreen({super.key});

  Future<void> _run(BuildContext context, WidgetRef ref, Future<void> Function() action, String done) async {
    try {
      await action();
      ref.invalidate(duplicatesProvider);
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(done)));
    } catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(duplicatesProvider);
    final theme = Theme.of(context);
    final repo = ref.read(multiBankRepositoryProvider);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Mögliche Duplikate'))),
      body: data.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Padding(padding: const EdgeInsets.all(24), child: Text(apiErrorMessage(e), textAlign: TextAlign.center)),
        ),
        data: (d) => ListView(
          padding: safeListPadding(context),
          children: [
            Text(d.explanation, style: theme.textTheme.bodySmall),
            const SizedBox(height: 12),
            if (d.pairs.isEmpty)
              Padding(
                padding: const EdgeInsets.all(24),
                child: Text(tr('Keine möglichen Duplikate gefunden.'), key: const Key('dup_empty'),
                    textAlign: TextAlign.center),
              ),
            for (final p in d.pairs)
              Card(
                key: Key('dup_${p.key}'),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Icon(p.kind == 'SAME_ACCOUNT' ? Icons.repeat : Icons.compare_arrows,
                          color: theme.colorScheme.tertiary),
                      const SizedBox(width: 8),
                      Expanded(child: Text(p.reason)),
                    ]),
                    const Divider(),
                    _Item(item: p.first),
                    _Item(item: p.second),
                    const SizedBox(height: 4),
                    Wrap(spacing: 8, runSpacing: 4, children: [
                      OutlinedButton(
                        key: Key('dup_dismiss_${p.key}'),
                        onPressed: () => _run(context, ref, () => repo.dismissDuplicate(p.key),
                            tr('Als „kein Duplikat“ gemerkt')),
                        child: Text(tr('Kein Duplikat')),
                      ),
                      for (final t in [p.first, p.second])
                        if (t.isExternal && !t.excluded)
                          TextButton(
                            key: Key('dup_exclude_${t.id}'),
                            onPressed: () => _run(context, ref, () => repo.excludeExternal(t.id),
                                tr('Umsatz zählt nicht mehr in der Auswertung')),
                            child: Text(tr('„{0}“ nicht mitzählen', [t.accountName])),
                          ),
                    ]),
                  ]),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _Item extends StatelessWidget {
  const _Item({required this.item});

  final TxItem item;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(item.title, style: const TextStyle(fontWeight: FontWeight.w600)),
            Text('${formatDate(item.date)} · ${[item.accountName, if (item.institution != null) item.institution!].join(' · ')}',
                style: theme.textTheme.bodySmall),
          ]),
        ),
        const SizedBox(width: 8),
        Text(formatCents(item.amountCents), style: const TextStyle(fontWeight: FontWeight.bold)),
      ]),
    );
  }
}
