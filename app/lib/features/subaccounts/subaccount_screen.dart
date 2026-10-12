import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../accounts/account.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/booking_tile.dart';
import '../accounts/bookings_repository.dart';
import 'subaccount_dialogs.dart';
import 'subaccount_icons.dart';
import 'subaccounts_repository.dart';

/// Detail eines Unterkontos: einzahlen, auszahlen, bearbeiten, auflösen (/F420/, /F430/).
class SubaccountScreen extends ConsumerStatefulWidget {
  const SubaccountScreen({super.key, required this.accountId});

  final int accountId;

  @override
  ConsumerState<SubaccountScreen> createState() => _SubaccountScreenState();
}

class _SubaccountScreenState extends ConsumerState<SubaccountScreen> {
  bool _busy = false;

  void _refresh() {
    ref.invalidate(accountsProvider);
    ref.invalidate(recentBookingsProvider);
  }

  Future<void> _run(Future<void> Function() action, String success) async {
    setState(() => _busy = true);
    try {
      await action();
      _refresh();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(success)));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _deposit(Account main, Account sub) async {
    final cents = await askAmount(context, title: 'Vom Girokonto einzahlen', maxCents: main.balanceCents);
    if (cents == null) return;
    await _run(() => ref.read(subaccountsRepositoryProvider).move(main.id, sub.id, cents),
        '${formatCents(cents)} eingezahlt');
  }

  Future<void> _withdraw(Account main, Account sub) async {
    final cents = await askAmount(context, title: 'Aufs Girokonto auszahlen', maxCents: sub.balanceCents);
    if (cents == null) return;
    await _run(() => ref.read(subaccountsRepositoryProvider).move(sub.id, main.id, cents),
        '${formatCents(cents)} ausgezahlt');
  }

  Future<void> _edit(Account sub) async {
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => SubaccountEditDialog(id: sub.id, initialName: sub.name, initialIcon: sub.icon ?? 'savings'),
    );
    if (saved == true) _refresh();
  }

  Future<void> _close(Account sub) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('„${sub.name}“ auflösen?'),
        content: Text(sub.balanceCents > 0
            ? 'Das Guthaben von ${formatCents(sub.balanceCents)} wird aufs Girokonto gebucht.'
            : 'Das Unterkonto wird gelöscht.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text('Abbrechen')),
          FilledButton(onPressed: () => Navigator.of(ctx).pop(true), child: const Text('Auflösen')),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      await ref.read(subaccountsRepositoryProvider).close(sub.id);
      _refresh();
      if (mounted) context.pop();
    } catch (e) {
      if (mounted) {
        setState(() => _busy = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final accounts = ref.watch(accountsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Unterkonto')),
      body: accounts.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (list) {
          final subs = list.where((a) => a.id == widget.accountId);
          final mains = list.where((a) => a.isMain);
          if (subs.isEmpty || mains.isEmpty) {
            return const Center(child: Text('Unterkonto nicht gefunden.'));
          }
          final sub = subs.first;
          final main = mains.first;
          final recent = ref.watch(recentBookingsProvider(sub.id));
          final theme = Theme.of(context);
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const SizedBox(height: 8),
              Center(
                child: CircleAvatar(radius: 32, child: Icon(subaccountIcon(sub.icon), size: 32)),
              ),
              const SizedBox(height: 12),
              Center(child: Text(sub.name, style: theme.textTheme.titleLarge)),
              const SizedBox(height: 4),
              Center(
                child: Text(formatCents(sub.balanceCents),
                    style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
              ),
              const SizedBox(height: 20),
              Row(children: [
                Expanded(
                  child: FilledButton.icon(
                    onPressed: _busy || main.balanceCents <= 0 ? null : () => _deposit(main, sub),
                    icon: const Icon(Icons.add),
                    label: const Text('Einzahlen'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.tonalIcon(
                    onPressed: _busy || sub.balanceCents <= 0 ? null : () => _withdraw(main, sub),
                    icon: const Icon(Icons.remove),
                    label: const Text('Auszahlen'),
                  ),
                ),
              ]),
              const SizedBox(height: 8),
              Text('Auf dem Girokonto verfügbar: ${formatCents(main.balanceCents)}',
                  textAlign: TextAlign.center, style: theme.textTheme.bodySmall),
              const SizedBox(height: 16),
              Card(
                child: Column(children: [
                  ListTile(
                    leading: const Icon(Icons.edit),
                    title: const Text('Name und Symbol ändern'),
                    onTap: _busy ? null : () => _edit(sub),
                  ),
                  ListTile(
                    leading: const Icon(Icons.receipt_long),
                    title: const Text('Alle Umsätze'),
                    onTap: () => context.push('/umsaetze/${sub.id}'),
                  ),
                  ListTile(
                    leading: Icon(Icons.delete_outline, color: theme.colorScheme.error),
                    title: Text('Unterkonto auflösen', style: TextStyle(color: theme.colorScheme.error)),
                    onTap: _busy ? null : () => _close(sub),
                  ),
                ]),
              ),
              const SizedBox(height: 8),
              Text('Letzte Bewegungen', style: theme.textTheme.titleMedium),
              recent.when(
                loading: () => const Padding(
                  padding: EdgeInsets.all(16),
                  child: Center(child: CircularProgressIndicator()),
                ),
                error: (e, _) => Text(apiErrorMessage(e)),
                data: (items) => items.isEmpty
                    ? const Padding(padding: EdgeInsets.all(16), child: Text('Noch keine Bewegungen.'))
                    : Column(children: [
                        for (final b in items)
                          BookingTile(item: b, onTap: () => context.push('/umsatz', extra: b)),
                      ]),
              ),
            ],
          );
        },
      ),
    );
  }
}
