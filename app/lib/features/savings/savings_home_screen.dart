import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../insights/demo_dialog.dart';
import '../insights/insights_models.dart';
import '../insights/insights_repository.dart';
import '../loans/loan_models.dart';
import '../loans/loans_repository.dart';
import '../subaccounts/subaccount_icons.dart';
import 'plan_models.dart';
import 'plans_repository.dart';
import '../../core/layout.dart';

/// Bereich "Sparen": Finanzüberblick, Spar- und Investpläne, Kredite (Pflichtenheft B08).
class SavingsHomeScreen extends ConsumerWidget {
  const SavingsHomeScreen({super.key});

  void _refresh(WidgetRef ref) {
    ref.invalidate(overviewProvider);
    ref.invalidate(plansProvider);
    ref.invalidate(loansProvider);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final overview = ref.watch(overviewProvider);
    final plans = ref.watch(plansProvider);
    final loans = ref.watch(loansProvider);
    final theme = Theme.of(context);

    return RefreshIndicator(
      onRefresh: () async {
        _refresh(ref);
        await ref.read(plansProvider.future);
      },
      child: ListView(
        padding: safeListPadding(context),
        children: [
          overview.when(
            loading: () => const Card(child: SizedBox(height: 120, child: Center(child: CircularProgressIndicator()))),
            error: (e, _) => Card(child: Padding(padding: const EdgeInsets.all(16), child: Text(apiErrorMessage(e)))),
            data: (o) => _OverviewCard(o: o, onDemoCreated: () => _refresh(ref)),
          ),
          const SizedBox(height: 16),
          Row(children: [
            Expanded(
              child: _ActionCard(
                icon: Icons.flag,
                title: 'Sparplan',
                subtitle: 'Für ein Ziel sparen',
                onTap: () => context.push('/plan/neu?type=GOAL'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _ActionCard(
                icon: Icons.show_chart,
                title: 'Investplan',
                subtitle: 'Monatlich in Fonds',
                onTap: () => context.push('/plan/neu?type=INVEST'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _ActionCard(
                icon: Icons.request_quote,
                title: 'Kredit',
                subtitle: 'Planen & vergleichen',
                onTap: () => context.push('/kredit/neu'),
              ),
            ),
          ]),
          const SizedBox(height: 20),
          Text('Meine Pläne', style: theme.textTheme.titleMedium),
          const SizedBox(height: 4),
          plans.when(
            loading: () => const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator())),
            error: (e, _) => Text(apiErrorMessage(e)),
            data: (list) => list.isEmpty
                ? const Padding(
                    padding: EdgeInsets.symmetric(vertical: 12),
                    child: Text('Noch keine Pläne. Starte oben mit einem Spar- oder Investplan.'),
                  )
                : Column(children: [for (final p in list) _PlanTile(plan: p)]),
          ),
          const SizedBox(height: 20),
          Text('Meine Kredite', style: theme.textTheme.titleMedium),
          const SizedBox(height: 4),
          loans.when(
            loading: () => const SizedBox.shrink(),
            error: (e, _) => Text(apiErrorMessage(e)),
            data: (list) => list.isEmpty
                ? const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Text('Keine Kredite.'))
                : Column(children: [for (final l in list) _LoanTile(loan: l)]),
          ),
        ],
      ),
    );
  }
}

class _OverviewCard extends StatelessWidget {
  const _OverviewCard({required this.o, required this.onDemoCreated});

  final FinanceOverview o;
  final VoidCallback onDemoCreated;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    if (!o.hasData) {
      return Card(
        color: scheme.secondaryContainer,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('Dein Finanzassistent', style: theme.textTheme.titleMedium),
            const SizedBox(height: 6),
            const Text('Sobald Einnahmen und Ausgaben bekannt sind, zeigt die App, wie viel dir jeden Monat übrig '
                'bleibt, und prüft deine Pläne. Zum Ausprobieren kannst du Beispielmonate erzeugen.'),
            const SizedBox(height: 12),
            Wrap(spacing: 8, children: [
              FilledButton.icon(
                onPressed: () async {
                  if (await showDemoDialog(context)) onDemoCreated();
                },
                icon: const Icon(Icons.auto_awesome),
                label: const Text('Beispielmonate erzeugen'),
              ),
              OutlinedButton(onPressed: () => context.push('/finanzen'), child: const Text('Finanzüberblick')),
            ]),
          ]),
        ),
      );
    }
    return Card(
      color: scheme.primaryContainer,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => context.push('/finanzen'),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(child: Text('Jeden Monat übrig', style: theme.textTheme.titleSmall)),
              const Icon(Icons.chevron_right),
            ]),
            Text(formatCents(o.avgFreeCents),
                style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
            Text('Ø Einnahmen ${formatCents(o.avgIncomeCents)} · Ø Ausgaben ${formatCents(o.avgExpenseCents)}',
                style: theme.textTheme.bodySmall),
            const Divider(height: 24),
            Row(children: [
              Expanded(child: Text('Verfügbar für neue Pläne', style: theme.textTheme.bodyMedium)),
              Text(formatCents(o.availableCents), style: const TextStyle(fontWeight: FontWeight.bold)),
            ]),
            if (o.planRatesCents > 0)
              Text('Bereits verplant: ${formatCents(o.planRatesCents)} pro Monat', style: theme.textTheme.bodySmall),
          ]),
        ),
      ),
    );
  }
}

class _ActionCard extends StatelessWidget {
  const _ActionCard({required this.icon, required this.title, required this.subtitle, required this.onTap});

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(icon, color: theme.colorScheme.primary),
            const SizedBox(height: 8),
            Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
            Text(subtitle, style: theme.textTheme.bodySmall, maxLines: 2),
          ]),
        ),
      ),
    );
  }
}

class _PlanTile extends StatelessWidget {
  const _PlanTile({required this.plan});

  final PlanSummary plan;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final p = plan;
    String status;
    if (p.status == 'COMPLETED') {
      status = 'Ausgezahlt';
    } else if (p.isPaused) {
      status = 'Pausiert';
    } else if (p.onTrack == true) {
      status = 'Im Plan';
    } else if (p.onTrack == false) {
      status = 'Hinter Plan';
    } else {
      status = '${formatCents(p.monthlyRateCents)} / Monat';
    }
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => context.push('/plan/${p.id}'),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              CircleAvatar(
                child: Icon(p.isInvest ? Icons.show_chart : subaccountIcon(p.icon), size: 20),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(p.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                  Text('${p.isInvest ? 'Investplan' : 'Sparplan'} · ${riskLevels[p.riskLevel] ?? p.riskLevel} · $status',
                      style: theme.textTheme.bodySmall),
                ]),
              ),
              Text(formatCents(p.currentValueCents), style: const TextStyle(fontWeight: FontWeight.bold)),
            ]),
            if (p.progressPercent != null && p.isOpen) ...[
              const SizedBox(height: 10),
              LinearProgressIndicator(
                value: (p.progressPercent! / 100).clamp(0.0, 1.0),
                minHeight: 8,
                borderRadius: BorderRadius.circular(4),
              ),
              const SizedBox(height: 4),
              Text(
                '${p.progressPercent!.toStringAsFixed(0)} % von ${formatCents(p.targetCents!)}'
                '${p.projectedDate != null ? ' · voraussichtlich ${formatDate(p.projectedDate!)}' : ''}',
                style: theme.textTheme.bodySmall,
              ),
            ],
          ]),
        ),
      ),
    );
  }
}

class _LoanTile extends StatelessWidget {
  const _LoanTile({required this.loan});

  final Loan loan;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => context.push('/kredit/${loan.id}'),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              const CircleAvatar(child: Icon(Icons.request_quote, size: 20)),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${loan.purposeLabel} · ${loan.bankName}', style: const TextStyle(fontWeight: FontWeight.bold)),
                  Text(
                    loan.isActive
                        ? '${formatCents(loan.monthlyRateCents)} / Monat · nächste Rate ${loan.nextDueDate == null ? '–' : formatDate(loan.nextDueDate!)}'
                        : 'Abbezahlt',
                    style: theme.textTheme.bodySmall,
                  ),
                ]),
              ),
              Text(formatCents(-loan.remainingCents), style: const TextStyle(fontWeight: FontWeight.bold)),
            ]),
            const SizedBox(height: 10),
            LinearProgressIndicator(
              value: (loan.progressPercent / 100).clamp(0.0, 1.0),
              minHeight: 8,
              borderRadius: BorderRadius.circular(4),
            ),
            const SizedBox(height: 4),
            Text('${loan.progressPercent.toStringAsFixed(0)} % getilgt', style: theme.textTheme.bodySmall),
          ]),
        ),
      ),
    );
  }
}
