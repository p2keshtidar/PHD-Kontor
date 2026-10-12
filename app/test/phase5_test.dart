import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/dates.dart';
import 'package:girokonto_app/features/loans/loan_models.dart';
import 'package:girokonto_app/features/savings/plan_models.dart';

/// Phase 5: /K010/ Erinnerung an die Sparrate, /K020/ Sondertilgung
void main() {
  Map<String, dynamic> loanJson({int? remaining, String status = 'ACTIVE'}) => {
        'id': 1,
        'bankName': 'Nordlicht Bank',
        'purposeLabel': 'Auto',
        'principalCents': 2000000,
        'interestRate': 5.59,
        'termMonths': 60,
        'monthlyRateCents': 38286,
        'startDate': '2026-10-12',
        'nextDueDate': '2026-12-12',
        'paidInstallments': 1,
        'remainingCents': 1478701,
        'progressPercent': 26.1,
        'status': status,
        if (remaining != null) 'remainingInstallments': remaining,
        'expectedEndDate': '2030-07-12',
        'extraPaidCents': 500000,
      };

  test('Kredit: Gesamtzahl Raten sinkt durch Sondertilgung', () {
    final l = Loan.fromJson(loanJson(remaining: 44));
    expect(l.totalInstallments, 45);
    expect(l.extraPaidCents, 500000);
    expect(formatMonthYear(l.expectedEndDate!), 'Juli 2030');
  });

  test('Kredit: ältere Server-Antwort ohne neue Felder', () {
    final j = loanJson()..remove('expectedEndDate')..remove('extraPaidCents');
    final l = Loan.fromJson(j);
    expect(l.totalInstallments, 1);
    expect(l.extraPaidCents, 0);
    expect(l.expectedEndDate, isNull);
  });

  test('Tilgungsplan-Zeile mit und ohne Sondertilgung', () {
    final row = {
      'month': 1, 'dueDate': '2026-11-12', 'rateCents': 38286, 'interestCents': 9317,
      'principalCents': 28969, 'remainingCents': 1471031, 'paid': true,
    };
    expect(ScheduleRow.fromJson(row).extraCents, 0);
    expect(ScheduleRow.fromJson({...row, 'extraCents': 500000}).extraCents, 500000);
  });

  test('Vorschau einer Sondertilgung', () {
    final p = ExtraPreview.fromJson({
      'amountCents': 500000, 'remainingBeforeCents': 2000000, 'remainingAfterCents': 1500000,
      'installmentsBefore': 60, 'installmentsAfter': 44, 'endBefore': '2031-10-12', 'endAfter': '2030-06-12',
      'interestSavedCents': 123456, 'availableCents': 900000,
    });
    expect(p.installmentsSaved, 16);
    expect(p.paysOff, isFalse);
    expect(formatMonthYear(p.endAfter), 'Juni 2030');
  });

  test('Detail mit Sondertilgungen', () {
    final d = LoanDetail.fromJson({
      'loan': loanJson(remaining: 44),
      'totalInterestCents': 200000,
      'interestSavedCents': 50000,
      'schedule': <dynamic>[],
      'extraPayments': [
        {'id': 1, 'amountCents': 500000, 'paidOn': '2026-11-20', 'afterInstallment': 1},
      ],
    });
    expect(d.interestSavedCents, 50000);
    expect(d.extraPayments.single.amountCents, 500000);
  });

  test('Sparplan: Erinnerung, Standard 3 Tage', () {
    final base = {
      'id': 7, 'type': 'INVEST', 'name': 'ETF', 'status': 'ACTIVE', 'riskLevel': 'AUSGEWOGEN',
      'monthlyRateCents': 20000, 'executionDay': 15, 'currentValueCents': 0, 'depositedCents': 0,
    };
    expect(PlanSummary.fromJson(base).reminderDays, 3);
    expect(PlanSummary.fromJson({...base, 'reminderDays': 0}).reminderDays, 0);
    expect(reminderOptions[0], 'Aus');
    expect(reminderOptions.keys, containsAll([1, 3, 7]));
  });
}
