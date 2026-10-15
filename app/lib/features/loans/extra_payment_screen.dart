import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../accounts/accounts_repository.dart';
import '../insights/insights_repository.dart';
import 'loan_models.dart';
import 'loans_repository.dart';
import '../../core/layout.dart';

/// /K020/ Sondertilgung: Betrag eingeben → Vorschau (Restschuld, Laufzeit, Zinsersparnis) → buchen.
/// Die Monatsrate bleibt gleich, darum endet der Kredit früher.
class ExtraPaymentScreen extends ConsumerStatefulWidget {
  const ExtraPaymentScreen({super.key, required this.loan});

  final Loan loan;

  @override
  ConsumerState<ExtraPaymentScreen> createState() => _ExtraPaymentScreenState();
}

class _ExtraPaymentScreenState extends ConsumerState<ExtraPaymentScreen> {
  final _amount = TextEditingController();
  ExtraPreview? _preview;
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  Future<void> _calculate() async {
    final cents = parseEuroToCents(_amount.text);
    if (cents == null || cents < 100) {
      setState(() {
        _error = tr('Bitte mindestens 1,00 € eingeben');
        _preview = null;
      });
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final p = await ref.read(loansRepositoryProvider).previewExtra(widget.loan.id, cents);
      if (mounted) setState(() => _preview = p);
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _pay() async {
    final p = _preview;
    if (p == null) return;
    setState(() => _busy = true);
    try {
      await ref.read(loansRepositoryProvider).extraPayment(widget.loan.id, p.amountCents);
      ref.invalidate(loanDetailProvider(widget.loan.id));
      ref.invalidate(loansProvider);
      ref.invalidate(accountsProvider);
      ref.invalidate(netWorthProvider);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(p.paysOff
            ? tr('Kredit vollständig zurückgezahlt 🎉')
            : tr('Sondertilgung über {0} gebucht', [formatCents(p.amountCents)])),
      ));
      Navigator.of(context).pop();
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l = widget.loan;
    final p = _preview;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Sondertilgung'))),
      body: SafeArea(
        child: ListView(padding: safeListPadding(context), children: [
          Text(tr('Zahle zusätzlich etwas zurück. Deine Monatsrate bleibt gleich – dadurch ist der Kredit früher abbezahlt und du sparst Zinsen.'),
              style: theme.textTheme.bodyMedium),
          const SizedBox(height: 16),
          Text(tr('Restschuld bei der {0}: {1}', [l.bankName, formatCents(l.remainingCents)]),
              style: theme.textTheme.titleSmall),
          const SizedBox(height: 12),
          TextField(
            key: const Key('extra_amount'),
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              labelText: tr('Betrag'),
              suffixText: '€',
              errorText: _error,
              prefixIcon: const Icon(Icons.euro),
            ),
            onChanged: (_) {
              if (_preview != null) setState(() => _preview = null);
            },
            onSubmitted: (_) => _calculate(),
          ),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 4, children: [
            for (final euro in const [500, 1000, 5000])
              if (euro * 100 < l.remainingCents)
                ActionChip(
                  label: Text(formatCents(euro * 100)),
                  onPressed: () {
                    _amount.text = '$euro';
                    _calculate();
                  },
                ),
            ActionChip(
              label: Text(tr('Alles')),
              onPressed: () {
                _amount.text = formatCents(l.remainingCents).replaceAll(' €', '').replaceAll('.', '');
                _calculate();
              },
            ),
          ]),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            key: const Key('extra_preview'),
            onPressed: _busy ? null : _calculate,
            icon: const Icon(Icons.calculate_outlined),
            label: Text(tr('Auswirkung berechnen')),
          ),
          if (p != null) ...[
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Column(children: [
                  _compare(tr('Restschuld'), formatCents(p.remainingBeforeCents), formatCents(p.remainingAfterCents)),
                  _compare(tr('Offene Raten'), '${p.installmentsBefore}', '${p.installmentsAfter}'),
                  _compare(tr('Letzte Rate'), formatMonthYear(p.endBefore),
                      p.paysOff ? tr('sofort') : formatMonthYear(p.endAfter)),
                  const Divider(),
                  ListTile(
                    dense: true,
                    leading: Icon(Icons.savings_outlined, color: theme.colorScheme.primary),
                    title: Text(tr('Du sparst Zinsen')),
                    trailing: Text(formatCents(p.interestSavedCents),
                        style: TextStyle(fontWeight: FontWeight.bold, color: theme.colorScheme.primary)),
                  ),
                  if (p.installmentsSaved > 0 && !p.paysOff)
                    ListTile(
                      dense: true,
                      leading: const Icon(Icons.event_available_outlined),
                      title: Text(tr('{0} {1} weniger', [p.installmentsSaved, p.installmentsSaved == 1 ? tr('Rate') : tr('Raten')])),
                    ),
                ]),
              ),
            ),
            if (p.availableCents < p.amountCents)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(tr('Auf dem Girokonto sind nur {0}.', [formatCents(p.availableCents)]),
                    style: TextStyle(color: theme.colorScheme.error)),
              ),
            const SizedBox(height: 16),
            FilledButton.icon(
              key: const Key('extra_pay'),
              onPressed: _busy || p.availableCents < p.amountCents ? null : _pay,
              icon: const Icon(Icons.check),
              label: Text(tr('{0} jetzt sondertilgen', [formatCents(p.amountCents)])),
            ),
          ],
        ]),
      ),
    );
  }

  /// Zeile "vorher → nachher"; bricht auf schmalen Bildschirmen um.
  Widget _compare(String label, String before, String after) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        child: Row(children: [
          Expanded(child: Text(label)),
          Flexible(
            child: Text.rich(
              TextSpan(children: [
                TextSpan(text: before, style: const TextStyle(decoration: TextDecoration.lineThrough)),
                const TextSpan(text: '  →  '),
                TextSpan(text: after, style: const TextStyle(fontWeight: FontWeight.bold)),
              ]),
              textAlign: TextAlign.right,
            ),
          ),
        ]),
      );
}
