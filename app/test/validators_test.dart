import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/validators.dart';

void main() {
  group('isAdult', () {
    final today = DateTime(2026, 10, 12);
    test('genau 18 am Geburtstag', () {
      expect(isAdult(DateTime(2008, 10, 12), today), isTrue);
    });
    test('einen Tag vor dem 18. Geburtstag', () {
      expect(isAdult(DateTime(2008, 10, 13), today), isFalse);
    });
    test('deutlich älter', () {
      expect(isAdult(DateTime(1990, 1, 1), today), isTrue);
    });
  });

  test('validateEmail', () {
    expect(validateEmail(''), isNotNull);
    expect(validateEmail('kein-at'), isNotNull);
    expect(validateEmail('max@example.org'), isNull);
  });

  test('validateNewPassword verlangt 10 Zeichen', () {
    expect(validateNewPassword('kurz'), isNotNull);
    expect(validateNewPassword('lang-genug-1'), isNull);
  });

  test('Datumsformate', () {
    expect(toIsoDate(DateTime(2001, 3, 9)), '2001-03-09');
    expect(toGermanDate(DateTime(2001, 3, 9)), '09.03.2001');
  });
}
