import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/loans/loan_models.dart';
import 'package:girokonto_app/features/loans/loan_planner_screen.dart';
import 'package:girokonto_app/features/planner/planner_screen.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

Map<String, dynamic> _result() => {
      'goal': 'AUTO', 'goalLabel': 'Auto', 'priceCents': 2500000, 'equityCents': 200000, 'months': 12,
      'availableCents': 78383, 'hasData': true, 'loanPurpose': 'AUTO',
      'recommendation': 'Ansparen bis zum Termin passt nicht in dein Budget. Günstigste tragbare Variante: '
          'Anzahlung ansparen, Rest finanzieren (Zinsen 2.169,02 €).',
      'options': [
        {'kind': 'SAVE', 'title': 'Ansparen', 'possible': false, 'monthlyCents': 191667, 'savingMonthlyCents': 191667,
         'extraCostCents': 0, 'totalCostCents': 2300000, 'feasibility': 'RED', 'bankName': null, 'termMonths': 12,
         'text': 'Bis 15.10.2027 jeden Monat 1.916,67 € zurücklegen – keine Zinsen.'},
        {'kind': 'FINANCE', 'title': 'Sofort finanzieren', 'possible': true, 'monthlyCents': 53480,
         'savingMonthlyCents': null, 'extraCostCents': 267011, 'totalCostCents': 2567011, 'feasibility': 'GREEN',
         'bankName': 'Nordlicht Kredit', 'termMonths': 48,
         'text': 'Kredit über 23.000,00 € für 48 Monate bei Nordlicht Kredit: 534,80 € im Monat, Zinsen 2.670,11 €.'},
        {'kind': 'MIX', 'title': 'Anzahlung ansparen, Rest finanzieren', 'possible': true, 'monthlyCents': 46186,
         'savingMonthlyCents': 25000, 'extraCostCents': 216902, 'totalCostCents': 2216902, 'feasibility': 'GREEN',
         'bankName': 'Mainufer Bank', 'termMonths': 48, 'text': '3.000,00 € Anzahlung in 12 Monaten ansparen …'},
      ],
      'disclaimer': 'Rechenbeispiele mit fiktiven Banken und deinen Durchschnittswerten – kein Angebot, keine Beratung.',
    };

/// Zentraler Planer: PL01–PL06.
void main() {
  testWidgets('PL01 Auto vergleichen: drei Wege mit Empfehlung', (tester) async {
    final fake = await pumpScreen(tester, const PlannerScreen(), size: const Size(400, 1600), backend: FakeBackend({
      'POST /api/planner/compare': (_) => _result(),
    }));
    await tester.tap(find.byKey(const Key('planner_goal_AUTO')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('planner_price')), '25000');
    await tester.enterText(find.byKey(const Key('planner_equity')), '2000');
    await tester.tap(find.byKey(const Key('planner_compare')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/planner/compare'),
        {'goal': 'AUTO', 'priceCents': 2500000, 'equityCents': 200000, 'months': 12});
    expect(find.byKey(const Key('planner_recommendation')), findsOneWidget);
    expect(find.byKey(const Key('planner_option_SAVE')), findsOneWidget);
    expect(find.byKey(const Key('planner_option_MIX')), findsOneWidget);
    expect(find.text('Zuerst 250,00 € pro Monat sparen'), findsOneWidget);
    expect(find.text('Zusätzliche Kosten (Zinsen): 2.670,11 €'), findsOneWidget);
    expect(find.text('passt nicht'), findsOneWidget);
    expect(find.textContaining('kein Angebot'), findsOneWidget);
  });

  testWidgets('PL02 Weiter zum Kreditvergleich', (tester) async {
    await pumpScreen(tester, const PlannerScreen(), size: const Size(400, 1600), backend: FakeBackend({
      'POST /api/planner/compare': (_) => _result(),
    }));
    await tester.enterText(find.byKey(const Key('planner_price')), '25000');
    await tester.tap(find.byKey(const Key('planner_compare')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('planner_go_FINANCE')));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/kredit/neu'), findsOneWidget);
  });

  testWidgets('PL03 ohne Preis keine Anfrage', (tester) async {
    final fake = await pumpScreen(tester, const PlannerScreen());
    await tester.tap(find.byKey(const Key('planner_compare')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('planner_error')), findsOneWidget);
    expect(fake.calls('POST /api/planner/compare'), isEmpty);
  });

  testWidgets('PL04 Kredit vergleichen übernimmt Zweck, Betrag, Eigenkapital und Laufzeit', (tester) async {
    final r = _result();
    final options = [for (final o in r['options'] as List) Map<String, dynamic>.from(o as Map)];
    options[1]
      ..['loanAmountCents'] = 2300000
      ..['loanEquityCents'] = 200000;
    options[2]
      ..['loanAmountCents'] = 1800000
      ..['loanEquityCents'] = 700000;
    r['options'] = options;
    await pumpScreen(tester, const PlannerScreen(), size: const Size(400, 1600), backend: FakeBackend({
      'POST /api/planner/compare': (_) => r,
    }));
    await tester.enterText(find.byKey(const Key('planner_price')), '25000');
    await tester.tap(find.byKey(const Key('planner_compare')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('planner_go_MIX')));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/kredit/neu?purpose=AUTO&amount=1800000&equity=700000&term=48'), findsOneWidget);
  });

  testWidgets('PL05 Kreditvergleich startet mit den Werten aus dem Planer', (tester) async {
    final fake = await pumpScreen(tester,
        LoanPlannerScreen.fromQuery(const {'purpose': 'AUTO', 'amount': '1700000', 'equity': '300000', 'term': '48'}),
        size: const Size(400, 1600),
        backend: FakeBackend({'POST /api/loans/offers': (_) => FakeResponse(200, FakeBackend.offerResult())}));
    expect(find.widgetWithText(TextField, '17000'), findsOneWidget);
    expect(find.widgetWithText(TextField, '3000'), findsOneWidget);
    expect(find.text('Laufzeit: 48 Monate (4 Jahre)'), findsOneWidget);
    await scrollTo(tester, find.text('Angebote vergleichen'));
    await tester.tap(find.text('Angebote vergleichen'));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/loans/offers');
    expect([body['purpose'], body['principalCents'], body['equityCents'], body['termMonths']], ['AUTO', 1700000, 300000, 48]);
  });

  test('PL06 ungültige oder fehlende Werte: Vorgaben des Kreditzwecks', () {
    final auto = loanPurposes.firstWhere((p) => p.key == 'AUTO');
    expect(snapTerm(auto, 48), 48);
    expect(snapTerm(auto, 50), 48);      // auf Jahresschritte
    expect(snapTerm(auto, 500), 96);     // höchstens die längste Laufzeit
    expect(snapTerm(auto, 1), 12);
    expect(euroInput(1700000), '17000');
    expect(euroInput(1234550), '12345,50');
    final empty = LoanPlannerScreen.fromQuery(const {'amount': 'abc', 'term': ''});
    expect([empty.purpose, empty.amountCents, empty.equityCents, empty.termMonths], [null, null, null, null]);
  });
}
