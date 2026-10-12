import 'package:flutter/material.dart';

import '../../core/dates.dart';
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
    Widget cell(String t, {bool bold = false, TextAlign align = TextAlign.right}) => Expanded(
          child: Text(t, textAlign: align, style: bold ? small?.copyWith(fontWeight: FontWeight.bold) : small),
        );
    return Column(children: [
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(children: [
          cell('Nr.', bold: true, align: TextAlign.left),
          cell('Datum', bold: true),
          cell('Rate', bold: true),
          cell('Zins', bold: true),
          cell('Tilgung', bold: true),
          cell('Rest', bold: true),
        ]),
      ),
      const Divider(height: 1),
      for (final r in rows)
        Container(
          color: r.paid ? theme.colorScheme.primaryContainer.withAlpha(90) : null,
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: Row(children: [
            cell('${r.month}${r.paid ? ' ✓' : ''}', align: TextAlign.left),
            cell(formatDate(r.dueDate)),
            cell(formatCents(r.rateCents).replaceAll(' €', '')),
            cell(formatCents(r.interestCents).replaceAll(' €', '')),
            cell(formatCents(r.principalCents).replaceAll(' €', '')),
            cell(formatCents(r.remainingCents).replaceAll(' €', '')),
          ]),
        ),
    ]);
  }
}
