import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import 'standing_order_models.dart';
import 'standing_orders_screen.dart';
import '../../core/layout.dart';

/// /K030/ Neuen Dauerauftrag anlegen.
class StandingOrderFormScreen extends ConsumerStatefulWidget {
  const StandingOrderFormScreen({super.key, this.recipient});

  /// Vorbelegung, z. B. aus dem Adressbuch
  final String? recipient;

  @override
  ConsumerState<StandingOrderFormScreen> createState() => _StandingOrderFormScreenState();
}

class _StandingOrderFormScreenState extends ConsumerState<StandingOrderFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final _recipient = TextEditingController(text: widget.recipient ?? '');
  final _amount = TextEditingController();
  final _reference = TextEditingController();
  int _day = 1;
  int _interval = 1;
  DateTime? _endDate;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _recipient.dispose();
    _amount.dispose();
    _reference.dispose();
    super.dispose();
  }

  Future<void> _pickDay() async {
    final d = await showDialog<int>(context: context, builder: (_) => DayPickerDialog(selected: _day));
    if (d != null) setState(() => _day = d);
  }

  Future<void> _pickEnd() async {
    final now = DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: _endDate ?? DateTime(now.year + 1, now.month, _day),
      firstDate: now,
      lastDate: DateTime(now.year + 30),
    );
    if (d != null) setState(() => _endDate = d);
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(standingOrdersRepositoryProvider).create(
            recipient: _recipient.text.trim(),
            amountCents: parseEuroToCents(_amount.text)!,
            reference: _reference.text.trim(),
            executionDay: _day,
            intervalMonths: _interval,
            endDate: _endDate,
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr('Dauerauftrag eingerichtet'))));
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Neuer Dauerauftrag'))),
      body: Form(
        key: _formKey,
        child: ListView(padding: safeListPadding(context), children: [
          TextFormField(
            key: const Key('so_recipient'),
            controller: _recipient,
            decoration: InputDecoration(
              labelText: tr('Empfänger'),
              hintText: tr('IBAN, E-Mail oder Telefonnummer'),
              prefixIcon: Icon(Icons.person),
            ),
            validator: (v) => (v == null || v.trim().isEmpty) ? tr('Bitte Empfänger eingeben') : null,
          ),
          const SizedBox(height: 12),
          TextFormField(
            key: const Key('so_amount'),
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(labelText: tr('Betrag'), suffixText: '€', prefixIcon: Icon(Icons.euro)),
            validator: (v) {
              final c = parseEuroToCents(v ?? '');
              if (c == null || c <= 0) return tr('Bitte gültigen Betrag eingeben');
              if (c > 500000) return tr('Höchstens 5.000,00 €');
              return null;
            },
          ),
          const SizedBox(height: 12),
          TextFormField(
            key: const Key('so_reference'),
            controller: _reference,
            maxLength: 140,
            decoration: InputDecoration(
              labelText: tr('Verwendungszweck (optional)'),
              hintText: tr('z. B. Miete, Taschengeld'),
              prefixIcon: Icon(Icons.notes),
            ),
          ),
          const SizedBox(height: 4),
          Text(tr('Wie oft?'), style: theme.textTheme.titleSmall),
          const SizedBox(height: 6),
          Wrap(spacing: 8, runSpacing: 4, children: [
            for (final e in intervalLabels.entries)
              ChoiceChip(
                label: Text(tr(e.value)),
                selected: _interval == e.key,
                onSelected: (_) => setState(() => _interval = e.key),
              ),
          ]),
          const SizedBox(height: 8),
          ListTile(
            key: const Key('so_day'),
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.today),
            title: Text(tr('Ausführungstag')),
            subtitle: Text(tr('Am {0}. des Monats', [_day])),
            trailing: const Icon(Icons.chevron_right),
            onTap: _pickDay,
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.event_busy),
            title: Text(tr('Enddatum (optional)')),
            subtitle: Text(_endDate == null ? tr('Unbegrenzt') : tr('Bis {0}', [formatDate(_endDate!)])),
            trailing: _endDate == null
                ? const Icon(Icons.chevron_right)
                : IconButton(icon: const Icon(Icons.clear), onPressed: () => setState(() => _endDate = null)),
            onTap: _pickEnd,
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
            ),
          const SizedBox(height: 16),
          FilledButton.icon(
            key: const Key('so_save'),
            onPressed: _busy ? null : _save,
            icon: _busy
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.check),
            label: Text(tr('Dauerauftrag einrichten')),
          ),
          const SizedBox(height: 8),
          Text(tr('Die erste Ausführung ist am nächsten passenden Tag. Es gelten dieselben Regeln wie bei einer Überweisung (Deckung, Tageslimit). Reicht das Geld nicht, bekommst du eine Benachrichtigung.'),
              style: theme.textTheme.bodySmall),
        ]),
      ),
    );
  }
}
