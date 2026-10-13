import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/charts.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../accounts/accounts_repository.dart';
import '../insights/insights_repository.dart';
import '../subaccounts/subaccount_dialogs.dart';
import '../subaccounts/subaccount_icons.dart';
import 'plan_models.dart';
import 'plans_repository.dart';
import '../../core/layout.dart';

/// Plan-Detail (Pflichtenheft B11): Fortschritt, Prognose, Depot, Verlauf, Spartipps, Bearbeiten, Auszahlen.
class PlanDetailScreen extends ConsumerStatefulWidget {
  const PlanDetailScreen({super.key, required this.planId});

  final int planId;

  @override
  ConsumerState<PlanDetailScreen> createState() => _PlanDetailScreenState();
}

class _PlanDetailScreenState extends ConsumerState<PlanDetailScreen> {
  bool _busy = false;

  void _refresh() {
    ref.invalidate(planDetailProvider(widget.planId));
    ref.invalidate(plansProvider);
    ref.invalidate(overviewProvider);
    ref.invalidate(accountsProvider);
    ref.invalidate(netWorthProvider);
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

  Future<void> _changeRate(PlanSummary p) async {
    final cents = await askAmount(context, title: 'Neue Monatsrate', maxCents: 1000000);
    if (cents == null) return;
    await _run(() => ref.read(plansRepositoryProvider).update(p.id, {'monthlyRateCents': cents}),
        'Rate auf ${formatCents(cents)} geändert');
  }

  Future<void> _changeRisk(PlanSummary p) async {
    final risk = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('Risikostufe ändern'),
        children: [
          for (final e in riskLevels.entries)
            SimpleDialogOption(
              onPressed: () => Navigator.of(ctx).pop(e.key),
              child: ListTile(
                title: Text(e.value),
                subtitle: Text(riskDescription(e.key, invest: p.isInvest)),
                trailing: e.key == p.riskLevel ? const Icon(Icons.check) : null,
              ),
            ),
        ],
      ),
    );
    if (risk == null || risk == p.riskLevel) return;
    await _run(() => ref.read(plansRepositoryProvider).update(p.id, {'riskLevel': risk}),
        'Gilt ab der nächsten Rate');
  }

  /// /K010/: Erinnerung vor der Abbuchung einstellen
  Future<void> _changeReminder(PlanSummary p) async {
    final days = await showDialog<int>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('Erinnerung an die Rate'),
        children: [
          for (final d in reminderOptions.entries)
            SimpleDialogOption(
              onPressed: () => Navigator.of(ctx).pop(d.key),
              child: ListTile(
                title: Text(d.value),
                trailing: d.key == p.reminderDays ? const Icon(Icons.check) : null,
              ),
            ),
        ],
      ),
    );
    if (days == null || days == p.reminderDays) return;
    await _run(() => ref.read(plansRepositoryProvider).update(p.id, {'reminderDays': days}),
        days == 0 ? 'Erinnerung ausgeschaltet' : 'Erinnerung: ${reminderOptions[days]}');
  }

  Future<void> _togglePause(PlanSummary p) =>
      _run(() => ref.read(plansRepositoryProvider).update(p.id, {'paused': !p.isPaused}),
          p.isPaused ? 'Plan fortgesetzt' : 'Plan pausiert');

  Future<void> _payoutOrClose(PlanSummary p, int value, {required bool payout}) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(payout ? 'Auszahlen?' : 'Plan auflösen?'),
        content: Text('${formatCents(value)} werden aufs Girokonto gebucht. '
            '${p.isInvest || value > 0 ? 'Die Fondsanteile werden zum aktuellen Preis verkauft. ' : ''}'
            'Der Plan wird beendet.'),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text('Abbrechen')),
          FilledButton(onPressed: () => Navigator.of(ctx).pop(true), child: Text(payout ? 'Auszahlen' : 'Auflösen')),
        ],
      ),
    );
    if (ok != true) return;
    final repo = ref.read(plansRepositoryProvider);
    await _run(() => payout ? repo.payout(p.id) : repo.close(p.id), 'Auf das Girokonto gebucht');
    if (!payout && mounted) context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(planDetailProvider(widget.planId));
    return Scaffold(
      appBar: AppBar(title: const Text('Plan')),
      body: detail.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (d) => RefreshIndicator(
          onRefresh: () => ref.refresh(planDetailProvider(widget.planId).future),
          child: _content(d),
        ),
      ),
    );
  }

  Widget _content(PlanDetail d) {
    final p = d.summary;
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final reached = p.progressPercent != null && p.progressPercent! >= 100;
    final gainColor = d.gainCents >= 0 ? IncomeExpenseChart.incomeColor : scheme.error;

    return ListView(padding: safeListPadding(context), children: [
      Row(children: [
        CircleAvatar(radius: 26, child: Icon(p.isInvest ? Icons.show_chart : subaccountIcon(p.icon))),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(p.name, style: theme.textTheme.titleLarge),
            Text('${p.isInvest ? 'Investplan' : 'Sparplan'} · ${riskLevels[p.riskLevel]}'
                '${p.isPaused ? ' · pausiert' : ''}${p.status == 'COMPLETED' ? ' · ausgezahlt' : ''}'),
          ]),
        ),
      ]),
      const SizedBox(height: 16),
      Text(formatCents(p.currentValueCents),
          style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
      Text('${d.gainCents >= 0 ? '+' : ''}${formatCents(d.gainCents)} (${d.gainPercent.toStringAsFixed(1).replaceAll('.', ',')} %) '
          'gegenüber ${formatCents(p.depositedCents)} eingezahlt',
          style: TextStyle(color: gainColor)),
      if (p.progressPercent != null && p.isOpen) ...[
        const SizedBox(height: 16),
        LinearProgressIndicator(
          value: (p.progressPercent! / 100).clamp(0.0, 1.0),
          minHeight: 12,
          borderRadius: BorderRadius.circular(6),
        ),
        const SizedBox(height: 6),
        Row(children: [
          Expanded(child: Text('${p.progressPercent!.toStringAsFixed(0)} % von ${formatCents(p.targetCents!)}')),
          if (p.onTrack != null)
            Chip(
              label: Text(p.onTrack! ? 'Im Plan' : 'Hinter Plan'),
              avatar: Icon(p.onTrack! ? Icons.check : Icons.schedule, size: 16),
              visualDensity: VisualDensity.compact,
            ),
        ]),
        Text(p.projectedDate == null
            ? 'Mit der aktuellen Rate ist das Ziel nicht erreichbar.'
            : 'Voraussichtlich erreicht: ${formatDate(p.projectedDate!)}'
                '${p.targetDate != null ? ' (Wunsch: ${formatDate(p.targetDate!)})' : ''}'),
        if (p.onTrack == false && d.requiredRateCents != null)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text('Für dein Wunschdatum bräuchtest du ${formatCents(d.requiredRateCents!)} pro Monat.',
                style: TextStyle(color: scheme.error)),
          ),
      ],
      if (reached && p.isOpen)
        Card(
          color: scheme.tertiaryContainer,
          margin: const EdgeInsets.only(top: 12),
          child: ListTile(
            leading: const Icon(Icons.celebration),
            title: const Text('Ziel erreicht!'),
            subtitle: const Text('Du kannst jetzt auszahlen oder weitersparen.'),
            trailing: FilledButton(
              onPressed: _busy ? null : () => _payoutOrClose(p, p.currentValueCents, payout: true),
              child: const Text('Auszahlen'),
            ),
          ),
        ),
      const SizedBox(height: 16),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('Verlauf', style: theme.textTheme.titleSmall),
            const SizedBox(height: 12),
            ValueLineChart(points: [for (final h in d.history) ValuePoint(h.$1, h.$2, h.$3)]),
          ]),
        ),
      ),
      Card(
        child: Column(children: [
          if (!p.isInvest) _row('Sparkonto-Anteil', formatCents(d.cashCents)),
          _row('Depotwert', formatCents(d.depotValueCents)),
          _row('Monatsrate', '${formatCents(p.monthlyRateCents)} am ${p.executionDay}.'),
          if (p.nextExecutionDate != null) _row('Nächste Rate', formatDate(p.nextExecutionDate!)),
          _row('Erwartete Rendite', '${(d.expectedReturnPa * 100).toStringAsFixed(1).replaceAll('.', ',')} % p. a.'),
          _row('Nur Sparkonto (Vergleich)', formatCents(p.depositedCents)),
        ]),
      ),
      if (d.positions.isNotEmpty) ...[
        const SizedBox(height: 8),
        Text('Musterdepot', style: theme.textTheme.titleSmall),
        Card(
          child: Column(children: [
            for (final pos in d.positions)
              ListTile(
                title: Text(pos.label),
                subtitle: Text('${pos.units.toStringAsFixed(4).replaceAll('.', ',')} Anteile à '
                    '${pos.price.toStringAsFixed(2).replaceAll('.', ',')} € · ${pos.weightPercent.toStringAsFixed(0)} %'),
                trailing: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.end, children: [
                  Text(formatCents(pos.valueCents), style: const TextStyle(fontWeight: FontWeight.bold)),
                  Text('investiert ${formatCents(pos.investedCents)}', style: theme.textTheme.bodySmall),
                ]),
              ),
          ]),
        ),
      ],
      if (d.tips.isNotEmpty) ...[
        const SizedBox(height: 8),
        Text('Spartipps', style: theme.textTheme.titleSmall),
        for (final t in d.tips)
          Card(
            color: scheme.secondaryContainer,
            child: ListTile(leading: const Icon(Icons.lightbulb_outline), title: Text(t.label), subtitle: Text(t.text)),
          ),
      ],
      Text(d.riskExplanation, style: theme.textTheme.bodySmall),
      if (p.isOpen) ...[
        const SizedBox(height: 16),
        Text('Einstellungen', style: theme.textTheme.titleSmall),
        Card(
          child: Column(children: [
            ListTile(
              leading: const Icon(Icons.edit),
              title: const Text('Monatsrate ändern'),
              onTap: _busy ? null : () => _changeRate(p),
            ),
            ListTile(
              leading: const Icon(Icons.tune),
              title: const Text('Risikostufe ändern'),
              onTap: _busy ? null : () => _changeRisk(p),
            ),
            ListTile(
              key: const Key('plan_reminder'),
              leading: Icon(p.reminderDays > 0
                  ? Icons.notifications_active_outlined
                  : Icons.notifications_off_outlined),
              title: const Text('Erinnerung vor der Abbuchung'),
              subtitle: Text(reminderOptions[p.reminderDays] ?? '${p.reminderDays} Tage vorher'),
              onTap: _busy ? null : () => _changeReminder(p),
            ),
            ListTile(
              leading: Icon(p.isPaused ? Icons.play_arrow : Icons.pause),
              title: Text(p.isPaused ? 'Fortsetzen' : 'Pausieren'),
              onTap: _busy ? null : () => _togglePause(p),
            ),
            ListTile(
              leading: Icon(Icons.delete_outline, color: scheme.error),
              title: Text('Plan auflösen und auszahlen', style: TextStyle(color: scheme.error)),
              onTap: _busy ? null : () => _payoutOrClose(p, p.currentValueCents, payout: false),
            ),
          ]),
        ),
      ],
    ]);
  }

  Widget _row(String label, String value) => ListTile(
        dense: true,
        title: Text(label),
        trailing: Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
      );
}
