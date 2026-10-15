import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import 'loan_models.dart';
import 'loan_steps.dart';
import 'loans_repository.dart';
import 'schedule_table.dart';
import '../../core/layout.dart';

/// Ein Angebot aus dem Vergleich im Detail (Pflichtenheft B13, /F1040/): Konditionen, Voraussetzungen,
/// Rechenbeispiel, Tilgungsplan – und als nächster Schritt die unverbindliche Anfrage.
class LoanOfferScreen extends ConsumerStatefulWidget {
  const LoanOfferScreen({super.key, required this.selected});

  final SelectedOffer? selected;

  @override
  ConsumerState<LoanOfferScreen> createState() => _LoanOfferScreenState();
}

class _LoanOfferScreenState extends ConsumerState<LoanOfferScreen> {
  Future<List<ScheduleRow>>? _schedule;
  bool _busy = false;
  bool _consent = false;

  @override
  void initState() {
    super.initState();
    final s = widget.selected;
    if (s != null) {
      _schedule = ref
          .read(loansRepositoryProvider)
          .schedule(s.result.principalCents, s.offer.interestRate, s.result.termMonths);
    }
  }

  Future<void> _request(SelectedOffer s) async {
    setState(() => _busy = true);
    try {
      final app = await ref.read(loansRepositoryProvider).request(
          s.offer.bank, s.result.purpose, s.result.principalCents, s.result.equityCents, s.result.termMonths,
          creditClass: s.result.creditClass);
      ref.invalidate(loanApplicationsProvider);
      if (mounted) context.pushReplacement('/kredit/anfrage/${app.id}');
    } catch (e) {
      if (mounted) {
        setState(() => _busy = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.selected;
    if (s == null) {
      return Scaffold(
        appBar: AppBar(title: Text(tr('Angebot'))),
        body: Center(child: Text(tr('Bitte das Angebot aus dem Vergleich öffnen.'))),
      );
    }
    final o = s.offer;
    final r = s.result;
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(o.bankName)),
      body: ListView(padding: safeListPadding(context), children: [
        const LoanSteps(current: 2),
        const SizedBox(height: 12),
        Text('${r.purposeLabel}: ${formatCents(r.principalCents)}', style: theme.textTheme.titleLarge),
        if (r.equityCents > 0) Text(tr('Eigenkapital: {0}', [formatCents(r.equityCents)])),
        Text(tr('Bonität (Selbsteinschätzung): {0}', [creditClasses[r.creditClass] ?? r.creditClass]),
            style: theme.textTheme.bodySmall),
        if (o.simulation)
          Card(
            color: theme.colorScheme.secondaryContainer,
            child: ListTile(
              key: const Key('loan_simulation_hint'),
              leading: const Icon(Icons.science_outlined),
              title: Text(tr('Fiktive Bank (Simulation)')),
              subtitle: Text(tr('Unverbindliches Rechenbeispiel – kein echtes Kreditangebot, kein echtes Geld.')),
            ),
          ),
        const SizedBox(height: 8),
        Card(
          child: Column(children: [
            _row(tr('Sollzins p. a. (gebunden)'), formatPercent(o.interestRate)),
            if (o.effectiveRate != null) _row(tr('Effektiver Jahreszins'), formatPercent(o.effectiveRate!)),
            _row(tr('Laufzeit'), tr('{0} Monate', [r.termMonths])),
            _row(tr('Monatsrate'), formatCents(o.monthlyRateCents)),
            _row(tr('Zinskosten gesamt'), formatCents(o.totalInterestCents)),
            _row(tr('Weitere Kosten'), formatCents(o.feesCents)),
            _row(tr('Gesamtbetrag'), formatCents(o.totalCostCents)),
            if (r.hasData) _row(tr('Deine tragbare Rate'), formatCents(r.maxRateCents)),
          ]),
        ),
        if (o.exampleText != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Text(o.exampleText!, key: const Key('loan_example'), style: theme.textTheme.bodySmall),
          ),
        if (o.declined)
          Card(
            color: theme.colorScheme.errorContainer,
            child: ListTile(leading: const Icon(Icons.block), title: Text(tr('Abgelehnt')), subtitle: Text(o.declineReason ?? '')),
          ),
        if (o.requirements.isNotEmpty) _list(tr('Voraussetzungen'), o.requirements, Icons.rule, theme),
        if (o.conditions.isNotEmpty) _list(tr('Konditionen'), o.conditions, Icons.description_outlined, theme),
        const SizedBox(height: 12),
        Text(tr('Tilgungsplan'), style: theme.textTheme.titleMedium),
        FutureBuilder<List<ScheduleRow>>(
          future: _schedule,
          builder: (context, snap) {
            if (snap.hasError) return Text(apiErrorMessage(snap.error!));
            if (!snap.hasData) return const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()));
            return ScheduleTable(rows: snap.data!);
          },
        ),
        const SizedBox(height: 16),
        if (!o.declined) ...[
          CheckboxListTile(
            key: const Key('loan_consent'),
            contentPadding: EdgeInsets.zero,
            value: _consent,
            onChanged: (v) => setState(() => _consent = v ?? false),
            title: Text(tr('Meine Angaben dürfen für diese unverbindliche Anfrage verwendet werden. (Simulation: Es werden keine Daten an eine Bank oder Auskunftei weitergegeben.)')),
          ),
          FilledButton.icon(
            key: const Key('loan_request'),
            onPressed: _busy || !_consent ? null : () => _request(s),
            icon: const Icon(Icons.send),
            label: Text(tr('Unverbindlich anfragen')),
          ),
          const SizedBox(height: 6),
          Text(tr('Die Anfrage ist unverbindlich. Den Vertrag schließt du erst im nächsten Schritt ab, die Auszahlung forderst du danach separat an.'),
              style: theme.textTheme.bodySmall),
        ],
      ]),
    );
  }

  Widget _list(String title, List<String> items, IconData icon, ThemeData theme) => Card(
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Icon(icon, size: 18, color: theme.colorScheme.primary),
              const SizedBox(width: 6),
              Text(title, style: theme.textTheme.titleSmall),
            ]),
            for (final i in items)
              Padding(padding: const EdgeInsets.only(top: 4), child: Text('• $i')),
          ]),
        ),
      );

  Widget _row(String k, String v) => ListTile(dense: true, title: Text(k), trailing: Text(v, style: const TextStyle(fontWeight: FontWeight.w600)));
}
