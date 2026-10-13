import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import 'csv_import.dart';
import 'insights_repository.dart';
import '../../core/layout.dart';

/// /K040/ Umsätze einer anderen Bank als CSV-Datei importieren.
class CsvImportScreen extends ConsumerStatefulWidget {
  const CsvImportScreen({super.key});

  @override
  ConsumerState<CsvImportScreen> createState() => _CsvImportScreenState();
}

class _CsvImportScreenState extends ConsumerState<CsvImportScreen> {
  bool _busy = false;
  String? _error;
  ImportResult? _result;

  Future<void> _upload(String fileName, Future<String> Function() content) async {
    setState(() {
      _busy = true;
      _error = null;
      _result = null;
    });
    try {
      final text = await content();
      final r = await ref.read(csvImportRepositoryProvider).upload(fileName, text);
      ref.invalidate(csvImportsProvider);
      ref.invalidate(monthsProvider);
      ref.invalidate(overviewProvider);
      if (mounted) setState(() => _result = r);
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _pickFile() async {
    const csv = XTypeGroup(
      label: 'CSV',
      extensions: ['csv', 'txt'],
      mimeTypes: ['text/csv', 'text/plain', 'text/comma-separated-values', 'application/vnd.ms-excel'],
      uniformTypeIdentifiers: ['public.comma-separated-values-text', 'public.plain-text'],
    );
    final XFile? file = await openFile(acceptedTypeGroups: const [csv]);
    if (file == null) return;
    await _upload(file.name, () async => decodeCsvBytes(await file.readAsBytes()));
  }

  Future<void> _sample() =>
      _upload('beispiel-sparkasse.csv', () => ref.read(csvImportRepositoryProvider).sample());

  Future<void> _delete(CsvImportInfo i) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Import löschen?'),
        content: Text('Die ${i.rowsImported} Umsätze aus „${i.fileName}“ werden aus dem Finanzüberblick entfernt.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Abbrechen')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Löschen')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(csvImportRepositoryProvider).delete(i.id);
      ref.invalidate(csvImportsProvider);
      ref.invalidate(monthsProvider);
      ref.invalidate(overviewProvider);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final imports = ref.watch(csvImportsProvider);
    final r = _result;
    return Scaffold(
      appBar: AppBar(title: const Text('CSV-Import')),
      body: ListView(padding: safeListPadding(context), children: [
        Text('Umsätze deiner echten Bank einbeziehen', style: theme.textTheme.titleMedium),
        const SizedBox(height: 6),
        const Text(
          'Exportiere im Online-Banking deine Umsätze als CSV-Datei (z. B. Sparkasse, Volksbank, ING, DKB, '
          'comdirect, N26) und lade sie hier hoch. Die Umsätze fließen in Einnahmen, Ausgaben und Kategorien '
          'ein – deine Kontostände in dieser App ändern sich dadurch nicht.',
        ),
        const SizedBox(height: 16),
        FilledButton.icon(
          key: const Key('csv_pick'),
          onPressed: _busy ? null : _pickFile,
          icon: const Icon(Icons.upload_file),
          label: const Text('CSV-Datei auswählen'),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          key: const Key('csv_sample'),
          onPressed: _busy ? null : _sample,
          icon: const Icon(Icons.science_outlined),
          label: const Text('Beispieldatei importieren'),
        ),
        if (_busy) const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator())),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
          ),
        if (r != null) ...[
          const SizedBox(height: 16),
          Card(
            color: theme.colorScheme.secondaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(r.imported > 0 ? '${r.imported} Umsätze importiert' : 'Keine neuen Umsätze',
                    style: theme.textTheme.titleMedium),
                if (r.firstDate != null && r.lastDate != null)
                  Text('Zeitraum: ${formatDate(r.firstDate!)} bis ${formatDate(r.lastDate!)}'),
                if (r.skipped > 0) Text('${r.skipped} schon vorhanden und übersprungen'),
                if (r.imported > 0) ...[
                  const SizedBox(height: 6),
                  Text('Einnahmen: ${formatCents(r.incomeCents)}'),
                  Text('Ausgaben: ${formatCents(r.expenseCents)}'),
                ],
                for (final e in r.errors)
                  Text(e, style: TextStyle(color: theme.colorScheme.error, fontSize: 12)),
              ]),
            ),
          ),
        ],
        const SizedBox(height: 24),
        Text('Bisherige Importe', style: theme.textTheme.titleSmall),
        imports.when(
          loading: () => const Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator()),
          error: (e, _) => Text(apiErrorMessage(e)),
          data: (list) => list.isEmpty
              ? const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: Text('Noch keine.'))
              : Column(children: [
                  for (final i in list)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.description_outlined),
                      title: Text(i.fileName, overflow: TextOverflow.ellipsis),
                      subtitle: Text('${i.rowsImported} Umsätze'
                          '${i.firstDate != null && i.lastDate != null ? ' · ${formatDate(i.firstDate!)} – ${formatDate(i.lastDate!)}' : ''}'),
                      trailing: IconButton(
                        tooltip: 'Löschen',
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => _delete(i),
                      ),
                    ),
                ]),
        ),
      ]),
    );
  }
}
