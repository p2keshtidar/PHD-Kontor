import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/charts.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import '../insights/insights_models.dart';
import 'planning_models.dart';
import 'planning_repository.dart';

/// Ausgabenkategorien, für die es Budgets gibt (alle wählbaren außer Einnahmen).
Map<String, String> get budgetCategories =>
    {for (final e in selectableCategories.entries) if (e.key != 'GEHALT' && e.key != 'SONSTIGE_EINNAHMEN') e.key: e.value};

/// Budgets & Prognose: Kontostand des Girokontos für die nächsten 90 Tage, Budgets je Kategorie,
/// ungewöhnliche Ausgaben. Alle Zahlen kommen vom Server und beruhen auf den eigenen Daten.
class PlanningScreen extends ConsumerWidget {
  const PlanningScreen({super.key});

  Future<void> _editBudget(BuildContext context, WidgetRef ref, {String? category, int? current}) async {
    final result = await showDialog<_BudgetResult>(
      context: context,
      builder: (_) => _BudgetDialog(category: category, current: current),
    );
    if (result == null) return;
    final repo = ref.read(planningRepositoryProvider);
    try {
      if (result.delete) {
        await repo.deleteBudget(result.category);
      } else {
        await repo.setBudget(result.category, result.cents!);
      }
      ref.invalidate(budgetsProvider);
    } catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final forecast = ref.watch(forecastProvider);
    final budgets = ref.watch(budgetsProvider);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Budgets & Prognose'))),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(forecastProvider);
          ref.invalidate(budgetsProvider);
          await ref.read(budgetsProvider.future);
        },
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: safeListPadding(context),
          children: [
            // ---------------------------------------------------------------- Prognose
            Semantics(header: true, child: Text(tr('Prognose Girokonto'), style: theme.textTheme.titleMedium)),
            const SizedBox(height: 8),
            forecast.when(
              loading: () => const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())),
              error: (e, _) => _ErrorBox(message: apiErrorMessage(e), onRetry: () => ref.invalidate(forecastProvider)),
              data: (f) => _ForecastView(f: f),
            ),
            const SizedBox(height: 24),
            // ---------------------------------------------------------------- Budgets
            Row(children: [
              Expanded(
                child: Semantics(header: true, child: Text(tr('Budgets diesen Monat'), style: theme.textTheme.titleMedium)),
              ),
              TextButton.icon(
                key: const Key('budget_add'),
                onPressed: () => _editBudget(context, ref),
                icon: const Icon(Icons.add),
                label: Text(tr('Budget')),
              ),
            ]),
            budgets.when(
              loading: () => const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())),
              error: (e, _) => _ErrorBox(message: apiErrorMessage(e), onRetry: () => ref.invalidate(budgetsProvider)),
              data: (b) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                if (b.items.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Text(tr('Noch keine Budgets. Lege fest, wie viel du pro Monat z. B. für Lebensmittel ausgeben möchtest.'),
                        key: const Key('budget_empty')),
                  ),
                for (final i in b.items)
                  _BudgetTile(
                    item: i,
                    onTap: () => _editBudget(context, ref, category: i.category, current: i.limitCents),
                  ),
                if (b.items.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      tr('Zusammen {0} von {1} · noch {2} Tage im Monat',
                          [formatCents(b.totalSpentCents), formatCents(b.totalLimitCents), b.daysLeft]),
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
                if (b.unbudgeted.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text(tr('Ausgaben ohne Budget'), style: theme.textTheme.titleSmall),
                  const SizedBox(height: 4),
                  Wrap(spacing: 6, runSpacing: 6, children: [
                    for (final u in b.unbudgeted)
                      ActionChip(
                        key: Key('budget_suggest_${u.category}'),
                        avatar: const Icon(Icons.add, size: 16),
                        label: Text('${u.label} · ${formatCents(u.spentCents)}'),
                        onPressed: () => _editBudget(context, ref, category: u.category),
                      ),
                  ]),
                ],
                const SizedBox(height: 24),
                Semantics(header: true, child: Text(tr('Ungewöhnliche Ausgaben'), style: theme.textTheme.titleMedium)),
                const SizedBox(height: 4),
                if (b.unusual.isEmpty)
                  Text(tr('Nichts Auffälliges in diesem Monat.'), key: const Key('unusual_empty'))
                else
                  for (final u in b.unusual)
                    Card(
                      key: Key('unusual_${u.kind}_${u.category}'),
                      child: ListTile(
                        leading: Icon(Icons.priority_high, color: theme.colorScheme.error),
                        title: Text(u.kind == 'SINGLE' && u.text != null ? u.text! : u.label),
                        subtitle: Text([
                          if (u.date != null) formatDate(u.date!),
                          u.reason,
                        ].join(' · ')),
                        trailing: Text(formatCents(u.amountCents), style: const TextStyle(fontWeight: FontWeight.bold)),
                      ),
                    ),
                const SizedBox(height: 12),
                for (final n in b.notes) Text(n, style: theme.textTheme.bodySmall),
              ]),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorBox extends StatelessWidget {
  const _ErrorBox({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(16),
        child: Column(children: [
          Text(message, textAlign: TextAlign.center),
          TextButton(onPressed: onRetry, child: Text(tr('Erneut versuchen'))),
        ]),
      );
}

class _ForecastView extends StatelessWidget {
  const _ForecastView({required this.f});

  final Forecast f;

  String _monthLabel(String ym) => '${monthName(int.parse(ym.substring(5, 7)))} ${ym.substring(0, 4)}';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      for (final w in f.warnings)
        Card(
          key: const Key('forecast_warning'),
          color: theme.colorScheme.errorContainer,
          child: ListTile(
            leading: Icon(Icons.warning_amber, color: theme.colorScheme.onErrorContainer),
            title: Text(w, style: TextStyle(color: theme.colorScheme.onErrorContainer)),
          ),
        ),
      Text(tr('Heute {0} · nächste {1} Tage', [formatCents(f.startBalanceCents), f.days]),
          style: theme.textTheme.bodyMedium),
      const SizedBox(height: 8),
      ForecastChart(values: [
        for (final p in f.points) ForecastValue(p.date, p.expectedCents, p.bestCents, p.worstCents),
      ]),
      const SizedBox(height: 12),
      for (final m in f.monthEnds)
        Padding(
          key: Key('forecast_month_${m.month}'),
          padding: const EdgeInsets.symmetric(vertical: 2),
          child: Row(children: [
            Expanded(child: Text(tr('Ende {0}', [_monthLabel(m.month)]))),
            Text(formatCents(m.expectedCents), style: const TextStyle(fontWeight: FontWeight.bold)),
          ]),
        ),
      if (f.lowest != null)
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text(tr('Tiefster Stand: {0} am {1}', [formatCents(f.lowest!.expectedCents), formatDate(f.lowest!.date)]),
              key: const Key('forecast_lowest'), style: theme.textTheme.bodySmall),
        ),
      if (f.events.isNotEmpty) ...[
        const SizedBox(height: 12),
        Text(tr('Bekannte Buchungen'), style: theme.textTheme.titleSmall),
        for (final e in f.events.take(8))
          ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            leading: Icon(_icon(e.source), size: 20),
            title: Text(e.label),
            subtitle: Text(formatDate(e.date)),
            trailing: Text(formatCents(e.amountCents),
                style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: e.amountCents > 0 ? IncomeExpenseChart.incomeColor : theme.colorScheme.error)),
          ),
        if (f.events.length > 8)
          Text(tr('und {0} weitere', [f.events.length - 8]), style: theme.textTheme.bodySmall),
      ],
      ExpansionTile(
        key: const Key('forecast_assumptions'),
        tilePadding: EdgeInsets.zero,
        title: Text(tr('So wird gerechnet'), style: theme.textTheme.titleSmall),
        children: [
          for (final a in f.assumptions)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('• '),
                Expanded(child: Text(a, style: theme.textTheme.bodySmall)),
              ]),
            ),
        ],
      ),
    ]);
  }

  static IconData _icon(String source) => switch (source) {
        'ENTRY' => Icons.edit_note,
        'STANDING_ORDER' => Icons.event_repeat,
        'PLAN' => Icons.savings,
        'LOAN' => Icons.request_quote_outlined,
        'ALLOCATION' => Icons.swap_horiz,
        _ => Icons.circle_outlined,
      };
}

class _BudgetTile extends StatelessWidget {
  const _BudgetTile({required this.item, required this.onTap});

  final BudgetItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = switch (item.status) {
      'OVER' => theme.colorScheme.error,
      'WARN' => const Color(0xFFE0A100),
      _ => IncomeExpenseChart.incomeColor,
    };
    final statusText = switch (item.status) {
      'OVER' => tr('überschritten'),
      'WARN' => tr('fast aufgebraucht'),
      _ => tr('im Rahmen'),
    };
    return InkWell(
      key: Key('budget_${item.category}'),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(child: Text(item.label, style: const TextStyle(fontWeight: FontWeight.w600))),
            Text(statusText, style: TextStyle(color: color, fontWeight: FontWeight.w600)),
          ]),
          Text(tr('{0} von {1}', [formatCents(item.spentCents), formatCents(item.limitCents)])),
          const SizedBox(height: 4),
          Semantics(
            label: tr('{0} % vom Budget', [item.percent.round()]),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: (item.percent / 100).clamp(0.0, 1.0),
                minHeight: 8,
                color: color,
                backgroundColor: theme.colorScheme.surfaceContainerHighest,
              ),
            ),
          ),
          const SizedBox(height: 2),
          Text(
            item.remainingCents >= 0
                ? tr('Noch {0} übrig', [formatCents(item.remainingCents)])
                : tr('{0} über dem Budget', [formatCents(-item.remainingCents)]),
            style: theme.textTheme.bodySmall,
          ),
          if (item.projectionText != null) Text(item.projectionText!, style: theme.textTheme.bodySmall),
        ]),
      ),
    );
  }
}

class _BudgetResult {
  const _BudgetResult(this.category, this.cents, {this.delete = false});

  final String category;
  final int? cents;
  final bool delete;
}

/// Budget festlegen oder ändern; bei bestehendem Budget auch löschen.
class _BudgetDialog extends StatefulWidget {
  const _BudgetDialog({this.category, this.current});

  final String? category;
  final int? current;

  @override
  State<_BudgetDialog> createState() => _BudgetDialogState();
}

class _BudgetDialogState extends State<_BudgetDialog> {
  late final TextEditingController _amount = TextEditingController(
      text: widget.current == null ? '' : formatCents(widget.current!).replaceAll(' €', '').replaceAll('€', ''));
  late String? _category = widget.category;
  String? _error;

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  void _save() {
    final cents = parseEuroToCents(_amount.text);
    if (_category == null) {
      setState(() => _error = tr('Bitte eine Kategorie wählen'));
    } else if (cents == null || cents < 100) {
      setState(() => _error = tr('Bitte mindestens 1,00 € eingeben'));
    } else {
      Navigator.of(context).pop(_BudgetResult(_category!, cents));
    }
  }

  @override
  Widget build(BuildContext context) {
    final categories = budgetCategories;
    return AlertDialog(
      title: Text(widget.current == null ? tr('Budget festlegen') : tr('Budget ändern')),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          DropdownButtonFormField<String>(
            key: const Key('budget_category'),
            isExpanded: true,
            value: _category,
            decoration: InputDecoration(labelText: tr('Kategorie')),
            items: [for (final e in categories.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
            onChanged: widget.current != null ? null : (v) => setState(() => _category = v),
          ),
          const SizedBox(height: 8),
          TextField(
            key: const Key('budget_amount'),
            controller: _amount,
            autofocus: true,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(labelText: tr('Betrag pro Monat'), suffixText: '€', errorText: _error),
            onSubmitted: (_) => _save(),
          ),
        ]),
      ),
      actions: [
        if (widget.current != null)
          TextButton(
            key: const Key('budget_delete'),
            onPressed: () => Navigator.of(context).pop(_BudgetResult(_category!, null, delete: true)),
            child: Text(tr('Löschen')),
          ),
        TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(tr('Abbrechen'))),
        FilledButton(key: const Key('budget_save'), onPressed: _save, child: Text(tr('Speichern'))),
      ],
    );
  }
}
