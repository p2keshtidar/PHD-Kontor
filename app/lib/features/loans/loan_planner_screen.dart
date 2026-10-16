import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../insights/demo_dialog.dart';
import '../insights/insights_repository.dart';
import 'loan_models.dart';
import 'loans_repository.dart';
import '../../core/layout.dart';

/// Betrag in Cent als Eingabetext: ganze Euro ohne Nachkommastellen, sonst mit Komma ("1700000" → "17000").
String euroInput(int cents) =>
    cents % 100 == 0 ? '${cents ~/ 100}' : (cents / 100).toStringAsFixed(2).replaceAll('.', ',');

/// Laufzeit auf den Regler des Kreditzwecks bringen: innerhalb der Grenzen, in Jahresschritten ab der kürzesten.
int snapTerm(LoanPurposeInfo p, int months) {
  final clamped = months.clamp(p.minTerm, p.maxTerm).toInt();
  return (p.minTerm + ((clamped - p.minTerm) / 12).round() * 12).clamp(p.minTerm, p.maxTerm).toInt();
}

/// Kreditplaner (Pflichtenheft B12): Wunsch eingeben, Tragfähigkeit sehen, Angebote vergleichen.
class LoanPlannerScreen extends ConsumerStatefulWidget {
  const LoanPlannerScreen({super.key, this.purpose, this.amountCents, this.equityCents, this.termMonths});

  /// Vorbelegung aus der Adresse, z. B. vom Planer: /kredit/neu?purpose=AUTO&amount=1700000&equity=300000&term=48
  /// (Beträge in Cent). Fehlende oder ungültige Werte bleiben bei den Vorgaben des Kreditzwecks.
  factory LoanPlannerScreen.fromQuery(Map<String, String> q) => LoanPlannerScreen(
        purpose: q['purpose'],
        amountCents: int.tryParse(q['amount'] ?? ''),
        equityCents: int.tryParse(q['equity'] ?? ''),
        termMonths: int.tryParse(q['term'] ?? ''),
      );

  final String? purpose;
  final int? amountCents;
  final int? equityCents;
  final int? termMonths;

  @override
  ConsumerState<LoanPlannerScreen> createState() => _LoanPlannerScreenState();
}

class _LoanPlannerScreenState extends ConsumerState<LoanPlannerScreen> {
  LoanPurposeInfo _purpose = loanPurposes.first;
  final _amount = TextEditingController(text: '20000');
  final _equity = TextEditingController(text: '0');
  final _maxRate = TextEditingController();
  final _budget = TextEditingController();
  String _class = 'GUT';
  bool _onlyEligible = false;
  int _term = 60;
  OfferResult? _result;
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    for (final p in loanPurposes) {
      if (p.key == widget.purpose) {
        _purpose = p;
        _amount.text = '${p.defaultEuro}';
        _term = p.defaultTerm;
      }
    }
    final amount = widget.amountCents, equity = widget.equityCents, term = widget.termMonths;
    if (amount != null && amount > 0) _amount.text = euroInput(amount);
    if (equity != null && equity >= 0) _equity.text = euroInput(equity);
    if (term != null) _term = snapTerm(_purpose, term);
  }

  @override
  void dispose() {
    _amount.dispose();
    _equity.dispose();
    _maxRate.dispose();
    _budget.dispose();
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
      setState(() => _error = tr('Kreditbetrag zwischen {0} und {1}', [formatCents(_purpose.minEuro * 100), formatCents(_purpose.maxEuro * 100)]));
      return;
    }
    if (equity == null) {
      setState(() => _error = tr('Eigenkapital ungültig'));
      return;
    }
    final maxRate = _maxRate.text.trim().isEmpty ? null : parseEuroToCents(_maxRate.text);
    final budget = _budget.text.trim().isEmpty ? null : parseEuroToCents(_budget.text);
    if ((_maxRate.text.trim().isNotEmpty && (maxRate == null || maxRate <= 0)) ||
        (_budget.text.trim().isNotEmpty && (budget == null || budget <= 0))) {
      setState(() => _error = tr('Filter: bitte gültige Beträge eingeben'));
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final r = await ref.read(loansRepositoryProvider).offers(_purpose.key, amount, equity, _term,
          creditClass: _class, maxMonthlyRateCents: maxRate, maxTotalCents: budget, onlyEligible: _onlyEligible);
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
      appBar: AppBar(title: Text(tr('Kredit planen'))),
      body: ListView(padding: safeListPadding(context), children: [
        overview.maybeWhen(
          data: (o) => o.hasData
              ? Card(
                  color: theme.colorScheme.primaryContainer,
                  child: ListTile(
                    leading: const Icon(Icons.verified),
                    title: Text(tr('Tragbare Rate: bis {0} pro Monat', [formatCents(o.maxLoanRateCents)])),
                    subtitle: Text(tr('70 % von {0}, die dir nach Ausgaben und Plänen bleiben', [formatCents(o.availableCents)])),
                  ),
                )
              : Card(
                  color: theme.colorScheme.secondaryContainer,
                  child: ListTile(
                    leading: const Icon(Icons.info_outline),
                    title: Text(tr('Noch keine Einnahmen bekannt')),
                    subtitle: Text(tr('Ohne Einnahmen lehnen alle Banken ab. Erzeuge zuerst Beispielmonate.')),
                    trailing: TextButton(
                      onPressed: () async {
                        if (await showDemoDialog(context)) ref.invalidate(overviewProvider);
                      },
                      child: Text(tr('Erzeugen')),
                    ),
                  ),
                ),
          orElse: () => const SizedBox.shrink(),
        ),
        const SizedBox(height: 16),
        Text(tr('Wofür?'), style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        SegmentedButton<String>(
          segments: [for (final p in loanPurposes) ButtonSegment(value: p.key, label: Text(tr(p.label)))],
          selected: {_purpose.key},
          onSelectionChanged: (s) => _selectPurpose(loanPurposes.firstWhere((p) => p.key == s.first)),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _amount,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(
            labelText: tr('Kreditbetrag'),
            suffixText: '€',
            helperText: tr('{0} bis {1}', [formatCents(_purpose.minEuro * 100), formatCents(_purpose.maxEuro * 100)]),
          ),
          onChanged: (_) => setState(() => _result = null),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _equity,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(
            labelText: tr('Eigenkapital (optional)'),
            suffixText: '€',
            helperText: tr('Ab 20 % Eigenanteil gibt es einen Zinsrabatt'),
          ),
          onChanged: (_) => setState(() => _result = null),
        ),
        const SizedBox(height: 16),
        Text(tr('Laufzeit: {0} Monate ({1} Jahre)', [_term, (_term / 12).toStringAsFixed(_term % 12 == 0 ? 0 : 1).localDecimal])),
        Slider(
          value: _term.toDouble(),
          min: _purpose.minTerm.toDouble(),
          max: _purpose.maxTerm.toDouble(),
          divisions: steps,
          label: tr('{0} Monate', [_term]),
          onChanged: (v) => setState(() {
            _term = v.round();
            _result = null;
          }),
        ),
        const SizedBox(height: 8),
        Text(tr('Bonität (Selbsteinschätzung)'), style: theme.textTheme.titleSmall),
        const SizedBox(height: 4),
        Wrap(spacing: 6, runSpacing: 4, children: [
          for (final c in creditClasses.entries)
            ChoiceChip(
              key: Key('loan_class_${c.key}'),
              label: Text(c.value),
              selected: _class == c.key,
              onSelected: (_) => setState(() {
                _class = c.key;
                _result = null;
              }),
            ),
        ]),
        Text(tr('Keine Abfrage bei einer Auskunftei – die Angabe bestimmt nur den Zins im Rechenbeispiel.'),
            style: theme.textTheme.bodySmall),
        ExpansionTile(
          key: const Key('loan_filters'),
          tilePadding: EdgeInsets.zero,
          title: Text(tr('Filter')),
          subtitle: Text(tr('Monatsrate, Budget, Voraussetzungen'), style: theme.textTheme.bodySmall),
          children: [
            TextField(
              key: const Key('loan_filter_rate'),
              controller: _maxRate,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(labelText: tr('Höchste Monatsrate (optional)'), suffixText: '€'),
              onChanged: (_) => setState(() => _result = null),
            ),
            const SizedBox(height: 8),
            TextField(
              key: const Key('loan_filter_budget'),
              controller: _budget,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: tr('Budget: höchstens insgesamt zurückzahlen (optional)'),
                suffixText: '€',
              ),
              onChanged: (_) => setState(() => _result = null),
            ),
            SwitchListTile(
              key: const Key('loan_filter_eligible'),
              contentPadding: EdgeInsets.zero,
              value: _onlyEligible,
              onChanged: (v) => setState(() {
                _onlyEligible = v;
                _result = null;
              }),
              title: Text(tr('Nur Angebote, deren Voraussetzungen ich erfülle')),
            ),
          ],
        ),
        if (_error != null) Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
        const SizedBox(height: 8),
        FilledButton.icon(
          onPressed: _busy ? null : _compare,
          icon: _busy
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.compare_arrows),
          label: Text(tr('Angebote vergleichen')),
        ),
        if (_result != null) ..._offers(_result!),
      ]),
    );
  }

  List<Widget> _offers(OfferResult r) {
    final theme = Theme.of(context);
    return [
      const SizedBox(height: 20),
      Text(tr('Angebote ({0})', [r.offers.length]), style: theme.textTheme.titleMedium),
      if (r.hiddenCount > 0)
        Text(tr('{0} Angebote durch Filter ausgeblendet', [r.hiddenCount]),
            key: const Key('loan_hidden'), style: theme.textTheme.bodySmall),
      if (r.hasData)
        Padding(
          padding: const EdgeInsets.only(top: 4, bottom: 8),
          child: Text(tr('Bei {0} Monaten wären höchstens ca. {1} tragbar.', [r.termMonths, formatCents(r.maxPrincipalCents)]),
              style: theme.textTheme.bodySmall),
        ),
      if (r.offers.isEmpty)
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Text(tr('Kein Angebot passt zu den Filtern.'), key: const Key('loan_no_offer')),
        ),
      for (final o in r.offers)
        Card(
          key: Key('loan_offer_${o.bank}'),
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
                    Chip(label: Text(tr('Empfohlen')), avatar: Icon(Icons.star, size: 16), visualDensity: VisualDensity.compact),
                ]),
                if (o.simulation)
                  Text(tr('Fiktive Bank – unverbindliches Rechenbeispiel'), style: theme.textTheme.bodySmall),
                const SizedBox(height: 6),
                Row(children: [
                  Expanded(child: _kv(tr('Sollzins p. a.'), formatPercent(o.interestRate))),
                  Expanded(child: _kv(tr('Effektiver Jahreszins'), o.effectiveRate == null ? '–' : formatPercent(o.effectiveRate!))),
                  Expanded(child: _kv(tr('Monatsrate'), formatCents(o.monthlyRateCents))),
                ]),
                const SizedBox(height: 6),
                Text(tr('Zinskosten {0} · Gesamtbetrag {1}', [formatCents(o.totalInterestCents), formatCents(o.totalCostCents)]),
                    style: theme.textTheme.bodySmall),
                if (o.marketDeltaPercent != null)
                  Text(
                    o.marketDeltaPercent! >= 0
                        ? tr('{0} Prozentpunkte über dem Bundesbank-Durchschnitt', [o.marketDeltaPercent!.toStringAsFixed(2).localDecimal])
                        : tr('{0} Prozentpunkte unter dem Bundesbank-Durchschnitt', [(-o.marketDeltaPercent!).toStringAsFixed(2).localDecimal]),
                    style: theme.textTheme.bodySmall,
                  ),
                if (o.declined)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(tr('Abgelehnt: {0}', [o.declineReason]), style: TextStyle(color: theme.colorScheme.error)),
                  )
                else if (!o.affordable)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(tr('Achtung: Rate über deiner tragbaren Rate'), style: TextStyle(color: Colors.orange.shade800)),
                  ),
              ]),
            ),
          ),
        ),
      if (r.referenceRates.isNotEmpty) ...[
        const SizedBox(height: 8),
        Card(
          key: const Key('loan_reference'),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(tr('Marktdurchschnitt (Deutsche Bundesbank)'), style: theme.textTheme.titleSmall),
              for (final rate in r.referenceRates)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text(
                    rate.stale
                        ? tr('{0}: {1} (Stand {2}, möglicherweise veraltet)', [rate.label, formatPercent(rate.ratePercent), rate.period])
                        : tr('{0}: {1} (Stand {2})', [rate.label, formatPercent(rate.ratePercent), rate.period]),
                  ),
                ),
              const SizedBox(height: 6),
              Text(r.referenceRates.first.note, style: theme.textTheme.bodySmall),
            ]),
          ),
        ),
      ],
      if (r.providers.isNotEmpty)
        ExpansionTile(
          key: const Key('loan_providers'),
          tilePadding: EdgeInsets.zero,
          title: Text(tr('Anbieter und Datenquellen')),
          children: [
            for (final p in r.providers)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: Icon(p.ok ? Icons.check_circle_outline : Icons.info_outline,
                    color: p.ok ? Colors.green.shade700 : theme.colorScheme.outline),
                title: Text('${p.name} · ${p.stateLabel}'),
                subtitle: p.message == null ? null : Text(p.message!),
              ),
          ],
        ),
      const SizedBox(height: 8),
      Text(r.disclaimerText ?? tr('Alle Banken und Zinssätze sind fiktiv und nach festen Regeln berechnet (Simulation).'),
          key: const Key('loan_disclaimer'), style: theme.textTheme.bodySmall),
    ];
  }

  Widget _kv(String k, String v) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(k, style: Theme.of(context).textTheme.bodySmall),
        Text(v, style: const TextStyle(fontWeight: FontWeight.w600)),
      ]);
}
