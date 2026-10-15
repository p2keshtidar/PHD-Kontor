import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/text_dialog.dart';
import '../../l10n/l10n.dart';
import 'market_repository.dart';

/// Meine (simulierten) Anträge aus dem Marktplatz: Status, Unterlagen, eigene Entscheidung/Notiz, zurückziehen.
class ApplicationsScreen extends ConsumerWidget {
  const ApplicationsScreen({super.key});

  Future<void> _run(BuildContext context, WidgetRef ref, Future<void> Function() action) async {
    try {
      await action();
      ref.invalidate(marketApplicationsProvider);
    } catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(marketApplicationsProvider);
    final theme = Theme.of(context);
    final repo = ref.read(marketRepositoryProvider);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Meine Anträge'))),
      body: data.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (list) => ListView(padding: safeListPadding(context), children: [
          Text(tr('Alle Anträge sind Simulationen – es entstehen keine Verträge, Konten oder Karten.'),
              style: theme.textTheme.bodySmall),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              key: const Key('applications_loans'),
              onPressed: () => context.push('/kredit/neu'),
              icon: const Icon(Icons.request_quote_outlined),
              label: Text(tr('Kreditanfragen im Kreditvergleich')),
            ),
          ),
          if (list.isEmpty)
            Padding(
              padding: const EdgeInsets.all(24),
              child: Text(tr('Noch keine Anträge. Im Marktplatz kannst du Angebote vergleichen und simuliert beantragen.'),
                  key: const Key('applications_empty'), textAlign: TextAlign.center),
            ),
          for (final a in list)
            Card(
              key: Key('application_${a.id}'),
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Wrap(spacing: 8, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
                    Text(a.productName, style: const TextStyle(fontWeight: FontWeight.bold)),
                    Chip(label: Text(a.statusLabel), visualDensity: VisualDensity.compact),
                  ]),
                  Text('${a.provider} · ${formatDate(a.createdAt)}', style: theme.textTheme.bodySmall),
                  if (a.statusReason != null) ...[
                    const SizedBox(height: 4),
                    Text(a.statusReason!, key: Key('application_reason_${a.id}')),
                  ],
                  if (a.documents.isNotEmpty) ...[
                    const SizedBox(height: 6),
                    Text(tr('Unterlagen'), style: theme.textTheme.titleSmall),
                    for (final d in a.documents) ...[
                      Row(children: [
                        Icon(d.provided ? Icons.check_circle : Icons.radio_button_unchecked, size: 18,
                            color: d.provided ? const Color(0xFF2E7D32) : null),
                        const SizedBox(width: 6),
                        Expanded(child: Text(d.label)),
                      ]),
                      if (!d.provided && a.status == 'DOCUMENTS_MISSING')
                        Align(
                          alignment: Alignment.centerRight,
                          child: TextButton(
                            key: Key('application_doc_${a.id}_${d.key}'),
                            onPressed: () => _run(context, ref, () => repo.provide(a.id, d.key)),
                            child: Text(tr('Bereitgestellt')),
                          ),
                        ),
                    ],
                  ],
                  if (a.note != null) ...[
                    const SizedBox(height: 4),
                    Text(tr('Meine Notiz: {0}', [a.note!]), key: Key('application_note_${a.id}')),
                  ],
                  Wrap(spacing: 4, children: [
                    TextButton(
                      key: Key('application_note_edit_${a.id}'),
                      onPressed: () async {
                        final text = await askText(context,
                            title: tr('Entscheidung oder Notiz'),
                            initial: a.note ?? '',
                            maxLength: 500,
                            maxLines: 3,
                            fieldKey: const Key('application_note_field'),
                            saveKey: const Key('application_note_save'));
                        if (text != null && context.mounted) await _run(context, ref, () => repo.note(a.id, text));
                      },
                      child: Text(tr('Notiz')),
                    ),
                    if (a.isOpen)
                      TextButton(
                        key: Key('application_withdraw_${a.id}'),
                        onPressed: () => _run(context, ref, () => repo.withdraw(a.id)),
                        child: Text(tr('Zurückziehen')),
                      ),
                  ]),
                ]),
              ),
            ),
        ]),
      ),
    );
  }
}
