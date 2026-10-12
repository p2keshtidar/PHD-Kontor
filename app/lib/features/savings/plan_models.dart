DateTime? _date(dynamic v) => v == null ? null : DateTime.parse(v as String);
int? _int(dynamic v) => v == null ? null : (v as num).toInt();

/// Risikostufen (/F710/), Schlüssel wie im Backend.
const riskLevels = <String, String>{
  'VORSICHTIG': 'Vorsichtig',
  'AUSGEWOGEN': 'Ausgewogen',
  'MUTIG': 'Mutig',
};

/// Kurze Erklärung je Risikostufe für den Assistenten.
String riskDescription(String level, {required bool invest}) => switch (level) {
      'VORSICHTIG' => invest
          ? '20 % Aktien, 80 % Anleihen. Geringe Schwankung, erwartet ca. 2,8 % pro Jahr.'
          : 'Alles bleibt auf dem Sparkonto. Kein Risiko, aber auch kein Ertrag.',
      'AUSGEWOGEN' => invest
          ? 'Halb Aktien, halb Anleihen. Mittlere Schwankung, erwartet ca. 4,0 % pro Jahr.'
          : 'Die Hälfte der Rate fließt in Fonds. Etwas Schwankung, erwartet ca. 2,0 % pro Jahr.',
      _ => invest
          ? '80 % Aktien, 20 % Anleihen. Deutliche Schwankung, erwartet ca. 5,2 % pro Jahr.'
          : '80 % der Rate fließen in Fonds, überwiegend Aktien. Erwartet ca. 4,2 % pro Jahr, kann aber schwanken.',
    };

/// Ergebnis der Machbarkeitsprüfung (/F620/).
class PlanCheck {
  const PlanCheck({
    required this.rateCents,
    required this.availableCents,
    required this.hasData,
    required this.feasibility,
    required this.message,
    required this.expectedReturnPa,
    this.requiredRateCents,
    this.projectedDate,
    this.alternativeRateCents,
    this.alternativeDate,
  });

  final int? requiredRateCents;
  final int rateCents;
  final int availableCents;
  final bool hasData;
  final String feasibility;
  final String message;
  final DateTime? projectedDate;
  final int? alternativeRateCents;
  final DateTime? alternativeDate;
  final double expectedReturnPa;

  factory PlanCheck.fromJson(Map<String, dynamic> j) => PlanCheck(
        requiredRateCents: _int(j['requiredRateCents']),
        rateCents: (j['rateCents'] as num).toInt(),
        availableCents: (j['availableCents'] as num).toInt(),
        hasData: j['hasData'] as bool,
        feasibility: j['feasibility'] as String,
        message: j['message'] as String,
        projectedDate: _date(j['projectedDate']),
        alternativeRateCents: _int(j['alternativeRateCents']),
        alternativeDate: _date(j['alternativeDate']),
        expectedReturnPa: (j['expectedReturnPa'] as num).toDouble(),
      );
}

class PlanSummary {
  const PlanSummary({
    required this.id,
    required this.type,
    required this.name,
    required this.status,
    required this.riskLevel,
    required this.monthlyRateCents,
    required this.executionDay,
    required this.currentValueCents,
    required this.depositedCents,
    this.icon,
    this.targetCents,
    this.targetDate,
    this.progressPercent,
    this.projectedDate,
    this.onTrack,
    this.nextExecutionDate,
    this.reminderDays = 3,
  });

  final int id;

  /// GOAL oder INVEST
  final String type;
  final String name;
  final String? icon;

  /// ACTIVE, PAUSED, COMPLETED, CLOSED
  final String status;
  final String riskLevel;
  final int? targetCents;
  final DateTime? targetDate;
  final int monthlyRateCents;
  final int executionDay;
  final int currentValueCents;
  final int depositedCents;
  final double? progressPercent;
  final DateTime? projectedDate;
  final bool? onTrack;
  final DateTime? nextExecutionDate;

  /// /K010/: Erinnerung so viele Tage vor der Abbuchung (0 = aus)
  final int reminderDays;

  bool get isInvest => type == 'INVEST';
  bool get isOpen => status == 'ACTIVE' || status == 'PAUSED';
  bool get isPaused => status == 'PAUSED';

  factory PlanSummary.fromJson(Map<String, dynamic> j) => PlanSummary(
        id: (j['id'] as num).toInt(),
        type: j['type'] as String,
        name: j['name'] as String,
        icon: j['icon'] as String?,
        status: j['status'] as String,
        riskLevel: j['riskLevel'] as String,
        targetCents: _int(j['targetCents']),
        targetDate: _date(j['targetDate']),
        monthlyRateCents: (j['monthlyRateCents'] as num).toInt(),
        executionDay: (j['executionDay'] as num).toInt(),
        currentValueCents: (j['currentValueCents'] as num).toInt(),
        depositedCents: (j['depositedCents'] as num).toInt(),
        progressPercent: (j['progressPercent'] as num?)?.toDouble(),
        projectedDate: _date(j['projectedDate']),
        onTrack: j['onTrack'] as bool?,
        nextExecutionDate: _date(j['nextExecutionDate']),
        reminderDays: (j['reminderDays'] as num?)?.toInt() ?? 3,
      );
}

class DepotPosition {
  const DepotPosition(this.label, this.units, this.price, this.valueCents, this.investedCents, this.weightPercent);

  final String label;
  final double units;
  final double price;
  final int valueCents;
  final int investedCents;
  final double weightPercent;

  factory DepotPosition.fromJson(Map<String, dynamic> j) => DepotPosition(
        j['label'] as String,
        (j['units'] as num).toDouble(),
        (j['price'] as num).toDouble(),
        (j['valueCents'] as num).toInt(),
        (j['investedCents'] as num).toInt(),
        (j['weightPercent'] as num).toDouble(),
      );
}

class SavingTip {
  const SavingTip(this.label, this.text, this.saveCents, this.monthsEarlier);

  final String label;
  final String text;
  final int saveCents;
  final int? monthsEarlier;

  factory SavingTip.fromJson(Map<String, dynamic> j) => SavingTip(
        j['label'] as String,
        j['text'] as String,
        (j['saveCents'] as num).toInt(),
        _int(j['monthsEarlier']),
      );
}

class PlanDetail {
  const PlanDetail({
    required this.summary,
    required this.cashCents,
    required this.depotValueCents,
    required this.gainCents,
    required this.gainPercent,
    required this.expectedReturnPa,
    required this.riskExplanation,
    required this.positions,
    required this.history,
    required this.tips,
    this.requiredRateCents,
  });

  final PlanSummary summary;
  final int cashCents;
  final int depotValueCents;
  final int gainCents;
  final double gainPercent;
  final double expectedReturnPa;
  final String riskExplanation;
  final int? requiredRateCents;
  final List<DepotPosition> positions;

  /// (Datum, Wert, eingezahlt)
  final List<(DateTime, int, int)> history;
  final List<SavingTip> tips;

  factory PlanDetail.fromJson(Map<String, dynamic> j) => PlanDetail(
        summary: PlanSummary.fromJson(j['summary'] as Map<String, dynamic>),
        cashCents: (j['cashCents'] as num).toInt(),
        depotValueCents: (j['depotValueCents'] as num).toInt(),
        gainCents: (j['gainCents'] as num).toInt(),
        gainPercent: (j['gainPercent'] as num).toDouble(),
        expectedReturnPa: (j['expectedReturnPa'] as num).toDouble(),
        riskExplanation: j['riskExplanation'] as String,
        requiredRateCents: _int(j['requiredRateCents']),
        positions: (j['positions'] as List<dynamic>)
            .map((e) => DepotPosition.fromJson(e as Map<String, dynamic>))
            .toList(),
        history: (j['history'] as List<dynamic>).map((e) {
          final m = e as Map<String, dynamic>;
          return (
            DateTime.parse(m['date'] as String),
            (m['valueCents'] as num).toInt(),
            (m['depositedCents'] as num).toInt(),
          );
        }).toList(),
        tips: (j['tips'] as List<dynamic>).map((e) => SavingTip.fromJson(e as Map<String, dynamic>)).toList(),
      );
}

/// /K010/: Auswahl für die Erinnerung (Tage vor der Abbuchung)
const reminderOptions = <int, String>{
  0: 'Aus',
  1: '1 Tag vorher',
  3: '3 Tage vorher',
  7: '7 Tage vorher',
};
