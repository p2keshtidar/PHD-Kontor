import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/charts.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import 'dashboard_models.dart';
import 'dashboard_repository.dart';

/// Zentrales Finanz-Dashboard: Gesamtvermögen, verfügbar und zurückgelegt, Vermögensverlauf, Cashflow,
/// größte Ausgaben, Schulden, Sparziele, Konten und Karten. Alle Zahlen kommen vom Server.
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(dashboardProvider);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Finanz-Dashboard'))),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(dashboardProvider);
          await ref.read(dashboardProvider.future);
        },
        child: data.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(24),
            children: [
              const SizedBox(height: 60),
              const Icon(Icons.cloud_off, size: 48),
              const SizedBox(height: 12),
              Text(apiErrorMessage(e), textAlign: TextAlign.center),
              const SizedBox(height: 12),
              Center(
                child: OutlinedButton(
                  key: const Key('dash_retry'),
                  onPressed: () => ref.invalidate(dashboardProvider),
                  child: Text(tr('Erneut versuchen')),
                ),
              ),
            ],
          ),
          data: (d) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: safeListPadding(context),
            children: [
              _NetWorthCard(d: d),
              const SizedBox(height: 12),
              _LiquidityCard(l: d.liquidity),
              _Section(
                key: const Key('dash_history'),
                title: tr('Vermögensverlauf'),
                subtitle: tr('Gesamtvermögen am Monatsende, letzte 12 Monate'),
                child: _History(points: d.history),
              ),
              _Section(
                key: const Key('dash_cashflow'),
                title: tr('Cashflow'),
                subtitle: tr('Einnahmen und Ausgaben, letzte 6 Monate'),
                child: _CashflowView(c: d.cashflow),
              ),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  key: const Key('dash_planning'),
                  onPressed: () => context.push('/planung'),
                  icon: const Icon(Icons.track_changes),
                  label: Text(tr('Budgets & Prognose')),
                ),
              ),
              _Section(
                key: const Key('dash_top_expenses'),
                title: tr('Größte Ausgaben'),
                subtitle: d.topExpensesMonth == null ? null : _monthLabel(d.topExpensesMonth!),
                child: _TopExpenses(items: d.topExpenses),
              ),
              _Section(
                key: const Key('dash_debts'),
                title: tr('Schulden'),
                child: _DebtsView(debts: d.debts),
              ),
              _Section(
                key: const Key('dash_goals'),
                title: tr('Sparziele'),
                child: _GoalsView(goals: d.goals),
              ),
              _Section(
                key: const Key('dash_accounts'),
                title: tr('Konten und Karten'),
                child: _AccountsView(accounts: d.accounts, cards: d.cards),
              ),
              const SizedBox(height: 8),
              _Notes(notes: d.notes),
            ],
          ),
        ),
      ),
    );
  }
}

String _monthLabel(String ym) => '${monthName(int.parse(ym.substring(5, 7)))} ${ym.substring(0, 4)}';

/// Prozent mit einer Nachkommastelle im Format der Sprache ("16,7" / "16.7")
String _pct(double v) => L10n.isEnglish ? v.toStringAsFixed(1) : v.toStringAsFixed(1).replaceAll('.', ',');

String _shortMonth(String ym) => '${monthShort(int.parse(ym.substring(5, 7)))} ${ym.substring(2, 4)}';

class _Section extends StatelessWidget {
  const _Section({super.key, required this.title, this.subtitle, required this.child});

  final String title;
  final String? subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 20),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Semantics(header: true, child: Text(title, style: theme.textTheme.titleMedium)),
        if (subtitle != null) Text(subtitle!, style: theme.textTheme.bodySmall),
        const SizedBox(height: 10),
        child,
      ]),
    );
  }
}

class _AmountRow extends StatelessWidget {
  const _AmountRow(this.label, this.cents, {this.bold = false, this.color, this.rowKey});

  final String label;
  final int cents;
  final bool bold;
  final Color? color;
  final Key? rowKey;

  @override
  Widget build(BuildContext context) => Padding(
        key: rowKey,
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(child: Text(label, style: TextStyle(fontWeight: bold ? FontWeight.bold : null, color: color))),
          const SizedBox(width: 8),
          Text(formatCents(cents),
              style: TextStyle(fontWeight: bold ? FontWeight.bold : FontWeight.w600, color: color)),
        ]),
      );
}

class _NetWorthCard extends StatelessWidget {
  const _NetWorthCard({required this.d});

  final Dashboard d;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final n = d.netWorth;
    final on = scheme.onPrimaryContainer;
    return Card(
      key: const Key('dash_networth'),
      color: scheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(tr('Gesamtvermögen'), style: TextStyle(color: on)),
          const SizedBox(height: 4),
          Text(formatCents(n.totalCents),
              key: const Key('dash_total'),
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(color: on, fontWeight: FontWeight.bold)),
          Text(tr('Stand {0}', [formatDate(d.date)]), style: TextStyle(color: on, fontSize: 12)),
          const SizedBox(height: 12),
          _AmountRow(tr('Bankkonten (Projektbank)'), n.accountsCents, color: on),
          if (n.digitalCents != 0) _AmountRow(tr('Digitale Konten'), n.digitalCents, color: on),
          if (n.plansCents != 0) _AmountRow(tr('Sparpläne'), n.plansCents, color: on),
          if (n.depotCents != 0) _AmountRow(tr('Depot'), n.depotCents, color: on),
          if (n.externalCents != 0) _AmountRow(tr('Andere Banken'), n.externalCents, color: on),
          if (n.loansCents != 0) _AmountRow(tr('Kredite'), n.loansCents, color: on),
        ]),
      ),
    );
  }
}

class _LiquidityCard extends StatelessWidget {
  const _LiquidityCard({required this.l});

  final DashLiquidity l;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final available = math.max(0, l.availableCents);
    final sum = available + math.max(0, l.reservedCents);
    final share = sum == 0 ? 0.0 : available / sum;
    return Card(
      key: const Key('dash_liquidity'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(tr('Verfügbar und zurückgelegt'), style: theme.textTheme.titleSmall),
          const SizedBox(height: 10),
          Semantics(
            label: tr('{0} % verfügbar', [(share * 100).round()]),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: share,
                minHeight: 12,
                color: scheme.primary,
                backgroundColor: scheme.tertiaryContainer,
              ),
            ),
          ),
          const SizedBox(height: 10),
          _AmountRow(tr('Verfügbar (Girokonten)'), l.availableCents, bold: true, rowKey: const Key('dash_available')),
          _AmountRow(tr('Zurückgelegt (Tagesgeld, digitale Konten, Sparpläne, Depot)'), l.reservedCents,
              rowKey: const Key('dash_reserved')),
          if (l.earmarkedCents > 0)
            _AmountRow(tr('Bei anderen Banken für Ziele vorgemerkt'), l.earmarkedCents,
                rowKey: const Key('dash_earmarked')),
        ]),
      ),
    );
  }
}

class _History extends StatelessWidget {
  const _History({required this.points});

  final List<DashHistoryPoint> points;

  @override
  Widget build(BuildContext context) {
    if (points.every((p) => p.totalCents == 0)) {
      return Text(tr('Noch kein Verlauf vorhanden.'));
    }
    final incomplete = points.any((p) => !p.externalIncluded);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      WealthChart(points: [
        for (final p in points) WealthPoint(_shortMonth(p.month), p.totalCents, complete: p.externalIncluded),
      ]),
      if (incomplete) ...[
        const SizedBox(height: 6),
        Text(tr('Hohle Punkte: ohne verbundene Banken (für diese Monate liegen keine Umsätze vor).'),
            style: Theme.of(context).textTheme.bodySmall),
      ],
    ]);
  }
}

class _CashflowView extends StatelessWidget {
  const _CashflowView({required this.c});

  final DashCashflow c;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasData = c.months.any((m) => m.incomeCents != 0 || m.expenseCents != 0);
    Widget cell(String label, int cents, {Color? color, Key? key}) => Expanded(
          child: Column(key: key, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(label, style: theme.textTheme.bodySmall),
            Text(formatCents(cents),
                style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold, color: color)),
          ]),
        );
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(tr('Dieser Monat'), style: theme.textTheme.labelLarge),
      const SizedBox(height: 4),
      Row(children: [
        cell(tr('Einnahmen'), c.incomeCents, color: IncomeExpenseChart.incomeColor),
        cell(tr('Ausgaben'), -c.expenseCents, color: theme.colorScheme.error),
        cell(tr('Saldo'), c.netCents, key: const Key('dash_net')),
      ]),
      const SizedBox(height: 12),
      if (hasData)
        IncomeExpenseChart(
          height: 150,
          months: [for (final m in c.months) MonthBar(monthShort(m.monthNumber), m.incomeCents, m.expenseCents)],
        )
      else
        Text(tr('Noch keine Einnahmen und Ausgaben. Trag sie ein oder erzeuge Beispielmonate im Finanzüberblick.'),
            style: theme.textTheme.bodySmall),
    ]);
  }
}

class _TopExpenses extends StatelessWidget {
  const _TopExpenses({required this.items});

  final List<DashCategory> items;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (items.isEmpty) return Text(tr('Noch keine Ausgaben.'));
    return Column(children: [
      for (final c in items)
        Padding(
          key: Key('dash_expense_${c.category}'),
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(child: Text(c.label)),
              Text(formatCents(c.cents), style: const TextStyle(fontWeight: FontWeight.w600)),
              SizedBox(
                width: 56,
                child: Text('${_pct(c.percent)} %',
                    textAlign: TextAlign.right, style: theme.textTheme.bodySmall),
              ),
            ]),
            const SizedBox(height: 4),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: (c.percent / 100).clamp(0.0, 1.0),
                minHeight: 8,
                backgroundColor: theme.colorScheme.surfaceContainerHighest,
              ),
            ),
          ]),
        ),
      const SizedBox(height: 4),
      Text(tr('Anteil an allen Ausgaben des Monats.'), style: theme.textTheme.bodySmall),
    ]);
  }
}

class _DebtsView extends StatelessWidget {
  const _DebtsView({required this.debts});

  final DashDebts debts;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (debts.loans.isEmpty) return Text(tr('Keine Kredite.'), key: const Key('dash_no_debts'));
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      _AmountRow(tr('Restschuld gesamt'), debts.totalCents, bold: true),
      _AmountRow(tr('Monatliche Raten'), debts.monthlyRatesCents),
      if (debts.debtRatioPercent != null)
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 3),
          child: Row(key: const Key('dash_debt_ratio'), children: [
            Expanded(child: Text(tr('Kreditraten im Verhältnis zu den Einnahmen'))),
            Text('${_pct(debts.debtRatioPercent!)} %',
                style: const TextStyle(fontWeight: FontWeight.w600)),
          ]),
        ),
      const SizedBox(height: 6),
      for (final l in debts.loans)
        ListTile(
          key: Key('dash_loan_${l.id}'),
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.request_quote_outlined),
          title: Text(l.name),
          subtitle: Text(l.status == 'CONTRACTED'
              ? tr('Vertrag geschlossen, noch nicht ausgezahlt')
              : l.nextDueDate == null
                  ? tr('{0} pro Monat', [formatCents(l.monthlyRateCents)])
                  : tr('{0} pro Monat · nächste Rate {1}', [formatCents(l.monthlyRateCents), formatDate(l.nextDueDate!)])),
          trailing: Text(formatCents(l.remainingCents), style: const TextStyle(fontWeight: FontWeight.w600)),
          onTap: () => context.push('/kredit/${l.id}'),
        ),
      Text(tr('Kredite der Projektbank sind eine Simulation.'), style: theme.textTheme.bodySmall),
    ]);
  }
}

class _GoalsView extends StatelessWidget {
  const _GoalsView({required this.goals});

  final List<DashGoal> goals;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (goals.isEmpty) {
      return Text(tr('Noch keine Sparziele. Lege ein digitales Konto mit Ziel oder einen Sparplan an.'),
          key: const Key('dash_no_goals'));
    }
    return Column(children: [
      for (final g in goals)
        InkWell(
          key: Key('dash_goal_${g.kind}_${g.id}'),
          onTap: () => context.push(g.kind == 'PLAN' ? '/plan/${g.id}' : '/unterkonto/${g.id}'),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Icon(g.reached ? Icons.check_circle : (g.kind == 'PLAN' ? Icons.savings : Icons.flag_outlined),
                    size: 20, color: g.reached ? IncomeExpenseChart.incomeColor : null),
                const SizedBox(width: 8),
                Expanded(child: Text(g.name, style: const TextStyle(fontWeight: FontWeight.w600))),
              ]),
              // Beträge in eigener Zeile: bei schmalem Bildschirm oder großer Schrift bleibt der Name lesbar
              Text(g.targetCents == null
                  ? formatCents(g.savedCents)
                  : tr('{0} von {1}', [formatCents(g.savedCents), formatCents(g.targetCents!)])),
              const SizedBox(height: 6),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: ((g.progressPercent ?? 0) / 100).clamp(0.0, 1.0),
                  minHeight: 8,
                  backgroundColor: theme.colorScheme.surfaceContainerHighest,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                [
                  if (g.progressPercent != null)
                    tr('{0} % erreicht', [_pct(g.progressPercent!)]),
                  if (g.targetDate != null) tr('Ziel bis {0}', [formatDate(g.targetDate!)]),
                  if (g.kind == 'PLAN') tr('Sparplan') else tr('Digitales Konto'),
                ].join(' · '),
                style: theme.textTheme.bodySmall,
              ),
            ]),
          ),
        ),
    ]);
  }
}

class _AccountsView extends StatelessWidget {
  const _AccountsView({required this.accounts, required this.cards});

  final List<DashAccount> accounts;
  final List<DashCard> cards;

  IconData _icon(String kind) => switch (kind) {
        'PROJEKTBANK' => Icons.account_balance,
        'DIGITAL' => Icons.savings_outlined,
        'PLAN' => Icons.trending_up,
        'EXTERNAL' => Icons.account_balance_outlined,
        'LOAN' => Icons.request_quote_outlined,
        _ => Icons.account_balance_wallet_outlined,
      };

  String? _route(DashAccount a) => switch (a.kind) {
        'PROJEKTBANK' => '/umsaetze/${a.id}',
        'DIGITAL' => '/unterkonto/${a.id}',
        'PLAN' => '/plan/${a.id}',
        'EXTERNAL' => '/banken',
        'LOAN' => '/kredit/${a.id}',
        _ => null,
      };

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(children: [
      for (final a in accounts)
        ListTile(
          key: Key('dash_account_${a.kind}_${a.id}'),
          contentPadding: EdgeInsets.zero,
          leading: Icon(_icon(a.kind)),
          title: Text(a.name),
          subtitle: Text([
            if (a.institution != null) a.institution!,
            if (a.note != null) a.note!,
          ].join(' · ')),
          trailing: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.end,
              children: [
            Text(formatCents(a.balanceCents),
                style: TextStyle(
                    fontWeight: FontWeight.w600, color: a.balanceCents < 0 ? theme.colorScheme.error : null)),
            if (a.stale)
              Row(mainAxisSize: MainAxisSize.min, children: [
                Icon(Icons.update_disabled, size: 14, color: theme.colorScheme.error),
                const SizedBox(width: 2),
                Text(tr('veraltet'), style: TextStyle(fontSize: 11, color: theme.colorScheme.error)),
              ]),
          ]),
          onTap: _route(a) == null ? null : () => context.push(_route(a)!),
        ),
      for (final c in cards)
        ListTile(
          key: Key('dash_card_${c.id}'),
          contentPadding: EdgeInsets.zero,
          leading: Icon(c.status == 'ACTIVE' ? Icons.credit_card : Icons.credit_card_off),
          title: Text(c.label),
          subtitle: Text([
            if (c.maskedNumber != null) c.maskedNumber!,
            c.status == 'ACTIVE' ? tr('aktiv') : tr('gesperrt'),
            tr('heute {0} von {1}', [formatCents(c.spentTodayCents), formatCents(c.dailyLimitCents)]),
          ].join(' · ')),
          onTap: () => context.go('/karte'),
        ),
    ]);
  }
}

class _Notes extends StatelessWidget {
  const _Notes({required this.notes});

  final List<String> notes;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      key: const Key('dash_notes'),
      color: theme.colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          for (final n in notes)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Icon(Icons.info_outline, size: 16),
                const SizedBox(width: 6),
                Expanded(child: Text(n, style: theme.textTheme.bodySmall)),
              ]),
            ),
        ]),
      ),
    );
  }
}
