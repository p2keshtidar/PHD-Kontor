import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/accounts/booking_detail_screen.dart';
import 'package:girokonto_app/features/accounts/booking_item.dart';
import 'package:girokonto_app/features/multibank/all_transactions_screen.dart';
import 'package:girokonto_app/features/multibank/duplicates_screen.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Transaktionsmanagement: Notizen, CSV-Export, CSV-Importe in „Alle Umsätze“, doppelte Umsätze – TM01–TM07.
void main() {
  testWidgets('TM01 Notiz zu einem Umsatz hinzufügen', (tester) async {
    final item = BookingItem.fromJson(
        (FakeBackend.bookingPage()['items'] as List).first as Map<String, dynamic>, accountId: 10);
    final fake = await pumpScreen(tester, BookingDetailScreen(item: item), backend: FakeBackend({
      'PUT /api/accounts/*/bookings/*/note': (o) => {'note': (o.data as Map)['note'].toString().trim()},
    }));
    await tester.scrollUntilVisible(find.byKey(const Key('booking_note')), 200,
        scrollable: find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first);
    expect(find.text('Keine Notiz – nur für dich sichtbar'), findsOneWidget);
    await tester.tap(find.byKey(const Key('booking_note_edit')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('booking_note_field')), ' Warmmiete inkl. Nebenkosten ');
    await tester.tap(find.byKey(const Key('booking_note_save')));
    await tester.pumpAndSettle();
    expect(fake.calls('PUT /api/accounts/*/bookings/*/note').single.path, '/api/accounts/10/bookings/100/note');
    expect(find.text('Warmmiete inkl. Nebenkosten'), findsOneWidget);
    expect(find.text('Ändern'), findsWidgets);
  });

  testWidgets('TM02 CSV-Export der aktuellen Auswahl in die Zwischenablage (ohne BOM)', (tester) async {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String?;
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
    final fake = await pumpScreen(tester, const AllTransactionsScreen());
    await tester.tap(find.byKey(const Key('tx_source_EXTERNAL')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('tx_export')));
    await tester.pumpAndSettle();
    final req = fake.calls('GET /api/transactions/export').single;
    expect(req.queryParameters['source'], 'EXTERNAL');
    expect(req.queryParameters['internal'], true);
    expect(copied, startsWith('Datum;Quelle;Konto;Bank;Gegenpartei'));
    expect(copied, contains(';-45,99;EUR;vorgemerkt;'));
    expect(find.textContaining('CSV in die Zwischenablage kopiert (1 Umsätze)'), findsOneWidget);
  });

  testWidgets('TM03 Hinweis auf mögliche Duplikate, Paar als „kein Duplikat“ markieren', (tester) async {
    final fake = await pumpScreen(tester, const AllTransactionsScreen());
    expect(find.text('1 möglicher doppelter Umsatz'), findsOneWidget);
    await tester.tap(find.byKey(const Key('tx_duplicates_hint')));
    await tester.pumpAndSettle();
    expect(find.text('Mögliche Duplikate'), findsOneWidget);
    expect(find.textContaining('in „konto.csv“ und in „Girokonto“'), findsOneWidget);
    expect(find.textContaining('konto.csv · CSV-Import'), findsOneWidget);
    expect(find.textContaining('Girokonto · Sandbox Hauptbank'), findsOneWidget);
    await tester.tap(find.byKey(const Key('dup_dismiss_EXTERNAL:701:601|IMPORT:801:5')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/transactions/duplicates/dismiss')['key'], 'EXTERNAL:701:601|IMPORT:801:5');
    expect(find.text('Als „kein Duplikat“ gemerkt'), findsOneWidget);
  });

  testWidgets('TM04 Umsatz der verbundenen Bank nicht mitzählen', (tester) async {
    final fake = await pumpScreen(tester, const DuplicatesScreen(), backend: FakeBackend({
      'PUT /api/banking/transactions/*': (_) => FakeBackend.bankTx(701),
    }));
    expect(find.byKey(const Key('dup_exclude_801')), findsNothing);   // CSV-Import: kein Ausschluss möglich
    await tester.tap(find.byKey(const Key('dup_exclude_701')));
    await tester.pumpAndSettle();
    expect(fake.calls('PUT /api/banking/transactions/*').single.path, '/api/banking/transactions/701');
    expect(fake.lastBody('PUT /api/banking/transactions/*'), {'excluded': true});
    expect(find.text('Umsatz zählt nicht mehr in der Auswertung'), findsOneWidget);
  });

  testWidgets('TM05 keine Duplikate: kein Hinweis, leere Liste erklärt', (tester) async {
    final backend = FakeBackend({'GET /api/transactions/duplicates': (_) => FakeBackend.duplicates(empty: true)});
    await pumpScreen(tester, const AllTransactionsScreen(), backend: backend);
    expect(find.byKey(const Key('tx_duplicates_hint')), findsNothing);
    await tester.tap(find.byKey(const Key('tx_duplicates')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('dup_empty')), findsOneWidget);
  });

  testWidgets('TM06 CSV-Importe und Notizen in der Liste', (tester) async {
    final page = FakeBackend.transactionsPage();
    final items = (page['items'] as List).cast<Map<String, dynamic>>();
    items[1] = {...items[1], 'note': 'Für den Urlaub'};
    items.add({...items.first, 'source': 'IMPORT', 'id': 801, 'accountId': 5, 'accountName': 'konto.csv',
      'institution': 'CSV-Import', 'status': 'BOOKED', 'counterpartyName': 'Bäckerei Korn', 'date': '2026-09-30'});
    final fake = await pumpScreen(tester, const AllTransactionsScreen(), backend: FakeBackend({
      'GET /api/transactions': (_) => page,
    }));
    expect(find.text('Für den Urlaub'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Bäckerei Korn'), 200,
        scrollable: find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first);
    expect(find.text('konto.csv · CSV-Import'), findsOneWidget);
    await tester.tap(find.text('Bäckerei Korn'));
    await tester.pumpAndSettle();
    expect(find.textContaining('ROUTE:'), findsNothing);   // Importe haben keine Detailseite
    await tester.drag(find.byType(ListView), const Offset(0, 2000));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('tx_source_IMPORT')));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/transactions').last.queryParameters['source'], 'IMPORT');
  });
}
