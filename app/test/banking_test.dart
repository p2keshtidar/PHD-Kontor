import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/accounts/account_home_screen.dart';
import 'package:girokonto_app/features/banking/bank_accounts_screen.dart';
import 'package:girokonto_app/features/banking/bank_models.dart';
import 'package:girokonto_app/features/banking/bank_repository.dart';
import 'package:girokonto_app/features/banking/bank_transaction_screen.dart';
import 'package:girokonto_app/features/banking/bank_transactions_screen.dart';
import 'package:girokonto_app/features/banking/connect_bank_screen.dart';
import 'package:girokonto_app/l10n/l10n.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';
import 'support/launcher.dart';

/// Scrollt in der Seitenliste, bis [target] sichtbar ist.
Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

/// Echte Bankkonten (Open Banking) mit simuliertem Server: BK01–BK12.
void main() {
  Future<void> open(WidgetTester tester) async {
    await tester.tap(find.byKey(const Key('launcher_open')));
    await tester.pumpAndSettle();
  }

  Map<String, dynamic> connectStart({bool sandbox = true, String? url}) => {
        'connection': {
          ...FakeBackend.bankConnections()[sandbox ? 0 : 1],
          'status': 'PENDING', 'statusLabel': 'Freigabe ausstehend', 'accounts': [],
        },
        'authorizationUrl': url,
        'sandbox': sandbox,
        'nextStepText': sandbox
            ? 'Sandbox: Es öffnet sich keine echte Bank. Bestätige die Test-Freigabe in der App.'
            : 'Melde dich auf der Seite deiner Bank an und bestätige den Lesezugriff. Danach zurück zur App.',
      };

  Map<String, dynamic> completed(String message) => {
        'connection': FakeBackend.bankConnections()[0],
        'sync': {'connectionId': 31, 'status': 'OK', 'accounts': 2, 'newTransactions': 131, 'updatedTransactions': 0,
                 'resultMessage': '131 neue Umsätze.', 'syncedAt': '2026-10-13T08:00:00Z'},
        'resultMessage': message,
      };

  group('Startseite', () {
    testWidgets('BK01 ohne verbundene Bank: Hinweis und Weg zum Verbinden', (tester) async {
      await pumpScreen(tester, const AccountHomeScreen(), backend: FakeBackend({
        'GET /api/banking/accounts': (_) => {
              'accounts': [], 'totalBalanceCents': 0, 'monthIncomeCents': 0, 'monthExpenseCents': 0,
              'anySandbox': false, 'anyStale': false,
            },
      }));
      await scrollTo(tester, find.byKey(const Key('home_bank_connect')));
      expect(find.text('Echte Bankkonten'), findsOneWidget);
      expect(find.text('Nur Lesezugriff – Kontostand und Umsätze an einem Ort'), findsOneWidget);
      await tester.tap(find.byKey(const Key('home_bank_connect')));
      await tester.pumpAndSettle();
      expect(find.text('ROUTE:/banken/verbinden'), findsOneWidget);
    });

    testWidgets('BK02 verbundene Konten getrennt vom Girokonto, Sandbox gekennzeichnet', (tester) async {
      await pumpScreen(tester, const AccountHomeScreen());
      await scrollTo(tester, find.byKey(const Key('home_bank_account_503')));
      expect(find.text('Tagesgeld'), findsOneWidget);
      expect(find.text('nicht aktuell'), findsOneWidget);   // abgelaufene Freigabe
      expect(find.textContaining('keine echte Bank'), findsOneWidget);
      await scrollTo(tester, find.text('Unterkonten'));
      await tester.ensureVisible(find.byKey(const Key('home_bank_account_502')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('home_bank_account_502')));
      await tester.pumpAndSettle();
      expect(find.text('ROUTE:/banken/umsaetze?konto=502&name=Tagesgeld'), findsOneWidget);
    });
  });

  group('Verbinden', () {
    testWidgets('BK03 Sandbox: Test-Freigabe bestätigen, erster Import', (tester) async {
      final fake = await pumpScreen(tester, const Launcher(ConnectBankScreen()), backend: FakeBackend({
        'POST /api/banking/connections': (_) => connectStart(),
        'POST /api/banking/connections/*/complete': (_) =>
            FakeResponse(200, completed('Bank verbunden: 2 Konto/Konten, 131 Umsätze importiert.')),
      }));
      await open(tester);
      expect(find.textContaining('keine echte Bank'), findsWidgets);
      await tester.enterText(find.byKey(const Key('bank_search')), 'haus');
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('institution_SANDBOX_ONLINEBANK')), findsNothing);
      await tester.tap(find.byKey(const Key('institution_SANDBOX_MAINBANK')));
      await tester.pumpAndSettle();
      expect(fake.lastBody('POST /api/banking/connections'),
          {'provider': 'SANDBOX', 'institutionId': 'SANDBOX_MAINBANK'});
      expect(find.text('Test-Freigabe (Sandbox)'), findsOneWidget);
      await tester.tap(find.byKey(const Key('sandbox_approve')));
      await tester.pumpAndSettle();
      expect(fake.lastBody('POST /api/banking/connections/*/complete'), {'approve': true});
      expect(find.text('Bank verbunden: 2 Konto/Konten, 131 Umsätze importiert.'), findsOneWidget);
      expect(find.byKey(const Key('launcher_open')), findsOneWidget);   // Seite geschlossen
    });

    testWidgets('BK04 Sandbox: Ablehnen verbindet nicht', (tester) async {
      final fake = await pumpScreen(tester, const Launcher(ConnectBankScreen()), backend: FakeBackend({
        'POST /api/banking/connections': (_) => connectStart(),
        'POST /api/banking/connections/*/complete': (_) => FakeResponse(200, {
              ...completed('Die Freigabe wurde bei der Bank abgelehnt.'),
              'connection': {...FakeBackend.bankConnections()[0], 'status': 'REJECTED'},
            }),
      }));
      await open(tester);
      await tester.tap(find.byKey(const Key('institution_SANDBOX_ONLINEBANK')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('sandbox_reject')));
      await tester.pumpAndSettle();
      expect(fake.lastBody('POST /api/banking/connections/*/complete'), {'approve': false});
      expect(find.text('Die Freigabe wurde bei der Bank abgelehnt.'), findsOneWidget);
      expect(find.byKey(const Key('bank_search')), findsOneWidget);   // bleibt auf der Seite
    });

    testWidgets('BK05 echte Bank: Seite der Bank öffnen, danach Freigabe abschließen', (tester) async {
      final opened = <Uri>[];
      var attempts = 0;
      final fake = await pumpScreen(
        tester,
        const Launcher(ConnectBankScreen()),
        overrides: [
          linkOpenerProvider.overrideWithValue((uri) async {
            opened.add(uri);
            return true;
          }),
        ],
        backend: FakeBackend({
          'GET /api/banking/providers': (_) => [
                {'id': 'GOCARDLESS', 'name': 'GoCardless Bank Account Data (PSD2)', 'sandbox': false,
                 'available': true, 'description': 'Echte Bank über einen zugelassenen Kontoinformationsdienst (PSD2).'},
              ],
          'GET /api/banking/institutions': (_) => [
                {'id': 'SPK_TEST', 'name': 'Sparkasse Test', 'bic': 'TESTDEFF', 'historyDays': 730, 'accessDays': 180,
                 'logo': null, 'sandbox': false},
              ],
          'POST /api/banking/connections': (_) => connectStart(sandbox: false, url: 'https://bank.test/auth/1'),
          'POST /api/banking/connections/*/complete': (_) => ++attempts == 1
              ? const FakeResponse(409, {'code': 'CONSENT_PENDING',
                  'message': 'Die Freigabe bei der Bank ist noch nicht abgeschlossen.'})
              : FakeResponse(200, completed('Bank verbunden: 1 Konto/Konten, 40 Umsätze importiert.')),
        }),
      );
      await open(tester);
      await tester.tap(find.byKey(const Key('institution_SPK_TEST')));
      await tester.pumpAndSettle();
      expect(opened, [Uri.parse('https://bank.test/auth/1')]);
      await tester.tap(find.byKey(const Key('bank_complete')));
      await tester.pumpAndSettle();
      // noch nicht freigegeben: Hinweis und erneut fragen
      expect(find.text('Die Freigabe bei der Bank ist noch nicht abgeschlossen.'), findsOneWidget);
      await tester.tap(find.byKey(const Key('bank_complete')));
      await tester.pumpAndSettle();
      expect(fake.calls('POST /api/banking/connections/*/complete'), hasLength(2));
      expect(fake.lastBody('POST /api/banking/connections/*/complete'), isEmpty);   // keine Sandbox-Entscheidung
      expect(find.byKey(const Key('launcher_open')), findsOneWidget);
    });
  });

  group('Verwalten', () {
    testWidgets('BK06 Status, Berechtigungen, Abruf und Widerruf mit Löschen', (tester) async {
      final fake = await pumpScreen(tester, const BankAccountsScreen(), backend: FakeBackend({
        'POST /api/banking/connections/*/sync': (_) => const FakeResponse(200,
            {'connectionId': 31, 'status': 'OK', 'accounts': 2, 'newTransactions': 3, 'updatedTransactions': 0,
             'resultMessage': '3 neue Umsätze.', 'syncedAt': '2026-10-13T09:00:00Z'}),
        'DELETE /api/banking/connections/*': (_) => const FakeResponse(200, {'connectionId': 31,
            'revokedAtProvider': true, 'dataDeleted': true,
            'resultMessage': 'Freigabe widerrufen und alle Daten dieser Bank gelöscht.'}),
      }));
      expect(find.text('Sandbox-Hausbank (Testdaten)'), findsOneWidget);
      expect(find.text('Verbunden'), findsOneWidget);
      expect(find.text('Berechtigungen: Kontodetails lesen, Salden lesen, Umsätze lesen'), findsOneWidget);
      expect(find.textContaining('noch 89 Tage'), findsOneWidget);
      await tester.tap(find.byKey(const Key('bank_sync_31')));
      await tester.pumpAndSettle();
      expect(fake.calls('POST /api/banking/connections/31/sync'), hasLength(1));
      expect(find.text('3 neue Umsätze.'), findsOneWidget);

      await tester.tap(find.byKey(const Key('bank_menu_31')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Zugriff widerrufen'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('revoke_delete_data')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('revoke_confirm')));
      await tester.pumpAndSettle();
      final del = fake.calls('DELETE /api/banking/connections/*').single;
      expect(del.path, '/api/banking/connections/31');
      expect(del.queryParameters['deleteData'], true);
      expect(find.text('Freigabe widerrufen und alle Daten dieser Bank gelöscht.'), findsOneWidget);
    });

    testWidgets('BK07 abgelaufene Freigabe: Fehler sichtbar, erneuern über die Bankseite', (tester) async {
      final opened = <Uri>[];
      final fake = await pumpScreen(
        tester,
        const BankAccountsScreen(),
        overrides: [
          linkOpenerProvider.overrideWithValue((uri) async {
            opened.add(uri);
            return false;   // Browser nicht verfügbar → Adresse anzeigen
          }),
        ],
        backend: FakeBackend({
          'POST /api/banking/connections/*/renew': (_) =>
              FakeResponse(200, connectStart(sandbox: false, url: 'https://bank.test/renew/32')),
        }),
      );
      await scrollTo(tester, find.byKey(const Key('bank_renew_32')));
      expect(find.text('Freigabe abgelaufen'), findsOneWidget);
      expect(find.text('Die Bank war nicht erreichbar. Nächster Versuch später.'), findsOneWidget);
      expect(find.byKey(const Key('bank_sync_32')), findsNothing);
      await tester.tap(find.byKey(const Key('bank_renew_32')));
      await tester.pumpAndSettle();
      expect(fake.calls('POST /api/banking/connections/32/renew'), hasLength(1));
      expect(opened.single.toString(), 'https://bank.test/renew/32');
      expect(find.text('https://bank.test/renew/32'), findsOneWidget);
      await tester.tap(find.text('Weiter'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Später'));
      await tester.pumpAndSettle();
      expect(fake.calls('POST /api/banking/connections/*/complete'), isEmpty);
    });
  });

  group('Umsätze', () {
    testWidgets('BK08 Liste mit Status, Filter und Suche', (tester) async {
      final fake = await pumpScreen(tester, const BankTransactionsScreen());
      expect(find.text('Bäckerei Huber'), findsOneWidget);
      expect(find.text('Hotel am Main'), findsOneWidget);
      expect(find.textContaining('Storniert'), findsWidgets);
      await tester.tap(find.byKey(const Key('bank_status_PENDING')));
      await tester.pumpAndSettle();
      expect(fake.calls('GET /api/banking/transactions').last.queryParameters['status'], 'PENDING');
      await tester.enterText(find.byKey(const Key('bank_tx_search')), 'rewe');
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();
      final q = fake.calls('GET /api/banking/transactions').last.queryParameters;
      expect(q['q'], 'rewe');
      expect(q['status'], 'PENDING');
      await tester.tap(find.byKey(const Key('bank_status_all')));
      await tester.pumpAndSettle();
      expect(fake.calls('GET /api/banking/transactions').last.queryParameters.containsKey('status'), isFalse);
    });

    testWidgets('BK09 eigene Angaben speichern, Bankdaten bleiben', (tester) async {
      final fake = await pumpScreen(tester, const Launcher(BankTransactionScreen(id: 602)), backend: FakeBackend({
        'PUT /api/banking/transactions/*': (_) => FakeBackend.bankTx(602),
      }));
      await open(tester);
      expect(find.text('Daten der Bank (unverändert)'), findsOneWidget);
      expect(find.text('REWE Markt Frankfurt-Bockenheim'), findsOneWidget);
      await tester.enterText(find.byKey(const Key('bank_tx_note')), 'Wocheneinkauf');
      await tester.enterText(find.byKey(const Key('bank_tx_tags')), 'Essen, Familie,');
      await scrollTo(tester, find.byKey(const Key('bank_tx_counted')));
      await tester.tap(find.byKey(const Key('bank_tx_counted')));
      await tester.pumpAndSettle();
      await scrollTo(tester, find.byKey(const Key('bank_tx_category')));
      await tester.tap(find.byKey(const Key('bank_tx_category')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Freizeit').last);
      await tester.pumpAndSettle();
      await scrollTo(tester, find.byKey(const Key('bank_tx_save')));
      await tester.tap(find.byKey(const Key('bank_tx_save')));
      await tester.pumpAndSettle();
      expect(fake.lastBody('PUT /api/banking/transactions/*'), {
        'category': 'FREIZEIT',
        'note': 'Wocheneinkauf',
        'tags': ['Essen', 'Familie'],
        'excluded': true,
      });
      expect(find.text('Gespeichert'), findsOneWidget);
      expect(find.byKey(const Key('launcher_open')), findsOneWidget);
    });

    testWidgets('BK10 zurückgebuchter und stornierter Umsatz erklärt', (tester) async {
      await pumpScreen(tester, const BankTransactionScreen(id: 604), backend: FakeBackend({
        'GET /api/banking/transactions/*': (_) => FakeBackend.bankTx(604, status: 'REVERSED', amount: -2990,
            counterparty: 'FitX Fitnessstudio', category: 'VERTRAEGE', label: 'Verträge & Abos'),
      }));
      expect(find.textContaining('zurückgebucht'), findsOneWidget);
      expect(find.text('Zurückgebucht'), findsOneWidget);
    });
  });

  group('Englisch und Modelle', () {
    testWidgets('BK11 Bankkonten auf Englisch', (tester) async {
      L10n.language.value = 'en';
      await pumpScreen(tester, const BankAccountsScreen());
      expect(find.text('Real bank accounts'), findsOneWidget);
      expect(find.text('Connect bank'), findsOneWidget);
      expect(find.textContaining('Read-only access via open banking'), findsOneWidget);
      expect(find.text('Retrieve now'), findsOneWidget);
    });

    test('BK12 Modelle und Filter', () {
      final t = BankTransaction.fromJson(FakeBackend.bankTx(603, status: 'CANCELLED', amount: -12000));
      expect(t.isInactive, isTrue);
      expect(t.isIncoming, isFalse);
      final o = BankOverview.fromJson(FakeBackend.bankOverview());
      expect(o.accounts, hasLength(3));
      expect(o.accounts.where((a) => !a.syncOk), hasLength(1));
      final c = BankConnection.fromJson(FakeBackend.bankConnections()[1]);
      expect(c.canRenew, isTrue);
      expect(c.isActive, isFalse);
      const f = BankTxFilter(accountId: 5);
      expect(f.copyWith(query: ' rewe ', status: 'PENDING').toQuery(1),
          {'page': 1, 'size': 30, 'accountId': 5, 'q': 'rewe', 'status': 'PENDING'});
      expect(f.copyWith(category: 'WOHNEN').copyWith(category: null).category, isNull);
      expect(formatBankAmount(-1234, 'USD'), '-12.34 USD');
      expect(formatBankAmount(-1234, 'EUR'), '-12,34 €');
    });
  });
}
