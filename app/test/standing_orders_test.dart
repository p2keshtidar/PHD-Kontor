import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/standing_orders/standing_order_models.dart';

/// /K030/ Daueraufträge
void main() {
  final json = {
    'id': 3,
    'recipientName': 'Ben Beispiel',
    'recipientIban': 'DE12000100010000000042',
    'amountCents': 5000,
    'reference': 'Miete Anteil',
    'executionDay': 5,
    'intervalMonths': 3,
    'intervalLabel': 'vierteljährlich',
    'startDate': '2026-11-05',
    'endDate': null,
    'status': 'ACTIVE',
    'nextExecutionDate': '2026-11-05',
    'lastExecutedMonth': null,
  };

  test('Dauerauftrag aus JSON', () {
    final o = StandingOrder.fromJson(json);
    expect(o.recipientName, 'Ben Beispiel');
    expect(o.intervalLabel, 'vierteljährlich');
    expect(o.isPaused, isFalse);
    expect(o.nextExecutionDate, DateTime(2026, 11, 5));
    expect(o.endDate, isNull);
  });

  test('Pausiert und ohne Intervall-Text vom Server', () {
    final o = StandingOrder.fromJson({...json, 'status': 'PAUSED', 'intervalLabel': null, 'intervalMonths': 12});
    expect(o.isPaused, isTrue);
    expect(o.intervalLabel, 'jährlich');
  });

  test('Alle Intervalle haben einen Text', () {
    expect(intervalLabels.keys, [1, 3, 6, 12]);
  });
}
