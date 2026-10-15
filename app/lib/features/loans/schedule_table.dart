import 'package:flutter/material.dart';
import '../../l10n/l10n.dart';

import '../../core/money.dart';
import 'loan_models.dart';

/// Tilgungsplan als Liste (/F1040/). Bezahlte Raten sind markiert.
class ScheduleTable extends StatelessWidget {
  const ScheduleTable({super.key, required this.rows});

  final List<ScheduleRow> rows;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final small = theme.textTheme.bodySmall;
    // Spaltenbreiten: Nr. schmal, Restschuld breit – passt auch auf kleine Handys ohne Umbruch
    Widget cell(String t, {bool bold = false, TextAlign align = TextAlign.right, int flex = 3}) => Expanded(
          flex: flex,
          child: Text(t, maxLines: 1, softWrap: false, overflow: TextOverflow.fade, textAlign: align, style: bold ? small?.copyWith(fontWeight: FontWeight.bold) : small),
        );
    return Column(children: [
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(children: [
          cell(tr('Nr.'), bold: true, align: TextAlign.left, flex: 2),
          cell(tr('Monat'), bold: true),
          cell(tr('Rate'), bold: true),
          cell(tr('Zins'), bold: true),
          cell(tr('Tilgung'), bold: true),
          cell(tr('Rest'), bold: true, flex: 4),
        ]),
      ),
      const Divider(height: 1),
      for (final r in rows) ...[
        Container(
          color: r.paid ? theme.colorScheme.primaryContainer.withAlpha(90) : null,
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: Row(children: [
            cell('${r.month}${r.paid ? ' ✓' : ''}', align: TextAlign.left, flex: 2),
            cell('${r.dueDate.month.toString().padLeft(2, '0')}/${r.dueDate.year % 100}'),
            cell(formatCents(r.rateCents).replaceAll(' €', '')),
            cell(formatCents(r.interestCents).replaceAll(' €', '')),
            cell(formatCents(r.principalCents).replaceAll(' €', '')),
            cell(formatCents(r.remainingCents).replaceAll(' €', ''), flex: 4),
          ]),
        ),
        if (r.extraCents > 0)
          Container(
            color: theme.colorScheme.tertiaryContainer.withAlpha(120),
            padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 4),
            child: Row(children: [
              Icon(Icons.bolt, size: 14, color: theme.colorScheme.tertiary),
              const SizedBox(width: 4),
              Expanded(child: Text(tr('Sondertilgung {0}', [formatCents(r.extraCents)]), style: small)),
            ]),
          ),
      ],
    ]);
  }
}
