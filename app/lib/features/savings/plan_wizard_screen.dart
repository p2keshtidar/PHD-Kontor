import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/charts.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../insights/insights_repository.dart';
import '../subaccounts/subaccount_icons.dart';
import 'plan_models.dart';
import 'plans_repository.dart';
import '../../core/layout.dart';

/// Assistent in 3 Schritten (Pflichtenheft B10):
/// Sparplan: Ziel -> Risikostufe -> Rate mit Ampel. Investplan: Betrag -> Risikostufe -> Prüfung.
class PlanWizardScreen extends ConsumerStatefulWidget {
  const PlanWizardScreen({super.key, required this.type});

  /// GOAL oder INVEST
  final String type;

  @override
  ConsumerState<PlanWizardScreen> createState() => _PlanWizardScreenState();
}

class _PlanWizardScreenState extends ConsumerState<PlanWizardScreen> {
  int _step = 0;
  final _name = TextEditingController();
  final _target = TextEditingController();
  final _rate = TextEditingController();
  String _icon = 'savings';
  DateTime? _targetDate;
  String _risk = 'VORSICHTIG';
  int _day = 1;

  PlanCheck? _check;
  bool _busy = false;
  String? _error;

  bool get _invest => widget.type == 'INVEST';

  @override
  void initState() {
    super.initState();
    if (_invest) {
      _icon = 'savings';
      _risk = 'AUSGEWOGEN';
      _rate.text = '200';
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _target.dispose();
    _rate.dispose();
    super.dispose();
  }

  // ---------------------------------------------------------------- Schritt-Logik

  String? _validateStep0() {
    if (_name.text.trim().isEmpty) return 'Bitte einen Namen eingeben';
    if (!_invest) {
      final t = parseEuroToCents(_target.text);
      if (t == null || t < 5000 || t > 10000000) return 'Zielbetrag zwischen 50 € und 100.000 €';
      if (_targetDate == null) return 'Bitte ein Wunschdatum wählen';
    } else {
      final r = parseEuroToCents(_rate.text);
      if (r == null || r < 1000 || r > 500000) return 'Monatsbetrag zwischen 10 € und 5.000 €';
      if (_target.text.trim().isNotEmpty && parseEuroToCents(_target.text) == null) return 'Zielbetrag ungültig';
    }
    return null;
  }

  Future<void> _next() async {
    setState(() => _error = null);
    if (_step == 0) {
      final err = _validateStep0();
      if (err != null) {
        setState(() => _error = err);
        return;
      }
      setState(() => _step = 1);
    } else if (_step == 1) {
      // beim Sparplan ohne eigene Rate: Vorschlag holen; beim Investplan: Betrag prüfen
      if (!_invest) _rate.clear();
      await _runCheck();
      if (_check != null) setState(() => _step = 2);
    }
  }

  Future<void> _runCheck() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final rate = _rate.text.trim().isEmpty ? null : parseEuroToCents(_rate.text);
      final targetText = _target.text.trim();
      final c = await ref.read(plansRepositoryProvider).check(
            type: widget.type,
            riskLevel: _risk,
            targetCents: targetText.isEmpty ? null : parseEuroToCents(targetText),
            targetDate: _invest ? null : _targetDate,
            monthlyRateCents: rate,
          );
      if (!mounted) return;
      setState(() {
        _check = c;
        if (_rate.text.trim().isEmpty) {
          _rate.text = formatCents(c.rateCents).replaceAll(' €', '');
        }
      });
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _create() async {
    final rate = parseEuroToCents(_rate.text);
    if (rate == null || rate < 100) {
      setState(() => _error = 'Bitte eine gültige Monatsrate eingeben');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final targetText = _target.text.trim();
      final plan = await ref.read(plansRepositoryProvider).create(
            type: widget.type,
            name: _name.text,
            icon: _icon,
            monthlyRateCents: rate,
            executionDay: _day,
            riskLevel: _risk,
            targetCents: targetText.isEmpty ? null : parseEuroToCents(targetText),
            targetDate: _invest ? null : _targetDate,
          );
      ref.invalidate(plansProvider);
      ref.invalidate(overviewProvider);
      if (mounted) context.pushReplacement('/plan/${plan.summary.id}');
    } catch (e) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = apiErrorMessage(e);
        });
      }
    }
  }

  Future<void> _pickDate() async {
    DateTime today;
    try {
      today = await ref.read(insightsRepositoryProvider).clock(); // Simulationsdatum
    } catch (_) {
      today = DateTime.now();
    }
    if (!mounted) return;
    final first = DateTime(today.year, today.month + 1, today.day);
    final picked = await showDatePicker(
      context: context,
      initialDate: _targetDate ?? DateTime(today.year + 1, today.month, 1),
      firstDate: first,
      lastDate: DateTime(today.year + 30),
      helpText: 'Bis wann?',
    );
    if (picked != null) setState(() => _targetDate = picked);
  }

  // ---------------------------------------------------------------- Oberfläche

  @override
  Widget build(BuildContext context) {
    final titles = _invest
        ? ['Betrag', 'Risikostufe', 'Prüfung']
        : ['Ziel', 'Risikostufe', 'Sparrate'];
    return Scaffold(
      appBar: AppBar(title: Text(_invest ? 'Neuer Investplan' : 'Neuer Sparplan')),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: Row(children: [
            for (var i = 0; i < 3; i++) ...[
              Expanded(
                child: Column(children: [
                  LinearProgressIndicator(value: i <= _step ? 1 : 0, minHeight: 4),
                  const SizedBox(height: 4),
                  Text('${i + 1}. ${titles[i]}',
                      style: TextStyle(fontWeight: i == _step ? FontWeight.bold : FontWeight.normal, fontSize: 12)),
                ]),
              ),
              if (i < 2) const SizedBox(width: 8),
            ],
          ]),
        ),
        Expanded(
          child: ListView(
            padding: safeListPadding(context),
            children: [
              if (_step == 0) _invest ? _investStep() : _goalStep(),
              if (_step == 1) _riskStep(),
              if (_step == 2) _rateStep(),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ),
            ],
          ),
        ),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(children: [
              if (_step > 0)
                Expanded(
                  child: OutlinedButton(
                    onPressed: _busy ? null : () => setState(() => _step--),
                    child: const Text('Zurück'),
                  ),
                ),
              if (_step > 0) const SizedBox(width: 12),
              Expanded(
                child: FilledButton(
                  onPressed: _busy ? null : (_step < 2 ? _next : _create),
                  child: _busy
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : Text(_step < 2 ? 'Weiter' : 'Plan anlegen'),
                ),
              ),
            ]),
          ),
        ),
      ]),
    );
  }

  Widget _iconPicker() => Wrap(spacing: 8, runSpacing: 8, children: [
        for (final e in subaccountIcons.entries)
          ChoiceChip(
            label: Icon(e.value, size: 20),
            selected: _icon == e.key,
            showCheckmark: false,
            onSelected: (_) => setState(() => _icon = e.key),
          ),
      ]);

  Widget _goalStep() => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text('Wofür möchtest du sparen?', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 12),
        TextField(
          controller: _name,
          maxLength: 60,
          decoration: const InputDecoration(labelText: 'Name', hintText: 'z. B. Urlaub in Italien'),
        ),
        _iconPicker(),
        const SizedBox(height: 16),
        TextField(
          controller: _target,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(labelText: 'Zielbetrag', suffixText: '€', hintText: 'z. B. 3000'),
        ),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          onPressed: _pickDate,
          icon: const Icon(Icons.event),
          label: Text(_targetDate == null ? 'Wunschdatum wählen' : 'Bis ${formatDate(_targetDate!)}'),
        ),
      ]);

  Widget _investStep() => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text('Wie viel möchtest du monatlich investieren?', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 12),
        TextField(
          controller: _name,
          maxLength: 60,
          decoration: const InputDecoration(labelText: 'Name', hintText: 'z. B. Altersvorsorge'),
        ),
        TextField(
          controller: _rate,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(labelText: 'Monatsbetrag', suffixText: '€'),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _target,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(
              labelText: 'Zielbetrag (optional)', suffixText: '€', helperText: 'Ohne Ziel: einfach dauerhaft investieren'),
        ),
        const SizedBox(height: 12),
        Text(
          'Das Geld fließt vollständig in ein Musterdepot aus zwei fiktiven Fonds (Welt-Aktien und Euro-Anleihen). '
          'Gerechnet wird mit Spielgeld.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ]);

  Widget _riskStep() => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text('Wie viel Risiko passt zu dir?', style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 12),
        for (final e in riskLevels.entries)
          Card(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
              side: BorderSide(
                color: _risk == e.key ? Theme.of(context).colorScheme.primary : Colors.transparent,
                width: 2,
              ),
            ),
            child: RadioListTile<String>(
              value: e.key,
              groupValue: _risk,
              onChanged: (v) => setState(() => _risk = v!),
              title: Text(e.value, style: const TextStyle(fontWeight: FontWeight.bold)),
              subtitle: Text(riskDescription(e.key, invest: _invest)),
            ),
          ),
        const SizedBox(height: 8),
        Text(
          'Renditen sind Annahmen für die Simulation und keine Prognose echter Märkte.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ]);

  Widget _rateStep() {
    final c = _check;
    final theme = Theme.of(context);
    if (c == null) return const SizedBox.shrink();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Row(children: [
        Expanded(child: Text('Machbarkeit', style: theme.textTheme.titleMedium)),
        FeasibilityBadge(level: c.feasibility),
      ]),
      const SizedBox(height: 8),
      Text(c.message),
      const SizedBox(height: 16),
      if (c.hasData)
        Card(
          child: ListTile(
            leading: const Icon(Icons.account_balance_wallet),
            title: const Text('Verfügbar für Pläne'),
            subtitle: const Text('Ø freier Betrag minus laufende Pläne'),
            trailing: Text(formatCents(c.availableCents), style: const TextStyle(fontWeight: FontWeight.bold)),
          ),
        ),
      if (c.requiredRateCents != null)
        Card(
          child: ListTile(
            leading: const Icon(Icons.calculate),
            title: const Text('Nötige Rate bis zum Wunschdatum'),
            trailing: Text(formatCents(c.requiredRateCents!), style: const TextStyle(fontWeight: FontWeight.bold)),
          ),
        ),
      if (c.alternativeRateCents != null)
        Card(
          color: theme.colorScheme.tertiaryContainer,
          child: ListTile(
            leading: const Icon(Icons.lightbulb),
            title: Text('Vorschlag: ${formatCents(c.alternativeRateCents!)} pro Monat'),
            subtitle: c.alternativeDate == null ? null : Text('Ziel erreicht am ${formatDate(c.alternativeDate!)}'),
            trailing: TextButton(
              onPressed: () {
                _rate.text = formatCents(c.alternativeRateCents!).replaceAll(' €', '');
                _runCheck();
              },
              child: const Text('Übernehmen'),
            ),
          ),
        ),
      const SizedBox(height: 12),
      Row(children: [
        Expanded(
          child: TextField(
            controller: _rate,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Monatsrate', suffixText: '€'),
          ),
        ),
        const SizedBox(width: 8),
        OutlinedButton(onPressed: _busy ? null : _runCheck, child: const Text('Neu prüfen')),
      ]),
      if (c.projectedDate != null)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text('Mit ${formatCents(c.rateCents)} pro Monat erreichst du das Ziel voraussichtlich am '
              '${formatDate(c.projectedDate!)} (erwartete Rendite ${(c.expectedReturnPa * 100).toStringAsFixed(1).replaceAll('.', ',')} % p. a.).'),
        ),
      const SizedBox(height: 16),
      DropdownButtonFormField<int>(
        value: _day,
        decoration: const InputDecoration(labelText: 'Abbuchung am', helperText: 'Tag im Monat, z. B. kurz nach dem Gehalt'),
        items: [for (var d = 1; d <= 28; d++) DropdownMenuItem(value: d, child: Text('$d.'))],
        onChanged: (v) => setState(() => _day = v ?? 1),
      ),
    ]);
  }
}
