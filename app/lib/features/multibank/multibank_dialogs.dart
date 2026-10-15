import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import '../accounts/account.dart';
import '../accounts/accounts_repository.dart';
import 'multibank_models.dart';
import 'multibank_repository.dart';

/// Zinssatz im Format der Sprache: "2,00 %" / "2.00 %"
String formatPercent(double v) =>
    '${L10n.isEnglish ? v.toStringAsFixed(2) : v.toStringAsFixed(2).replaceAll('.', ',')} %';

/// Kurzbeschreibung eines Bankkontos der Projektbank für Listen.
String productLine(Account a) {
  final p = a.product;
  if (a.isCall) {
    final rate = p?.interestPercent;
    return rate == null
        ? tr('Tagesgeld (Simulation)')
        : tr('Tagesgeld · {0} p. a. (Simulation, fiktiv)', [formatPercent(rate)]);
  }
  if (a.isCurrent) return tr('Weiteres Girokonto (Simulation)');
  return tr('Projektbank (Simulation)');
}

/// Dialog „Konto eröffnen“: weiteres Girokonto oder Tagesgeld (Simulation). Gibt das neue Konto zurück.
Future<Account?> showOpenAccountDialog(BuildContext context) =>
    showDialog<Account>(context: context, builder: (_) => const _OpenAccountDialog());

class _OpenAccountDialog extends ConsumerStatefulWidget {
  const _OpenAccountDialog();

  @override
  ConsumerState<_OpenAccountDialog> createState() => _OpenAccountDialogState();
}

class _OpenAccountDialogState extends ConsumerState<_OpenAccountDialog> {
  final _name = TextEditingController();
  String _type = 'CALL';
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  Future<void> _open() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final account = await ref.read(multiBankRepositoryProvider).open(_type, _name.text);
      ref.invalidate(accountsProvider);
      ref.invalidate(bankProductsProvider);
      if (mounted) Navigator.of(context).pop(account);
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = apiErrorMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final products = ref.watch(bankProductsProvider);
    final theme = Theme.of(context);
    return AlertDialog(
      title: Text(tr('Konto eröffnen')),
      content: SingleChildScrollView(
        child: products.when(
          loading: () => const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())),
          error: (e, _) => Text(apiErrorMessage(e)),
          data: (p) => Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            for (final product in p.products)
              Card(
                key: Key('open_type_${product.type}'),
                color: _type == product.type ? theme.colorScheme.secondaryContainer : null,
                child: InkWell(
                  onTap: p.canOpen ? () => setState(() => _type = product.type) : null,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Icon(_type == product.type ? Icons.radio_button_checked : Icons.radio_button_unchecked,
                          size: 20),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text(product.label, style: const TextStyle(fontWeight: FontWeight.bold)),
                          if (product.interestPercent != null)
                            Text(tr('{0} p. a. – fiktiver Zinssatz der Simulation', [formatPercent(product.interestPercent!)]),
                                style: TextStyle(color: theme.colorScheme.primary, fontWeight: FontWeight.w600)),
                          const SizedBox(height: 4),
                          Text(product.description, style: theme.textTheme.bodySmall),
                        ]),
                      ),
                    ]),
                  ),
                ),
              ),
            const SizedBox(height: 8),
            TextField(
              key: const Key('open_name'),
              controller: _name,
              maxLength: 40,
              enabled: p.canOpen,
              decoration: InputDecoration(
                labelText: tr('Name (optional)'),
                hintText: _type == 'CALL' ? tr('Tagesgeld') : tr('z. B. Haushaltskonto'),
              ),
            ),
            if (!p.canOpen)
              Text(tr('Du hast schon {0} weitere Konten. Löse zuerst eines auf.', [p.maxCount]),
                  key: const Key('open_limit'), style: TextStyle(color: theme.colorScheme.error)),
            if (_error != null) Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
            const SizedBox(height: 8),
            Text(p.disclaimer, style: theme.textTheme.bodySmall),
          ]),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(tr('Abbrechen'))),
        FilledButton(
          key: const Key('open_submit'),
          onPressed: _saving || !(products.valueOrNull?.canOpen ?? false) ? null : _open,
          child: Text(tr('Eröffnen')),
        ),
      ],
    );
  }
}

/// Dialog „Umbuchen“ zwischen eigenen Konten der Projektbank. Gibt true zurück, wenn gebucht wurde.
Future<bool> showMoveMoneyDialog(BuildContext context, {int? fromId, int? toId}) async =>
    await showDialog<bool>(context: context, builder: (_) => _MoveMoneyDialog(fromId: fromId, toId: toId)) ?? false;

class _MoveMoneyDialog extends ConsumerStatefulWidget {
  const _MoveMoneyDialog({this.fromId, this.toId});

  final int? fromId;
  final int? toId;

  @override
  ConsumerState<_MoveMoneyDialog> createState() => _MoveMoneyDialogState();
}

class _MoveMoneyDialogState extends ConsumerState<_MoveMoneyDialog> {
  final _amount = TextEditingController();
  int? _from;
  int? _to;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _from = widget.fromId;
    _to = widget.toId;
  }

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  /// Eigene Konten, zwischen denen umgebucht werden kann (geteilte digitale Konten nach Rolle).
  static bool _canSend(Account a) => a.isBankAccount || (a.isSub && (a.digital?.canWithdraw ?? !a.isForeign));
  static bool _canReceive(Account a) => a.isBankAccount || (a.isSub && (a.digital?.canDeposit ?? !a.isForeign));

  Future<void> _move(List<Account> accounts) async {
    final cents = parseEuroToCents(_amount.text);
    String? error;
    if (_from == null || _to == null) {
      error = tr('Bitte Quelle und Ziel wählen');
    } else if (_from == _to) {
      error = tr('Quelle und Ziel sind gleich.');
    } else if (cents == null || cents <= 0) {
      error = tr('Bitte gültigen Betrag eingeben');
    } else {
      final from = accounts.firstWhere((a) => a.id == _from);
      if (cents > from.balanceCents) {
        error = tr('Höchstens {0} verfügbar', [formatCents(from.balanceCents)]);
      }
    }
    if (error != null) {
      setState(() => _error = error);
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(multiBankRepositoryProvider).move(_from!, _to!, cents!);
      ref.invalidate(accountsProvider);
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = apiErrorMessage(e);
        });
      }
    }
  }

  DropdownMenuItem<int> _item(Account a) => DropdownMenuItem(
        value: a.id,
        child: Text('${a.name} · ${formatCents(a.balanceCents)}', overflow: TextOverflow.ellipsis),
      );

  @override
  Widget build(BuildContext context) {
    final accounts = ref.watch(accountsProvider).valueOrNull ?? const <Account>[];
    final senders = accounts.where(_canSend).toList();
    final receivers = accounts.where(_canReceive).toList();
    final theme = Theme.of(context);
    return AlertDialog(
      title: Text(tr('Umbuchen')),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          DropdownButtonFormField<int>(
            key: const Key('move_from'),
            isExpanded: true,
            value: senders.any((a) => a.id == _from) ? _from : null,
            decoration: InputDecoration(labelText: tr('Von')),
            items: [for (final a in senders) _item(a)],
            onChanged: (v) => setState(() => _from = v),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<int>(
            key: const Key('move_to'),
            isExpanded: true,
            value: receivers.any((a) => a.id == _to) ? _to : null,
            decoration: InputDecoration(labelText: tr('Nach')),
            items: [for (final a in receivers) _item(a)],
            onChanged: (v) => setState(() => _to = v),
          ),
          const SizedBox(height: 8),
          TextField(
            key: const Key('move_amount'),
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(labelText: tr('Betrag'), suffixText: '€'),
          ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, key: const Key('move_error'), style: TextStyle(color: theme.colorScheme.error)),
          ],
          const SizedBox(height: 8),
          Text(tr('Umbuchungen zwischen deinen Konten der Projektbank sind sofort gebucht und kostenlos (Simulation).'),
              style: theme.textTheme.bodySmall),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(tr('Abbrechen'))),
        FilledButton(
          key: const Key('move_submit'),
          onPressed: _saving ? null : () => _move(accounts),
          child: Text(tr('Umbuchen')),
        ),
      ],
    );
  }
}

/// Menü für weitere Konten (weiteres Girokonto, Tagesgeld): umbuchen, umbenennen, auflösen.
class ExtraAccountMenu extends ConsumerWidget {
  const ExtraAccountMenu({super.key, required this.account, this.onClosed});

  final Account account;

  /// nach dem Auflösen (z. B. Seite schließen)
  final VoidCallback? onClosed;

  Future<void> _rename(BuildContext context, WidgetRef ref) async {
    final controller = TextEditingController(text: account.name);
    final name = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(tr('Konto umbenennen')),
        content: TextField(
          key: const Key('extra_name'),
          controller: controller,
          maxLength: 40,
          autofocus: true,
          decoration: InputDecoration(labelText: tr('Name')),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(), child: Text(tr('Abbrechen'))),
          FilledButton(
            key: const Key('extra_name_save'),
            onPressed: () => Navigator.of(ctx).pop(controller.text),
            child: Text(tr('Speichern')),
          ),
        ],
      ),
    );
    controller.dispose();
    if (name == null || name.trim().isEmpty || !context.mounted) return;
    try {
      await ref.read(multiBankRepositoryProvider).rename(account.id, name);
      ref.invalidate(accountsProvider);
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    }
  }

  Future<void> _close(BuildContext context, WidgetRef ref) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(tr('„{0}“ auflösen?', [account.name])),
        content: Text(account.isCall
            ? tr('Die Zinsen bis gestern werden gutgeschrieben, das Guthaben geht aufs Girokonto. Danach ist das Konto geschlossen.')
            : tr('Das Guthaben geht aufs Girokonto. Danach ist das Konto geschlossen und die IBAN nimmt nichts mehr an.')),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: Text(tr('Abbrechen'))),
          FilledButton(
            key: const Key('extra_close_confirm'),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: Text(tr('Auflösen')),
          ),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    try {
      final r = await ref.read(multiBankRepositoryProvider).close(account.id);
      ref.invalidate(accountsProvider);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(r.message)));
      }
      onClosed?.call();
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return PopupMenuButton<String>(
      key: const Key('extra_menu'),
      tooltip: tr('Konto verwalten'),
      onSelected: (v) async {
        switch (v) {
          case 'move':
            if (await showMoveMoneyDialog(context, fromId: account.id) && context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr('Umgebucht'))));
            }
          case 'rename':
            await _rename(context, ref);
          case 'close':
            await _close(context, ref);
        }
      },
      itemBuilder: (_) => [
        PopupMenuItem(value: 'move', key: const Key('extra_move'), child: Text(tr('Umbuchen'))),
        PopupMenuItem(value: 'rename', key: const Key('extra_rename'), child: Text(tr('Umbenennen'))),
        PopupMenuItem(value: 'close', key: const Key('extra_close'), child: Text(tr('Auflösen'))),
      ],
    );
  }
}
