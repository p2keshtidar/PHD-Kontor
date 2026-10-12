import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import 'loan_models.dart';
import 'loans_repository.dart';
import 'schedule_table.dart';

/// Kreditübersicht (Pflichtenheft B14, /F1070/).
class LoanDetailScreen extends ConsumerWidget {
  const LoanDetailScreen({super.key, required this.loanId});

  final int loanId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final detail = ref.watch(loanDetailProvider(loanId));
    return Scaffold(
      appBar: AppBar(title: const Text('Kredit')),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (d) => RefreshIndicator(
          onRefresh: () => ref.refresh(loanDetailProvider(loanId).future),
          child: _content(context, d),
        ),
      ),
    );
  }

  Widget _content(BuildContext context, LoanDetail d) {
    final l = d.loan;
    final theme = Theme.of(context);
    return ListView(padding: const EdgeInsets.all(16), children: [
      Text('${l.purposeLabel} · ${l.bankName}', style: theme.textTheme.titleLarge),
      const SizedBox(height: 12),
      Text(l.isActive ? 'Restschuld' : 'Abbezahlt', style: theme.textTheme.bodySmall),
      Text(formatCents(l.remainingCents), style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
      const SizedBox(height: 12),
      LinearProgressIndicator(
        value: (l.progressPercent / 100).clamp(0.0, 1.0),
        minHeight: 12,
        borderRadius: BorderRadius.circular(6),
      ),
      const SizedBox(height: 4),
      Text('${l.paidInstallments} von ${l.termMonths} Raten bezahlt · ${l.progressPercent.toStringAsFixed(0)} % getilgt'),
      const SizedBox(height: 12),
      Card(
        child: Column(children: [
          _row('Kreditbetrag', formatCents(l.principalCents)),
          _row('Zinssatz p. a.', formatPercent(l.interestRate)),
          _row('Monatsrate', formatCents(l.monthlyRateCents)),
          if (l.nextDueDate != null) _row('Nächste Rate', formatDate(l.nextDueDate!)),
          _row('Beginn', formatDate(l.startDate)),
          _row('Zinskosten gesamt', formatCents(d.totalInterestCents)),
        ]),
      ),
      const SizedBox(height: 12),
      Text('Tilgungsplan', style: theme.textTheme.titleMedium),
      ScheduleTable(rows: d.schedule),
    ]);
  }

  Widget _row(String k, String v) => ListTile(dense: true, title: Text(k), trailing: Text(v, style: const TextStyle(fontWeight: FontWeight.w600)));
}
