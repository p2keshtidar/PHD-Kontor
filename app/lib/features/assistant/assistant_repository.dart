import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';

int? _n(Object? v) => (v as num?)?.toInt();

class AssistantFact {
  const AssistantFact(this.label, this.amountCents, this.value);

  final String label;
  final int? amountCents;
  final String? value;

  factory AssistantFact.fromJson(Map<String, dynamic> j) =>
      AssistantFact(j['label'] as String, _n(j['amountCents']), j['value'] as String?);
}

class AssistantAnswer {
  const AssistantAnswer(this.intent, this.question, this.text, this.facts, this.notes, this.note, this.disclaimer);

  final String intent;
  final String question;
  final String text;
  final List<AssistantFact> facts;
  final List<String> notes;
  final String note;
  final String disclaimer;

  factory AssistantAnswer.fromJson(Map<String, dynamic> j) => AssistantAnswer(
        j['intent'] as String,
        j['question'] as String? ?? '',
        j['text'] as String,
        [for (final f in j['facts'] as List<dynamic>) AssistantFact.fromJson(f as Map<String, dynamic>)],
        [for (final n in j['notes'] as List<dynamic>? ?? const []) n as String],
        j['note'] as String? ?? '',
        j['disclaimer'] as String? ?? '',
      );
}

class ReportLine {
  const ReportLine(this.label, this.cents, this.previousCents, this.changePercent);

  final String label;
  final int cents;
  final int previousCents;
  final int? changePercent;
}

class MonthReport {
  const MonthReport(this.month, this.monthLabel, this.complete, this.hasData, this.incomeCents, this.expenseCents,
      this.freeCents, this.savingsRatePercent, this.categories, this.notes, this.disclaimer);

  final String month;
  final String monthLabel;
  final bool complete;
  final bool hasData;
  final int incomeCents;
  final int expenseCents;
  final int freeCents;
  final int? savingsRatePercent;
  final List<ReportLine> categories;
  final List<String> notes;
  final String disclaimer;

  factory MonthReport.fromJson(Map<String, dynamic> j) => MonthReport(
        j['month'] as String,
        j['monthLabel'] as String,
        j['complete'] as bool,
        j['hasData'] as bool,
        _n(j['incomeCents'])!,
        _n(j['expenseCents'])!,
        _n(j['freeCents'])!,
        _n(j['savingsRatePercent']),
        [
          for (final c in j['categories'] as List<dynamic>)
            ReportLine((c as Map<String, dynamic>)['label'] as String, _n(c['cents'])!, _n(c['previousCents'])!,
                _n(c['changePercent']))
        ],
        [for (final n in j['notes'] as List<dynamic>) n as String],
        j['disclaimer'] as String? ?? '',
      );
}

class ScenarioResult {
  const ScenarioResult(this.text, this.monthlyEffectCents, this.yearlyEffectCents, this.currentFreeCents,
      this.newFreeCents, this.currentRate, this.newRate, this.monthsToTarget, this.warnings, this.assumptions,
      this.disclaimer);

  final String text;
  final int monthlyEffectCents;
  final int yearlyEffectCents;
  final int currentFreeCents;
  final int newFreeCents;
  final int? currentRate;
  final int? newRate;
  final int? monthsToTarget;
  final List<String> warnings;
  final List<String> assumptions;
  final String disclaimer;

  factory ScenarioResult.fromJson(Map<String, dynamic> j) => ScenarioResult(
        j['text'] as String,
        _n(j['monthlyEffectCents'])!,
        _n(j['yearlyEffectCents'])!,
        _n(j['currentFreeCents'])!,
        _n(j['newFreeCents'])!,
        _n(j['currentSavingsRatePercent']),
        _n(j['newSavingsRatePercent']),
        _n(j['monthsToTarget']),
        [for (final w in j['warnings'] as List<dynamic>) w as String],
        [for (final a in j['assumptions'] as List<dynamic>) a as String],
        j['disclaimer'] as String? ?? '',
      );
}

class AssistantRepository {
  AssistantRepository(this._api);

  final ApiClient _api;

  Future<List<String>> questions() async {
    final res = await _api.dio.get<List<dynamic>>('/api/assistant/questions');
    return [for (final q in res.data!) (q as Map<String, dynamic>)['text'] as String];
  }

  Future<AssistantAnswer> ask(String question) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/assistant/ask', data: {'question': question});
    return AssistantAnswer.fromJson(res.data!);
  }

  Future<MonthReport> report(String? month) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/assistant/report',
        queryParameters: {if (month != null) 'month': month});
    return MonthReport.fromJson(res.data!);
  }

  Future<ScenarioResult> scenario(Map<String, Object?> body) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/assistant/scenario', data: body);
    return ScenarioResult.fromJson(res.data!);
  }
}

final assistantRepositoryProvider =
    Provider<AssistantRepository>((ref) => AssistantRepository(ref.watch(apiClientProvider)));

final assistantQuestionsProvider =
    FutureProvider.autoDispose<List<String>>((ref) => ref.watch(assistantRepositoryProvider).questions());

final monthReportProvider = FutureProvider.autoDispose
    .family<MonthReport, String?>((ref, month) => ref.watch(assistantRepositoryProvider).report(month));
