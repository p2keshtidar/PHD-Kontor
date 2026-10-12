import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/insights/insights_models.dart';
import 'package:girokonto_app/features/loans/loan_models.dart';
import 'package:girokonto_app/features/savings/plan_models.dart';

void main() {
  test('Monatsübersicht aus JSON mit Bezeichnungen', () {
    final m = MonthSummary.fromJson({
      'month': '2026-09',
      'complete': true,
      'hasData': true,
      'incomeCents': 265000,
      'expenseCents': 190000,
      'fixedCents': 100000,
      'freeCents': 75000,
      'categories': [
        {'category': 'GEHALT', 'label': 'Gehalt', 'kind': 'INCOME', 'cents': 265000},
        {'category': 'WOHNEN', 'label': 'Wohnen', 'kind': 'FIXED', 'cents': 90500},
      ],
    });
    expect(m.shortLabel, 'Sep');
    expect(m.longLabel, 'September 2026');
    expect(m.expenses.length, 1);
  });

  test('Plan-Zusammenfassung mit optionalen Feldern', () {
    final p = PlanSummary.fromJson({
      'id': 3,
      'type': 'INVEST',
      'name': 'ETF',
      'icon': null,
      'status': 'ACTIVE',
      'riskLevel': 'AUSGEWOGEN',
      'targetCents': null,
      'targetDate': null,
      'monthlyRateCents': 20000,
      'executionDay': 5,
      'currentValueCents': 60500,
      'depositedCents': 60000,
      'progressPercent': null,
      'projectedDate': null,
      'onTrack': null,
      'nextExecutionDate': '2026-11-05',
      'accountId': null,
    });
    expect(p.isInvest, isTrue);
    expect(p.isOpen, isTrue);
    expect(p.nextExecutionDate, DateTime(2026, 11, 5));
  });

  test('Risikostufen-Texte unterscheiden Spar- und Investplan', () {
    expect(riskDescription('VORSICHTIG', invest: false), contains('Sparkonto'));
    expect(riskDescription('VORSICHTIG', invest: true), contains('Anleihen'));
  });

  test('Kreditzwecke und Prozentformat', () {
    expect(loanPurposes.map((p) => p.key), ['AUTO', 'IMMOBILIE', 'SONSTIGES']);
    expect(formatPercent(5.59), '5,59 %');
  });

  test('Kategorien zum Auswählen enthalten keine festen Kategorien', () {
    expect(selectableCategories.containsKey('UMBUCHUNG'), isFalse);
    expect(selectableCategories.containsKey('SPAREN'), isFalse);
    expect(selectableCategories['VERTRAEGE'], 'Verträge & Abos');
  });
}
