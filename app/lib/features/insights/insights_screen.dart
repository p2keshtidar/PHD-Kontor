import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/charts.dart';
import '../../core/money.dart';
import 'demo_dialog.dart';
import 'insights_models.dart';
import 'insights_repository.dart';
import 'csv_import_screen.dart';
import '../../core/layout.dart';
import '../../core/theme.dart';

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
      appBar: AppBar(title: Text(tr('Finanzüberblick')), actions: [
        IconButton(
          key: const Key('insights_assistant'),
          tooltip: tr('Finanzassistent'),
          icon: const Icon(Icons.support_agent),
          onPressed: () => context.push('/assistent'),
        ),
        IconButton(
          key: const Key('insights_planning'),
          tooltip: tr('Budgets & Prognose'),
          icon: const Icon(Icons.track_changes),
          onPressed: () => context.push('/planung'),
        ),
        IconButton(
          key: const Key('csv_import_open'),
          tooltip: tr('Umsätze anderer Bank importieren (CSV)'),
          icon: const Icon(Icons.upload_file),
          onPressed: () async {
            await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const CsvImportScreen()));
            _refreshAll();
          },
        ),
      ]),
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
              padding: safeListPadding(context),
              children: [
                if (!anyData)
                  Card(
                    color: theme.colorScheme.secondaryContainer,
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(tr('Noch keine Einnahmen und Ausgaben'), style: TextStyle(fontWeight: FontWeight.bold)),
                        const SizedBox(height: 6),
                        Text(tr('Trag dein Gehalt, deine Miete und andere Ausgaben ein, erzeuge Beispielmonate oder importiere die Umsätze deiner echten Bank als CSV-Datei.')),
                        const SizedBox(height: 12),
                        FilledButton.icon(
                          key: const Key('insights_entries'),
                          onPressed: () async {
                            await context.push('/eintraege');
                            _refreshAll();
                          },
                          icon: const Icon(Icons.edit_note),
                          label: Text(tr('Einnahmen & Ausgaben eintragen')),
                        ),
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          onPressed: () async {
                            if (await showDemoDialog(context)) _refreshAll();
                          },
                          icon: const Icon(Icons.auto_awesome),
                          label: Text(tr('Beispielmonate erzeugen')),
                        ),
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          onPressed: () async {
                            await Navigator.of(context)
                                .push(MaterialPageRoute<void>(builder: (_) => const CsvImportScreen()));
                            _refreshAll();
                          },
                          icon: const Icon(Icons.upload_file),
                          label: Text(tr('CSV-Datei importieren')),
                        ),
                      ]),
                    ),
                  ),
                overview.maybeWhen(
                  data: (o) => o.hasData ? _AverageCard(o: o) : const SizedBox.shrink(),
                  orElse: () => const SizedBox.shrink(),
                ),
                const SizedBox(height: 12),
                Text(tr('Letzte 6 Monate'), style: theme.textTheme.titleMedium),
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
                          label: Text(list[i].shortLabel + (list[i].complete ? '' : tr(' (laufend)'))),
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
                      _row(tr('Einnahmen'), selected.incomeCents, color: positiveColor(context)),
                      _row(tr('Ausgaben'), -selected.expenseCents, color: theme.colorScheme.error),
                      _row(tr('davon Fixkosten'), -selected.fixedCents),
                      const Divider(),
                      _row(tr('Freier Betrag'), selected.freeCents, bold: true),
                      const SizedBox(height: 16),
                      Text(tr('Ausgaben nach Kategorie'), style: theme.textTheme.titleSmall),
                      const SizedBox(height: 8),
                      CategoryBars(items: [for (final c in selected.expenses) (c.label, c.cents)]),
                    ]),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  tr('Umbuchungen, Sparraten, Startguthaben und Kreditauszahlungen zählen nicht als Einnahme oder Ausgabe. Die Kategorie eines Umsatzes kannst du in der Umsatzdetailansicht ändern.'),
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
          Text(tr('Durchschnitt der letzten {0} Monate', [o.monthsUsed]), style: theme.textTheme.titleSmall),
          const SizedBox(height: 12),
          Row(children: [cell(tr('Einnahmen'), o.avgIncomeCents), cell(tr('Ausgaben'), o.avgExpenseCents)]),
          const SizedBox(height: 12),
          Row(children: [cell(tr('Freier Betrag'), o.avgFreeCents), cell(tr('Verfügbar für Pläne'), o.availableCents)]),
          if (o.planRatesCents > 0) ...[
            const SizedBox(height: 8),
            Text(tr('Bereits verplant: {0} pro Monat in Spar- und Investplänen.', [formatCents(o.planRatesCents)]),
                style: theme.textTheme.bodySmall),
          ],
        ]),
      ),
    );
  }
}
