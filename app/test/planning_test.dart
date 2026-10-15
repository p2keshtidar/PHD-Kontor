import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/charts.dart';
import 'package:girokonto_app/features/planning/planning_screen.dart';
import 'package:girokonto_app/l10n/l10n.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

/// Budgets & Prognose: PL01–PL06.
void main() {
  testWidgets('PL01 Prognose: Warnung, Verlauf, Monatsende, bekannte Buchungen, Annahmen', (tester) async {
    await pumpScreen(tester, const PlanningScreen());
    expect(find.byKey(const Key('forecast_warning')), findsOneWidget);
    expect(find.textContaining('nur noch 70,00 €'), findsOneWidget);
    expect(find.text('Heute 1.200,00 € · nächste 30 Tage'), findsOneWidget);
    expect(tester.widget<ForecastChart>(find.byType(ForecastChart)).values, hasLength(30));
    await scrollTo(tester, find.byKey(const Key('forecast_assumptions')));
    expect(find.text('Ende Oktober 2026'), findsOneWidget);
    expect(find.text('Miete'), findsOneWidget);
    expect(find.text('-950,00 €'), findsOneWidget);
    await tester.tap(find.byKey(const Key('forecast_assumptions')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Eine Schätzung, keine Zusage'), findsOneWidget);
  });

  testWidgets('PL02 Budgets mit Status, Hochrechnung und Vorschlag', (tester) async {
    await pumpScreen(tester, const PlanningScreen());
    await scrollTo(tester, find.byKey(const Key('budget_suggest_WOHNEN')));
    expect(find.text('überschritten'), findsOneWidget);
    expect(find.text('fast aufgebraucht'), findsOneWidget);
    expect(find.text('48,71 € über dem Budget'), findsOneWidget);
    expect(find.text('Noch 15,00 € übrig'), findsOneWidget);
    expect(find.textContaining('593,00 € – 393,00 € über dem Budget'), findsOneWidget);
    expect(find.text('Wohnen · 905,00 €'), findsOneWidget);
  });

  testWidgets('PL03 Budget festlegen aus Vorschlag, ändern und löschen', (tester) async {
    final fake = await pumpScreen(tester, const PlanningScreen(), backend: FakeBackend({
      'PUT /api/budgets/*': (_) => FakeBackend.budgets(),
      'DELETE /api/budgets/*': (_) => const FakeResponse(204),
    }));
    await scrollTo(tester, find.byKey(const Key('budget_suggest_WOHNEN')));
    await tester.tap(find.byKey(const Key('budget_suggest_WOHNEN')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('budget_amount')), '0,50');
    await tester.tap(find.byKey(const Key('budget_save')));
    await tester.pumpAndSettle();
    expect(find.text('Bitte mindestens 1,00 € eingeben'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('budget_amount')), '950');
    await tester.tap(find.byKey(const Key('budget_save')));
    await tester.pumpAndSettle();
    expect(fake.calls('PUT /api/budgets/*').single.path, '/api/budgets/WOHNEN');
    expect(fake.lastBody('PUT /api/budgets/*'), {'monthlyCents': 95000});

    await scrollTo(tester, find.byKey(const Key('budget_FREIZEIT')));
    await tester.tap(find.byKey(const Key('budget_FREIZEIT')));
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(find.byKey(const Key('budget_amount'))).controller!.text, '100,00');
    await tester.tap(find.byKey(const Key('budget_delete')));
    await tester.pumpAndSettle();
    expect(fake.calls('DELETE /api/budgets/*').single.path, '/api/budgets/FREIZEIT');
  });

  testWidgets('PL04 ungewöhnliche Ausgabe mit Begründung', (tester) async {
    await pumpScreen(tester, const PlanningScreen());
    await scrollTo(tester, find.byKey(const Key('unusual_SINGLE_SHOPPING')));
    expect(find.text('Neuer Fernseher'), findsOneWidget);
    expect(find.textContaining('14-mal so viel wie sonst'), findsOneWidget);
  });

  testWidgets('PL05 ohne Budgets und ohne Warnung: Leerzustände', (tester) async {
    await pumpScreen(tester, const PlanningScreen(), backend: FakeBackend({
      'GET /api/budgets': (_) => FakeBackend.budgets(empty: true),
      'GET /api/forecast': (_) => FakeBackend.forecast(warning: false),
    }));
    expect(find.byKey(const Key('forecast_warning')), findsNothing);
    await scrollTo(tester, find.byKey(const Key('unusual_empty')));
    expect(find.byKey(const Key('budget_empty')), findsOneWidget);
  });

  testWidgets('PL06 Englisch', (tester) async {
    L10n.language.value = 'en';
    await pumpScreen(tester, const PlanningScreen());
    expect(find.text('Budgets & forecast'), findsOneWidget);
    expect(find.text('Current account forecast'), findsOneWidget);
  });
}
