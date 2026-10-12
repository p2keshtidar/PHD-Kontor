import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/charts.dart';
import '../../core/money.dart';
import 'demo_dialog.dart';
import 'insights_models.dart';
import 'insights_repository.dart';

/// Finanzüberblick (/F560/ bis /F590/): Einnahmen und Ausgaben je Monat, Kategorien, Durchschnitt.
class InsightsScreen extends ConsumerStatefulWidget {
  const InsightsScreen({super.key});

  @override
  ConsumerState<InsightsScreen> createState() => _InsightsScreenState();
}

class _InsightsScreenState extends ConsumerState<InsightsScreen> {
  int? _selected;

  void _refreshAll() {
    ref.invalidate(monthsProvider);
    ref.invalidate(overviewProvider);
  }

  @override
  Widget build(BuildContext context) {
    final months = ref.watch(monthsProvider);
    final overview = ref.watch(overviewProvider);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Finanzüberblick')),
      body: RefreshIndicator(
        onRefresh: () async {
          _refreshAll();
          await ref.read(monthsProvider.future);
        },
        child: months.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(children: [Padding(padding: const EdgeInsets.all(24), child: Text(apiErrorMessage(e)))]),
          data: (list) {
            final anyData = list.any((m) => m.hasData);
            final index = (_selected ?? list.length - 2).clamp(0, list.length - 1);
            final selected = list[index];
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (!anyData)
                  Card(
                    color: theme.colorScheme.secondaryContainer,
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        const Text('Noch keine Einnahmen und Ausgaben', style: TextStyle(fontWeight: FontWeight.bold)),
                        const SizedBox(height: 6),
                        const Text('Erzeuge Beispielmonate, um den Finanzüberblick und die Planung auszuprobieren.'),
                        const SizedBox(height: 12),
                        FilledButton.icon(
                          onPressed: () async {
                            if (await showDemoDialog(context)) _refreshAll();
                          },
                          icon: const Icon(Icons.auto_awesome),
                          label: const Text('Beispielmonate erzeugen'),
                        ),
                      ]),
                    ),
                  ),
                overview.maybeWhen(
                  data: (o) => o.hasData ? _AverageCard(o: o) : const SizedBox.shrink(),
                  orElse: () => const SizedBox.shrink(),
                ),
                const SizedBox(height: 12),
                Text('Letzte 6 Monate', style: theme.textTheme.titleMedium),
                const SizedBox(height: 12),
                IncomeExpenseChart(
                  months: [for (final m in list) MonthBar(m.shortLabel, m.incomeCents, m.expenseCents)],
                ),
                const SizedBox(height: 16),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(children: [
                    for (var i = 0; i < list.length; i++)
                      Padding(
                        padding: const EdgeInsets.only(right: 6),
                        child: ChoiceChip(
                          label: Text(list[i].shortLabel + (list[i].complete ? '' : ' (laufend)')),
                          selected: i == index,
                          onSelected: (_) => setState(() => _selected = i),
                        ),
                      ),
                  ]),
                ),
                const SizedBox(height: 12),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(selected.longLabel, style: theme.textTheme.titleMedium),
                      const SizedBox(height: 8),
                      _row('Einnahmen', selected.incomeCents, color: IncomeExpenseChart.incomeColor),
                      _row('Ausgaben', -selected.expenseCents, color: theme.colorScheme.error),
                      _row('davon Fixkosten', -selected.fixedCents),
                      const Divider(),
                      _row('Freier Betrag', selected.freeCents, bold: true),
                      const SizedBox(height: 16),
                      Text('Ausgaben nach Kategorie', style: theme.textTheme.titleSmall),
                      const SizedBox(height: 8),
                      CategoryBars(items: [for (final c in selected.expenses) (c.label, c.cents)]),
                    ]),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  'Umbuchungen, Sparraten, Startguthaben und Kreditauszahlungen zählen nicht als Einnahme oder Ausgabe. '
                  'Die Kategorie eines Umsatzes kannst du in der Umsatzdetailansicht ändern.',
                  style: theme.textTheme.bodySmall,
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _row(String label, int cents, {Color? color, bool bold = false}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(child: Text(label, style: TextStyle(fontWeight: bold ? FontWeight.bold : null))),
          Text(formatCents(cents),
              style: TextStyle(color: color, fontWeight: bold ? FontWeight.bold : FontWeight.w600)),
        ]),
      );
}

class _AverageCard extends StatelessWidget {
  const _AverageCard({required this.o});

  final FinanceOverview o;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    Widget cell(String label, int cents) => Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(label, style: theme.textTheme.bodySmall),
            Text(formatCents(cents), style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          ]),
        );
    return Card(
      color: theme.colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Durchschnitt der letzten ${o.monthsUsed} Monate', style: theme.textTheme.titleSmall),
          const SizedBox(height: 12),
          Row(children: [cell('Einnahmen', o.avgIncomeCents), cell('Ausgaben', o.avgExpenseCents)]),
          const SizedBox(height: 12),
          Row(children: [cell('Freier Betrag', o.avgFreeCents), cell('Verfügbar für Pläne', o.availableCents)]),
          if (o.planRatesCents > 0) ...[
            const SizedBox(height: 8),
            Text('Bereits verplant: ${formatCents(o.planRatesCents)} pro Monat in Spar- und Investplänen.',
                style: theme.textTheme.bodySmall),
          ],
        ]),
      ),
    );
  }
}
