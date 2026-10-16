import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/state_views.dart';
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
import '../../core/theme.dart';

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
    final cents = await askAmount(context, title: tr('Neue Monatsrate'), maxCents: 1000000);
    if (cents == null) return;
    await _run(() => ref.read(plansRepositoryProvider).update(p.id, {'monthlyRateCents': cents}),
        tr('Rate auf {0} geändert', [formatCents(cents)]));
  }

  Future<void> _changeRisk(PlanSummary p) async {
    final risk = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(tr('Risikostufe ändern')),
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
        tr('Gilt ab der nächsten Rate'));
  }

  /// /K010/: Erinnerung vor der Abbuchung einstellen
  Future<void> _changeReminder(PlanSummary p) async {
    final days = await showDialog<int>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(tr('Erinnerung an die Rate')),
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
        days == 0 ? tr('Erinnerung ausgeschaltet') : tr('Erinnerung: {0}', [reminderOptions[days]]));
  }

  Future<void> _togglePause(PlanSummary p) =>
      _run(() => ref.read(plansRepositoryProvider).update(p.id, {'paused': !p.isPaused}),
          p.isPaused ? tr('Plan fortgesetzt') : tr('Plan pausiert'));

  Future<void> _payoutOrClose(PlanSummary p, int value, {required bool payout}) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(payout ? tr('Auszahlen?') : tr('Plan auflösen?')),
        content: Text(tr('{0} werden aufs Girokonto gebucht. {1}Der Plan wird beendet.', [formatCents(value), p.isInvest || value > 0 ? tr('Die Fondsanteile werden zum aktuellen Preis verkauft. ') : ''])),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: Text(tr('Abbrechen'))),
          FilledButton(onPressed: () => Navigator.of(ctx).pop(true), child: Text(payout ? tr('Auszahlen') : tr('Auflösen'))),
        ],
      ),
    );
    if (ok != true) return;
    final repo = ref.read(plansRepositoryProvider);
    await _run(() => payout ? repo.payout(p.id) : repo.close(p.id), tr('Auf das Girokonto gebucht'));
    if (!payout && mounted) context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(planDetailProvider(widget.planId));
    return Scaffold(
      appBar: AppBar(title: Text(tr('Plan'))),
      body: detail.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(planDetailProvider(widget.planId))),
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
    final gainColor = d.gainCents >= 0 ? positiveColor(context) : scheme.error;

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
      Text(tr('{0}{1} ({2} %) gegenüber {3} eingezahlt', [d.gainCents >= 0 ? '+' : '', formatCents(d.gainCents), d.gainPercent.toStringAsFixed(1).localDecimal, formatCents(p.depositedCents)]),
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
          Expanded(child: Text(tr('{0} % von {1}', [p.progressPercent!.toStringAsFixed(0), formatCents(p.targetCents!)]))),
          if (p.onTrack != null)
            Chip(
              label: Text(p.onTrack! ? tr('Im Plan') : tr('Hinter Plan')),
              avatar: Icon(p.onTrack! ? Icons.check : Icons.schedule, size: 16),
              visualDensity: VisualDensity.compact,
            ),
        ]),
        Text(p.projectedDate == null
            ? tr('Mit der aktuellen Rate ist das Ziel nicht erreichbar.')
            : tr('Voraussichtlich erreicht: {0}{1}', [formatDate(p.projectedDate!), p.targetDate != null ? tr(' (Wunsch: {0})', [formatDate(p.targetDate!)]) : ''])),
        if (p.onTrack == false && d.requiredRateCents != null)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text(tr('Für dein Wunschdatum bräuchtest du {0} pro Monat.', [formatCents(d.requiredRateCents!)]),
                style: TextStyle(color: scheme.error)),
          ),
      ],
      if (reached && p.isOpen)
        Card(
          color: scheme.tertiaryContainer,
          margin: const EdgeInsets.only(top: 12),
          child: ListTile(
            leading: const Icon(Icons.celebration),
            title: Text(tr('Ziel erreicht!')),
            subtitle: Text(tr('Du kannst jetzt auszahlen oder weitersparen.')),
            trailing: FilledButton(
              onPressed: _busy ? null : () => _payoutOrClose(p, p.currentValueCents, payout: true),
              child: Text(tr('Auszahlen')),
            ),
          ),
        ),
      const SizedBox(height: 16),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(tr('Verlauf'), style: theme.textTheme.titleSmall),
            const SizedBox(height: 12),
            ValueLineChart(points: [for (final h in d.history) ValuePoint(h.$1, h.$2, h.$3)]),
          ]),
        ),
      ),
      Card(
        child: Column(children: [
          if (!p.isInvest) _row(tr('Sparkonto-Anteil'), formatCents(d.cashCents)),
          _row(tr('Depotwert'), formatCents(d.depotValueCents)),
          _row(tr('Monatsrate'), tr('{0} am {1}.', [formatCents(p.monthlyRateCents), p.executionDay])),
          if (p.nextExecutionDate != null) _row(tr('Nächste Rate'), formatDate(p.nextExecutionDate!)),
          _row(tr('Erwartete Rendite'), '${(d.expectedReturnPa * 100).toStringAsFixed(1).localDecimal} % p. a.'),
          _row(tr('Nur Sparkonto (Vergleich)'), formatCents(p.depositedCents)),
        ]),
      ),
      if (d.positions.isNotEmpty) ...[
        const SizedBox(height: 8),
        Text(tr('Musterdepot'), style: theme.textTheme.titleSmall),
        Card(
          child: Column(children: [
            for (final pos in d.positions)
              ListTile(
                title: Text(pos.label),
                subtitle: Text(tr('{0} Anteile à {1} € · {2} %', [pos.units.toStringAsFixed(4).localDecimal, pos.price.toStringAsFixed(2).localDecimal, pos.weightPercent.toStringAsFixed(0)])),
                trailing: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.end, children: [
                  Text(formatCents(pos.valueCents), style: const TextStyle(fontWeight: FontWeight.bold)),
                  Text(tr('investiert {0}', [formatCents(pos.investedCents)]), style: theme.textTheme.bodySmall),
                ]),
              ),
          ]),
        ),
      ],
      if (d.tips.isNotEmpty) ...[
        const SizedBox(height: 8),
        Text(tr('Spartipps'), style: theme.textTheme.titleSmall),
        for (final t in d.tips)
          Card(
            color: scheme.secondaryContainer,
            child: ListTile(leading: const Icon(Icons.lightbulb_outline), title: Text(t.label), subtitle: Text(t.text)),
          ),
      ],
      Text(d.riskExplanation, style: theme.textTheme.bodySmall),
      if (p.isOpen) ...[
        const SizedBox(height: 16),
        Text(tr('Einstellungen'), style: theme.textTheme.titleSmall),
        Card(
          child: Column(children: [
            ListTile(
              leading: const Icon(Icons.edit),
              title: Text(tr('Monatsrate ändern')),
              onTap: _busy ? null : () => _changeRate(p),
            ),
            ListTile(
              leading: const Icon(Icons.tune),
              title: Text(tr('Risikostufe ändern')),
              onTap: _busy ? null : () => _changeRisk(p),
            ),
            ListTile(
              key: const Key('plan_reminder'),
              leading: Icon(p.reminderDays > 0
                  ? Icons.notifications_active_outlined
                  : Icons.notifications_off_outlined),
              title: Text(tr('Erinnerung vor der Abbuchung')),
              subtitle: Text(reminderOptions[p.reminderDays] ?? tr('{0} Tage vorher', [p.reminderDays])),
              onTap: _busy ? null : () => _changeReminder(p),
            ),
            ListTile(
              leading: Icon(p.isPaused ? Icons.play_arrow : Icons.pause),
              title: Text(p.isPaused ? tr('Fortsetzen') : tr('Pausieren')),
              onTap: _busy ? null : () => _togglePause(p),
            ),
            ListTile(
              leading: Icon(Icons.delete_outline, color: scheme.error),
              title: Text(tr('Plan auflösen und auszahlen'), style: TextStyle(color: scheme.error)),
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
