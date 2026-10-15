import '../../l10n/l10n.dart';

DateTime? _date(dynamic v) => v == null ? null : DateTime.parse(v as String);
int? _int(dynamic v) => (v as num?)?.toInt();

/// Rhythmen einer Sparregel (Schlüssel wie im Backend).
Map<String, String> get allocationIntervals => {
      'WEEKLY': tr('wöchentlich'),
      'BIWEEKLY': tr('alle 2 Wochen'),
      'MONTHLY': tr('monatlich'),
      'QUARTERLY': tr('vierteljährlich'),
    };

/// Mögliche Finanzierungsquelle: Girokonto der Projektbank (Umbuchung) oder verbundenes Bankkonto (nur Vormerkung).
class AllocationSource {
  const AllocationSource({
    required this.type,
    required this.id,
    required this.sourceName,
    required this.bankName,
    required this.usable,
    required this.movesMoney,
    required this.explanation,
    this.ibanMasked,
    this.balanceCents,
    this.earmarkedCents = 0,
    this.availableCents,
    this.unusableReason,
    this.sandbox = false,
    this.coverageWarning = false,
    this.coverageText,
  });

  /// PROJEKTBANK oder EXTERNAL
  final String type;
  final int id;
  final String sourceName;
  final String bankName;
  final String? ibanMasked;
  final int? balanceCents;
  final int earmarkedCents;
  final int? availableCents;
  final bool usable;
  final String? unusableReason;
  final bool movesMoney;
  final String explanation;
  final bool sandbox;
  final bool coverageWarning;
  final String? coverageText;

  bool get isProjektbank => type == 'PROJEKTBANK';

  /// "Girokonto · Projektbank (Simulation)"
  String get label => '${isProjektbank ? tr('Girokonto') : sourceName} · $bankName';

  factory AllocationSource.fromJson(Map<String, dynamic> j) => AllocationSource(
        type: j['type'] as String,
        id: (j['id'] as num).toInt(),
        sourceName: j['sourceName'] as String? ?? '',
        bankName: j['bankName'] as String? ?? '',
        ibanMasked: j['ibanMasked'] as String?,
        balanceCents: _int(j['balanceCents']),
        earmarkedCents: _int(j['earmarkedCents']) ?? 0,
        availableCents: _int(j['availableCents']),
        usable: j['usable'] as bool? ?? false,
        unusableReason: j['unusableReason'] as String?,
        movesMoney: j['movesMoney'] as bool? ?? false,
        explanation: j['sourceExplanation'] as String? ?? '',
        sandbox: j['sandbox'] as bool? ?? false,
        coverageWarning: j['coverageWarning'] as bool? ?? false,
        coverageText: j['coverageText'] as String?,
      );
}

/// Ein Vorgang: Umbuchung, Vormerkung oder aufgehobene Vormerkung, mit Status.
class AllocationRun {
  const AllocationRun({
    required this.id,
    required this.kind,
    required this.kindLabel,
    required this.dueDate,
    required this.amountCents,
    required this.status,
    required this.statusLabel,
    this.ruleId,
    this.sourceType = 'PROJEKTBANK',
    this.sourceName,
    this.attempts = 0,
    this.nextAttempt,
    this.failureReason,
    this.manual = false,
  });

  final int id;
  final int? ruleId;
  final String sourceType;
  final String? sourceName;

  /// TRANSFER, EARMARK, RELEASE
  final String kind;
  final String kindLabel;
  final DateTime dueDate;
  final int amountCents;

  /// PENDING, COMPLETED, RETRYING, FAILED, CANCELLED
  final String status;
  final String statusLabel;
  final int attempts;
  final DateTime? nextAttempt;
  final String? failureReason;
  final bool manual;

  bool get canRetry => status == 'RETRYING' || status == 'FAILED';
  bool get failed => status == 'FAILED';
  bool get completed => status == 'COMPLETED';

  factory AllocationRun.fromJson(Map<String, dynamic> j) => AllocationRun(
        id: (j['id'] as num).toInt(),
        ruleId: _int(j['ruleId']),
        sourceType: j['sourceType'] as String? ?? 'PROJEKTBANK',
        sourceName: j['sourceName'] as String?,
        kind: j['kind'] as String,
        kindLabel: j['kindLabel'] as String? ?? j['kind'] as String,
        dueDate: DateTime.parse(j['dueDate'] as String),
        amountCents: (j['amountCents'] as num).toInt(),
        status: j['status'] as String,
        statusLabel: j['statusLabel'] as String? ?? j['status'] as String,
        attempts: _int(j['attempts']) ?? 0,
        nextAttempt: _date(j['nextAttempt']),
        failureReason: j['failureReason'] as String?,
        manual: j['manual'] as bool? ?? false,
      );
}

/// Sparregel: regelmäßig einen Betrag in ein digitales Konto zuweisen.
class AllocationRule {
  const AllocationRule({
    required this.id,
    required this.sourceType,
    required this.amountCents,
    required this.interval,
    required this.startDate,
    required this.status,
    required this.statusLabel,
    required this.movesMoney,
    this.sourceId,
    this.sourceName,
    this.bankName,
    this.explanation = '',
    this.intervalLabel,
    this.endDate,
    this.nextDate,
    this.statusReason,
    this.lastRun,
  });

  final int id;
  final String sourceType;
  final int? sourceId;
  final String? sourceName;
  final String? bankName;
  final bool movesMoney;
  final String explanation;
  final int amountCents;
  final String interval;
  final String? intervalLabel;
  final DateTime startDate;
  final DateTime? endDate;
  final DateTime? nextDate;

  /// ACTIVE, PAUSED, ENDED
  final String status;
  final String statusLabel;
  final String? statusReason;
  final AllocationRun? lastRun;

  bool get active => status == 'ACTIVE';
  bool get paused => status == 'PAUSED';
  String get rhythm => intervalLabel ?? allocationIntervals[interval] ?? interval;

  /// "Girokonto" bzw. Name des verbundenen Kontos und Bank
  String get sourceLabel => sourceType == 'PROJEKTBANK'
      ? tr('Girokonto')
      : sourceName == null
          ? tr('Bankkonto nicht mehr verbunden')
          : '$sourceName · ${bankName ?? ''}';

  factory AllocationRule.fromJson(Map<String, dynamic> j) => AllocationRule(
        id: (j['id'] as num).toInt(),
        sourceType: j['sourceType'] as String,
        sourceId: _int(j['sourceId']),
        sourceName: j['sourceName'] as String?,
        bankName: j['bankName'] as String?,
        movesMoney: j['movesMoney'] as bool? ?? true,
        explanation: j['sourceExplanation'] as String? ?? '',
        amountCents: (j['amountCents'] as num).toInt(),
        interval: j['interval'] as String,
        intervalLabel: j['intervalLabel'] as String?,
        startDate: DateTime.parse(j['startDate'] as String),
        endDate: _date(j['endDate']),
        nextDate: _date(j['nextDate']),
        status: j['status'] as String,
        statusLabel: j['statusLabel'] as String? ?? j['status'] as String,
        statusReason: j['statusReason'] as String?,
        lastRun: j['lastRun'] == null ? null : AllocationRun.fromJson(j['lastRun'] as Map<String, dynamic>),
      );
}

/// Vormerkung aus einem verbundenen Bankkonto (Geld bleibt dort).
class EarmarkInfo {
  const EarmarkInfo(this.externalAccountId, this.sourceName, this.bankName, this.amountCents,
      {this.own = true, this.active = true});

  final int? externalAccountId;
  final String sourceName;
  final String? bankName;
  final int amountCents;
  final bool own;
  final bool active;

  factory EarmarkInfo.fromJson(Map<String, dynamic> j) => EarmarkInfo(
        _int(j['externalAccountId']),
        j['sourceName'] as String? ?? '',
        j['bankName'] as String?,
        (j['amountCents'] as num).toInt(),
        own: j['own'] as bool? ?? false,
        active: j['active'] as bool? ?? true,
      );
}

class UpcomingAllocation {
  const UpcomingAllocation(this.date, this.amountCents, this.movesMoney, this.sourceName);

  final DateTime date;
  final int amountCents;
  final bool movesMoney;
  final String? sourceName;

  factory UpcomingAllocation.fromJson(Map<String, dynamic> j) => UpcomingAllocation(
        DateTime.parse(j['date'] as String),
        (j['amountCents'] as num).toInt(),
        j['movesMoney'] as bool? ?? true,
        j['sourceName'] as String?,
      );
}

/// Alles zu Sparregeln eines digitalen Kontos.
class AccountAllocations {
  const AccountAllocations({
    required this.accountId,
    required this.canAllocate,
    required this.balanceCents,
    required this.earmarkedCents,
    this.canRelease = false,
    this.earmarks = const [],
    this.rules = const [],
    this.otherRules = 0,
    this.runs = const [],
    this.upcoming = const [],
    this.projectedGoalDate,
    this.projectionText,
    this.earmarkExplanation = '',
  });

  final int accountId;
  final bool canAllocate;
  final bool canRelease;
  final int balanceCents;
  final int earmarkedCents;
  final List<EarmarkInfo> earmarks;
  final List<AllocationRule> rules;
  final int otherRules;
  final List<AllocationRun> runs;
  final List<UpcomingAllocation> upcoming;
  final DateTime? projectedGoalDate;
  final String? projectionText;
  final String earmarkExplanation;

  factory AccountAllocations.fromJson(Map<String, dynamic> j) => AccountAllocations(
        accountId: (j['accountId'] as num).toInt(),
        canAllocate: j['canAllocate'] as bool? ?? false,
        canRelease: j['canRelease'] as bool? ?? false,
        balanceCents: _int(j['balanceCents']) ?? 0,
        earmarkedCents: _int(j['earmarkedCents']) ?? 0,
        earmarks: ((j['earmarks'] as List<dynamic>?) ?? const [])
            .map((e) => EarmarkInfo.fromJson(e as Map<String, dynamic>))
            .toList(),
        rules: ((j['rules'] as List<dynamic>?) ?? const [])
            .map((e) => AllocationRule.fromJson(e as Map<String, dynamic>))
            .toList(),
        otherRules: _int(j['otherRules']) ?? 0,
        runs: ((j['runs'] as List<dynamic>?) ?? const [])
            .map((e) => AllocationRun.fromJson(e as Map<String, dynamic>))
            .toList(),
        upcoming: ((j['upcoming'] as List<dynamic>?) ?? const [])
            .map((e) => UpcomingAllocation.fromJson(e as Map<String, dynamic>))
            .toList(),
        projectedGoalDate: _date(j['projectedGoalDate']),
        projectionText: j['projectionText'] as String?,
        earmarkExplanation: j['earmarkExplanation'] as String? ?? '',
      );
}
