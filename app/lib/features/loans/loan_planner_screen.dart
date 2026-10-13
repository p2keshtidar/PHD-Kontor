import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../insights/demo_dialog.dart';
import '../insights/insights_repository.dart';
import 'loan_models.dart';
import 'loans_repository.dart';
import '../../core/layout.dart';

/// Kreditplaner (Pflichtenheft B12): Wunsch eingeben, Tragfähigkeit sehen, Angebote vergleichen.
class LoanPlannerScreen extends ConsumerStatefulWidget {
  const LoanPlannerScreen({super.key});

  @override
  ConsumerState<LoanPlannerScreen> createState() => _LoanPlannerScreenState();
}

class _LoanPlannerScreenState extends ConsumerState<LoanPlannerScreen> {
  LoanPurposeInfo _purpose = loanPurposes.first;
  final _amount = TextEditingController(text: '20000');
  final _equity = TextEditingController(text: '0');
  int _term = 60;
  OfferResult? _result;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    _equity.dispose();
    super.dispose();
  }

  void _selectPurpose(LoanPurposeInfo p) {
    setState(() {
      _purpose = p;
      _amount.text = '${p.defaultEuro}';
      _term = p.defaultTerm;
      _result = null;
      _error = null;
    });
  }

  Future<void> _compare() async {
    final amount = parseEuroToCents(_amount.text);
    final equity = _equity.text.trim().isEmpty ? 0 : parseEuroToCents(_equity.text);
    if (amount == null || amount < _purpose.minEuro * 100 || amount > _purpose.maxEuro * 100) {
      setState(() => _error = 'Kreditbetrag zwischen ${formatCents(_purpose.minEuro * 100)} und ${formatCents(_purpose.maxEuro * 100)}');
      return;
    }
    if (equity == null) {
      setState(() => _error = 'Eigenkapital ungültig');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final r = await ref.read(loansRepositoryProvider).offers(_purpose.key, amount, equity, _term);
      if (mounted) setState(() => _result = r);
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final overview = ref.watch(overviewProvider);
    final theme = Theme.of(context);
    final steps = ((_purpose.maxTerm - _purpose.minTerm) / 12).round();
    return Scaffold(
      appBar: AppBar(title: const Text('Kredit planen')),
      body: ListView(padding: safeListPadding(context), children: [
        overview.maybeWhen(
          data: (o) => o.hasData
              ? Card(
                  color: theme.colorScheme.primaryContainer,
                  child: ListTile(
                    leading: const Icon(Icons.verified),
                    title: Text('Tragbare Rate: bis ${formatCents(o.maxLoanRateCents)} pro Monat'),
                    subtitle: Text('70 % von ${formatCents(o.availableCents)}, die dir nach Ausgaben und Plänen bleiben'),
                  ),
                )
              : Card(
                  color: theme.colorScheme.secondaryContainer,
                  child: ListTile(
                    leading: const Icon(Icons.info_outline),
                    title: const Text('Noch keine Einnahmen bekannt'),
                    subtitle: const Text('Ohne Einnahmen lehnen alle Banken ab. Erzeuge zuerst Beispielmonate.'),
                    trailing: TextButton(
                      onPressed: () async {
                        if (await showDemoDialog(context)) ref.invalidate(overviewProvider);
                      },
                      child: const Text('Erzeugen'),
                    ),
                  ),
                ),
          orElse: () => const SizedBox.shrink(),
        ),
        const SizedBox(height: 16),
        Text('Wofür?', style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        SegmentedButton<String>(
          segments: [for (final p in loanPurposes) ButtonSegment(value: p.key, label: Text(p.label))],
          selected: {_purpose.key},
          onSelectionChanged: (s) => _selectPurpose(loanPurposes.firstWhere((p) => p.key == s.first)),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _amount,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(
            labelText: 'Kreditbetrag',
            suffixText: '€',
            helperText: '${formatCents(_purpose.minEuro * 100)} bis ${formatCents(_purpose.maxEuro * 100)}',
          ),
          onChanged: (_) => setState(() => _result = null),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _equity,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(
            labelText: 'Eigenkapital (optional)',
            suffixText: '€',
            helperText: 'Ab 20 % Eigenanteil gibt es einen Zinsrabatt',
          ),
          onChanged: (_) => setState(() => _result = null),
        ),
        const SizedBox(height: 16),
        Text('Laufzeit: $_term Monate (${(_term / 12).toStringAsFixed(_term % 12 == 0 ? 0 : 1).replaceAll('.', ',')} Jahre)'),
        Slider(
          value: _term.toDouble(),
          min: _purpose.minTerm.toDouble(),
          max: _purpose.maxTerm.toDouble(),
          divisions: steps,
          label: '$_term Monate',
          onChanged: (v) => setState(() {
            _term = v.round();
            _result = null;
          }),
        ),
        if (_error != null) Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
        const SizedBox(height: 8),
        FilledButton.icon(
          onPressed: _busy ? null : _compare,
          icon: _busy
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.compare_arrows),
          label: const Text('Angebote vergleichen'),
        ),
        if (_result != null) ..._offers(_result!),
      ]),
    );
  }

  List<Widget> _offers(OfferResult r) {
    final theme = Theme.of(context);
    return [
      const SizedBox(height: 20),
      Text('Angebote (${r.offers.length})', style: theme.textTheme.titleMedium),
      if (r.hasData)
        Padding(
          padding: const EdgeInsets.only(top: 4, bottom: 8),
          child: Text('Bei ${r.termMonths} Monaten wären höchstens ca. ${formatCents(r.maxPrincipalCents)} tragbar.',
              style: theme.textTheme.bodySmall),
        ),
      for (final o in r.offers)
        Card(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(color: o.recommended ? theme.colorScheme.primary : Colors.transparent, width: 2),
          ),
          child: InkWell(
            borderRadius: BorderRadius.circular(12),
            onTap: () => context.push('/kredit/angebot', extra: SelectedOffer(r, o)),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Expanded(child: Text(o.bankName, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16))),
                  if (o.recommended)
                    const Chip(label: Text('Empfohlen'), avatar: Icon(Icons.star, size: 16), visualDensity: VisualDensity.compact),
                ]),
                const SizedBox(height: 6),
                Row(children: [
                  Expanded(child: _kv('Zins p. a.', formatPercent(o.interestRate))),
                  Expanded(child: _kv('Monatsrate', formatCents(o.monthlyRateCents))),
                  Expanded(child: _kv('Zinskosten', formatCents(o.totalInterestCents))),
                ]),
                const SizedBox(height: 6),
                Text('Gesamtkosten ${formatCents(o.totalCostCents)}', style: theme.textTheme.bodySmall),
                if (o.declined)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text('Abgelehnt: ${o.declineReason}', style: TextStyle(color: theme.colorScheme.error)),
                  )
                else if (!o.affordable)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text('Achtung: Rate über deiner tragbaren Rate', style: TextStyle(color: Colors.orange.shade800)),
                  ),
              ]),
            ),
          ),
        ),
      const SizedBox(height: 8),
      Text('Alle Banken und Zinssätze sind fiktiv und nach festen Regeln berechnet (Simulation).',
          style: theme.textTheme.bodySmall),
    ];
  }

  Widget _kv(String k, String v) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(k, style: Theme.of(context).textTheme.bodySmall),
        Text(v, style: const TextStyle(fontWeight: FontWeight.w600)),
      ]);
}
