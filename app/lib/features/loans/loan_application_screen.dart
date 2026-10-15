import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import '../accounts/accounts_repository.dart';
import 'loan_models.dart';
import 'loan_steps.dart';
import 'loans_repository.dart';

/// Kreditanfrage (Schritt 2) mit Antwort der (fiktiven) Bank; bei Zusage: Vertrag abschließen (Schritt 3) oder
/// Anfrage zurückziehen.
class LoanApplicationScreen extends ConsumerStatefulWidget {
  const LoanApplicationScreen({super.key, required this.applicationId});

  final int applicationId;

  @override
  ConsumerState<LoanApplicationScreen> createState() => _LoanApplicationScreenState();
}

class _LoanApplicationScreenState extends ConsumerState<LoanApplicationScreen> {
  bool _busy = false;
  bool _confirmed = false;

  Future<void> _accept(LoanApplication a) async {
    setState(() => _busy = true);
    try {
      final detail = await ref.read(loansRepositoryProvider).accept(a.id);
      ref.invalidate(loansProvider);
      ref.invalidate(loanApplicationsProvider);
      ref.invalidate(accountsProvider);
      if (mounted) context.pushReplacement('/kredit/${detail.loan.id}');
    } catch (e) {
      if (mounted) {
        setState(() => _busy = false);
        ref.invalidate(loanApplicationProvider(a.id));
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    }
  }

  Future<void> _withdraw(LoanApplication a) async {
    setState(() => _busy = true);
    try {
      await ref.read(loansRepositoryProvider).withdraw(a.id);
      ref.invalidate(loanApplicationProvider(a.id));
      ref.invalidate(loanApplicationsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr('Anfrage zurückgezogen'))));
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final app = ref.watch(loanApplicationProvider(widget.applicationId));
    return Scaffold(
      appBar: AppBar(title: Text(tr('Kreditanfrage'))),
      body: app.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (a) => _content(context, a),
      ),
    );
  }

  Widget _content(BuildContext context, LoanApplication a) {
    final theme = Theme.of(context);
    final color = switch (a.status) {
      'APPROVED' || 'ACCEPTED' => theme.colorScheme.primaryContainer,
      'DECLINED' => theme.colorScheme.errorContainer,
      _ => theme.colorScheme.surfaceContainerHighest,
    };
    return ListView(padding: safeListPadding(context), children: [
      LoanSteps(current: a.approved ? 3 : (a.status == 'ACCEPTED' ? 4 : 2)),
      const SizedBox(height: 12),
      Text('${a.purposeLabel}: ${formatCents(a.principalCents)}', style: theme.textTheme.titleLarge),
      Text(a.bankName, style: theme.textTheme.titleMedium),
      const SizedBox(height: 8),
      Card(
        color: color,
        child: ListTile(
          key: const Key('loan_application_status'),
          leading: Icon(a.status == 'DECLINED' ? Icons.block : (a.approved ? Icons.verified : Icons.info_outline)),
          title: Text(a.statusLabel),
          subtitle: a.decisionReason == null ? null : Text(a.decisionReason!),
        ),
      ),
      if (a.interestRate != null)
        Card(
          child: Column(children: [
            _row(tr('Sollzins p. a. (gebunden)'), formatPercent(a.interestRate!)),
            if (a.effectiveRate != null) _row(tr('Effektiver Jahreszins'), formatPercent(a.effectiveRate!)),
            _row(tr('Laufzeit'), tr('{0} Monate', [a.termMonths])),
            if (a.monthlyRateCents != null) _row(tr('Monatsrate'), formatCents(a.monthlyRateCents!)),
            if (a.totalCents != null) _row(tr('Gesamtbetrag'), formatCents(a.totalCents!)),
            if (a.validUntil != null && a.approved) _row(tr('Zusage gilt bis'), formatDate(a.validUntil!)),
          ]),
        ),
      if (a.nextStepText != null)
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Text(a.nextStepText!, style: theme.textTheme.bodyMedium),
        ),
      if (a.approved) ...[
        CheckboxListTile(
          key: const Key('loan_confirm'),
          contentPadding: EdgeInsets.zero,
          value: _confirmed,
          onChanged: (v) => setState(() => _confirmed = v ?? false),
          title: Text(tr('Ich schließe den Kreditvertrag (Simulation mit Spielgeld) zu diesen Konditionen ab. Ausgezahlt wird erst, wenn ich die Auszahlung anfordere.')),
        ),
        FilledButton.icon(
          key: const Key('loan_accept'),
          onPressed: _busy || !_confirmed ? null : () => _accept(a),
          icon: const Icon(Icons.handshake),
          label: Text(tr('Vertrag abschließen')),
        ),
        const SizedBox(height: 8),
        TextButton.icon(
          key: const Key('loan_withdraw'),
          onPressed: _busy ? null : () => _withdraw(a),
          icon: const Icon(Icons.undo),
          label: Text(tr('Anfrage zurückziehen')),
        ),
      ],
      if (a.status == 'ACCEPTED' && a.loanId != null)
        FilledButton.tonalIcon(
          key: const Key('loan_open'),
          onPressed: () => context.push('/kredit/${a.loanId}'),
          icon: const Icon(Icons.request_quote),
          label: Text(tr('Zum Kredit')),
        ),
    ]);
  }

  Widget _row(String k, String v) =>
      ListTile(dense: true, title: Text(k), trailing: Text(v, style: const TextStyle(fontWeight: FontWeight.w600)));
}
