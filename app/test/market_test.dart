import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/market/applications_screen.dart';
import 'package:girokonto_app/features/market/market_screen.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

/// Finanz-Marktplatz: MK01–MK06.
void main() {
  testWidgets('MK01 Angebote mit Hinweis Simulation, Beispielrechnung, Vor- und Nachteile', (tester) async {
    final fake = await pumpScreen(tester, const MarketScreen());
    await tester.tap(find.byKey(const Key('market_cat_CALL')));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/market/products').last.queryParameters['category'], 'CALL');
    expect(fake.calls('GET /api/market/products').last.queryParameters['amountCents'], 1000000);
    expect(find.textContaining('fiktive Anbieter'), findsOneWidget);
    expect(find.text('Zinsen in 12 Monaten für 10.000,00 €: 310,00 €'), findsOneWidget);
    expect(find.text('Täglich verfügbar'), findsNothing);
    await tester.tap(find.byKey(const Key('market_more_a')));
    await tester.pumpAndSettle();
    expect(find.text('Täglich verfügbar'), findsOneWidget);
  });

  testWidgets('MK02 Favorit setzen und entfernen', (tester) async {
    final fake = await pumpScreen(tester, const MarketScreen());
    await tester.tap(find.byKey(const Key('market_fav_a')));
    await tester.pumpAndSettle();
    expect(fake.calls('PUT /api/market/favorites/*').single.path, '/api/market/favorites/a');
    await tester.tap(find.byKey(const Key('market_fav_b')));
    await tester.pumpAndSettle();
    expect(fake.calls('DELETE /api/market/favorites/*').single.path, '/api/market/favorites/b');
  });

  testWidgets('MK03 Vergleichsliste mit zwei Angeboten', (tester) async {
    final fake = await pumpScreen(tester, const MarketScreen(), backend: FakeBackend({
      'GET /api/market/compare': (_) => {
            'products': [FakeBackend.marketProduct('a'), FakeBackend.marketProduct('c')],
            'rows': [
              {'label': 'Zinssatz', 'values': ['3,10 %', '2,20 %']},
              {'label': 'Mindestbetrag', 'values': ['–', '–']},
            ],
            'disclaimer': 'Simulation: fiktive Anbieter.',
          },
    }));
    expect(find.byKey(const Key('market_compare')), findsNothing);
    await tester.tap(find.byKey(const Key('market_cmp_a')));
    await tester.pumpAndSettle();
    await scrollTo(tester, find.byKey(const Key('market_cmp_c')));
    await tester.tap(find.byKey(const Key('market_cmp_c')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('market_compare')));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/market/compare').single.queryParameters['ids'], 'a,c');
    expect(find.text('3,10 %'), findsOneWidget);
    expect(find.text('Zinssatz'), findsOneWidget);
  });

  testWidgets('MK04 Antrag nur mit Bestätigung, dass es eine Simulation ist', (tester) async {
    final fake = await pumpScreen(tester, const MarketScreen(), backend: FakeBackend({
      'POST /api/market/applications': (_) => FakeResponse(201, FakeBackend.marketApplication()),
    }));
    await tester.tap(find.byKey(const Key('market_apply_a')));
    await tester.pumpAndSettle();
    expect(find.textContaining('kein Konto eröffnet'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byKey(const Key('market_apply_confirm'))).onPressed, isNull);
    await tester.tap(find.byKey(const Key('market_consent')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('market_apply_confirm')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/market/applications'), {'productId': 'a', 'consent': true});
    expect(find.text('ROUTE:/antraege'), findsOneWidget);
  });

  testWidgets('MK05 Anträge: Unterlage bereitstellen, Notiz, zurückziehen', (tester) async {
    final fake = await pumpScreen(tester, const ApplicationsScreen(), backend: FakeBackend({
      'POST /api/market/applications/*/documents/*': (_) => FakeBackend.marketApplication(status: 'SUBMITTED'),
      'PUT /api/market/applications/*/note': (_) => FakeBackend.marketApplication(note: 'Zu teuer'),
      'POST /api/market/applications/*/withdraw': (_) => FakeBackend.marketApplication(),
    }));
    expect(find.text('Reise Card'), findsOneWidget);
    expect(find.text('Unterlagen fehlen'), findsOneWidget);
    await tester.tap(find.byKey(const Key('application_doc_41_doc2')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/market/applications/*/documents/*').single.path,
        '/api/market/applications/41/documents/doc2');
    await tester.tap(find.byKey(const Key('application_note_edit_41')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('application_note_field')), 'Zu teuer');
    await tester.tap(find.byKey(const Key('application_note_save')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('PUT /api/market/applications/*/note'), {'note': 'Zu teuer'});
    await tester.tap(find.byKey(const Key('application_withdraw_41')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/market/applications/*/withdraw'), hasLength(1));
  });

  testWidgets('MK06 Ergebnis der simulierten Prüfung wird angezeigt', (tester) async {
    await pumpScreen(tester, const ApplicationsScreen(), backend: FakeBackend({
      'GET /api/market/applications': (_) => [FakeBackend.marketApplication(status: 'APPROVED',
          reason: 'Simulierte Zusage – es entsteht kein echter Vertrag. Es wird keine Karte bestellt oder ausgestellt.')],
    }));
    expect(find.text('Simulierte Zusage'), findsOneWidget);
    expect(find.textContaining('keine Karte bestellt'), findsOneWidget);
    expect(find.byKey(const Key('application_withdraw_41')), findsNothing);
  });
}
