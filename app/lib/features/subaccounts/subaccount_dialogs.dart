import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import 'subaccount_icons.dart';
import 'subaccounts_repository.dart';

/// Dialog zum Anlegen (id == null) oder Umbenennen eines Unterkontos. Gibt true zurück, wenn gespeichert.
class SubaccountEditDialog extends ConsumerStatefulWidget {
  const SubaccountEditDialog({super.key, this.id, this.initialName = '', this.initialIcon = 'savings'});

  final int? id;
  final String initialName;
  final String initialIcon;

  @override
  ConsumerState<SubaccountEditDialog> createState() => _SubaccountEditDialogState();
}

class _SubaccountEditDialogState extends ConsumerState<SubaccountEditDialog> {
  late final _name = TextEditingController(text: widget.initialName);
  late String _icon = widget.initialIcon;
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_name.text.trim().isEmpty) {
      setState(() => _error = 'Bitte Namen eingeben');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final repo = ref.read(subaccountsRepositoryProvider);
      if (widget.id == null) {
        await repo.create(_name.text, _icon);
      } else {
        await repo.rename(widget.id!, _name.text, _icon);
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
      title: Text(widget.id == null ? 'Neues Unterkonto' : 'Unterkonto bearbeiten'),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          TextField(
            controller: _name,
            maxLength: 40,
            autofocus: true,
            decoration: const InputDecoration(labelText: 'Name', hintText: 'z. B. Urlaub'),
          ),
          const Text('Symbol'),
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
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Abbrechen')),
        FilledButton(onPressed: _saving ? null : _save, child: const Text('Speichern')),
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
      setState(() => _error = 'Bitte gültigen Betrag eingeben');
      return;
    }
    if (cents > widget.maxCents) {
      setState(() => _error = 'Höchstens ${formatCents(widget.maxCents)} verfügbar');
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
          decoration: InputDecoration(labelText: 'Betrag', suffixText: '€', errorText: _error),
          onSubmitted: (_) => _ok(),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: () => _amount.text = formatCents(widget.maxCents).replaceAll(' €', ''),
          child: Text('Alles (${formatCents(widget.maxCents)})'),
        ),
      ])),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Abbrechen')),
        FilledButton(onPressed: _ok, child: const Text('Umbuchen')),
      ],
    );
  }
}
