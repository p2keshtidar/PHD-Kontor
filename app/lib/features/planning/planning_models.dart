/// Budgets, ungewöhnliche Ausgaben und Prognose des Girokontos (vom Server berechnet).
library;

int _i(Object? v) => (v as num).toInt();
int? _iOrNull(Object? v) => v == null ? null : (v as num).toInt();
DateTime? _date(Object? v) => v == null ? null : DateTime.parse(v as String);
List<String> _strings(Object? v) => [for (final x in (v as List<dynamic>? ?? const [])) x as String];

class BudgetItem {
  const BudgetItem(this.category, this.label, this.limitCents, this.spentCents, this.remainingCents, this.percent,
      this.status, this.projectedCents, this.projectionText);

  final String category;
  final String label;
  final int limitCents;
  final int spentCents;
  final int remainingCents;
  final double percent;

  /// OK, WARN (ab 80 %), OVER (überschritten)
  final String status;
  final int? projectedCents;
  final String? projectionText;

  factory BudgetItem.fromJson(Map<String, dynamic> j) => BudgetItem(
        j['category'] as String,
        j['label'] as String,
        _i(j['limitCents']),
        _i(j['spentCents']),
        _i(j['remainingCents']),
        (j['percent'] as num).toDouble(),
        j['status'] as String,
        _iOrNull(j['projectedCents']),
        j['projectionText'] as String?,
      );
}

class Unbudgeted {
  const Unbudgeted(this.category, this.label, this.spentCents);

  final String category;
  final String label;
  final int spentCents;

  factory Unbudgeted.fromJson(Map<String, dynamic> j) =>
      Unbudgeted(j['category'] as String, j['label'] as String, _i(j['spentCents']));
}

class UnusualExpense {
  const UnusualExpense(this.kind, this.category, this.label, this.date, this.text, this.amountCents, this.usualCents,
      this.reason);

  /// SINGLE (einzelne Ausgabe) oder CATEGORY (ganzer Monat)
  final String kind;
  final String category;
  final String label;
  final DateTime? date;
  final String? text;
  final int amountCents;
  final int usualCents;
  final String reason;

  factory UnusualExpense.fromJson(Map<String, dynamic> j) => UnusualExpense(
        j['kind'] as String,
        j['category'] as String,
        j['label'] as String,
        _date(j['date']),
        j['text'] as String?,
        _i(j['amountCents']),
        _i(j['usualCents']),
        j['reason'] as String? ?? '',
      );
}

class BudgetOverview {
  const BudgetOverview(this.month, this.daysLeft, this.items, this.totalLimitCents, this.totalSpentCents,
      this.unbudgeted, this.unusual, this.notes);

  final String month;
  final int daysLeft;
  final List<BudgetItem> items;
  final int totalLimitCents;
  final int totalSpentCents;
  final List<Unbudgeted> unbudgeted;
  final List<UnusualExpense> unusual;
  final List<String> notes;

  factory BudgetOverview.fromJson(Map<String, dynamic> j) => BudgetOverview(
        j['month'] as String,
        _i(j['daysLeft']),
        [for (final x in j['items'] as List<dynamic>) BudgetItem.fromJson(x as Map<String, dynamic>)],
        _i(j['totalLimitCents']),
        _i(j['totalSpentCents']),
        [for (final x in j['unbudgeted'] as List<dynamic>) Unbudgeted.fromJson(x as Map<String, dynamic>)],
        [for (final x in j['unusual'] as List<dynamic>) UnusualExpense.fromJson(x as Map<String, dynamic>)],
        _strings(j['notes']),
      );
}

class ForecastPoint {
  const ForecastPoint(this.date, this.expectedCents, this.bestCents, this.worstCents);

  final DateTime date;
  final int expectedCents;
  final int bestCents;
  final int worstCents;

  factory ForecastPoint.fromJson(Map<String, dynamic> j) => ForecastPoint(DateTime.parse(j['date'] as String),
      _i(j['expectedCents']), _i(j['bestCents']), _i(j['worstCents']));
}

class ForecastEvent {
  const ForecastEvent(this.date, this.label, this.amountCents, this.source);

  final DateTime date;
  final String label;
  final int amountCents;

  /// ENTRY, STANDING_ORDER, PLAN, LOAN, ALLOCATION
  final String source;

  factory ForecastEvent.fromJson(Map<String, dynamic> j) => ForecastEvent(
      DateTime.parse(j['date'] as String), j['label'] as String, _i(j['amountCents']), j['source'] as String);
}

class MonthEnd {
  const MonthEnd(this.month, this.expectedCents, this.bestCents, this.worstCents);

  /// "2026-10"
  final String month;
  final int expectedCents;
  final int bestCents;
  final int worstCents;

  factory MonthEnd.fromJson(Map<String, dynamic> j) =>
      MonthEnd(j['month'] as String, _i(j['expectedCents']), _i(j['bestCents']), _i(j['worstCents']));
}

class Forecast {
  const Forecast(this.date, this.startBalanceCents, this.days, this.points, this.events, this.monthEnds, this.lowest,
      this.warnings, this.assumptions, this.historyMonths);

  final DateTime date;
  final int startBalanceCents;
  final int days;
  final List<ForecastPoint> points;
  final List<ForecastEvent> events;
  final List<MonthEnd> monthEnds;
  final ForecastPoint? lowest;
  final List<String> warnings;
  final List<String> assumptions;
  final int historyMonths;

  factory Forecast.fromJson(Map<String, dynamic> j) => Forecast(
        DateTime.parse(j['date'] as String),
        _i(j['startBalanceCents']),
        _i(j['days']),
        [for (final x in j['points'] as List<dynamic>) ForecastPoint.fromJson(x as Map<String, dynamic>)],
        [for (final x in j['events'] as List<dynamic>) ForecastEvent.fromJson(x as Map<String, dynamic>)],
        [for (final x in j['monthEnds'] as List<dynamic>) MonthEnd.fromJson(x as Map<String, dynamic>)],
        j['lowest'] == null ? null : ForecastPoint.fromJson(j['lowest'] as Map<String, dynamic>),
        _strings(j['warnings']),
        _strings(j['assumptions']),
        _i(j['historyMonths']),
      );
}
