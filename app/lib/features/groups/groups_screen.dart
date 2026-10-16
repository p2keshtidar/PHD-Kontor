import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/state_views.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../core/text_dialog.dart';
import '../../l10n/l10n.dart';
import 'groups_repository.dart';
import '../../core/theme.dart';

/// Text zum Saldo aus Sicht des Nutzers.
String balanceText(int cents) {
  if (cents > 0) return tr('Du bekommst {0}', [formatCents(cents)]);
  if (cents < 0) return tr('Du schuldest {0}', [formatCents(-cents)]);
  return tr('Alles ausgeglichen');
}

Color? balanceColor(BuildContext context, int cents) =>
    cents > 0 ? positiveColor(context) : (cents < 0 ? Theme.of(context).colorScheme.error : null);

/// Gemeinsame Ausgaben: Liste der Gruppen (WG, Reise, Paar) mit eigenem Saldo.
class GroupsScreen extends ConsumerWidget {
  const GroupsScreen({super.key});

  Future<void> _create(BuildContext context, WidgetRef ref) async {
    final name = await askText(context,
        title: tr('Neue Gruppe'),
        hint: tr('z. B. WG Berger, Urlaub Lissabon'),
        maxLength: 60,
        fieldKey: const Key('group_name_field'),
        saveKey: const Key('group_name_save'));
    if (name == null || name.trim().isEmpty || !context.mounted) return;
    try {
      final g = await ref.read(groupsRepositoryProvider).create(name.trim());
      ref.invalidate(groupsProvider);
      if (context.mounted) context.push('/gruppen/${g.id}');
    } catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(groupsProvider);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Gemeinsame Ausgaben'))),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('groups_create'),
        onPressed: () => _create(context, ref),
        icon: const Icon(Icons.group_add),
        label: Text(tr('Gruppe')),
      ),
      body: data.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(groupsProvider)),
        data: (list) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(groupsProvider),
          child: ListView(padding: safeListPadding(context), children: [
            Text(tr('Für WG, Reisen oder Paare: Ausgaben eintragen, fair aufteilen und sehen, wer wem wie viel schuldet.'),
                style: theme.textTheme.bodyMedium),
            const SizedBox(height: 12),
            if (list.isEmpty)
              Padding(
                padding: const EdgeInsets.all(24),
                child: Text(tr('Noch keine Gruppe. Lege eine an und füge Mitbewohner über E-Mail, Telefon oder IBAN hinzu.'),
                    key: const Key('groups_empty'), textAlign: TextAlign.center),
              ),
            for (final g in list)
              Card(
                key: Key('group_${g.id}'),
                child: ListTile(
                  leading: const CircleAvatar(child: Icon(Icons.groups_outlined)),
                  title: Text(g.name),
                  subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(tr('{0} Personen · zusammen {1}', ['${g.memberCount}', formatCents(g.totalSpentCents)])),
                    Text(balanceText(g.myBalanceCents),
                        key: Key('group_balance_${g.id}'),
                        style: TextStyle(color: balanceColor(context, g.myBalanceCents), fontWeight: FontWeight.w600)),
                  ]),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push('/gruppen/${g.id}'),
                ),
              ),
            const SizedBox(height: 80),
          ]),
        ),
      ),
    );
  }
}
