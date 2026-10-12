DateTime? _date(dynamic v) => v == null ? null : DateTime.parse(v as String);

/// Zwecke (/F1010/) mit Grenzen wie im Backend (Pflichtenheft Kap. 8.5).
class LoanPurposeInfo {
  const LoanPurposeInfo(this.key, this.label, this.minEuro, this.maxEuro, this.minTerm, this.maxTerm, this.defaultEuro,
      this.defaultTerm);

  final String key;
  final String label;
  final int minEuro;
  final int maxEuro;
  final int minTerm;
  final int maxTerm;
  final int defaultEuro;
  final int defaultTerm;
}

const loanPurposes = [
  LoanPurposeInfo('AUTO', 'Auto', 1000, 100000, 12, 96, 20000, 60),
  LoanPurposeInfo('IMMOBILIE', 'Wohnung/Haus', 50000, 1000000, 60, 360, 300000, 240),
  LoanPurposeInfo('SONSTIGES', 'Sonstiges', 1000, 50000, 12, 120, 5000, 36),
];

class LoanOffer {
  const LoanOffer({
    required this.bank,
    required this.bankName,
    required this.interestRate,
    required this.monthlyRateCents,
    required this.totalInterestCents,
    required this.totalCostCents,
    required this.affordable,
    required this.declined,
    required this.recommended,
    this.declineReason,
  });

  final String bank;
  final String bankName;
  final double interestRate;
  final int monthlyRateCents;
  final int totalInterestCents;
  final int totalCostCents;
  final bool affordable;
  final bool declined;
  final String? declineReason;
  final bool recommended;

  factory LoanOffer.fromJson(Map<String, dynamic> j) => LoanOffer(
        bank: j['bank'] as String,
        bankName: j['bankName'] as String,
        interestRate: (j['interestRate'] as num).toDouble(),
        monthlyRateCents: (j['monthlyRateCents'] as num).toInt(),
        totalInterestCents: (j['totalInterestCents'] as num).toInt(),
        totalCostCents: (j['totalCostCents'] as num).toInt(),
        affordable: j['affordable'] as bool,
        declined: j['declined'] as bool,
        declineReason: j['declineReason'] as String?,
        recommended: j['recommended'] as bool,
      );
}

/// Ergebnis des Angebotsvergleichs (/F1030/).
class OfferResult {
  const OfferResult({
    required this.purpose,
    required this.purposeLabel,
    required this.principalCents,
    required this.equityCents,
    required this.termMonths,
    required this.hasData,
    required this.availableCents,
    required this.maxRateCents,
    required this.maxPrincipalCents,
    required this.offers,
  });

  final String purpose;
  final String purposeLabel;
  final int principalCents;
  final int equityCents;
  final int termMonths;
  final bool hasData;
  final int availableCents;
  final int maxRateCents;
  final int maxPrincipalCents;
  final List<LoanOffer> offers;

  factory OfferResult.fromJson(Map<String, dynamic> j) => OfferResult(
        purpose: j['purpose'] as String,
        purposeLabel: j['purposeLabel'] as String,
        principalCents: (j['principalCents'] as num).toInt(),
        equityCents: (j['equityCents'] as num).toInt(),
        termMonths: (j['termMonths'] as num).toInt(),
        hasData: j['hasData'] as bool,
        availableCents: (j['availableCents'] as num).toInt(),
        maxRateCents: (j['maxRateCents'] as num).toInt(),
        maxPrincipalCents: (j['maxPrincipalCents'] as num).toInt(),
        offers: (j['offers'] as List<dynamic>).map((e) => LoanOffer.fromJson(e as Map<String, dynamic>)).toList(),
      );
}

/// Angebot + Wunsch, wird an die Angebotsseite übergeben.
class SelectedOffer {
  const SelectedOffer(this.result, this.offer);

  final OfferResult result;
  final LoanOffer offer;
}

class ScheduleRow {
  const ScheduleRow(this.month, this.dueDate, this.rateCents, this.interestCents, this.principalCents,
      this.remainingCents, this.paid);

  final int month;
  final DateTime dueDate;
  final int rateCents;
  final int interestCents;
  final int principalCents;
  final int remainingCents;
  final bool paid;

  factory ScheduleRow.fromJson(Map<String, dynamic> j) => ScheduleRow(
        (j['month'] as num).toInt(),
        DateTime.parse(j['dueDate'] as String),
        (j['rateCents'] as num).toInt(),
        (j['interestCents'] as num).toInt(),
        (j['principalCents'] as num).toInt(),
        (j['remainingCents'] as num).toInt(),
        j['paid'] as bool,
      );
}

class Loan {
  const Loan({
    required this.id,
    required this.bankName,
    required this.purposeLabel,
    required this.principalCents,
    required this.interestRate,
    required this.termMonths,
    required this.monthlyRateCents,
    required this.startDate,
    required this.paidInstallments,
    required this.remainingCents,
    required this.progressPercent,
    required this.status,
    this.nextDueDate,
  });

  final int id;
  final String bankName;
  final String purposeLabel;
  final int principalCents;
  final double interestRate;
  final int termMonths;
  final int monthlyRateCents;
  final DateTime startDate;
  final DateTime? nextDueDate;
  final int paidInstallments;
  final int remainingCents;
  final double progressPercent;

  /// ACTIVE oder PAID_OFF
  final String status;

  bool get isActive => status == 'ACTIVE';

  factory Loan.fromJson(Map<String, dynamic> j) => Loan(
        id: (j['id'] as num).toInt(),
        bankName: j['bankName'] as String,
        purposeLabel: j['purposeLabel'] as String,
        principalCents: (j['principalCents'] as num).toInt(),
        interestRate: (j['interestRate'] as num).toDouble(),
        termMonths: (j['termMonths'] as num).toInt(),
        monthlyRateCents: (j['monthlyRateCents'] as num).toInt(),
        startDate: DateTime.parse(j['startDate'] as String),
        nextDueDate: _date(j['nextDueDate']),
        paidInstallments: (j['paidInstallments'] as num).toInt(),
        remainingCents: (j['remainingCents'] as num).toInt(),
        progressPercent: (j['progressPercent'] as num).toDouble(),
        status: j['status'] as String,
      );
}

class LoanDetail {
  const LoanDetail(this.loan, this.totalInterestCents, this.schedule);

  final Loan loan;
  final int totalInterestCents;
  final List<ScheduleRow> schedule;

  factory LoanDetail.fromJson(Map<String, dynamic> j) => LoanDetail(
        Loan.fromJson(j['loan'] as Map<String, dynamic>),
        (j['totalInterestCents'] as num).toInt(),
        (j['schedule'] as List<dynamic>).map((e) => ScheduleRow.fromJson(e as Map<String, dynamic>)).toList(),
      );
}

/// "5.59" -> "5,59 %"
String formatPercent(double value) => '${value.toStringAsFixed(2).replaceAll('.', ',')} %';
