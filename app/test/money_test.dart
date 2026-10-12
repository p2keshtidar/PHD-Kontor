import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/money.dart';

void main() {
  group('formatCents', () {
    test('Tausenderpunkt und Komma', () {
      expect(formatCents(123456), '1.234,56 €');
      expect(formatCents(100000), '1.000,00 €');
      expect(formatCents(123456789), '1.234.567,89 €');
    });
    test('kleine Beträge', () {
      expect(formatCents(0), '0,00 €');
      expect(formatCents(5), '0,05 €');
      expect(formatCents(99), '0,99 €');
    });
    test('negative Beträge', () {
      expect(formatCents(-2500), '-25,00 €');
    });
  });

  group('parseEuroToCents', () {
    test('deutsches Format', () {
      expect(parseEuroToCents('25,50'), 2550);
      expect(parseEuroToCents('25,5'), 2550);
      expect(parseEuroToCents('1.234,56'), 123456);
      expect(parseEuroToCents('12 €'), 1200);
    });
    test('Punkt als Dezimaltrenner', () {
      expect(parseEuroToCents('25.50'), 2550);
      expect(parseEuroToCents('0.01'), 1);
    });
    test('ungültige Eingaben', () {
      expect(parseEuroToCents(''), isNull);
      expect(parseEuroToCents('abc'), isNull);
      expect(parseEuroToCents('1,234'), isNull);
      expect(parseEuroToCents('-5'), isNull);
    });
  });

  test('formatIban gruppiert in Vierern', () {
    expect(formatIban('DE23000100011000000001'), 'DE23 0001 0001 1000 0000 01');
  });
}
