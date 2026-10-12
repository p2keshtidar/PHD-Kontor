/// Ein Monat im Finanzüberblick (/F570/).
class MonthSummary {
  const MonthSummary({
    required this.month,
    required this.complete,
    required this.hasData,
    required this.incomeCents,
    required this.expenseCents,
    required this.fixedCents,
    required this.freeCents,
    required this.categories,
  });

  /// "2026-09"
  final String month;
  final bool complete;
  final bool hasData;
  final int incomeCents;
  final int expenseCents;
  final int fixedCents;
  final int freeCents;
  final List<CategoryAmount> categories;

  static const _names = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];
  static const _longNames = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'
  ];

  int get _monthIndex => int.parse(month.substring(5, 7)) - 1;
  String get shortLabel => _names[_monthIndex];
  String get longLabel => '${_longNames[_monthIndex]} ${month.substring(0, 4)}';

  List<CategoryAmount> get expenses => categories.where((c) => c.kind != 'INCOME').toList();

  factory MonthSummary.fromJson(Map<String, dynamic> j) => MonthSummary(
        month: j['month'] as String,
        complete: j['complete'] as bool,
        hasData: j['hasData'] as bool,
        incomeCents: (j['incomeCents'] as num).toInt(),
        expenseCents: (j['expenseCents'] as num).toInt(),
        fixedCents: (j['fixedCents'] as num).toInt(),
        freeCents: (j['freeCents'] as num).toInt(),
        categories: (j['categories'] as List<dynamic>)
            .map((e) => CategoryAmount.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class CategoryAmount {
  const CategoryAmount(this.category, this.label, this.kind, this.cents);

  final String category;
  final String label;

  /// INCOME, FIXED, VARIABLE
  final String kind;
  final int cents;

  factory CategoryAmount.fromJson(Map<String, dynamic> j) => CategoryAmount(
        j['category'] as String,
        j['label'] as String,
        j['kind'] as String,
        (j['cents'] as num).toInt(),
      );
}

/// Durchschnitte und "verfügbar für Pläne" (/F580/).
class FinanceOverview {
  const FinanceOverview({
    required this.hasData,
    required this.monthsUsed,
    required this.avgIncomeCents,
    required this.avgExpenseCents,
    required this.avgFixedCents,
    required this.avgFreeCents,
    required this.planRatesCents,
    required this.availableCents,
    required this.maxLoanRateCents,
    this.demoProfile,
  });

  final bool hasData;
  final int monthsUsed;
  final int avgIncomeCents;
  final int avgExpenseCents;
  final int avgFixedCents;
  final int avgFreeCents;
  final int planRatesCents;
  final int availableCents;
  final int maxLoanRateCents;
  final String? demoProfile;

  factory FinanceOverview.fromJson(Map<String, dynamic> j) => FinanceOverview(
        hasData: j['hasData'] as bool,
        monthsUsed: (j['monthsUsed'] as num).toInt(),
        avgIncomeCents: (j['avgIncomeCents'] as num).toInt(),
        avgExpenseCents: (j['avgExpenseCents'] as num).toInt(),
        avgFixedCents: (j['avgFixedCents'] as num).toInt(),
        avgFreeCents: (j['avgFreeCents'] as num).toInt(),
        planRatesCents: (j['planRatesCents'] as num).toInt(),
        availableCents: (j['availableCents'] as num).toInt(),
        maxLoanRateCents: (j['maxLoanRateCents'] as num).toInt(),
        demoProfile: j['demoProfile'] as String?,
      );
}

class NetWorth {
  const NetWorth(this.accountsCents, this.depotCents, this.loansCents, this.totalCents);

  final int accountsCents;
  final int depotCents;
  final int loansCents;
  final int totalCents;

  factory NetWorth.fromJson(Map<String, dynamic> j) => NetWorth(
        (j['accountsCents'] as num).toInt(),
        (j['depotCents'] as num).toInt(),
        (j['loansCents'] as num).toInt(),
        (j['totalCents'] as num).toInt(),
      );
}

/// Demo-Profile (/F590/), Schlüssel wie im Backend.
const demoProfiles = <String, String>{
  'STUDENT': 'Studierende (ca. 1.170 € Einnahmen)',
  'EINSTEIGER': 'Berufseinsteiger (2.650 € Gehalt)',
  'FAMILIE': 'Familie (3.900 € Gehalt + Kindergeld)',
};

/// Kategorien, die der Nutzer wählen kann (/F560/).
const selectableCategories = <String, String>{
  'GEHALT': 'Gehalt',
  'SONSTIGE_EINNAHMEN': 'Sonstige Einnahmen',
  'WOHNEN': 'Wohnen',
  'VERTRAEGE': 'Verträge & Abos',
  'KREDIT': 'Kredit',
  'LEBENSMITTEL': 'Lebensmittel',
  'MOBILITAET': 'Mobilität',
  'FREIZEIT': 'Freizeit',
  'SHOPPING': 'Shopping',
  'SONSTIGES': 'Sonstiges',
};
