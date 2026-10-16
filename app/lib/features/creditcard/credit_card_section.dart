import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/messenger.dart';
import '../../core/money.dart';
import '../../core/text_dialog.dart';
import '../../l10n/l10n.dart';

int _i(Object? v) => (v as num).toInt();

class CreditCardView {
  const CreditCardView(this.exists, this.maskedNumber, this.status, this.limitCents, this.openCents,
      this.availableCents, this.feePercent, this.transactions, this.statements, this.note);

  final bool exists;
  final String? maskedNumber;
  final String? status;
  final int limitCents;
  final int openCents;
  final int availableCents;
  final double feePercent;
  final List<Map<String, dynamic>> transactions;
  final List<Map<String, dynamic>> statements;
  final String note;

  bool get blocked => status == 'BLOCKED';

  factory CreditCardView.fromJson(Map<String, dynamic> j) => CreditCardView(
        j['exists'] as bool,
        j['maskedNumber'] as String?,
        j['status'] as String?,
        _i(j['limitCents']),
        _i(j['openCents']),
        _i(j['availableCents']),
        (j['foreignFeePercent'] as num?)?.toDouble() ?? 0,
        [for (final t in j['transactions'] as List<dynamic>) t as Map<String, dynamic>],
        [for (final s in j['statements'] as List<dynamic>) s as Map<String, dynamic>],
        j['note'] as String? ?? '',
      );
}

final creditCardProvider = FutureProvider.autoDispose<CreditCardView>((ref) async {
  final res = await ref.watch(apiClientProvider).dio.get<Map<String, dynamic>>('/api/credit-card');
  return CreditCardView.fromJson(res.data!);
});

/// Virtuelle Kreditkarte (Simulation): einrichten, Limit, sperren, Demo-Zahlung, offene Umsätze, Abrechnungen.
class CreditCardSection extends ConsumerWidget {
  const CreditCardSection({super.key});

  Future<void> _call(BuildContext context, WidgetRef ref, Future<dynamic> Function() action, [String? done]) async {
    try {
      final r = await action();
      ref.invalidate(creditCardProvider);
      final msg = r is Map && r['message'] is String ? r['message'] as String : done;
      if (msg != null && context.mounted) showNow(ScaffoldMessenger.of(context), SnackBar(content: Text(msg)));
    } catch (e) {
      if (context.mounted) showNow(ScaffoldMessenger.of(context), SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  Future<int?> _askLimit(BuildContext context, int? current) async {
    final text = await askText(context,
        title: tr('Verfügungsrahmen (100 € bis 5.000 €)'),
        initial: current == null ? '1000' : (current ~/ 100).toString(),
        label: tr('Betrag'),
        maxLength: 6,
        fieldKey: const Key('cc_limit_field'),
        saveKey: const Key('cc_limit_save'));
    return text == null ? null : parseEuroToCents(text);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(creditCardProvider);
    final dio = ref.read(apiClientProvider).dio;
    final theme = Theme.of(context);
    return Card(
      key: const Key('cc_section'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: data.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text(apiErrorMessage(e)),
          data: (c) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const Icon(Icons.credit_card),
              const SizedBox(width: 8),
              Expanded(child: Text(tr('Kreditkarte (Simulation)'), style: theme.textTheme.titleMedium)),
              if (c.exists) Text(c.maskedNumber ?? ''),
            ]),
            const SizedBox(height: 6),
            Text(c.note, style: theme.textTheme.bodySmall),
            const SizedBox(height: 8),
            if (!c.exists)
              FilledButton.tonal(
                key: const Key('cc_create'),
                onPressed: () async {
                  final limit = await _askLimit(context, null);
                  if (limit != null && context.mounted) {
                    await _call(context, ref,
                        () => dio.post<Map<String, dynamic>>('/api/credit-card', data: {'limitCents': limit}),
                        tr('Kreditkarte eingerichtet (Simulation)'));
                  }
                },
                child: Text(tr('Kreditkarte einrichten (Simulation)')),
              )
            else ...[
              Text(tr('Offen: {0} · frei: {1} von {2}',
                  [formatCents(c.openCents), formatCents(c.availableCents), formatCents(c.limitCents)]),
                  key: const Key('cc_amounts')),
              const SizedBox(height: 4),
              LinearProgressIndicator(
                value: c.limitCents == 0 ? 0 : (c.openCents / c.limitCents).clamp(0.0, 1.0),
                minHeight: 8,
                borderRadius: BorderRadius.circular(4),
              ),
              Text(tr('Abrechnung am Monatsersten vom Girokonto · Auslandseinsatz {0} %',
                  [c.feePercent.toStringAsFixed(2).replaceAll('.', L10n.isEnglish ? '.' : ',')]),
                  style: theme.textTheme.bodySmall),
              SwitchListTile(
                key: const Key('cc_lock'),
                contentPadding: EdgeInsets.zero,
                title: Text(tr('Kreditkarte sperren')),
                value: c.blocked,
                onChanged: (v) => _call(context, ref,
                    () => dio.post<Map<String, dynamic>>(v ? '/api/credit-card/lock' : '/api/credit-card/unlock')),
              ),
              Wrap(spacing: 8, children: [
                OutlinedButton(
                  key: const Key('cc_limit'),
                  onPressed: () async {
                    final limit = await _askLimit(context, c.limitCents);
                    if (limit != null && context.mounted) {
                      await _call(context, ref,
                          () => dio.put<Map<String, dynamic>>('/api/credit-card/limit', data: {'limitCents': limit}),
                          tr('Limit geändert'));
                    }
                  },
                  child: Text(tr('Limit ändern')),
                ),
                OutlinedButton(
                  key: const Key('cc_pay'),
                  onPressed: () async {
                    final r = await showDialog<(String, int, bool)>(context: context, builder: (_) => const _PayDialog());
                    if (r != null && context.mounted) {
                      await _call(context, ref, () async => (await dio.post<Map<String, dynamic>>(
                          '/api/credit-card/payment',
                          data: {'merchant': r.$1, 'amountCents': r.$2, 'foreignCurrency': r.$3})).data);
                    }
                  },
                  child: Text(tr('Demo-Zahlung')),
                ),
              ]),
              if (c.transactions.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(tr('Seit der letzten Abrechnung'), style: theme.textTheme.titleSmall),
                for (final t in c.transactions)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: Text(t['merchant'] as String),
                    subtitle: Text([
                      formatDateTime(DateTime.parse(t['bookedAt'] as String)),
                      if (_i(t['feeCents']) > 0) tr('+ {0} Auslandseinsatz', [formatCents(_i(t['feeCents']))]),
                    ].join(' · ')),
                    trailing: Text(formatCents(-_i(t['amountCents']))),
                  ),
              ],
              if (c.statements.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(tr('Abrechnungen'), style: theme.textTheme.titleSmall),
                for (final s in c.statements)
                  ListTile(
                    key: Key('cc_statement_${s['period']}'),
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: Text(s['period'] as String),
                    subtitle: Text(s['status'] == 'PAID'
                        ? tr('vom Girokonto eingezogen')
                        : tr('offen – Girokonto reicht nicht')),
                    trailing: Text(formatCents(_i(s['totalCents']))),
                  ),
              ],
            ],
          ]),
        ),
      ),
    );
  }
}

class _PayDialog extends StatefulWidget {
  const _PayDialog();

  @override
  State<_PayDialog> createState() => _PayDialogState();
}

class _PayDialogState extends State<_PayDialog> {
  final _merchant = TextEditingController(text: 'Hotel Roma');
  final _amount = TextEditingController(text: '50');
  bool _foreign = false;
  String? _error;

  @override
  void dispose() {
    _merchant.dispose();
    _amount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text(tr('Demo-Zahlung mit der Kreditkarte')),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(key: const Key('cc_pay_merchant'), controller: _merchant,
                decoration: InputDecoration(labelText: tr('Händler'))),
            TextField(
              key: const Key('cc_pay_amount'),
              controller: _amount,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(labelText: tr('Betrag'), suffixText: '€', errorText: _error),
            ),
            SwitchListTile(
              key: const Key('cc_pay_foreign'),
              contentPadding: EdgeInsets.zero,
              title: Text(tr('In Fremdwährung (mit Auslandseinsatz)')),
              value: _foreign,
              onChanged: (v) => setState(() => _foreign = v),
            ),
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(tr('Abbrechen'))),
          FilledButton(
            key: const Key('cc_pay_submit'),
            onPressed: () {
              final cents = parseEuroToCents(_amount.text);
              if (cents == null || cents <= 0) {
                setState(() => _error = tr('Bitte gültigen Betrag eingeben'));
                return;
              }
              Navigator.of(context).pop((_merchant.text.trim(), cents, _foreign));
            },
            child: Text(tr('Bezahlen')),
          ),
        ],
      );
}
