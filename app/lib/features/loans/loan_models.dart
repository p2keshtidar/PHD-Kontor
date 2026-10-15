import '../../l10n/l10n.dart';

DateTime? _date(dynamic v) => v == null ? null : DateTime.parse(v as String);
List<String> _strings(dynamic v) => ((v as List<dynamic>?) ?? const []).map((e) => e as String).toList();

/// Bonität für die Beispielrechnung (Selbsteinschätzung, keine Auskunftei).
Map<String, String> get creditClasses => {
      'SEHR_GUT': tr('sehr gut'),
      'GUT': tr('gut'),
      'MITTEL': tr('mittel'),
      'SCHWACH': tr('schwach'),
    };

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
    this.providerId = 'SIMULATION',
    this.simulation = true,
    this.effectiveRate,
    this.feesCents = 0,
    this.minCreditClass,
    this.eligible = true,
    this.requirements = const [],
    this.conditions = const [],
    this.exampleText,
    this.marketDeltaPercent,
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
  final String providerId;

  /// fiktive Bank (Simulation) – unverbindliches Rechenbeispiel
  final bool simulation;

  /// effektiver Jahreszins (mit allen Kosten)
  final double? effectiveRate;
  final int feesCents;
  final String? minCreditClass;
  final bool eligible;
  final List<String> requirements;
  final List<String> conditions;
  final String? exampleText;

  /// Abstand zum Bundesbank-Durchschnitt (Prozentpunkte), falls vorhanden
  final double? marketDeltaPercent;

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
        providerId: j['providerId'] as String? ?? 'SIMULATION',
        simulation: j['simulation'] as bool? ?? true,
        effectiveRate: (j['effectiveRate'] as num?)?.toDouble(),
        feesCents: (j['feesCents'] as num?)?.toInt() ?? 0,
        minCreditClass: j['minCreditClass'] as String?,
        eligible: j['eligible'] as bool? ?? true,
        requirements: _strings(j['requirements']),
        conditions: _strings(j['conditions']),
        exampleText: j['exampleText'] as String?,
        marketDeltaPercent: (j['marketDeltaPercent'] as num?)?.toDouble(),
      );
}

/// Durchschnittszins der Deutschen Bundesbank (Referenz, kein Angebot).
class ReferenceRate {
  const ReferenceRate(this.label, this.ratePercent, this.period, {this.stale = false, this.note = '', this.source = ''});

  final String label;
  final double ratePercent;

  /// Berichtsmonat "2026-08"
  final String period;
  final bool stale;
  final String note;
  final String source;

  factory ReferenceRate.fromJson(Map<String, dynamic> j) => ReferenceRate(
        j['label'] as String? ?? '',
        (j['ratePercent'] as num).toDouble(),
        j['period'] as String? ?? '',
        stale: j['stale'] as bool? ?? false,
        note: j['note'] as String? ?? '',
        source: j['source'] as String? ?? '',
      );
}

/// Anbieter im Kreditvergleich und sein Zustand (OK, STALE, UNAVAILABLE, DISABLED, NOT_CONNECTED).
class LoanProviderStatus {
  const LoanProviderStatus(this.id, this.name, this.state, this.stateLabel, {this.message, this.simulation = false});

  final String id;
  final String name;
  final String state;
  final String stateLabel;
  final String? message;
  final bool simulation;

  bool get ok => state == 'OK';

  factory LoanProviderStatus.fromJson(Map<String, dynamic> j) => LoanProviderStatus(
        j['id'] as String,
        j['name'] as String? ?? j['id'] as String,
        j['state'] as String? ?? 'UNAVAILABLE',
        j['stateLabel'] as String? ?? j['state'] as String? ?? '',
        message: j['message'] as String?,
        simulation: j['simulation'] as bool? ?? false,
      );
}

/// Kreditanfrage (Schritt 2) mit Zusage oder Ablehnung.
class LoanApplication {
  const LoanApplication({
    required this.id,
    required this.bank,
    required this.bankName,
    required this.purposeLabel,
    required this.principalCents,
    required this.termMonths,
    required this.status,
    required this.statusLabel,
    this.creditClass = 'GUT',
    this.interestRate,
    this.effectiveRate,
    this.monthlyRateCents,
    this.totalCents,
    this.decisionReason,
    this.validUntil,
    this.loanId,
    this.nextStepText,
    this.simulation = true,
  });

  final int id;
  final String bank;
  final String bankName;
  final String purposeLabel;
  final int principalCents;
  final int termMonths;

  /// APPROVED, DECLINED, ACCEPTED, WITHDRAWN, EXPIRED
  final String status;
  final String statusLabel;
  final String creditClass;
  final double? interestRate;
  final double? effectiveRate;
  final int? monthlyRateCents;
  final int? totalCents;
  final String? decisionReason;
  final DateTime? validUntil;
  final int? loanId;
  final String? nextStepText;
  final bool simulation;

  bool get approved => status == 'APPROVED';

  factory LoanApplication.fromJson(Map<String, dynamic> j) => LoanApplication(
        id: (j['id'] as num).toInt(),
        bank: j['bank'] as String,
        bankName: j['bankName'] as String,
        purposeLabel: j['purposeLabel'] as String? ?? '',
        principalCents: (j['principalCents'] as num).toInt(),
        termMonths: (j['termMonths'] as num).toInt(),
        status: j['status'] as String,
        statusLabel: j['statusLabel'] as String? ?? j['status'] as String,
        creditClass: j['creditClass'] as String? ?? 'GUT',
        interestRate: (j['interestRate'] as num?)?.toDouble(),
        effectiveRate: (j['effectiveRate'] as num?)?.toDouble(),
        monthlyRateCents: (j['monthlyRateCents'] as num?)?.toInt(),
        totalCents: (j['totalCents'] as num?)?.toInt(),
        decisionReason: j['decisionReason'] as String?,
        validUntil: _date(j['validUntil']),
        loanId: (j['loanId'] as num?)?.toInt(),
        nextStepText: j['nextStepText'] as String?,
        simulation: j['simulation'] as bool? ?? true,
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
    this.creditClass = 'GUT',
    this.hiddenCount = 0,
    this.referenceRates = const [],
    this.providers = const [],
    this.disclaimerText,
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
  final String creditClass;

  /// durch Filter ausgeblendete Angebote
  final int hiddenCount;
  final List<ReferenceRate> referenceRates;
  final List<LoanProviderStatus> providers;
  final String? disclaimerText;

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
        creditClass: j['creditClass'] as String? ?? 'GUT',
        hiddenCount: (j['hiddenCount'] as num?)?.toInt() ?? 0,
        referenceRates: ((j['referenceRates'] as List<dynamic>?) ?? const [])
            .map((e) => ReferenceRate.fromJson(e as Map<String, dynamic>))
            .toList(),
        providers: ((j['providers'] as List<dynamic>?) ?? const [])
            .map((e) => LoanProviderStatus.fromJson(e as Map<String, dynamic>))
            .toList(),
        disclaimerText: j['disclaimerText'] as String?,
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
      this.remainingCents, this.paid, [this.extraCents = 0]);

  final int month;
  final DateTime dueDate;
  final int rateCents;
  final int interestCents;
  final int principalCents;
  final int remainingCents;
  final bool paid;

  /// /K020/: Sondertilgung nach dieser Rate
  final int extraCents;

  factory ScheduleRow.fromJson(Map<String, dynamic> j) => ScheduleRow(
        (j['month'] as num).toInt(),
        DateTime.parse(j['dueDate'] as String),
        (j['rateCents'] as num).toInt(),
        (j['interestCents'] as num).toInt(),
        (j['principalCents'] as num).toInt(),
        (j['remainingCents'] as num).toInt(),
        j['paid'] as bool,
        (j['extraCents'] as num?)?.toInt() ?? 0,
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
    this.remainingInstallments,
    this.expectedEndDate,
    this.extraPaidCents = 0,
    this.statusLabel,
    this.effectiveRate,
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

  /// ACTIVE (ausgezahlt), CONTRACTED (Vertrag, Auszahlung ausstehend), PAID_OFF, CANCELLED
  final String status;
  final String? statusLabel;
  final double? effectiveRate;

  /// Noch offene Raten (sinkt durch Sondertilgungen)
  final int? remainingInstallments;
  final DateTime? expectedEndDate;
  final int extraPaidCents;

  /// Anzahl Raten insgesamt (bezahlt + offen)
  int get totalInstallments =>
      isActive && remainingInstallments != null ? paidInstallments + remainingInstallments! : paidInstallments;

  bool get isActive => status == 'ACTIVE';
  bool get isContracted => status == 'CONTRACTED';
  bool get isCancelled => status == 'CANCELLED';

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
        remainingInstallments: (j['remainingInstallments'] as num?)?.toInt(),
        expectedEndDate: _date(j['expectedEndDate']),
        extraPaidCents: (j['extraPaidCents'] as num?)?.toInt() ?? 0,
        statusLabel: j['statusLabel'] as String?,
        effectiveRate: (j['effectiveRate'] as num?)?.toDouble(),
      );
}

class LoanDetail {
  const LoanDetail(this.loan, this.totalInterestCents, this.schedule,
      {this.interestSavedCents = 0, this.extraPayments = const []});

  final Loan loan;
  final int totalInterestCents;
  final List<ScheduleRow> schedule;

  /// /K020/: gesparte Zinsen durch Sondertilgungen
  final int interestSavedCents;
  final List<ExtraPayment> extraPayments;

  factory LoanDetail.fromJson(Map<String, dynamic> j) => LoanDetail(
        Loan.fromJson(j['loan'] as Map<String, dynamic>),
        (j['totalInterestCents'] as num).toInt(),
        (j['schedule'] as List<dynamic>).map((e) => ScheduleRow.fromJson(e as Map<String, dynamic>)).toList(),
        interestSavedCents: (j['interestSavedCents'] as num?)?.toInt() ?? 0,
        extraPayments: ((j['extraPayments'] as List<dynamic>?) ?? const [])
            .map((e) => ExtraPayment.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

/// /K020/: eine gebuchte Sondertilgung
class ExtraPayment {
  const ExtraPayment(this.amountCents, this.paidOn);

  final int amountCents;
  final DateTime paidOn;

  factory ExtraPayment.fromJson(Map<String, dynamic> j) =>
      ExtraPayment((j['amountCents'] as num).toInt(), DateTime.parse(j['paidOn'] as String));
}

/// /K020/: Vorschau einer Sondertilgung
class ExtraPreview {
  const ExtraPreview({
    required this.amountCents,
    required this.remainingBeforeCents,
    required this.remainingAfterCents,
    required this.installmentsBefore,
    required this.installmentsAfter,
    required this.endBefore,
    required this.endAfter,
    required this.interestSavedCents,
    required this.availableCents,
  });

  final int amountCents;
  final int remainingBeforeCents;
  final int remainingAfterCents;
  final int installmentsBefore;
  final int installmentsAfter;
  final DateTime endBefore;
  final DateTime endAfter;
  final int interestSavedCents;
  final int availableCents;

  int get installmentsSaved => installmentsBefore - installmentsAfter;
  bool get paysOff => remainingAfterCents == 0;

  factory ExtraPreview.fromJson(Map<String, dynamic> j) => ExtraPreview(
        amountCents: (j['amountCents'] as num).toInt(),
        remainingBeforeCents: (j['remainingBeforeCents'] as num).toInt(),
        remainingAfterCents: (j['remainingAfterCents'] as num).toInt(),
        installmentsBefore: (j['installmentsBefore'] as num).toInt(),
        installmentsAfter: (j['installmentsAfter'] as num).toInt(),
        endBefore: DateTime.parse(j['endBefore'] as String),
        endAfter: DateTime.parse(j['endAfter'] as String),
        interestSavedCents: (j['interestSavedCents'] as num).toInt(),
        availableCents: (j['availableCents'] as num).toInt(),
      );
}

/// "5.59" -> "5,59 %"
String formatPercent(double value) =>
    L10n.isEnglish ? '${value.toStringAsFixed(2)}%' : '${value.toStringAsFixed(2).localDecimal} %';
