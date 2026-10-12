import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/insights/csv_import.dart';

/// /K040/ CSV-Import
void main() {
  test('UTF-8 und ISO-8859-1 werden erkannt', () {
    expect(decodeCsvBytes(utf8.encode('Empfänger;Betrag')), 'Empfänger;Betrag');
    expect(decodeCsvBytes(latin1.encode('Empfänger;Betrag')), 'Empfänger;Betrag');
  });

  test('Importergebnis aus JSON', () {
    final r = ImportResult.fromJson({
      'importId': 4, 'format': 'CSV (Semikolon)', 'imported': 33, 'skipped': 0, 'errors': ['Zeile 9: Datum nicht lesbar'],
      'firstDate': '2026-07-01', 'lastDate': '2026-09-27', 'incomeCents': 720000, 'expenseCents': 368868,
    });
    expect(r.imported, 33);
    expect(r.errors.single, contains('Zeile 9'));
    expect(r.lastDate, DateTime(2026, 9, 27));
  });

  test('Ergebnis ohne neue Umsätze', () {
    final r = ImportResult.fromJson({
      'importId': null, 'format': 'CSV (Semikolon)', 'imported': 0, 'skipped': 33, 'errors': <String>[],
      'firstDate': null, 'lastDate': null, 'incomeCents': 0, 'expenseCents': 0,
    });
    expect(r.firstDate, isNull);
    expect(r.skipped, 33);
  });
}
