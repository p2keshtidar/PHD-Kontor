/// Zentrales Finanz-Dashboard (GET /api/dashboard). Alle Werte kommen vom Server und stammen aus den
/// vorhandenen Daten – die App rechnet nichts hinzu.
library;

int _i(Object? v) => (v as num).toInt();
int? _iOrNull(Object? v) => v == null ? null : (v as num).toInt();
double? _dOrNull(Object? v) => v == null ? null : (v as num).toDouble();
DateTime? _date(Object? v) => v == null ? null : DateTime.parse(v as String);

class DashNetWorth {
  const DashNetWorth({
    required this.accountsCents,
    required this.digitalCents,
    required this.plansCents,
    required this.depotCents,
    required this.externalCents,
    required this.loansCents,
    required this.totalCents,
  });

  final int accountsCents;
  final int digitalCents;
  final int plansCents;
  final int depotCents;
  final int externalCents;

  /// negativ (Restschulden)
  final int loansCents;
  final int totalCents;

  factory DashNetWorth.fromJson(Map<String, dynamic> j) => DashNetWorth(
        accountsCents: _i(j['accountsCents']),
        digitalCents: _i(j['digitalCents']),
        plansCents: _i(j['plansCents']),
        depotCents: _i(j['depotCents']),
        externalCents: _i(j['externalCents']),
        loansCents: _i(j['loansCents']),
        totalCents: _i(j['totalCents']),
      );
}

class DashLiquidity {
  const DashLiquidity(this.availableCents, this.reservedCents, this.earmarkedCents, this.externalCents);

  /// Girokonto der Projektbank (frei verfügbar)
  final int availableCents;

  /// in digitalen Konten, Sparplänen und Depot zurückgelegt
  final int reservedCents;

  /// bei verbundenen Banken für Ziele vorgemerkt (bleibt dort)
  final int earmarkedCents;
  final int externalCents;

  factory DashLiquidity.fromJson(Map<String, dynamic> j) => DashLiquidity(
      _i(j['availableCents']), _i(j['reservedCents']), _i(j['earmarkedCents']), _i(j['externalCents']));
}

class DashAccount {
  const DashAccount({
    required this.kind,
    required this.id,
    required this.name,
    required this.institution,
    required this.balanceCents,
    required this.simulation,
    required this.stale,
    this.note,
  });

  /// PROJEKTBANK, DIGITAL, PLAN, EXTERNAL, LOAN
  final String kind;
  final int? id;
  final String name;
  final String? institution;
  final int balanceCents;
  final bool simulation;
  final bool stale;
  final String? note;

  factory DashAccount.fromJson(Map<String, dynamic> j) => DashAccount(
        kind: j['kind'] as String,
        id: _iOrNull(j['id']),
        name: j['name'] as String,
        institution: j['institution'] as String?,
        balanceCents: _i(j['balanceCents']),
        simulation: j['simulation'] as bool? ?? true,
        stale: j['stale'] as bool? ?? false,
        note: j['note'] as String?,
      );
}

class DashCard {
  const DashCard(this.id, this.label, this.maskedNumber, this.status, this.dailyLimitCents, this.spentTodayCents,
      this.simulation);

  final int? id;
  final String label;
  final String? maskedNumber;

  /// ACTIVE, BLOCKED …
  final String status;
  final int dailyLimitCents;
  final int spentTodayCents;
  final bool simulation;

  factory DashCard.fromJson(Map<String, dynamic> j) => DashCard(
        _iOrNull(j['id']),
        j['label'] as String,
        j['maskedNumber'] as String?,
        j['status'] as String? ?? '',
        _i(j['dailyLimitCents'] ?? 0),
        _i(j['spentTodayCents'] ?? 0),
        j['simulation'] as bool? ?? true,
      );
}

class DashLoan {
  const DashLoan(this.id, this.name, this.status, this.remainingCents, this.monthlyRateCents, this.nextDueDate);

  final int id;
  final String name;

  /// ACTIVE oder CONTRACTED (Vertrag geschlossen, noch nicht ausgezahlt)
  final String status;
  final int remainingCents;
  final int monthlyRateCents;
  final DateTime? nextDueDate;

  factory DashLoan.fromJson(Map<String, dynamic> j) => DashLoan(_i(j['id']), j['name'] as String,
      j['status'] as String, _i(j['remainingCents']), _i(j['monthlyRateCents']), _date(j['nextDueDate']));
}

class DashDebts {
  const DashDebts(this.totalCents, this.monthlyRatesCents, this.debtRatioPercent, this.loans);

  final int totalCents;
  final int monthlyRatesCents;

  /// Kreditraten im Verhältnis zu den durchschnittlichen Einnahmen; null ohne Einnahmen
  final double? debtRatioPercent;
  final List<DashLoan> loans;

  factory DashDebts.fromJson(Map<String, dynamic> j) => DashDebts(
        _i(j['totalCents']),
        _i(j['monthlyRatesCents']),
        _dOrNull(j['debtRatioPercent']),
        [for (final l in j['loans'] as List<dynamic>) DashLoan.fromJson(l as Map<String, dynamic>)],
      );
}

class DashCashflowMonth {
  const DashCashflowMonth(this.month, this.complete, this.incomeCents, this.expenseCents, this.netCents);

  /// "2026-10"
  final String month;
  final bool complete;
  final int incomeCents;
  final int expenseCents;
  final int netCents;

  int get monthNumber => int.parse(month.substring(5, 7));

  factory DashCashflowMonth.fromJson(Map<String, dynamic> j) => DashCashflowMonth(j['month'] as String,
      j['complete'] as bool? ?? true, _i(j['incomeCents']), _i(j['expenseCents']), _i(j['netCents']));
}

class DashCashflow {
  const DashCashflow(this.incomeCents, this.expenseCents, this.netCents, this.months);

  /// laufender Monat
  final int incomeCents;
  final int expenseCents;
  final int netCents;
  final List<DashCashflowMonth> months;

  factory DashCashflow.fromJson(Map<String, dynamic> j) => DashCashflow(
        _i(j['incomeCents']),
        _i(j['expenseCents']),
        _i(j['netCents']),
        [for (final m in j['months'] as List<dynamic>) DashCashflowMonth.fromJson(m as Map<String, dynamic>)],
      );
}

class DashGoal {
  const DashGoal(this.kind, this.id, this.name, this.savedCents, this.targetCents, this.progressPercent,
      this.targetDate, this.reached);

  /// DIGITAL oder PLAN
  final String kind;
  final int id;
  final String name;
  final int savedCents;
  final int? targetCents;
  final double? progressPercent;
  final DateTime? targetDate;
  final bool reached;

  factory DashGoal.fromJson(Map<String, dynamic> j) => DashGoal(
        j['kind'] as String,
        _i(j['id']),
        j['name'] as String,
        _i(j['savedCents']),
        _iOrNull(j['targetCents']),
        _dOrNull(j['progressPercent']),
        _date(j['targetDate']),
        j['reached'] as bool? ?? false,
      );
}

class DashCategory {
  const DashCategory(this.category, this.label, this.cents, this.percent);

  final String category;
  final String label;
  final int cents;

  /// Anteil an allen Ausgaben des Monats
  final double percent;

  factory DashCategory.fromJson(Map<String, dynamic> j) =>
      DashCategory(j['category'] as String, j['label'] as String, _i(j['cents']), (j['percent'] as num).toDouble());
}

class DashHistoryPoint {
  const DashHistoryPoint(this.month, this.totalCents, this.projektbankCents, this.depotCents, this.externalCents,
      this.externalIncluded);

  /// "2026-10"
  final String month;
  final int totalCents;
  final int projektbankCents;
  final int depotCents;

  /// null: verbundene Banken in diesem Monat nicht enthalten (Stand unbekannt) oder keine verbunden
  final int? externalCents;
  final bool externalIncluded;

  int get monthNumber => int.parse(month.substring(5, 7));
  int get year => int.parse(month.substring(0, 4));

  factory DashHistoryPoint.fromJson(Map<String, dynamic> j) => DashHistoryPoint(
        j['month'] as String,
        _i(j['totalCents']),
        _i(j['projektbankCents']),
        _i(j['depotCents']),
        _iOrNull(j['externalCents']),
        j['externalIncluded'] as bool? ?? true,
      );
}

class Dashboard {
  const Dashboard({
    required this.date,
    required this.netWorth,
    required this.liquidity,
    required this.accounts,
    required this.cards,
    required this.debts,
    required this.cashflow,
    required this.goals,
    required this.topExpenses,
    required this.topExpensesMonth,
    required this.history,
    required this.notes,
  });

  final DateTime date;
  final DashNetWorth netWorth;
  final DashLiquidity liquidity;
  final List<DashAccount> accounts;
  final List<DashCard> cards;
  final DashDebts debts;
  final DashCashflow cashflow;
  final List<DashGoal> goals;
  final List<DashCategory> topExpenses;

  /// "2026-10" oder null, wenn es noch keine Ausgaben gibt
  final String? topExpensesMonth;
  final List<DashHistoryPoint> history;
  final List<String> notes;

  factory Dashboard.fromJson(Map<String, dynamic> j) => Dashboard(
        date: DateTime.parse(j['date'] as String),
        netWorth: DashNetWorth.fromJson(j['netWorth'] as Map<String, dynamic>),
        liquidity: DashLiquidity.fromJson(j['liquidity'] as Map<String, dynamic>),
        accounts: [for (final a in j['accounts'] as List<dynamic>) DashAccount.fromJson(a as Map<String, dynamic>)],
        cards: [for (final c in j['cards'] as List<dynamic>) DashCard.fromJson(c as Map<String, dynamic>)],
        debts: DashDebts.fromJson(j['debts'] as Map<String, dynamic>),
        cashflow: DashCashflow.fromJson(j['cashflow'] as Map<String, dynamic>),
        goals: [for (final g in j['goals'] as List<dynamic>) DashGoal.fromJson(g as Map<String, dynamic>)],
        topExpenses: [
          for (final t in j['topExpenses'] as List<dynamic>) DashCategory.fromJson(t as Map<String, dynamic>)
        ],
        topExpensesMonth: j['topExpensesMonth'] as String?,
        history: [
          for (final h in j['history'] as List<dynamic>) DashHistoryPoint.fromJson(h as Map<String, dynamic>)
        ],
        notes: [for (final n in j['notes'] as List<dynamic>) n as String],
      );
}
