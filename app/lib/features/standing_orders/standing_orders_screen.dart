import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../subaccounts/subaccount_dialogs.dart';
import 'standing_order_form_screen.dart';
import 'standing_order_models.dart';

/// /K030/ Übersicht der Daueraufträge: anlegen, Betrag/Tag ändern, pausieren, löschen.
class StandingOrdersScreen extends ConsumerWidget {
  const StandingOrdersScreen({super.key});

  Future<void> _openForm(BuildContext context, WidgetRef ref) async {
    final created = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => const StandingOrderFormScreen()),
    );
    if (created == true) ref.invalidate(standingOrdersProvider);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final orders = ref.watch(standingOrdersProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Daueraufträge')),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('standing_order_new'),
        onPressed: () => _openForm(context, ref),
        icon: const Icon(Icons.add),
        label: const Text('Neuer Dauerauftrag'),
      ),
      body: orders.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (list) => RefreshIndicator(
          onRefresh: () => ref.refresh(standingOrdersProvider.future),
          child: list.isEmpty
              ? ListView(padding: const EdgeInsets.all(24), children: const [
                  SizedBox(height: 40),
                  Icon(Icons.event_repeat, size: 56),
                  SizedBox(height: 12),
                  Text('Noch keine Daueraufträge.\nZum Beispiel für Miete, Taschengeld oder die WG-Kasse.',
                      textAlign: TextAlign.center),
                ])
              : ListView(padding: const EdgeInsets.fromLTRB(16, 16, 16, 96), children: [
                  for (final o in list) _OrderCard(order: o),
                ]),
        ),
      ),
    );
  }
}

class _OrderCard extends ConsumerWidget {
  const _OrderCard({required this.order});

  final StandingOrder order;

  Future<void> _change(BuildContext context, WidgetRef ref, Map<String, dynamic> changes, String done) async {
    try {
      await ref.read(standingOrdersRepositoryProvider).update(order.id, changes);
      ref.invalidate(standingOrdersProvider);
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(done)));
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    }
  }

  Future<void> _actions(BuildContext context, WidgetRef ref) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.euro),
            title: const Text('Betrag ändern'),
            onTap: () => Navigator.pop(ctx, 'amount'),
          ),
          ListTile(
            leading: const Icon(Icons.today),
            title: const Text('Ausführungstag ändern'),
            onTap: () => Navigator.pop(ctx, 'day'),
          ),
          ListTile(
            leading: Icon(order.isPaused ? Icons.play_arrow : Icons.pause),
            title: Text(order.isPaused ? 'Fortsetzen' : 'Pausieren'),
            onTap: () => Navigator.pop(ctx, 'pause'),
          ),
          ListTile(
            leading: Icon(Icons.delete_outline, color: Theme.of(ctx).colorScheme.error),
            title: Text('Löschen', style: TextStyle(color: Theme.of(ctx).colorScheme.error)),
            onTap: () => Navigator.pop(ctx, 'delete'),
          ),
        ]),
      ),
    );
    if (!context.mounted || action == null) return;
    switch (action) {
      case 'amount':
        final cents = await askAmount(context, title: 'Neuer Betrag', maxCents: 500000);
        if (cents != null && context.mounted) {
          await _change(context, ref, {'amountCents': cents}, 'Betrag auf ${formatCents(cents)} geändert');
        }
      case 'day':
        final day = await showDialog<int>(context: context, builder: (_) => DayPickerDialog(selected: order.executionDay));
        if (day != null && context.mounted) {
          await _change(context, ref, {'executionDay': day}, 'Ausführung jetzt am $day.');
        }
      case 'pause':
        await _change(context, ref, {'paused': !order.isPaused},
            order.isPaused ? 'Dauerauftrag fortgesetzt' : 'Dauerauftrag pausiert');
      case 'delete':
        final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('Dauerauftrag löschen?'),
            content: Text('${formatCents(order.amountCents)} an ${order.recipientName} werden nicht mehr überwiesen.'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Abbrechen')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Löschen')),
            ],
          ),
        );
        if (ok == true) {
          try {
            await ref.read(standingOrdersRepositoryProvider).delete(order.id);
            ref.invalidate(standingOrdersProvider);
          } catch (e) {
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
            }
          }
        }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final o = order;
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => _actions(context, ref),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              CircleAvatar(child: Text(o.recipientName.isEmpty ? '?' : o.recipientName[0])),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(o.recipientName, style: theme.textTheme.titleMedium, overflow: TextOverflow.ellipsis),
                  Text(o.reference, style: theme.textTheme.bodySmall, overflow: TextOverflow.ellipsis),
                ]),
              ),
              Text(formatCents(o.amountCents), style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            ]),
            const SizedBox(height: 10),
            Wrap(spacing: 8, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
              Chip(
                visualDensity: VisualDensity.compact,
                avatar: const Icon(Icons.event_repeat, size: 16),
                label: Text('${o.intervalLabel}, am ${o.executionDay}.'),
              ),
              if (o.isPaused)
                const Chip(visualDensity: VisualDensity.compact, avatar: Icon(Icons.pause, size: 16), label: Text('Pausiert'))
              else if (o.nextExecutionDate != null)
                Text('Nächste: ${formatDate(o.nextExecutionDate!)}', style: theme.textTheme.bodySmall),
              if (o.endDate != null) Text('bis ${formatDate(o.endDate!)}', style: theme.textTheme.bodySmall),
            ]),
          ]),
        ),
      ),
    );
  }
}

/// Auswahl des Ausführungstags 1–28
class DayPickerDialog extends StatelessWidget {
  const DayPickerDialog({super.key, required this.selected});

  final int selected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AlertDialog(
      title: const Text('Ausführungstag'),
      content: SizedBox(
        width: 280,
        child: SingleChildScrollView(
          child: Wrap(spacing: 6, runSpacing: 6, children: [
            for (var d = 1; d <= 28; d++)
              SizedBox(
                width: 40,
                height: 40,
                child: Material(
                  color: d == selected ? scheme.primary : scheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(20),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(20),
                    onTap: () => Navigator.pop(context, d),
                    child: Center(
                      child: Text('$d', style: TextStyle(color: d == selected ? scheme.onPrimary : null)),
                    ),
                  ),
                ),
              ),
          ]),
        ),
      ),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Abbrechen'))],
    );
  }
}
