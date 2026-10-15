import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/bookings_repository.dart';
import '../insights/insights_repository.dart';
import 'extra_payment_screen.dart';
import 'loan_models.dart';
import 'loan_steps.dart';
import 'loans_repository.dart';
import 'schedule_table.dart';
import '../../core/layout.dart';

/// Kreditübersicht (Pflichtenheft B14, /F1070/). Bei geschlossenem Vertrag: Auszahlung anfordern (Schritt 4)
/// oder den Vertrag vor der Auszahlung widerrufen.
class LoanDetailScreen extends ConsumerStatefulWidget {
  const LoanDetailScreen({super.key, required this.loanId});

  final int loanId;

  @override
  ConsumerState<LoanDetailScreen> createState() => _LoanDetailScreenState();
}

class _LoanDetailScreenState extends ConsumerState<LoanDetailScreen> {
  bool _busy = false;

  void _refreshAll() {
    ref.invalidate(loanDetailProvider(widget.loanId));
    ref.invalidate(loansProvider);
    ref.invalidate(accountsProvider);
    ref.invalidate(recentBookingsProvider);
    ref.invalidate(netWorthProvider);
    ref.invalidate(overviewProvider);
  }

  Future<void> _payout(Loan l) async {
    setState(() => _busy = true);
    try {
      await ref.read(loansRepositoryProvider).payout(l.id);
      _refreshAll();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(tr('{0} aufs Girokonto ausgezahlt', [formatCents(l.principalCents)]))));
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _cancel(Loan l) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(tr('Vertrag widerrufen?')),
        content: Text(tr('Der Kreditvertrag mit der {0} wird aufgehoben. Es wurde noch kein Geld ausgezahlt.', [l.bankName])),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(tr('Abbrechen'))),
          FilledButton(
              key: const Key('loan_cancel_confirm'),
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(tr('Widerrufen'))),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      await ref.read(loansRepositoryProvider).cancel(l.id);
      _refreshAll();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(loanDetailProvider(widget.loanId));
    return Scaffold(
      appBar: AppBar(title: Text(tr('Kredit'))),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (d) => RefreshIndicator(
          onRefresh: () => ref.refresh(loanDetailProvider(widget.loanId).future),
          child: _content(context, d),
        ),
      ),
    );
  }

  Widget _content(BuildContext context, LoanDetail d) {
    final l = d.loan;
    final theme = Theme.of(context);
    if (l.isContracted || l.isCancelled) return _contract(context, d, theme);
    return ListView(padding: safeListPadding(context), children: [
      Text('${l.purposeLabel} · ${l.bankName}', style: theme.textTheme.titleLarge),
      const SizedBox(height: 12),
      Text(l.isActive ? tr('Restschuld') : tr('Abbezahlt'), style: theme.textTheme.bodySmall),
      Text(formatCents(l.remainingCents), style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
      const SizedBox(height: 12),
      LinearProgressIndicator(
        value: (l.progressPercent / 100).clamp(0.0, 1.0),
        minHeight: 12,
        borderRadius: BorderRadius.circular(6),
      ),
      const SizedBox(height: 4),
      Text(tr('{0} von {1} Raten bezahlt · {2} % getilgt', [l.paidInstallments, l.totalInstallments, l.progressPercent.toStringAsFixed(0)])),
      const SizedBox(height: 12),
      Card(
        child: Column(children: [
          _row(tr('Kreditbetrag'), formatCents(l.principalCents)),
          _row(tr('Zinssatz p. a.'), formatPercent(l.interestRate)),
          if (l.effectiveRate != null) _row(tr('Effektiver Jahreszins'), formatPercent(l.effectiveRate!)),
          _row(tr('Monatsrate'), formatCents(l.monthlyRateCents)),
          if (l.nextDueDate != null) _row(tr('Nächste Rate'), formatDate(l.nextDueDate!)),
          _row(tr('Beginn'), formatDate(l.startDate)),
          if (l.isActive && l.expectedEndDate != null)
            _row(tr('Voraussichtlich abbezahlt'), formatMonthYear(l.expectedEndDate!)),
          _row(tr('Zinskosten gesamt'), formatCents(d.totalInterestCents)),
          if (l.extraPaidCents > 0) _row(tr('Sondertilgungen'), formatCents(l.extraPaidCents)),
          if (d.interestSavedCents > 0) _row(tr('Gesparte Zinsen'), formatCents(d.interestSavedCents)),
        ]),
      ),
      if (l.isActive) ...[
        const SizedBox(height: 8),
        FilledButton.tonalIcon(
          key: const Key('loan_extra_payment'),
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => ExtraPaymentScreen(loan: l)),
          ),
          icon: const Icon(Icons.bolt),
          label: Text(tr('Sondertilgung')),
        ),
      ],
      const SizedBox(height: 12),
      Text(tr('Tilgungsplan'), style: theme.textTheme.titleMedium),
      ScheduleTable(rows: d.schedule),
    ]);
  }

  /// Vertrag geschlossen (noch nicht ausgezahlt) oder widerrufen.
  Widget _contract(BuildContext context, LoanDetail d, ThemeData theme) {
    final l = d.loan;
    return ListView(padding: safeListPadding(context), children: [
      LoanSteps(current: l.isContracted ? 4 : 3),
      const SizedBox(height: 12),
      Text('${l.purposeLabel} · ${l.bankName}', style: theme.textTheme.titleLarge),
      const SizedBox(height: 8),
      Card(
        color: l.isCancelled ? theme.colorScheme.surfaceContainerHighest : theme.colorScheme.primaryContainer,
        child: ListTile(
          key: const Key('loan_contract_status'),
          leading: Icon(l.isCancelled ? Icons.undo : Icons.handshake),
          title: Text(l.statusLabel ?? (l.isCancelled ? tr('Widerrufen') : tr('Vertrag geschlossen'))),
          subtitle: Text(l.isCancelled
              ? tr('Es wurde kein Geld ausgezahlt.')
              : tr('Noch ist kein Geld geflossen. Die Raten beginnen einen Monat nach der Auszahlung.')),
        ),
      ),
      Card(
        child: Column(children: [
          _row(tr('Kreditbetrag'), formatCents(l.principalCents)),
          _row(tr('Sollzins p. a. (gebunden)'), formatPercent(l.interestRate)),
          if (l.effectiveRate != null) _row(tr('Effektiver Jahreszins'), formatPercent(l.effectiveRate!)),
          _row(tr('Laufzeit'), tr('{0} Monate', [l.termMonths])),
          _row(tr('Monatsrate'), formatCents(l.monthlyRateCents)),
          _row(tr('Zinskosten gesamt'), formatCents(d.totalInterestCents)),
        ]),
      ),
      if (l.isContracted) ...[
        const SizedBox(height: 8),
        FilledButton.icon(
          key: const Key('loan_payout'),
          onPressed: _busy ? null : () => _payout(l),
          icon: const Icon(Icons.account_balance_wallet),
          label: Text(tr('Auszahlung anfordern ({0})', [formatCents(l.principalCents)])),
        ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          key: const Key('loan_cancel'),
          onPressed: _busy ? null : () => _cancel(l),
          icon: const Icon(Icons.undo),
          label: Text(tr('Vertrag widerrufen')),
        ),
        const SizedBox(height: 8),
        Text(tr('Simulation mit Spielgeld: Die Auszahlung bucht den Betrag auf dein Girokonto der Projektbank.'),
            style: theme.textTheme.bodySmall),
      ],
    ]);
  }

  Widget _row(String k, String v) => ListTile(dense: true, title: Text(k), trailing: Text(v, style: const TextStyle(fontWeight: FontWeight.w600)));
}
