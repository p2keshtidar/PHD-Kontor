import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/dates.dart';
import 'package:girokonto_app/core/key_value_store.dart';
import 'package:girokonto_app/core/messenger.dart';
import 'package:girokonto_app/core/money.dart';
import 'package:girokonto_app/features/accounts/booking_item.dart';
import 'package:girokonto_app/features/entries/entry_models.dart';
import 'package:girokonto_app/features/loans/loan_models.dart';
import 'package:girokonto_app/features/standing_orders/standing_order_models.dart';
import 'package:girokonto_app/l10n/l10n.dart';
import 'package:girokonto_app/l10n/language_controller.dart';
import 'package:girokonto_app/l10n/strings_en.dart';

/// Zweisprachigkeit: fehlende Übersetzungen und nicht übersetzte Texte finden (L01–L08).
List<File> _sources() => Directory('lib')
    .listSync(recursive: true)
    .whereType<File>()
    .where((f) => f.path.endsWith('.dart') && !f.path.contains('${Platform.pathSeparator}l10n${Platform.pathSeparator}'))
    .toList();

String _unescape(String s) => s.replaceAll(r"\'", "'").replaceAll(r'\$', r'$').replaceAll(r'\n', '\n').replaceAll(r'\\', r'\');

void main() {
  tearDown(() => setLanguageForTest('de'));

  test('L01 jeder Text in tr(…) hat eine englische Übersetzung', () {
    final missing = <String>{};
    final re = RegExp(r"\btr\(\s*'((?:[^'\\]|\\.)*)'");
    for (final f in _sources()) {
      for (final m in re.allMatches(f.readAsStringSync())) {
        final key = _unescape(m.group(1)!);
        if (!stringsEn.containsKey(key)) missing.add('${f.path}: $key');
      }
    }
    expect(missing, isEmpty, reason: 'Fehlende Einträge in lib/l10n/strings_en.dart:\n${missing.join('\n')}');
  });

  test('L02 keine fest codierten Texte an typischen Stellen der Oberfläche', () {
    final re = RegExp(
        r"""(\bText\(\s*|labelText:\s*|hintText:\s*|helperText:\s*|tooltip:\s*|title:\s*|label:\s*|content:\s*|semanticLabel:\s*)'([^'$]*[A-Za-zÄÖÜäöüß]{3}[^']*)'""");
    const allowed = {'Girokonto', 'Projektbank', 'IBAN', 'CVC', 'DE', 'EN', 'Online', 'CSV'};
    final found = <String>[];
    for (final f in _sources()) {
      for (final m in re.allMatches(f.readAsStringSync())) {
        if (!allowed.contains(m.group(2)) && !m.group(2)!.startsWith('http')) found.add('${f.path}: ${m.group(0)}');
      }
    }
    expect(found, isEmpty, reason: 'Bitte mit tr(…) übersetzen:\n${found.join('\n')}');
  });

  test('L03 Platzhalter stimmen in beiden Sprachen überein', () {
    final ph = RegExp(r'\{\d+\}');
    final wrong = [
      for (final e in stringsEn.entries)
        if ((ph.allMatches(e.key).map((m) => m.group(0)).toSet()..removeAll(ph.allMatches(e.value).map((m) => m.group(0))))
            .isNotEmpty)
          e.key
    ];
    expect(wrong, isEmpty);
    expect(stringsEn.values.where((v) => v.trim().isEmpty), isEmpty);
  });

  test('L04 Werte aus Konstanten-Tabellen sind übersetzt', () {
    final values = <String>[
      ...bookingTypeLabels.values,
      ...intervalLabels.values,
      for (final p in loanPurposes) p.label,
      for (final s in [...incomeSuggestions, ...expenseSuggestions]) s.title,
      'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember',
    ];
    expect(values.where((v) => !stringsEn.containsKey(v)), isEmpty);
  });

  test('L05 Geldbeträge und Zahlen je Sprache', () {
    expect(formatCents(123456), '1.234,56 €');
    expect(formatCents(-5), '-0,05 €');
    expect(parseEuroToCents('1.234,5'), 123450);
    setLanguageForTest('en');
    expect(formatCents(123456), '€1,234.56');
    expect(formatCents(-5), '-€0.05');
    expect(formatCents(100000000), '€1,000,000.00');
    expect(parseEuroToCents('1,234.50'), 123450);
    expect(parseEuroToCents('25.5'), 2550);
    expect(parseEuroToCents('1,234'), 123400);
    expect(parseEuroToCents('25,50'), 2550);   // europäische Eingabe bleibt möglich
    expect('4.2'.localDecimal, '4.2');
    expect(formatPercent(5.59), '5.59%');
    setLanguageForTest('de');
    expect('4.2'.localDecimal, '4,2');
    expect(formatPercent(5.59), '5,59 %');
  });

  test('L06 Datum je Sprache', () {
    final d = DateTime(2026, 3, 5, 14, 7);
    expect(formatDate(d), '05.03.2026');
    expect(formatDateTime(d), '05.03.2026, 14:07');
    expect(formatMonthYear(d), 'März 2026');
    expect(monthShort(3), 'Mär');
    setLanguageForTest('en');
    expect(formatDate(d), '5 Mar 2026');
    expect(formatDateTime(d), '5 Mar 2026, 14:07');
    expect(formatMonthYear(d), 'March 2026');
    expect(monthShort(10), 'Oct');
    expect(dayHeader(d, d), 'Today');
  });

  test('L07 Platzhalter und Rückfall auf Deutsch', () {
    expect(tr('Hallo {0}!', ['Erika']), 'Hallo Erika!');
    setLanguageForTest('en');
    expect(tr('Hallo {0}!', ['Erika']), 'Hello Erika!');
    expect(tr('Gibt es nicht {0}', [1]), 'Gibt es nicht 1');
    expect(L10n.fromDevice('en'), 'en');
    expect(L10n.fromDevice('fr'), 'de');
    expect(L10n.fromDevice(null), 'de');
  });

  testWidgets('L08 Sprachwechsel schließt offene und wartende Hinweise in der alten Sprache', (tester) async {
    KeyValueStore.memoryOnly = true;
    await tester.pumpWidget(MaterialApp(scaffoldMessengerKey: rootMessengerKey, home: const Scaffold(body: SizedBox())));
    rootMessengerKey.currentState!
      ..showSnackBar(const SnackBar(content: Text('Zahlung erfolgreich (Simulation)')))
      ..showSnackBar(const SnackBar(content: Text('Karte entsperrt')));
    await tester.pumpAndSettle();
    expect(find.text('Zahlung erfolgreich (Simulation)'), findsOneWidget);

    await LanguageController.set('en');
    await tester.pumpAndSettle();
    expect(find.text('Zahlung erfolgreich (Simulation)'), findsNothing);
    await tester.pump(const Duration(seconds: 6)); // die wartende Meldung käme sonst jetzt
    await tester.pumpAndSettle();
    expect(find.text('Karte entsperrt'), findsNothing);

    // gleiche Sprache noch einmal gewählt: Hinweise bleiben stehen
    rootMessengerKey.currentState!.showSnackBar(const SnackBar(content: Text('Card unlocked')));
    await tester.pumpAndSettle();
    await LanguageController.set('en');
    await tester.pumpAndSettle();
    expect(find.text('Card unlocked'), findsOneWidget);
  });
}
