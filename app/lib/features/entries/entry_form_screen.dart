import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../insights/insights_repository.dart';
import 'entry_models.dart';

/// Neue Einnahme oder Ausgabe eintragen – einmalig (z. B. Urlaub) oder regelmäßig (z. B. Gehalt, Miete).
class EntryFormScreen extends ConsumerStatefulWidget {
  const EntryFormScreen({super.key, this.kind = 'EXPENSE'});

  /// INCOME oder EXPENSE (Vorauswahl)
  final String kind;

  @override
  ConsumerState<EntryFormScreen> createState() => _EntryFormScreenState();
}

class _EntryFormScreenState extends ConsumerState<EntryFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _amount = TextEditingController();
  late String _kind = widget.kind == 'INCOME' ? 'INCOME' : 'EXPENSE';
  String? _category; // null = automatisch aus der Bezeichnung
  int _interval = 0;
  DateTime? _date;
  DateTime? _endDate;
  DateTime _today = DateTime.now();
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    // Datum der Simulation (Demo-Modus kann vorgespult sein)
    ref.read(clockProvider.future).then((d) {
      if (mounted) {
        setState(() {
          _today = DateTime(d.year, d.month, d.day);
          _date ??= _today;
        });
      }
    }).catchError((_) {
      if (mounted) setState(() => _date ??= _today);
    });
  }

  @override
  void dispose() {
    _title.dispose();
    _amount.dispose();
    super.dispose();
  }

  DateTime get _dateOrToday => _date ?? _today;

  bool get _inPast => !_dateOrToday.isAfter(_today);

  void _setKind(String kind) {
    if (kind == _kind) return;
    setState(() {
      _kind = kind;
      _category = null; // Kategorien unterscheiden sich
    });
  }

  void _useSuggestion(EntrySuggestion s) {
    setState(() {
      _title.text = s.title;
      _category = s.category;
      _interval = s.interval;
    });
  }

  Future<void> _pickDate() async {
    final d = await showDatePicker(
      context: context,
      initialDate: _dateOrToday,
      firstDate: DateTime(_today.year - 1, _today.month, _today.day),
      lastDate: DateTime(_today.year + 2, _today.month, _today.day),
      helpText: _interval == 0 ? 'Datum' : 'Erste Buchung',
    );
    if (d != null) setState(() => _date = d);
  }

  Future<void> _pickEnd() async {
    final d = await showDatePicker(
      context: context,
      initialDate: _endDate ?? DateTime(_dateOrToday.year + 1, _dateOrToday.month, _dateOrToday.day),
      firstDate: _dateOrToday,
      lastDate: DateTime(_today.year + 30),
      helpText: 'Letzte Buchung',
    );
    if (d != null) setState(() => _endDate = d);
  }

  /// Wie oft wird bis heute schon gebucht? (für den Hinweis unter dem Datum)
  int _occurrencesUntilToday() {
    if (!_inPast) return 0;
    if (_interval == 0) return 1;
    var n = 0;
    var d = _dateOrToday;
    while (!d.isAfter(_today)) {
      if (_endDate != null && d.isAfter(_endDate!)) break;
      n++;
      d = DateTime(d.year, d.month + _interval, d.day);
    }
    return n;
  }

  String? _dateProblem() {
    if (_interval > 0 && _dateOrToday.day > 28) {
      return 'Regelmäßig: bitte einen Tag zwischen 1. und 28. wählen (nicht jeder Monat hat mehr Tage).';
    }
    return null;
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final problem = _dateProblem();
    if (problem != null) {
      setState(() => _error = problem);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final r = await ref.read(entriesRepositoryProvider).create(
            kind: _kind,
            title: _title.text.trim(),
            amountCents: parseEuroToCents(_amount.text)!,
            intervalMonths: _interval,
            date: _dateOrToday,
            category: _category,
            endDate: _endDate,
          );
      refreshAfterEntryChange(ref);
      if (!mounted) return;
      final text = r.bookedNow == 0
          ? 'Eingetragen – gebucht wird am ${formatDate(r.entry.nextDate ?? _dateOrToday)}'
          : r.bookedNow == 1
              ? 'Gebucht: ${_signed(r.bookedNowCents)}'
              : '${r.bookedNow} Monate nachgebucht: ${_signed(r.bookedNowCents)}';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  static String _signed(int cents) => cents >= 0 ? '+${formatCents(cents)}' : formatCents(cents);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final income = _kind == 'INCOME';
    final categories = ref.watch(entryCategoriesProvider).valueOrNull ?? const <EntryCategory>[];
    final options = categories.where((c) => c.kind == _kind).toList();
    final suggestions = income ? incomeSuggestions : expenseSuggestions;
    final occurrences = _occurrencesUntilToday();
    final dateProblem = _dateProblem();

    return Scaffold(
      appBar: AppBar(title: Text(income ? 'Einnahme eintragen' : 'Ausgabe eintragen')),
      body: Form(
        key: _formKey,
        child: ListView(padding: safeListPadding(context), children: [
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(
                  value: 'INCOME',
                  label: Text('Einnahme', key: Key('entry_kind_income')),
                  icon: Icon(Icons.south_west)),
              ButtonSegment(
                  value: 'EXPENSE',
                  label: Text('Ausgabe', key: Key('entry_kind_expense')),
                  icon: Icon(Icons.north_east)),
            ],
            selected: {_kind},
            onSelectionChanged: (s) => _setKind(s.first),
          ),
          const SizedBox(height: 16),
          Wrap(spacing: 8, runSpacing: 4, children: [
            for (final s in suggestions)
              ActionChip(
                key: Key('entry_suggest_${s.title}'),
                label: Text(s.title),
                onPressed: () => _useSuggestion(s),
              ),
          ]),
          const SizedBox(height: 12),
          TextFormField(
            key: const Key('entry_title'),
            controller: _title,
            maxLength: 60,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(
              labelText: 'Bezeichnung',
              hintText: income ? 'z. B. Gehalt, Nebenjob' : 'z. B. Miete, Urlaub, Einkauf',
              prefixIcon: const Icon(Icons.edit_note),
            ),
            validator: (v) => (v == null || v.trim().isEmpty) ? 'Bitte eine Bezeichnung eingeben' : null,
          ),
          const SizedBox(height: 4),
          TextFormField(
            key: const Key('entry_amount'),
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Betrag', suffixText: '€', prefixIcon: Icon(Icons.euro)),
            validator: (v) {
              final c = parseEuroToCents(v ?? '');
              if (c == null || c <= 0) return 'Bitte gültigen Betrag eingeben';
              if (c > 5000000) return 'Höchstens 50.000,00 € je Eintrag';
              return null;
            },
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String?>(
            key: const Key('entry_category'),
            // ignore: deprecated_member_use
            value: options.any((o) => o.key == _category) ? _category : null,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Kategorie', prefixIcon: Icon(Icons.category)),
            items: [
              const DropdownMenuItem<String?>(value: null, child: Text('Automatisch (aus der Bezeichnung)')),
              for (final o in options) DropdownMenuItem<String?>(value: o.key, child: Text(o.label)),
            ],
            onChanged: (v) => setState(() => _category = v),
          ),
          const SizedBox(height: 16),
          Text('Wie oft?', style: theme.textTheme.titleSmall),
          const SizedBox(height: 6),
          Wrap(spacing: 8, runSpacing: 4, children: [
            for (final e in entryIntervals.entries)
              ChoiceChip(
                key: Key('entry_interval_${e.key}'),
                label: Text(e.value),
                selected: _interval == e.key,
                onSelected: (_) => setState(() {
                  _interval = e.key;
                  if (_interval == 0) _endDate = null;
                }),
              ),
          ]),
          const SizedBox(height: 8),
          ListTile(
            key: const Key('entry_date'),
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.event),
            title: Text(_interval == 0 ? 'Datum' : 'Erste Buchung'),
            subtitle: Text(_interval == 0
                ? formatDate(_dateOrToday)
                : '${formatDate(_dateOrToday)} – danach ${entryIntervals[_interval]} am ${_dateOrToday.day}.'),
            trailing: const Icon(Icons.chevron_right),
            onTap: _pickDate,
          ),
          if (_interval > 0)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.event_busy),
              title: const Text('Enddatum (optional)'),
              subtitle: Text(_endDate == null ? 'Unbegrenzt' : 'Bis ${formatDate(_endDate!)}'),
              trailing: _endDate == null
                  ? const Icon(Icons.chevron_right)
                  : IconButton(icon: const Icon(Icons.clear), onPressed: () => setState(() => _endDate = null)),
              onTap: _pickEnd,
            ),
          if (dateProblem != null)
            Text(dateProblem, style: TextStyle(color: theme.colorScheme.error))
          else
            Text(
              occurrences == 0
                  ? 'Gebucht wird am ${formatDate(_dateOrToday)}.'
                  : occurrences == 1
                      ? 'Wird sofort gebucht (mit diesem Datum).'
                      : 'Wird sofort ${occurrences}× gebucht – für jeden Termin seit ${formatDate(_dateOrToday)}.',
              style: theme.textTheme.bodySmall,
            ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
            ),
          const SizedBox(height: 16),
          FilledButton.icon(
            key: const Key('entry_save'),
            onPressed: _busy ? null : _save,
            icon: _busy
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.check),
            label: Text(income ? 'Einnahme speichern' : 'Ausgabe speichern'),
          ),
          const SizedBox(height: 8),
          Text(
            'Der Betrag wird auf dein Girokonto gebucht – als Geld von außerhalb der Bank (z. B. Gehalt vom '
            'Arbeitgeber) bzw. nach außerhalb (z. B. Miete an den Vermieter). So stimmen Kontostand, Umsätze und '
            'Finanzüberblick. Termine in der Vergangenheit (bis 12 Monate) werden sofort nachgebucht.',
            style: theme.textTheme.bodySmall,
          ),
        ]),
      ),
    );
  }
}
