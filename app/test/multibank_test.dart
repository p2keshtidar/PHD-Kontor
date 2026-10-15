import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/accounts/account_home_screen.dart';
import 'package:girokonto_app/features/accounts/transactions_screen.dart';
import 'package:girokonto_app/features/multibank/all_transactions_screen.dart';
import 'package:girokonto_app/l10n/l10n.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Scrollt in der Seitenliste, bis [target] sichtbar ist.
Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

FakeBackend _backend([Map<String, Object? Function(dynamic)> extra = const {}]) => FakeBackend({
      'GET /api/accounts': (_) => FakeBackend.accountsWithExtra(),
      for (final e in extra.entries) e.key: (o) => e.value(o),
    });

/// Auswahl in einem Aufklappmenü treffen.
Future<void> choose(WidgetTester tester, Key dropdown, String item) async {
  await tester.tap(find.byKey(dropdown));
  await tester.pumpAndSettle();
  await tester.tap(find.text(item).last);
  await tester.pumpAndSettle();
}

/// Multi-Banking: weitere Konten der Projektbank, Umbuchen, alle Umsätze – MB01–MB09.
void main() {
  testWidgets('MB01 Startseite: Tagesgeld mit fiktivem Zinssatz, weiteres Girokonto mit IBAN', (tester) async {
    await pumpScreen(tester, const AccountHomeScreen(), backend: _backend());
    await scrollTo(tester, find.byKey(const Key('home_all_transactions')));
    expect(find.text('Tagesgeld · 2,00 % p. a. (Simulation, fiktiv)'), findsOneWidget);
    expect(find.text('Weiteres Girokonto (Simulation) · DE89 0001 0001 0000 0000 21'), findsOneWidget);
    expect(find.text('2.500,00 €'), findsOneWidget);
    expect(find.byKey(const Key('home_open_account')), findsOneWidget);
    expect(find.byKey(const Key('home_move')), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('home_account_20')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('home_account_20')));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/umsaetze/20'), findsOneWidget);
  });

  testWidgets('MB02 Konto eröffnen: Angebote, Hinweis Simulation, weiteres Girokonto mit Namen', (tester) async {
    final fake = await pumpScreen(tester, const AccountHomeScreen(), backend: _backend({
      'POST /api/accounts': (_) => FakeResponse(201, FakeBackend.accountsWithExtra().last),
    }));
    await scrollTo(tester, find.byKey(const Key('home_open_account')));
    await tester.tap(find.byKey(const Key('home_open_account')));
    await tester.pumpAndSettle();
    expect(find.text('2,00 % p. a. – fiktiver Zinssatz der Simulation'), findsOneWidget);
    expect(find.descendant(of: find.byType(AlertDialog), matching: find.textContaining('keine Einlagensicherung')),
        findsOneWidget);
    await tester.tap(find.byKey(const Key('open_type_CURRENT')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('open_name')), 'Haushalt');
    await tester.tap(find.byKey(const Key('open_submit')));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/accounts');
    expect(body['type'], 'CURRENT');
    expect(body['name'], 'Haushalt');
    expect(find.text('„Haushalt“ eröffnet (Simulation)'), findsOneWidget);
  });

  testWidgets('MB03 Grenze erreicht: Hinweis, Eröffnen gesperrt', (tester) async {
    final fake = await pumpScreen(tester, const AccountHomeScreen(), backend: _backend({
      'GET /api/accounts/products': (_) => FakeBackend.bankProducts(canOpen: false),
    }));
    await scrollTo(tester, find.byKey(const Key('home_open_account')));
    await tester.tap(find.byKey(const Key('home_open_account')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('open_limit')), findsOneWidget);
    final submit = tester.widget<FilledButton>(find.byKey(const Key('open_submit')));
    expect(submit.onPressed, isNull);
    expect(fake.calls('POST /api/accounts'), isEmpty);
  });

  testWidgets('MB04 Umbuchen: Girokonto → Tagesgeld; Prüfungen vor dem Senden', (tester) async {
    final fake = await pumpScreen(tester, const AccountHomeScreen(), backend: _backend());
    await scrollTo(tester, find.byKey(const Key('home_move')));
    await tester.tap(find.byKey(const Key('home_move')));
    await tester.pumpAndSettle();
    // fremdes, nur lesbares digitales Konto steht nicht zur Wahl
    await tester.tap(find.byKey(const Key('move_from')));
    await tester.pumpAndSettle();
    expect(find.descendant(of: find.byType(DropdownMenuItem<int>), matching: find.textContaining('WG-Kasse')),
        findsNothing);
    expect(find.descendant(of: find.byType(DropdownMenuItem<int>), matching: find.textContaining('Tagesgeld')),
        findsWidgets);
    await tester.tap(find.text('Girokonto · 5.234,50 €').last);
    await tester.pumpAndSettle();
    await choose(tester, const Key('move_to'), 'Girokonto · 5.234,50 €');
    await tester.enterText(find.byKey(const Key('move_amount')), '250');
    await tester.tap(find.byKey(const Key('move_submit')));
    await tester.pumpAndSettle();
    expect(find.text('Quelle und Ziel sind gleich.'), findsOneWidget);
    await choose(tester, const Key('move_to'), 'Tagesgeld · 2.500,00 €');
    await tester.enterText(find.byKey(const Key('move_amount')), '9999');
    await tester.tap(find.byKey(const Key('move_submit')));
    await tester.pumpAndSettle();
    expect(find.text('Höchstens 5.234,50 € verfügbar'), findsOneWidget);
    expect(fake.calls('POST /api/accounts/move'), isEmpty);
    await tester.enterText(find.byKey(const Key('move_amount')), '250');
    await tester.tap(find.byKey(const Key('move_submit')));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/accounts/move');
    expect(body, {'fromAccountId': 10, 'toAccountId': 20, 'amountCents': 25000});
    expect(find.text('Umgebucht'), findsOneWidget);
  });

  testWidgets('MB05 Umbuchen: Fehlermeldung des Servers bleibt im Dialog', (tester) async {
    await pumpScreen(tester, const AccountHomeScreen(), backend: _backend({
      'POST /api/accounts/move': (_) => const FakeResponse(400, {'code': 'NOT_MOVABLE',
        'message': 'Umbuchungen sind nur zwischen deinen Konten der Projektbank möglich (Girokonten, Tagesgeld, digitale Konten).'}),
    }));
    await scrollTo(tester, find.byKey(const Key('home_move')));
    await tester.tap(find.byKey(const Key('home_move')));
    await tester.pumpAndSettle();
    await choose(tester, const Key('move_from'), 'Tagesgeld · 2.500,00 €');
    await choose(tester, const Key('move_to'), 'Haushalt · 123,45 €');
    await tester.enterText(find.byKey(const Key('move_amount')), '10');
    await tester.tap(find.byKey(const Key('move_submit')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('move_error')), findsOneWidget);
    expect(find.textContaining('nur zwischen deinen Konten der Projektbank'), findsOneWidget);
  });

  testWidgets('MB06 Alle Umsätze: beide Quellen, vorgemerkt, Umbuchung, Summen, Filter', (tester) async {
    final fake = await pumpScreen(tester, const AllTransactionsScreen(), backend: _backend({
      'GET /api/transactions': (o) => o.queryParameters['page'] == 0
          ? FakeBackend.transactionsPage(hasMore: true)
          : FakeBackend.transactionsPage(empty: true),
    }));
    expect(find.text('Alle Umsätze'), findsOneWidget);
    expect(find.text('Supermarkt Nord'), findsOneWidget);
    expect(find.text('vorgemerkt'), findsOneWidget);
    expect(find.text('Girokonto · Sandbox Hauptbank'), findsOneWidget);
    expect(find.text('Umbuchung'), findsNWidgets(2));
    expect(find.text('0,47 €'), findsWidgets);
    final first = fake.calls('GET /api/transactions').first.queryParameters;
    expect(first['source'], 'ALL');
    expect(first['internal'], true);

    await scrollTo(tester, find.byKey(const Key('tx_more')));
    await tester.tap(find.byKey(const Key('tx_more')));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/transactions').last.queryParameters['page'], 1);

    // Filter stehen oben – zurückscrollen (die Liste baut nur sichtbare Einträge)
    await tester.drag(find.byType(ListView), const Offset(0, 3000));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('tx_source_EXTERNAL')));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/transactions').last.queryParameters['source'], 'EXTERNAL');
    expect(fake.calls('GET /api/transactions').last.queryParameters['page'], 0);
    await tester.tap(find.byKey(const Key('tx_internal')));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/transactions').last.queryParameters['internal'], false);
    await tester.enterText(find.byKey(const Key('tx_search')), 'miete');
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/transactions').last.queryParameters['q'], 'miete');
  });

  testWidgets('MB07 Tagesgeld verwalten: umbenennen und auflösen', (tester) async {
    final fake = await pumpScreen(tester, const TransactionsScreen(accountId: 20), backend: _backend({
      'PUT /api/accounts/*': (_) => FakeBackend.accountsWithExtra()[3],
      'DELETE /api/accounts/*': (_) => {'interestCents': 8, 'movedCents': 250008,
        'message': '„Tagesgeld“ aufgelöst: 2.500,08 € aufs Girokonto gebucht.'},
    }));
    await tester.tap(find.byKey(const Key('extra_menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('extra_rename')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('extra_name')), 'Rücklage');
    await tester.tap(find.byKey(const Key('extra_name_save')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('PUT /api/accounts/*')['name'], 'Rücklage');

    await tester.tap(find.byKey(const Key('extra_menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('extra_close')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Die Zinsen bis gestern werden gutgeschrieben'), findsOneWidget);
    await tester.tap(find.byKey(const Key('extra_close_confirm')));
    await tester.pumpAndSettle();
    expect(fake.calls('DELETE /api/accounts/*').single.path, '/api/accounts/20');
    expect(find.text('„Tagesgeld“ aufgelöst: 2.500,08 € aufs Girokonto gebucht.'), findsOneWidget);
  });

  testWidgets('MB08 Girokonto hat kein Verwaltungsmenü', (tester) async {
    await pumpScreen(tester, const TransactionsScreen(accountId: 10), backend: _backend());
    expect(find.byKey(const Key('extra_menu')), findsNothing);
  });

  testWidgets('MB09 Englisch', (tester) async {
    L10n.language.value = 'en';
    await pumpScreen(tester, const AccountHomeScreen(), backend: _backend());
    await scrollTo(tester, find.byKey(const Key('home_all_transactions')));
    expect(find.text('Instant-access savings · 2.00 % p.a. (simulation, fictitious)'), findsOneWidget);
    expect(find.text('Open account'), findsOneWidget);
    expect(find.text('All transactions'), findsOneWidget);
  });
}
