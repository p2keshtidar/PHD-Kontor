import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/assistant/assistant_screen.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Finanzassistent: AS01–AS05.
void main() {
  testWidgets('AS01 Vorschlag antippen: Antwort mit Zahlen und Hinweis', (tester) async {
    final fake = await pumpScreen(tester, const AssistantScreen());
    await tester.tap(find.byKey(const Key('assistant_suggestion_1')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/assistant/ask'), {'question': 'Wie viel habe ich diesen Monat für Lebensmittel ausgegeben?'});
    expect(find.byKey(const Key('assistant_answer_0')), findsOneWidget);
    expect(find.textContaining('insgesamt 476,66 €'), findsOneWidget);
    expect(find.text('447,03 €'), findsOneWidget);
    expect(find.textContaining('keine Finanzberatung'), findsOneWidget);
  });

  testWidgets('AS02 eigene Frage eingeben und senden', (tester) async {
    final fake = await pumpScreen(tester, const AssistantScreen());
    await tester.enterText(find.byKey(const Key('assistant_input')), 'Was kostet mich die Miete?');
    await tester.tap(find.byKey(const Key('assistant_send')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/assistant/ask'), {'question': 'Was kostet mich die Miete?'});
    expect(find.text('Was kostet mich die Miete?'), findsOneWidget);
  });

  testWidgets('AS03 Monatsbericht mit Hinweisen, vorheriger Monat', (tester) async {
    final fake = await pumpScreen(tester, const AssistantScreen());
    await tester.tap(find.byKey(const Key('assistant_tab_report')));
    await tester.pumpAndSettle();
    expect(find.text('September 2026'), findsOneWidget);
    expect(find.text('770,26 €'), findsOneWidget);
    expect(find.text('• Sparquote 29 %.'), findsOneWidget);
    expect(find.text('Vormonat 436,94 € (+9 %)'), findsOneWidget);
    await tester.tap(find.byKey(const Key('report_prev')));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/assistant/report').last.queryParameters['month'], '2026-08');
    expect(find.text('August 2026'), findsOneWidget);
  });

  testWidgets('AS04 Szenario: weniger für Freizeit ausgeben', (tester) async {
    final fake = await pumpScreen(tester, const AssistantScreen(), backend: FakeBackend({
      'POST /api/assistant/scenario': (_) => {
            'type': 'CUT_CATEGORY',
            'text': 'Wenn du 50,00 € pro Monat weniger für Freizeit ausgibst, hast du 600,00 € im Jahr mehr übrig.',
            'monthlyEffectCents': 5000, 'yearlyEffectCents': 60000, 'currentFreeCents': 78383, 'newFreeCents': 83383,
            'currentSavingsRatePercent': 30, 'newSavingsRatePercent': 31, 'monthsToTarget': null, 'warnings': [],
            'assumptions': ['Durchschnitt der letzten 3 vollen Monate: Einnahmen 2.650,00 €, Ausgaben 1.866,17 €.'],
            'disclaimer': 'Rechenbeispiel aus deinen Durchschnittswerten – keine Prognose, keine Beratung.',
          },
    }));
    await tester.tap(find.byKey(const Key('assistant_tab_scenario')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('scenario_amount')), '50');
    await tester.tap(find.byKey(const Key('scenario_calculate')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/assistant/scenario'),
        {'type': 'CUT_CATEGORY', 'amountCents': 5000, 'category': 'FREIZEIT'});
    expect(find.text('Frei pro Monat: 783,83 € → 833,83 €'), findsOneWidget);
    expect(find.textContaining('Rechenbeispiel'), findsOneWidget);
  });

  testWidgets('AS05 Szenario: Sparziel, Fehler vom Server wird angezeigt', (tester) async {
    final fake = await pumpScreen(tester, const AssistantScreen(), backend: FakeBackend({
      'POST /api/assistant/scenario': (_) => FakeResponse(409, {
            'code': 'NO_DATA',
            'message': 'Für Szenarien brauche ich mindestens einen vollen Monat mit Einnahmen und Ausgaben.',
          }),
    }));
    await tester.tap(find.byKey(const Key('assistant_tab_scenario')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('scenario_type_EXTRA_SAVING')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('scenario_amount')), '200');
    await tester.enterText(find.byKey(const Key('scenario_target')), '3000');
    await tester.tap(find.byKey(const Key('scenario_calculate')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/assistant/scenario'),
        {'type': 'EXTRA_SAVING', 'amountCents': 20000, 'targetCents': 300000});
    expect(find.byKey(const Key('scenario_error')), findsOneWidget);
    expect(find.textContaining('mindestens einen vollen Monat'), findsOneWidget);
  });
}
