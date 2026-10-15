import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../l10n/l10n.dart';
import '../insights/insights_models.dart';
import 'bank_accounts_screen.dart';
import 'bank_models.dart';
import 'bank_repository.dart';

final _bankTransactionProvider = FutureProvider.autoDispose.family<BankTransaction, int>(
    (ref, id) => ref.watch(bankRepositoryProvider).transaction(id));

/// Ein Umsatz eines verbundenen Kontos: oben die Daten der Bank (unverändert), darunter eigene Angaben.
class BankTransactionScreen extends ConsumerWidget {
  const BankTransactionScreen({super.key, required this.id});

  final int id;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tx = ref.watch(_bankTransactionProvider(id));
    return Scaffold(
      appBar: AppBar(title: Text(tr('Umsatz'))),
      body: tx.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (t) => _Body(key: ValueKey(t.id), tx: t),
      ),
    );
  }
}

class _Body extends ConsumerStatefulWidget {
  const _Body({super.key, required this.tx});

  final BankTransaction tx;

  @override
  ConsumerState<_Body> createState() => _BodyState();
}

class _BodyState extends ConsumerState<_Body> {
  late String _category = widget.tx.category == widget.tx.autoCategory ? '' : widget.tx.category;
  late final _note = TextEditingController(text: widget.tx.note ?? '');
  late final _tags = TextEditingController(text: widget.tx.tags.join(', '));
  late bool _counted = !widget.tx.excluded;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _note.dispose();
    _tags.dispose();
    super.dispose();
  }

  Map<String, String> get _categories => {
        ...selectableCategories,
        'UMBUCHUNG': tr('Umbuchung'),
        'SPAREN': tr('Sparen & Investieren'),
      };

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final tags = _tags.text.split(',').map((s) => s.trim()).where((s) => s.isNotEmpty).toList();
      await ref.read(bankRepositoryProvider).update(widget.tx.id,
          category: _category, note: _note.text.trim(), tags: tags, excluded: !_counted);
      refreshBanking(ref);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr('Gespeichert'))));
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = widget.tx;
    final theme = Theme.of(context);
    final categories = _categories;
    final autoLabel = categories[t.autoCategory] ?? t.autoCategory;
    return ListView(padding: safeListPadding(context), children: [
      Text(
        '${t.isIncoming ? '+' : ''}${formatBankAmount(t.amountCents, t.currency)}',
        key: const Key('bank_tx_amount'),
        style: theme.textTheme.headlineMedium?.copyWith(
          fontWeight: FontWeight.bold,
          decoration: t.isInactive ? TextDecoration.lineThrough : null,
        ),
      ),
      const SizedBox(height: 4),
      Wrap(spacing: 6, runSpacing: 4, children: [
        BankChip(t.statusLabel, warning: t.isInactive),
        if (t.sandbox) BankChip(tr('Testdaten')),
        if (t.reversal) BankChip(tr('Rückbuchung')),
      ]),
      if (t.status == 'REVERSED')
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(tr('Dieser Umsatz wurde später zurückgebucht und zählt nicht im Finanzüberblick.')),
        ),
      if (t.status == 'CANCELLED')
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(tr('Diese Vormerkung wurde nicht gebucht (z. B. aufgehobene Kartenreservierung).')),
        ),
      if (t.status == 'PENDING')
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(tr('Vorgemerkt: Die Bank hat den Umsatz noch nicht endgültig gebucht.')),
        ),
      const SizedBox(height: 16),
      Text(tr('Daten der Bank (unverändert)'), style: theme.textTheme.titleSmall),
      Card(
        child: Column(children: [
          _Row(tr('Empfänger/Absender'), t.counterparty ?? '–'),
          _Row(tr('Verwendungszweck'), t.remittance ?? '–'),
          _Row(tr('Buchungstag'), formatDate(t.bookingDate)),
          if (t.valueDate != null) _Row(tr('Wertstellung'), formatDate(t.valueDate!)),
          _Row(tr('Konto'), '${t.accountName} · ${t.bankName}'),
          _Row(tr('Automatische Kategorie'), autoLabel),
        ]),
      ),
      const SizedBox(height: 16),
      Text(tr('Deine Angaben'), style: theme.textTheme.titleSmall),
      const SizedBox(height: 8),
      DropdownButtonFormField<String>(
        key: const Key('bank_tx_category'),
        // ignore: deprecated_member_use
        value: categories.containsKey(_category) ? _category : '',
        isExpanded: true,
        decoration: InputDecoration(labelText: tr('Kategorie'), prefixIcon: const Icon(Icons.category)),
        items: [
          DropdownMenuItem(value: '', child: Text(tr('Automatisch ({0})', [autoLabel]))),
          for (final e in categories.entries) DropdownMenuItem(value: e.key, child: Text(e.value)),
        ],
        onChanged: (v) => setState(() => _category = v ?? ''),
      ),
      const SizedBox(height: 12),
      TextField(
        key: const Key('bank_tx_note'),
        controller: _note,
        maxLength: 500,
        maxLines: 3,
        minLines: 1,
        decoration: InputDecoration(labelText: tr('Notiz'), prefixIcon: const Icon(Icons.notes)),
      ),
      TextField(
        key: const Key('bank_tx_tags'),
        controller: _tags,
        decoration: InputDecoration(
          labelText: tr('Schlagwörter'),
          hintText: tr('mit Komma trennen, z. B. urlaub, italien'),
          prefixIcon: const Icon(Icons.sell),
        ),
      ),
      SwitchListTile(
        key: const Key('bank_tx_counted'),
        contentPadding: EdgeInsets.zero,
        value: _counted,
        onChanged: (v) => setState(() => _counted = v),
        title: Text(tr('Im Finanzüberblick mitzählen')),
        subtitle: Text(tr('Ausschalten z. B. für Überträge zwischen eigenen Konten')),
      ),
      if (_error != null) Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
      const SizedBox(height: 8),
      FilledButton.icon(
        key: const Key('bank_tx_save'),
        onPressed: _busy ? null : _save,
        icon: _busy
            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : const Icon(Icons.check),
        label: Text(tr('Speichern')),
      ),
      const SizedBox(height: 8),
      Text(
        tr('Deine Angaben werden getrennt gespeichert. Die Daten der Bank bleiben unverändert.'),
        style: theme.textTheme.bodySmall,
      ),
    ]);
  }
}

class _Row extends StatelessWidget {
  const _Row(this.label, this.value);

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => ListTile(
        dense: true,
        title: Text(label, style: Theme.of(context).textTheme.bodySmall),
        subtitle: Text(value, style: Theme.of(context).textTheme.bodyMedium),
      );
}
