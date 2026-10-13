import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/entries/entry_models.dart';

import 'support/fake_backend.dart';

void main() {
  test('Eintrag aus JSON', () {
    final e = Entry.fromJson(FakeBackend.entry());
    expect(e.isIncome, isTrue);
    expect(e.isOnce, isFalse);
    expect(e.intervalLabel, 'monatlich');
    expect(e.signedCents, 265000);
    expect(e.nextDate, DateTime(2026, 11, 1));
    expect(e.bookedCount, 4);
  });

  test('Ausgabe hat negatives Vorzeichen, einmalig ohne Monatswert', () {
    final e = Entry.fromJson(FakeBackend.entry(kind: 'EXPENSE', interval: 0, status: 'DONE'));
    expect(e.signedCents, -265000);
    expect(e.isOnce, isTrue);
    expect(e.isDone, isTrue);
    expect(e.monthlyCents, 0);
    expect(e.nextDate, isNull);
  });

  test('Ältere Antwort ohne optionale Felder', () {
    final e = Entry.fromJson({
      'id': 1, 'kind': 'EXPENSE', 'title': 'Miete', 'amountCents': 1000, 'intervalMonths': 3,
      'startDate': '2026-01-05',
    });
    expect(e.intervalLabel, 'vierteljährlich');
    expect(e.status, 'ACTIVE');
    expect(e.bookedCount, 0);
  });

  test('Übersicht trennt regelmäßig und einmalig', () {
    final s = EntriesSummary.fromJson(FakeBackend.entriesSummary());
    expect(s.recurring.length, 3);
    expect(s.once.length, 1);
    expect(s.monthlyBalanceCents, 175000);
  });

  test('Ergebnis beim Anlegen', () {
    final r = EntryCreateResult.fromJson({'entry': FakeBackend.entry(), 'bookedNow': 4, 'bookedNowCents': 1060000});
    expect(r.bookedNow, 4);
    expect(r.bookedNowCents, 1060000);
  });

  test('Alle Häufigkeiten und Schnellauswahlen sind gültig', () {
    expect(entryIntervals.keys, [0, 1, 3, 6, 12]);
    for (final s in [...incomeSuggestions, ...expenseSuggestions]) {
      expect(entryIntervals.containsKey(s.interval), isTrue, reason: s.title);
    }
    final categories = FakeBackend.entryCategories().map((c) => c['key']).toSet();
    for (final s in incomeSuggestions) {
      expect(['GEHALT', 'SONSTIGE_EINNAHMEN'], contains(s.category));
    }
    expect(categories, containsAll(['GEHALT', 'WOHNEN']));
  });
}
