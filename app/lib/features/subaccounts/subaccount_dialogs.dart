import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../accounts/account.dart';
import 'subaccount_icons.dart';
import 'subaccounts_repository.dart';

/// Dialog zum Anlegen (id == null) oder Bearbeiten eines digitalen Kontos: Name, Zweck, Symbol, optional Ziel.
/// Gibt true zurück, wenn gespeichert.
class SubaccountEditDialog extends ConsumerStatefulWidget {
  const SubaccountEditDialog({
    super.key,
    this.id,
    this.initialName = '',
    this.initialIcon = 'savings',
    this.initialPurpose = 'SPARZIEL',
    this.initialTargetCents,
    this.initialTargetDate,
  });

  final int? id;
  final String initialName;
  final String initialIcon;
  final String initialPurpose;
  final int? initialTargetCents;
  final DateTime? initialTargetDate;

  @override
  ConsumerState<SubaccountEditDialog> createState() => _SubaccountEditDialogState();
}

class _SubaccountEditDialogState extends ConsumerState<SubaccountEditDialog> {
  late final _name = TextEditingController(text: widget.initialName);
  late final _target = TextEditingController(
      text: widget.initialTargetCents == null ? '' : formatCents(widget.initialTargetCents!).replaceAll(RegExp(r'[^0-9,.]'), ''));
  late String _icon = widget.initialIcon;
  late String _purpose = digitalPurposes.containsKey(widget.initialPurpose) ? widget.initialPurpose : 'SONSTIGES';
  late DateTime? _date = widget.initialTargetDate;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    _target.dispose();
    super.dispose();
  }

  void _setPurpose(String p) {
    setState(() {
      // Symbol mitwechseln, solange es noch das Standard-Symbol des bisherigen Zwecks ist
      if (_icon == digitalPurposes[_purpose]?.$2) _icon = digitalPurposes[p]!.$2;
      _purpose = p;
    });
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: _date ?? DateTime(now.year + 1, now.month, 1),
      firstDate: now.add(const Duration(days: 1)),
      lastDate: DateTime(now.year + 40),
      helpText: tr('Zieldatum'),
    );
    if (d != null) setState(() => _date = d);
  }

  Future<void> _save() async {
    if (_name.text.trim().isEmpty) {
      setState(() => _error = tr('Bitte Namen eingeben'));
      return;
    }
    int? target;
    if (_target.text.trim().isNotEmpty) {
      target = parseEuroToCents(_target.text);
      if (target == null || target <= 0) {
        setState(() => _error = tr('Bitte gültigen Zielbetrag eingeben'));
        return;
      }
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final repo = ref.read(subaccountsRepositoryProvider);
      if (widget.id == null) {
        await repo.create(_name.text, _icon, purpose: _purpose, targetCents: target, targetDate: target == null ? null : _date);
      } else {
        await repo.rename(widget.id!, _name.text, _icon,
            purpose: _purpose, targetCents: target, targetDate: target == null ? null : _date);
      }
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
    final scheme = Theme.of(context).colorScheme;
    return AlertDialog(
      title: Text(widget.id == null ? tr('Neues digitales Konto') : tr('Digitales Konto bearbeiten')),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          TextField(
            key: const Key('digital_name'),
            controller: _name,
            maxLength: 40,
            autofocus: true,
            decoration: InputDecoration(labelText: tr('Name'), hintText: tr('z. B. Urlaub')),
          ),
          Text(tr('Zweck')),
          const SizedBox(height: 8),
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final e in digitalPurposes.entries)
              ChoiceChip(
                key: Key('digital_purpose_${e.key}'),
                avatar: Icon(subaccountIcon(e.value.$2), size: 18),
                label: Text(e.value.$1),
                selected: _purpose == e.key,
                showCheckmark: false,
                onSelected: (_) => _setPurpose(e.key),
              ),
          ]),
          const SizedBox(height: 12),
          TextField(
            key: const Key('digital_target'),
            controller: _target,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              labelText: tr('Zielbetrag (optional)'),
              suffixText: '€',
              helperText: tr('Für Fortschritt und Restbetrag'),
            ),
            onChanged: (_) => setState(() {}),
          ),
          if (_target.text.trim().isNotEmpty)
            ListTile(
              key: const Key('digital_date'),
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.event),
              title: Text(tr('Zieldatum (optional)')),
              subtitle: Text(_date == null ? tr('Kein Datum') : formatDate(_date!)),
              trailing: _date == null
                  ? const Icon(Icons.chevron_right)
                  : IconButton(icon: const Icon(Icons.clear), onPressed: () => setState(() => _date = null)),
              onTap: _pickDate,
            ),
          const SizedBox(height: 8),
          Text(tr('Symbol')),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final e in subaccountIcons.entries)
              ChoiceChip(
                label: Icon(e.value, size: 20,
                    color: _icon == e.key ? scheme.onSecondaryContainer : null),
                selected: _icon == e.key,
                showCheckmark: false,
                onSelected: (_) => setState(() => _icon = e.key),
              ),
          ]),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(_error!, style: TextStyle(color: scheme.error)),
            ),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(tr('Abbrechen'))),
        FilledButton(
            key: const Key('digital_save'), onPressed: _saving ? null : _save, child: Text(tr('Speichern'))),
      ],
    );
  }
}

/// Fragt einen Betrag ab. Gibt Cent zurück oder null bei Abbruch.
Future<int?> askAmount(BuildContext context, {required String title, required int maxCents}) {
  return showDialog<int>(
    context: context,
    builder: (_) => _AmountDialog(title: title, maxCents: maxCents),
  );
}

class _AmountDialog extends StatefulWidget {
  const _AmountDialog({required this.title, required this.maxCents});

  final String title;
  final int maxCents;

  @override
  State<_AmountDialog> createState() => _AmountDialogState();
}

class _AmountDialogState extends State<_AmountDialog> {
  final _amount = TextEditingController();
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
    if (cents > widget.maxCents) {
      setState(() => _error = tr('Höchstens {0} verfügbar', [formatCents(widget.maxCents)]));
      return;
    }
    Navigator.of(context).pop(cents);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        TextField(
          controller: _amount,
          autofocus: true,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(labelText: tr('Betrag'), suffixText: '€', errorText: _error),
          onSubmitted: (_) => _ok(),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: () => _amount.text = formatCents(widget.maxCents).replaceAll(' €', ''),
          child: Text(tr('Alles ({0})', [formatCents(widget.maxCents)])),
        ),
      ])),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(tr('Abbrechen'))),
        FilledButton(onPressed: _ok, child: Text(tr('Umbuchen'))),
      ],
    );
  }
}
