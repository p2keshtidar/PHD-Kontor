import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/charts.dart';
import 'package:girokonto_app/features/accounts/account_home_screen.dart';
import 'package:girokonto_app/features/dashboard/dashboard_screen.dart';
import 'package:girokonto_app/l10n/l10n.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Scrollt in der Seitenliste, bis [target] sichtbar ist.
Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

Finder _in(Key section, Finder f) => find.descendant(of: find.byKey(section), matching: f);

/// Zentrales Finanz-Dashboard: DB01–DB12.
void main() {
  testWidgets('DB01 Gesamtvermögen mit allen Bestandteilen und Datum', (tester) async {
    await pumpScreen(tester, const DashboardScreen());
    expect(find.text('Finanz-Dashboard'), findsOneWidget);
    const card = Key('dash_networth');
    expect(_in(card, find.text('5.327,05 €')), findsOneWidget);
    expect(_in(card, find.text('Stand 13.10.2026')), findsOneWidget);
    expect(_in(card, find.text('Bankkonten (Projektbank)')), findsOneWidget);
    expect(_in(card, find.text('6.479,50 €')), findsOneWidget);
    expect(_in(card, find.text('Digitale Konten')), findsOneWidget);
    expect(_in(card, find.text('Depot')), findsOneWidget);
    expect(_in(card, find.text('Andere Banken')), findsOneWidget);
    expect(_in(card, find.text('3.120,45 €')), findsOneWidget);
    expect(_in(card, find.text('Kredite')), findsOneWidget);
    expect(_in(card, find.text('-5.000,00 €')), findsOneWidget);
    expect(_in(card, find.text('Sparpläne')), findsNothing);   // 0 € → nicht angezeigt
  });

  testWidgets('DB02 verfügbar und zurückgelegt getrennt, Vormerkungen bei anderen Banken', (tester) async {
    await pumpScreen(tester, const DashboardScreen());
    expect(_in(const Key('dash_available'), find.text('6.479,50 €')), findsOneWidget);
    expect(_in(const Key('dash_reserved'), find.text('727,10 €')), findsOneWidget);
    expect(_in(const Key('dash_earmarked'), find.text('150,00 €')), findsOneWidget);
    final bar = tester.widget<LinearProgressIndicator>(
        _in(const Key('dash_liquidity'), find.byType(LinearProgressIndicator)));
    expect(bar.value, closeTo(647950 / (647950 + 72710), 0.0001));
  });

  testWidgets('DB03 Vermögensverlauf: 12 Monate, Bildschirmleser-Text, Hinweis auf unvollständige Monate',
      (tester) async {
    final semantics = tester.ensureSemantics();
    await pumpScreen(tester, const DashboardScreen());
    await scrollTo(tester, find.byKey(const Key('dash_history')));
    final chart = tester.widget<WealthChart>(find.byType(WealthChart));
    expect(chart.points, hasLength(12));
    expect(chart.points.first.label, 'Nov 25');
    expect(chart.points.last.cents, 580000);
    expect(chart.points.where((p) => !p.complete), hasLength(8));
    expect(find.bySemanticsLabel('Vermögensverlauf: 0,00 € im Nov 25, 5.800,00 € im Okt 26'), findsOneWidget);
    expect(find.textContaining('Hohle Punkte: ohne verbundene Banken'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('DB04 Cashflow: laufender Monat und 6 Monate als Diagramm', (tester) async {
    await pumpScreen(tester, const DashboardScreen());
    await scrollTo(tester, find.byKey(const Key('dash_cashflow')));
    const s = Key('dash_cashflow');
    expect(_in(s, find.text('1.450,00 €')), findsOneWidget);
    expect(_in(s, find.text('-612,30 €')), findsOneWidget);
    expect(_in(const Key('dash_net'), find.text('837,70 €')), findsOneWidget);
    final chart = tester.widget<IncomeExpenseChart>(_in(s, find.byType(IncomeExpenseChart)));
    expect(chart.months, hasLength(6));
    expect(chart.months.last.label, 'Okt');
  });

  testWidgets('DB05 größte Ausgaben mit Anteil am ganzen Monat', (tester) async {
    await pumpScreen(tester, const DashboardScreen());
    await scrollTo(tester, find.byKey(const Key('dash_expense_FREIZEIT')));
    expect(find.text('September 2026'), findsOneWidget);
    expect(_in(const Key('dash_expense_WOHNEN'), find.text('905,00 €')), findsOneWidget);
    expect(_in(const Key('dash_expense_WOHNEN'), find.text('62,3 %')), findsOneWidget);
    expect(_in(const Key('dash_expense_FREIZEIT'), find.text('5,5 %')), findsOneWidget);
  });

  testWidgets('DB06 Schulden: Restschuld, Raten, Verhältnis zu den Einnahmen, Kredit öffnen', (tester) async {
    await pumpScreen(tester, const DashboardScreen());
    await scrollTo(tester, find.byKey(const Key('dash_loan_7')));
    const s = Key('dash_debts');
    expect(_in(s, find.text('Restschuld gesamt')), findsOneWidget);
    expect(_in(s, find.text('220,46 €')), findsOneWidget);
    expect(_in(const Key('dash_debt_ratio'), find.text('7,5 %')), findsOneWidget);
    expect(_in(s, find.text('220,46 € pro Monat · nächste Rate 13.11.2026')), findsOneWidget);
    await tester.tap(find.byKey(const Key('dash_loan_7')));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/kredit/7'), findsOneWidget);
  });

  testWidgets('DB07 Sparziele mit Fortschritt, erreichtes Ziel markiert, Ziel öffnen', (tester) async {
    await pumpScreen(tester, const DashboardScreen());
    await scrollTo(tester, find.byKey(const Key('dash_goal_PLAN_5')));
    const urlaub = Key('dash_goal_DIGITAL_11');
    expect(_in(urlaub, find.text('500,00 € von 3.000,00 €')), findsOneWidget);
    expect(_in(urlaub, find.text('16,7 % erreicht · Ziel bis 30.06.2027 · Digitales Konto')), findsOneWidget);
    expect(_in(const Key('dash_goal_PLAN_5'), find.byIcon(Icons.check_circle)), findsOneWidget);
    expect(_in(const Key('dash_goal_PLAN_5'), find.text('100,0 % erreicht · Sparplan')), findsOneWidget);
    await tester.ensureVisible(find.byKey(urlaub));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(urlaub));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/unterkonto/11'), findsOneWidget);
  });

  testWidgets('DB08 Konten und Karten: veralteter Stand gekennzeichnet, Simulation erkennbar', (tester) async {
    await pumpScreen(tester, const DashboardScreen());
    await scrollTo(tester, find.byKey(const Key('dash_card_3')));
    const ext = Key('dash_account_EXTERNAL_601');
    expect(_in(ext, find.text('Sandbox Hauptbank · Stand 10.10.2026')), findsOneWidget);
    expect(_in(ext, find.text('veraltet')), findsOneWidget);
    expect(find.text('veraltet'), findsOneWidget);   // nur die verbundene Bank
    expect(_in(const Key('dash_account_LOAN_7'), find.text('-5.000,00 €')), findsOneWidget);
    expect(_in(const Key('dash_card_3'), find.text('Debitkarte (Simulation)')), findsOneWidget);
    expect(_in(const Key('dash_card_3'), find.text('•••• 4242 · aktiv · heute 23,99 € von 1.000,00 €')),
        findsOneWidget);
    await tester.tap(find.byKey(ext));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/banken'), findsOneWidget);
  });

  testWidgets('DB09 Hinweise: Simulation und veraltete Kontostände werden genannt', (tester) async {
    await pumpScreen(tester, const DashboardScreen());
    await scrollTo(tester, find.byKey(const Key('dash_notes')));
    expect(find.textContaining('Simulation mit Spielgeld'), findsOneWidget);
    expect(find.textContaining('älter als 2 Tage'), findsOneWidget);
  });

  testWidgets('DB10 neuer Nutzer: leere Bereiche erklären, was fehlt', (tester) async {
    await pumpScreen(tester, const DashboardScreen(), backend: FakeBackend({
      'GET /api/dashboard': (_) => FakeBackend.dashboard(empty: true),
    }));
    expect(_in(const Key('dash_networth'), find.text('1.000,00 €')), findsWidgets);
    expect(find.byKey(const Key('dash_earmarked')), findsNothing);
    await scrollTo(tester, find.byKey(const Key('dash_no_goals')));
    expect(find.textContaining('Noch keine Einnahmen und Ausgaben'), findsOneWidget);
    expect(find.text('Noch keine Ausgaben.'), findsOneWidget);
    expect(find.text('Keine Kredite.'), findsOneWidget);
    expect(find.textContaining('Noch keine Sparziele'), findsOneWidget);
    expect(find.textContaining('Hohle Punkte'), findsNothing);
  });

  testWidgets('DB11 Fehler beim Laden: Meldung und erneut versuchen', (tester) async {
    var calls = 0;
    await pumpScreen(tester, const DashboardScreen(), backend: FakeBackend({
      'GET /api/dashboard': (_) => ++calls == 1
          ? const FakeResponse(500, {'code': 'INTERNAL', 'message': 'Unerwarteter Fehler'})
          : FakeBackend.dashboard(),
    }));
    expect(find.byKey(const Key('dash_retry')), findsOneWidget);
    expect(find.byKey(const Key('dash_networth')), findsNothing);
    await tester.tap(find.byKey(const Key('dash_retry')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('dash_networth')), findsOneWidget);
    expect(calls, 2);
  });

  testWidgets('DB12 Englisch und Einstieg von der Startseite', (tester) async {
    L10n.language.value = 'en';
    await pumpScreen(tester, const DashboardScreen());
    expect(find.text('Finance dashboard'), findsOneWidget);
    expect(_in(const Key('dash_networth'), find.text('€5,327.05')), findsOneWidget);
    expect(find.text('Available and set aside'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('dash_goal_DIGITAL_11')));
    expect(find.textContaining('16.7 % reached'), findsOneWidget);
    L10n.language.value = 'de';

    await pumpScreen(tester, const AccountHomeScreen());
    expect(find.text('Finanz-Dashboard: Verlauf, Cashflow, Schulden, Ziele'), findsOneWidget);
    await tester.tap(find.byKey(const Key('home_dashboard')));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/dashboard'), findsOneWidget);
  });
}
