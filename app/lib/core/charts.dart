import 'dart:math' as math;
import '../l10n/l10n.dart';

import 'package:flutter/material.dart';

import 'money.dart';

/// Schlichte Diagramme ohne zusätzliches Paket (CustomPainter).

/// Ein Monat mit Einnahmen und Ausgaben in Cent.
class MonthBar {
  const MonthBar(this.label, this.income, this.expense);

  final String label;
  final int income;
  final int expense;
}

/// Gruppierte Balken: Einnahmen (grün) und Ausgaben (rot) je Monat (/F570/).
class IncomeExpenseChart extends StatelessWidget {
  const IncomeExpenseChart({super.key, required this.months, this.height = 180});

  final List<MonthBar> months;
  final double height;

  static const incomeColor = Color(0xFF2E7D32);

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          height: height,
          width: double.infinity,
          child: CustomPaint(
            painter: _BarsPainter(months, incomeColor, scheme.error, scheme.onSurfaceVariant),
          ),
        ),
        const SizedBox(height: 8),
        // Wrap statt Row: bei großer Schrift in die nächste Zeile umbrechen statt überlaufen
        Wrap(spacing: 16, runSpacing: 4, children: [
          _Legend(color: incomeColor, text: tr('Einnahmen')),
          _Legend(color: scheme.error, text: tr('Ausgaben')),
        ]),
      ],
    );
  }
}

class _Legend extends StatelessWidget {
  const _Legend({required this.color, required this.text});

  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) => Row(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 12, height: 12, decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(3))),
        const SizedBox(width: 6),
        Text(text, style: Theme.of(context).textTheme.bodySmall),
      ]);
}

class _BarsPainter extends CustomPainter {
  _BarsPainter(this.months, this.incomeColor, this.expenseColor, this.textColor);

  final List<MonthBar> months;
  final Color incomeColor;
  final Color expenseColor;
  final Color textColor;

  @override
  void paint(Canvas canvas, Size size) {
    if (months.isEmpty) return;
    const labelHeight = 18.0;
    final chartHeight = size.height - labelHeight;
    final maxValue = months.fold<int>(1, (m, e) => math.max(m, math.max(e.income, e.expense)));
    final groupWidth = size.width / months.length;
    final barWidth = math.min(18.0, groupWidth / 3);

    final axis = Paint()
      ..color = textColor.withAlpha(60)
      ..strokeWidth = 1;
    canvas.drawLine(Offset(0, chartHeight), Offset(size.width, chartHeight), axis);

    for (var i = 0; i < months.length; i++) {
      final m = months[i];
      final center = groupWidth * i + groupWidth / 2;
      void bar(double left, int value, Color color) {
        final h = chartHeight * value.clamp(0, maxValue) / maxValue;
        final rect = RRect.fromRectAndCorners(
          Rect.fromLTWH(left, chartHeight - h, barWidth, h),
          topLeft: const Radius.circular(3),
          topRight: const Radius.circular(3),
        );
        canvas.drawRRect(rect, Paint()..color = color);
      }

      bar(center - barWidth - 1, m.income, incomeColor);
      bar(center + 1, m.expense, expenseColor);

      final tp = TextPainter(
        text: TextSpan(text: m.label, style: TextStyle(color: textColor, fontSize: 11)),
        textDirection: TextDirection.ltr,
      )..layout(maxWidth: groupWidth);
      tp.paint(canvas, Offset(center - tp.width / 2, chartHeight + 3));
    }
  }

  @override
  bool shouldRepaint(covariant _BarsPainter old) => old.months != months;
}

/// Waagerechte Balken für Kategorien (Anteil an der Summe).
class CategoryBars extends StatelessWidget {
  const CategoryBars({super.key, required this.items});

  /// (Bezeichnung, Betrag in Cent)
  final List<(String, int)> items;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final total = items.fold<int>(0, (s, e) => s + e.$2);
    if (total <= 0) return Text(tr('Keine Ausgaben in diesem Monat.'));
    return Column(children: [
      for (final (label, cents) in items)
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 5),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(child: Text(label)),
              Text(formatCents(cents), style: const TextStyle(fontWeight: FontWeight.w600)),
              SizedBox(
                width: 48,
                child: Text('${(cents * 100 / total).round()} %',
                    textAlign: TextAlign.right, style: Theme.of(context).textTheme.bodySmall),
              ),
            ]),
            const SizedBox(height: 4),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: cents / total,
                minHeight: 8,
                backgroundColor: scheme.surfaceContainerHighest,
              ),
            ),
          ]),
        ),
    ]);
  }
}

/// Ein Verlaufspunkt: Wert und eingezahlte Summe in Cent.
class ValuePoint {
  const ValuePoint(this.date, this.value, this.deposited);

  final DateTime date;
  final int value;
  final int deposited;
}

/// Liniendiagramm: Wert des Plans und eingezahlte Summe (/F830/).
class ValueLineChart extends StatelessWidget {
  const ValueLineChart({super.key, required this.points, this.height = 170});

  final List<ValuePoint> points;
  final double height;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    if (points.length < 2) {
      return SizedBox(
        height: 60,
        child: Center(
          child: Text(tr('Der Verlauf erscheint nach der ersten Rate.'), style: Theme.of(context).textTheme.bodySmall),
        ),
      );
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SizedBox(
        height: height,
        width: double.infinity,
        child: CustomPaint(painter: _LinePainter(points, scheme.primary, scheme.outline, scheme.onSurfaceVariant)),
      ),
      const SizedBox(height: 8),
      Wrap(spacing: 16, runSpacing: 4, children: [
        _Legend(color: scheme.primary, text: tr('Wert')),
        _Legend(color: scheme.outline, text: tr('Eingezahlt')),
      ]),
    ]);
  }
}

class _LinePainter extends CustomPainter {
  _LinePainter(this.points, this.valueColor, this.depositColor, this.textColor);

  final List<ValuePoint> points;
  final Color valueColor;
  final Color depositColor;
  final Color textColor;

  @override
  void paint(Canvas canvas, Size size) {
    const bottom = 18.0;
    final h = size.height - bottom;
    final maxV = points.fold<int>(1, (m, p) => math.max(m, math.max(p.value, p.deposited)));
    final n = points.length;
    Offset at(int i, int v) => Offset(size.width * i / (n - 1), h - h * v / maxV);

    Path path(int Function(ValuePoint) pick) {
      final p = Path()..moveTo(at(0, pick(points[0])).dx, at(0, pick(points[0])).dy);
      for (var i = 1; i < n; i++) {
        final o = at(i, pick(points[i]));
        p.lineTo(o.dx, o.dy);
      }
      return p;
    }

    canvas.drawLine(Offset(0, h), Offset(size.width, h), Paint()..color = textColor.withAlpha(60));
    canvas.drawPath(
        path((p) => p.deposited),
        Paint()
          ..color = depositColor
          ..strokeWidth = 2
          ..style = PaintingStyle.stroke);
    canvas.drawPath(
        path((p) => p.value),
        Paint()
          ..color = valueColor
          ..strokeWidth = 3
          ..style = PaintingStyle.stroke);

    String label(DateTime d) => '${d.month.toString().padLeft(2, '0')}/${d.year % 100}';
    for (final i in {0, n - 1}) {
      final tp = TextPainter(
        text: TextSpan(text: label(points[i].date), style: TextStyle(color: textColor, fontSize: 11)),
        textDirection: TextDirection.ltr,
      )..layout();
      final x = i == 0 ? 0.0 : size.width - tp.width;
      tp.paint(canvas, Offset(x, h + 3));
    }
  }

  @override
  bool shouldRepaint(covariant _LinePainter old) => old.points != points;
}

/// Ein Punkt im Vermögensverlauf (Monatsende).
class WealthPoint {
  const WealthPoint(this.label, this.cents, {this.complete = true});

  final String label;
  final int cents;

  /// false: in diesem Monat fehlen Teile (z. B. verbundene Banken ohne Umsätze) – Punkt wird hohl gezeichnet
  final bool complete;
}

/// Linie für den Vermögensverlauf; Werte dürfen negativ sein (Kredite), dann wird die Null-Linie gezeigt.
class WealthChart extends StatelessWidget {
  const WealthChart({super.key, required this.points, this.height = 170});

  final List<WealthPoint> points;
  final double height;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    if (points.length < 2) return const SizedBox.shrink();
    return Semantics(
      container: true,
      label: tr('Vermögensverlauf: {0} im {1}, {2} im {3}',
          [formatCents(points.first.cents), points.first.label, formatCents(points.last.cents), points.last.label]),
      child: ExcludeSemantics(
        child: SizedBox(
          height: height,
          width: double.infinity,
          child: CustomPaint(painter: _WealthPainter(points, scheme.primary, scheme.error, scheme.onSurfaceVariant)),
        ),
      ),
    );
  }
}

class _WealthPainter extends CustomPainter {
  _WealthPainter(this.points, this.color, this.negativeColor, this.textColor);

  final List<WealthPoint> points;
  final Color color;
  final Color negativeColor;
  final Color textColor;

  @override
  void paint(Canvas canvas, Size size) {
    const bottom = 18.0;
    const top = 6.0;
    final h = size.height - bottom - top;
    final maxV = points.fold<int>(1, (m, p) => math.max(m, p.cents));
    final minV = points.fold<int>(0, (m, p) => math.min(m, p.cents));
    final range = (maxV - minV).toDouble();
    final n = points.length;
    double y(int v) => top + h - h * (v - minV) / range;
    Offset at(int i) => Offset(size.width * i / (n - 1), y(points[i].cents));

    final zero = y(0);
    canvas.drawLine(Offset(0, zero), Offset(size.width, zero), Paint()..color = textColor.withAlpha(70));

    final line = Path()..moveTo(at(0).dx, at(0).dy);
    for (var i = 1; i < n; i++) {
      line.lineTo(at(i).dx, at(i).dy);
    }
    final area = Path.from(line)
      ..lineTo(size.width, zero)
      ..lineTo(0, zero)
      ..close();
    canvas.drawPath(area, Paint()..color = color.withAlpha(35));
    canvas.drawPath(
        line,
        Paint()
          ..color = color
          ..strokeWidth = 3
          ..style = PaintingStyle.stroke);
    for (var i = 0; i < n; i++) {
      final p = points[i];
      final c = p.cents < 0 ? negativeColor : color;
      canvas.drawCircle(at(i), 3.5, Paint()..color = c);
      if (!p.complete) canvas.drawCircle(at(i), 2, Paint()..color = Colors.white);
    }

    for (final i in {0, n ~/ 2, n - 1}) {
      final tp = TextPainter(
        text: TextSpan(text: points[i].label, style: TextStyle(color: textColor, fontSize: 11)),
        textDirection: TextDirection.ltr,
      )..layout();
      final x = (size.width * i / (n - 1) - tp.width / 2).clamp(0.0, size.width - tp.width);
      tp.paint(canvas, Offset(x, size.height - bottom + 3));
    }
  }

  @override
  bool shouldRepaint(covariant _WealthPainter old) => old.points != points;
}

/// Ein Tag der Prognose: erwartet, günstig, ungünstig (Cent).
class ForecastValue {
  const ForecastValue(this.date, this.expected, this.best, this.worst);

  final DateTime date;
  final int expected;
  final int best;
  final int worst;
}

/// Prognose als Band: erwarteter Verlauf (dick), günstiger und ungünstiger Fall (dünn), Null-Linie.
class ForecastChart extends StatelessWidget {
  const ForecastChart({super.key, required this.values, this.height = 170});

  final List<ForecastValue> values;
  final double height;

  static const bestColor = Color(0xFF2E7D32);

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    if (values.length < 2) return const SizedBox.shrink();
    final last = values.last;
    return Semantics(
      container: true,
      label: tr('Prognose: am {0} erwartet {1}, günstig {2}, ungünstig {3}', [
        '${last.date.day.toString().padLeft(2, '0')}.${last.date.month.toString().padLeft(2, '0')}.',
        formatCents(last.expected),
        formatCents(last.best),
        formatCents(last.worst),
      ]),
      child: ExcludeSemantics(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(
            height: height,
            width: double.infinity,
            child: CustomPaint(
              painter: _ForecastPainter(values, scheme.primary, bestColor, scheme.error, scheme.onSurfaceVariant),
            ),
          ),
          const SizedBox(height: 8),
          Wrap(spacing: 16, runSpacing: 4, children: [
            _Legend(color: scheme.primary, text: tr('erwartet')),
            _Legend(color: bestColor, text: tr('günstig')),
            _Legend(color: scheme.error, text: tr('ungünstig')),
          ]),
        ]),
      ),
    );
  }
}

class _ForecastPainter extends CustomPainter {
  _ForecastPainter(this.values, this.expectedColor, this.bestColor, this.worstColor, this.textColor);

  final List<ForecastValue> values;
  final Color expectedColor;
  final Color bestColor;
  final Color worstColor;
  final Color textColor;

  @override
  void paint(Canvas canvas, Size size) {
    const bottom = 18.0;
    const top = 6.0;
    final h = size.height - bottom - top;
    var maxV = 1;
    var minV = 0;
    for (final v in values) {
      maxV = math.max(maxV, math.max(v.best, math.max(v.expected, v.worst)));
      minV = math.min(minV, math.min(v.best, math.min(v.expected, v.worst)));
    }
    final range = (maxV - minV).toDouble();
    final n = values.length;
    double y(int v) => top + h - h * (v - minV) / range;
    double x(int i) => size.width * i / (n - 1);

    canvas.drawLine(Offset(0, y(0)), Offset(size.width, y(0)), Paint()..color = textColor.withAlpha(90));

    void line(int Function(ForecastValue) pick, Color color, double width) {
      final p = Path()..moveTo(x(0), y(pick(values[0])));
      for (var i = 1; i < n; i++) {
        p.lineTo(x(i), y(pick(values[i])));
      }
      canvas.drawPath(
          p,
          Paint()
            ..color = color
            ..strokeWidth = width
            ..style = PaintingStyle.stroke);
    }

    line((v) => v.best, bestColor, 1.5);
    line((v) => v.worst, worstColor, 1.5);
    line((v) => v.expected, expectedColor, 3);

    String label(DateTime d) => '${d.day.toString().padLeft(2, '0')}.${d.month.toString().padLeft(2, '0')}.';
    for (final i in {0, n ~/ 2, n - 1}) {
      final tp = TextPainter(
        text: TextSpan(text: label(values[i].date), style: TextStyle(color: textColor, fontSize: 11)),
        textDirection: TextDirection.ltr,
      )..layout();
      final left = (x(i) - tp.width / 2).clamp(0.0, size.width - tp.width);
      tp.paint(canvas, Offset(left, size.height - bottom + 3));
    }
  }

  @override
  bool shouldRepaint(covariant _ForecastPainter old) => old.values != values;
}

/// Ampel für die Machbarkeit (/F620/).
class FeasibilityBadge extends StatelessWidget {
  const FeasibilityBadge({super.key, required this.level});

  /// GREEN, YELLOW, RED, UNKNOWN
  final String level;

  @override
  Widget build(BuildContext context) {
    final (color, text, icon) = switch (level) {
      'GREEN' => (const Color(0xFF2E7D32), tr('Gut machbar'), Icons.check_circle),
      'YELLOW' => (const Color(0xFFE0A100), tr('Knapp'), Icons.warning_amber),
      'RED' => (Theme.of(context).colorScheme.error, tr('Nicht machbar'), Icons.cancel),
      _ => (Colors.grey, tr('Unbekannt'), Icons.help_outline),
    };
    return Chip(
      avatar: Icon(icon, color: color, size: 18),
      label: Text(text, style: TextStyle(color: color, fontWeight: FontWeight.bold)),
      side: BorderSide(color: color),
    );
  }
}
