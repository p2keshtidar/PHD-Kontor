import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/bookings_repository.dart';
import '../insights/insights_repository.dart';
import 'loan_models.dart';
import 'loans_repository.dart';
import 'schedule_table.dart';
import '../../core/layout.dart';

/// Kreditangebot mit Tilgungsplan und Abschluss (Pflichtenheft B13, /F1040/, /F1050/).
class LoanOfferScreen extends ConsumerStatefulWidget {
  const LoanOfferScreen({super.key, required this.selected});

  final SelectedOffer? selected;

  @override
  ConsumerState<LoanOfferScreen> createState() => _LoanOfferScreenState();
}

class _LoanOfferScreenState extends ConsumerState<LoanOfferScreen> {
  Future<List<ScheduleRow>>? _schedule;
  bool _busy = false;
  bool _understood = false;

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

  Future<void> _conclude(SelectedOffer s) async {
    setState(() => _busy = true);
    try {
      final loan = await ref.read(loansRepositoryProvider).create(
          s.offer.bank, s.result.purpose, s.result.principalCents, s.result.equityCents, s.result.termMonths);
      ref.invalidate(loansProvider);
      ref.invalidate(accountsProvider);
      ref.invalidate(recentBookingsProvider);
      ref.invalidate(netWorthProvider);
      ref.invalidate(overviewProvider);
      if (mounted) context.pushReplacement('/kredit/${loan.loan.id}');
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
        Text('${r.purposeLabel}: ${formatCents(r.principalCents)}', style: theme.textTheme.titleLarge),
        if (r.equityCents > 0) Text(tr('Eigenkapital: {0}', [formatCents(r.equityCents)])),
        const SizedBox(height: 12),
        Card(
          child: Column(children: [
            _row(tr('Zinssatz p. a.'), formatPercent(o.interestRate)),
            _row(tr('Laufzeit'), tr('{0} Monate', [r.termMonths])),
            _row(tr('Monatsrate'), formatCents(o.monthlyRateCents)),
            _row(tr('Zinskosten gesamt'), formatCents(o.totalInterestCents)),
            _row(tr('Gesamtkosten'), formatCents(o.totalCostCents)),
            if (r.hasData) _row(tr('Deine tragbare Rate'), formatCents(r.maxRateCents)),
          ]),
        ),
        if (o.declined)
          Card(
            color: theme.colorScheme.errorContainer,
            child: ListTile(leading: const Icon(Icons.block), title: Text(tr('Abgelehnt')), subtitle: Text(o.declineReason ?? '')),
          ),
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
            contentPadding: EdgeInsets.zero,
            value: _understood,
            onChanged: (v) => setState(() => _understood = v ?? false),
            title: Text(tr('Mir ist klar, dass dies eine Simulation mit Spielgeld ist. Der Betrag wird auf mein Girokonto ausgezahlt, die Raten werden monatlich automatisch abgebucht.')),
          ),
          FilledButton.icon(
            onPressed: _busy || !_understood ? null : () => _conclude(s),
            icon: const Icon(Icons.handshake),
            label: Text(tr('Kredit abschließen')),
          ),
        ],
      ]),
    );
  }

  Widget _row(String k, String v) => ListTile(dense: true, title: Text(k), trailing: Text(v, style: const TextStyle(fontWeight: FontWeight.w600)));
}
