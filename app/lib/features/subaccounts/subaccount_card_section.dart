import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';

final subaccountCardProvider = FutureProvider.autoDispose.family<Map<String, dynamic>, int>((ref, id) async {
  final res = await ref.watch(apiClientProvider).dio.get<Map<String, dynamic>>('/api/subaccounts/$id/card');
  return res.data!;
});

/// Demo-Karte für ein digitales Konto: Zahlungen gehen direkt von diesem Konto ab (Spielgeld, keine echte Karte).
class SubaccountCardSection extends ConsumerWidget {
  const SubaccountCardSection({super.key, required this.accountId, this.onChanged});

  final int accountId;
  final VoidCallback? onChanged;

  Future<void> _run(BuildContext context, WidgetRef ref, Future<String?> Function() action) async {
    try {
      final msg = await action();
      ref.invalidate(subaccountCardProvider(accountId));
      onChanged?.call();
      if (msg != null && context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    } catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  Future<void> _pay(BuildContext context, WidgetRef ref) async {
    final result = await showDialog<(String, int)>(context: context, builder: (_) => const _PayDialog());
    if (result == null || !context.mounted) return;
    await _run(context, ref, () async {
      final res = await ref.read(apiClientProvider).dio.post<Map<String, dynamic>>(
          '/api/subaccounts/$accountId/card/payment', data: {'merchant': result.$1, 'amountCents': result.$2});
      return res.data!['message'] as String;
    });
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(subaccountCardProvider(accountId));
    final dio = ref.read(apiClientProvider).dio;
    final theme = Theme.of(context);
    return data.maybeWhen(
      orElse: () => const SizedBox.shrink(),
      data: (c) {
        final exists = c['exists'] as bool;
        final canPay = c['canPay'] as bool;
        final locked = c['status'] == 'LOCKED';
        return Card(
          key: const Key('subcard_section'),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                const Icon(Icons.credit_card),
                const SizedBox(width: 8),
                Expanded(child: Text(tr('Karte für dieses Konto (Demo)'), style: theme.textTheme.titleSmall)),
                if (exists) Text('•••• ${c['last4']}', key: const Key('subcard_last4')),
              ]),
              const SizedBox(height: 4),
              Text(c['note'] as String, style: theme.textTheme.bodySmall),
              if (!exists && canPay)
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    key: const Key('subcard_create'),
                    onPressed: () => _run(context, ref, () async {
                      await dio.post<void>('/api/subaccounts/$accountId/card', data: {'dailyLimitCents': 20000});
                      return tr('Karte eingerichtet (Demo)');
                    }),
                    child: Text(tr('Karte einrichten')),
                  ),
                ),
              if (exists) ...[
                Text(tr('Heute bezahlt: {0} von {1}', [
                  formatCents((c['spentTodayCents'] as num).toInt()),
                  formatCents((c['dailyLimitCents'] as num).toInt()),
                ])),
                if (locked) Text(tr('Karte gesperrt'), style: TextStyle(color: theme.colorScheme.error)),
                if (canPay)
                  Wrap(spacing: 4, alignment: WrapAlignment.end, children: [
                    TextButton(
                      key: const Key('subcard_lock'),
                      onPressed: () => _run(context, ref, () async {
                        await dio.post<void>('/api/subaccounts/$accountId/card/${locked ? 'unlock' : 'lock'}');
                        return null;
                      }),
                      child: Text(locked ? tr('Entsperren') : tr('Sperren')),
                    ),
                    FilledButton.tonal(
                      key: const Key('subcard_pay'),
                      onPressed: locked ? null : () => _pay(context, ref),
                      child: Text(tr('Demo-Zahlung')),
                    ),
                  ]),
              ],
            ]),
          ),
        );
      },
    );
  }
}

class _PayDialog extends StatefulWidget {
  const _PayDialog();

  @override
  State<_PayDialog> createState() => _PayDialogState();
}

class _PayDialogState extends State<_PayDialog> {
  final _merchant = TextEditingController(text: 'Supermarkt');
  final _amount = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _merchant.dispose();
    _amount.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text(tr('Demo-Zahlung mit der Karte')),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(
            key: const Key('subcard_merchant'),
            controller: _merchant,
            maxLength: 100,
            decoration: InputDecoration(labelText: tr('Händler')),
          ),
          TextField(
            key: const Key('subcard_amount'),
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(labelText: tr('Betrag in €'), errorText: _error),
          ),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(tr('Abbrechen'))),
          FilledButton(
            key: const Key('subcard_pay_submit'),
            onPressed: () {
              final cents = parseEuroToCents(_amount.text);
              if (cents == null || cents <= 0) {
                setState(() => _error = tr('Bitte einen gültigen Betrag eingeben.'));
                return;
              }
              Navigator.pop(context, (_merchant.text.trim(), cents));
            },
            child: Text(tr('Bezahlen')),
          ),
        ],
      );
}
