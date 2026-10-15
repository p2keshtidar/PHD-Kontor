import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import '../insights/insights_repository.dart';
import 'allocation_models.dart';
import 'allocations_repository.dart';

AllocationSource? _firstUsable(Iterable<AllocationSource> list) {
  for (final s in list) {
    if (s.usable) return s;
  }
  return null;
}

/// Neue Sparregel für ein digitales Konto: Quelle, Betrag, Rhythmus, Start und optional Ende.
/// Gibt true zurück, wenn gespeichert.
class AllocationRuleDialog extends ConsumerStatefulWidget {
  const AllocationRuleDialog({super.key, required this.accountId, required this.accountName});

  final int accountId;
  final String accountName;

  @override
  ConsumerState<AllocationRuleDialog> createState() => _AllocationRuleDialogState();
}

class _AllocationRuleDialogState extends ConsumerState<AllocationRuleDialog> {
  final _amount = TextEditingController();
  AllocationSource? _source;
  String _interval = 'MONTHLY';
  DateTime? _start;
  DateTime? _end;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  Future<DateTime?> _pick(DateTime first, DateTime? initial, String help) async {
    return showDatePicker(
      context: context,
      initialDate: initial ?? first,
      firstDate: first,
      lastDate: DateTime(first.year + 10),
      helpText: help,
    );
  }

  Future<void> _save(List<AllocationSource> sources) async {
    final source = _source ?? _firstUsable(sources);
    final cents = parseEuroToCents(_amount.text);
    if (source == null) {
      setState(() => _error = tr('Bitte eine Quelle wählen'));
      return;
    }
    if (cents == null || cents <= 0) {
      setState(() => _error = tr('Bitte gültigen Betrag eingeben'));
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(allocationsRepositoryProvider).createRule(
            targetAccountId: widget.accountId,
            sourceType: source.type,
            sourceId: source.isProjektbank ? null : source.id,
            amountCents: cents,
            interval: _interval,
            startDate: _start,
            endDate: _end,
          );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = apiErrorMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final sources = ref.watch(allocationSourcesProvider);
    final today = ref.watch(clockProvider).valueOrNull ?? DateTime.now();
    final theme = Theme.of(context);
    return AlertDialog(
      title: Text(tr('Sparregel für „{0}“', [widget.accountName])),
      content: SingleChildScrollView(
        child: sources.when(
          loading: () => const Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator()),
          error: (e, _) => Text(apiErrorMessage(e)),
          data: (list) {
            final selected = _source ?? _firstUsable(list);
            return Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(tr('Woher kommt das Geld?'), style: theme.textTheme.titleSmall),
              for (final s in list)
                RadioListTile<int>(
                  key: Key('alloc_source_${s.type}_${s.id}'),
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  value: list.indexOf(s),
                  // ignore: deprecated_member_use
                  groupValue: selected == null ? null : list.indexOf(selected),
                  // ignore: deprecated_member_use
                  onChanged: s.usable ? (_) => setState(() => _source = s) : null,
                  title: Text(s.label),
                  subtitle: Text(!s.usable
                      ? (s.unusableReason ?? tr('Nicht verfügbar'))
                      : s.movesMoney
                          ? s.explanation
                          : '${s.explanation}\n${tr('Frei: {0}', [formatCents(s.availableCents ?? 0)])}'),
                ),
              const SizedBox(height: 8),
              TextField(
                key: const Key('alloc_amount'),
                controller: _amount,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(labelText: tr('Betrag je Termin'), suffixText: '€'),
              ),
              const SizedBox(height: 12),
              Text(tr('Rhythmus'), style: theme.textTheme.titleSmall),
              Wrap(spacing: 6, runSpacing: 4, children: [
                for (final e in allocationIntervals.entries)
                  ChoiceChip(
                    key: Key('alloc_interval_${e.key}'),
                    label: Text(e.value),
                    selected: _interval == e.key,
                    onSelected: (_) => setState(() => _interval = e.key),
                  ),
              ]),
              const SizedBox(height: 8),
              ListTile(
                key: const Key('alloc_start'),
                contentPadding: EdgeInsets.zero,
                dense: true,
                leading: const Icon(Icons.event),
                title: Text(_start == null ? tr('Start: heute') : tr('Start: {0}', [formatDate(_start!)])),
                subtitle: Text(tr('Zum Ändern tippen')),
                onTap: () async {
                  final d = await _pick(today, _start, tr('Start'));
                  if (d != null) setState(() => _start = d);
                },
              ),
              ListTile(
                key: const Key('alloc_end'),
                contentPadding: EdgeInsets.zero,
                dense: true,
                leading: const Icon(Icons.event_busy),
                title: Text(_end == null ? tr('Ohne Enddatum') : tr('Ende: {0}', [formatDate(_end!)])),
                trailing: _end == null
                    ? null
                    : IconButton(
                        tooltip: tr('Kein Enddatum'),
                        icon: const Icon(Icons.clear),
                        onPressed: () => setState(() => _end = null),
                      ),
                onTap: () async {
                  final d = await _pick(_start ?? today, _end, tr('Ende'));
                  if (d != null) setState(() => _end = d);
                },
              ),
              if (selected != null && !selected.movesMoney)
                Text(tr('Es wird kein Geld bewegt: Der Betrag wird nur vorgemerkt und deinem Ziel zugerechnet.'),
                    style: theme.textTheme.bodySmall),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
                ),
            ]);
          },
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(tr('Abbrechen'))),
        FilledButton(
          key: const Key('alloc_save'),
          onPressed: _saving || !sources.hasValue ? null : () => _save(sources.value!),
          child: Text(tr('Speichern')),
        ),
      ],
    );
  }
}

/// Zusätzliche Vormerkung aus einem verbundenen Bankkonto. Gibt true zurück, wenn vorgemerkt.
class EarmarkDialog extends ConsumerStatefulWidget {
  const EarmarkDialog({super.key, required this.accountId, required this.accountName});

  final int accountId;
  final String accountName;

  @override
  ConsumerState<EarmarkDialog> createState() => _EarmarkDialogState();
}

class _EarmarkDialogState extends ConsumerState<EarmarkDialog> {
  final _amount = TextEditingController();
  AllocationSource? _source;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  Future<void> _save(AllocationSource? source) async {
    final cents = parseEuroToCents(_amount.text);
    if (source == null) {
      setState(() => _error = tr('Bitte eine Quelle wählen'));
      return;
    }
    if (cents == null || cents <= 0) {
      setState(() => _error = tr('Bitte gültigen Betrag eingeben'));
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(allocationsRepositoryProvider).earmark(widget.accountId, source.id, cents);
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = apiErrorMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final sources = ref.watch(allocationSourcesProvider);
    final theme = Theme.of(context);
    final external = sources.valueOrNull?.where((s) => !s.movesMoney).toList() ?? const <AllocationSource>[];
    final selected = _source ?? _firstUsable(external);
    return AlertDialog(
      title: Text(tr('Für „{0}“ vormerken', [widget.accountName])),
      content: SingleChildScrollView(
        child: sources.when(
          loading: () => const Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator()),
          error: (e, _) => Text(apiErrorMessage(e)),
          data: (_) => Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(tr('Das Geld bleibt auf deinem Konto bei der anderen Bank. Es wird keine Zahlung ausgelöst – der Betrag wird nur deinem Ziel zugerechnet.'),
                style: theme.textTheme.bodySmall),
            const SizedBox(height: 8),
            if (external.isEmpty)
              Text(tr('Verbinde zuerst ein Bankkonto unter „Bankkonten“.'), key: const Key('alloc_no_bank'))
            else
              for (final s in external)
                RadioListTile<int>(
                  key: Key('alloc_earmark_source_${s.id}'),
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  value: s.id,
                  // ignore: deprecated_member_use
                  groupValue: selected?.id,
                  // ignore: deprecated_member_use
                  onChanged: s.usable ? (_) => setState(() => _source = s) : null,
                  title: Text(s.label),
                  subtitle: Text(s.usable
                      ? tr('Frei: {0}', [formatCents(s.availableCents ?? 0)])
                      : (s.unusableReason ?? tr('Nicht verfügbar'))),
                ),
            if (external.isNotEmpty)
              TextField(
                key: const Key('alloc_earmark_amount'),
                controller: _amount,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(labelText: tr('Betrag'), suffixText: '€'),
              ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
              ),
          ]),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(tr('Abbrechen'))),
        if (external.isNotEmpty)
          FilledButton(
            key: const Key('alloc_earmark_save'),
            onPressed: _saving ? null : () => _save(selected),
            child: Text(tr('Vormerken')),
          ),
      ],
    );
  }
}

/// Neuen Betrag für eine Sparregel abfragen.
Future<int?> askRuleAmount(BuildContext context, int currentCents) => showDialog<int>(
      context: context,
      builder: (_) => _RuleAmountDialog(currentCents: currentCents),
    );

class _RuleAmountDialog extends StatefulWidget {
  const _RuleAmountDialog({required this.currentCents});

  final int currentCents;

  @override
  State<_RuleAmountDialog> createState() => _RuleAmountDialogState();
}

class _RuleAmountDialogState extends State<_RuleAmountDialog> {
  late final _amount = TextEditingController(text: formatCents(widget.currentCents).replaceAll(RegExp(r'[^0-9,.]'), ''));
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  void _ok() {
    final cents = parseEuroToCents(_amount.text);
    if (cents == null || cents <= 0) {
      setState(() => _error = tr('Bitte gültigen Betrag eingeben'));
      return;
    }
    Navigator.of(context).pop(cents);
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text(tr('Betrag ändern')),
        content: TextField(
          key: const Key('alloc_new_amount'),
          controller: _amount,
          autofocus: true,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(labelText: tr('Betrag je Termin'), suffixText: '€', errorText: _error),
          onSubmitted: (_) => _ok(),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(tr('Abbrechen'))),
          FilledButton(key: const Key('alloc_new_amount_ok'), onPressed: _ok, child: Text(tr('Speichern'))),
        ],
      );
}
