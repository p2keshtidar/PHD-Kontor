import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/config.dart';
import 'package:girokonto_app/features/accounts/account_home_screen.dart';
import 'package:girokonto_app/features/auth/login_screen.dart';
import 'package:girokonto_app/features/insights/csv_import_screen.dart';
import 'package:girokonto_app/features/loans/extra_payment_screen.dart';
import 'package:girokonto_app/features/loans/loan_models.dart';
import 'package:girokonto_app/features/profile/two_factor_screen.dart';
import 'package:girokonto_app/features/savings/plan_detail_screen.dart';
import 'package:girokonto_app/features/standing_orders/standing_orders_screen.dart';
import 'package:girokonto_app/features/subaccounts/subaccount_screen.dart';
import 'package:girokonto_app/features/transfer/transfer_screen.dart';
import 'package:qr_flutter/qr_flutter.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';
import 'support/launcher.dart';

/// Abläufe in der App mit simuliertem Server (A01–A14).
/// Scrollt in der Seitenliste (nicht in Textfeldern, die intern auch scrollbar sind).
Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

void main() {
  Future<void> open(WidgetTester tester) async {
    await tester.tap(find.byKey(const Key('launcher_open')));
    await tester.pumpAndSettle();
  }

  group('Anmeldung', () {
    testWidgets('A01 leere Felder: Hinweise, keine Anfrage', (tester) async {
      final fake = await pumpScreen(tester, const LoginScreen());
      await tester.tap(find.byKey(const Key('login_submit')));
      await tester.pumpAndSettle();
      expect(find.text('Bitte Passwort eingeben'), findsOneWidget);
      expect(fake.calls('POST /api/auth/login'), isEmpty);
    });

    testWidgets('A02 Zwei-Faktor: Code-Feld erscheint und Code wird mitgeschickt', (tester) async {
      var attempts = 0;
      final fake = FakeBackend({
        'POST /api/auth/login': (o) {
          attempts++;
          final body = o.data as Map;
          if (body['code'] == null) {
            return const FakeResponse(401, {
              'code': 'TOTP_REQUIRED',
              'message': 'Bitte den 6-stelligen Code aus deiner Authenticator-App eingeben.',
            });
          }
          return body['code'] == '123456'
              ? FakeBackend.tokens(twoFactor: true)
              : const FakeResponse(401, {'code': 'TOTP_INVALID', 'message': 'Der Code ist falsch oder abgelaufen.'});
        },
      });
      await pumpScreen(tester, const LoginScreen(), backend: fake);
      await tester.enterText(find.byKey(const Key('login_email')), 'erika@example.org');
      await tester.enterText(find.byKey(const Key('login_password')), 'geheim12345');
      await tester.tap(find.byKey(const Key('login_submit')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('login_code')), findsOneWidget);
      expect(find.textContaining('6-stelligen Code'), findsWidgets);

      await tester.enterText(find.byKey(const Key('login_code')), '000000');
      await tester.tap(find.byKey(const Key('login_submit')));
      await tester.pumpAndSettle();
      expect(find.text('Der Code ist falsch oder abgelaufen.'), findsOneWidget);

      await tester.enterText(find.byKey(const Key('login_code')), '123 456'); // so zeigt es die Authenticator-App
      await tester.tap(find.byKey(const Key('login_submit')));
      await tester.pumpAndSettle();
      expect(attempts, 3);
      expect(fake.lastBody('POST /api/auth/login')['code'], '123456');
    });

    testWidgets('A03 Server-Adresse einstellen', (tester) async {
      await pumpScreen(tester, const LoginScreen());
      await tester.tap(find.byKey(const Key('server_settings')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).last, '192.168.178.20:8080/');
      await tester.tap(find.text('Speichern'));
      await tester.pumpAndSettle();
      expect(AppConfig.apiBaseUrl, 'http://192.168.178.20:8080');
      expect(find.textContaining('192.168.178.20'), findsOneWidget);
      await AppConfig.save('');
      expect(AppConfig.apiBaseUrl, AppConfig.defaultUrl);
    });
  });

  testWidgets('A04 Überweisung: prüfen, Zusammenfassung, senden mit Idempotenz-Schlüssel', (tester) async {
    final fake = FakeBackend({
      'POST /api/transfers/check': (o) => {
            'recipientName': 'Ben Beispiel', 'recipientIban': 'DE12000100010000000042', 'amountCents': 2550,
            'description': 'Pizza', 'availableCents': 523450, 'remainingDailyLimitCents': 497450,
          },
      'POST /api/transfers': (o) => {
            'bookingId': 900, 'recipientName': 'Ben Beispiel', 'recipientIban': 'DE12000100010000000042',
            'recipientUserId': 2, 'amountCents': 2550, 'description': 'Pizza', 'bookedAt': '2026-10-13T08:00:00Z',
            'newBalanceCents': 520900,
          },
    });
    await pumpScreen(tester, const TransferScreen(), backend: fake);
    await tester.enterText(find.byKey(const Key('transfer_recipient')), 'ben@example.org');
    await tester.enterText(find.byKey(const Key('transfer_amount')), '25,50');
    await tester.ensureVisible(find.byKey(const Key('transfer_next')));
    await tester.tap(find.byKey(const Key('transfer_next')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/transfers/check')['amountCents'], 2550);
    expect(find.text('Ben Beispiel'), findsWidgets);
    await tester.tap(find.text('Jetzt senden'));
    await tester.pumpAndSettle();
    final send = fake.calls('POST /api/transfers').single;
    expect(send.headers['Idempotency-Key'], isNotEmpty);
    expect((send.data as Map)['amountCents'], 2550);
    expect(find.text('ROUTE:/ueberweisung-erfolg'), findsOneWidget);
  });

  testWidgets('A05 Überweisung: ungültiger Betrag wird nicht geschickt', (tester) async {
    final fake = await pumpScreen(tester, const TransferScreen());
    await tester.enterText(find.byKey(const Key('transfer_recipient')), 'ben@example.org');
    await tester.enterText(find.byKey(const Key('transfer_amount')), '0');
    await tester.ensureVisible(find.byKey(const Key('transfer_next')));
    await tester.tap(find.byKey(const Key('transfer_next')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/transfers/check'), isEmpty);
  });

  testWidgets('A06 Dauerauftrag anlegen', (tester) async {
    final fake = FakeBackend({'POST /api/standing-orders': (o) => FakeBackend.standingOrder()});
    await pumpScreen(tester, const StandingOrdersScreen(), backend: fake);
    expect(find.text('Ben Beispiel'), findsOneWidget);
    expect(find.text('monatlich, am 5.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('standing_order_new')));
    await tester.pumpAndSettle();
    // ohne Betrag: Fehlermeldung, keine Anfrage
    await tester.enterText(find.byKey(const Key('so_recipient')), 'ben@example.org');
    await tester.ensureVisible(find.byKey(const Key('so_save')));
    await tester.tap(find.byKey(const Key('so_save')));
    await tester.pumpAndSettle();
    expect(find.text('Bitte gültigen Betrag eingeben'), findsOneWidget);
    expect(fake.calls('POST /api/standing-orders'), isEmpty);

    await tester.enterText(find.byKey(const Key('so_amount')), '50');
    await tester.enterText(find.byKey(const Key('so_reference')), 'Miete Anteil');
    await tester.ensureVisible(find.text('vierteljährlich'));
    await tester.tap(find.text('vierteljährlich'));
    await tester.ensureVisible(find.byKey(const Key('so_day')));
    await tester.tap(find.byKey(const Key('so_day')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('15'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const Key('so_save')));
    await tester.tap(find.byKey(const Key('so_save')));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/standing-orders');
    expect(body['amountCents'], 5000);
    expect(body['intervalMonths'], 3);
    expect(body['executionDay'], 15);
    expect(body['reference'], 'Miete Anteil');
  });

  testWidgets('A07 Dauerauftrag pausieren', (tester) async {
    final fake = FakeBackend({'PUT /api/standing-orders/*': (o) => FakeBackend.standingOrder(status: 'PAUSED')});
    await pumpScreen(tester, const StandingOrdersScreen(), backend: fake);
    await tester.tap(find.text('Ben Beispiel'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Pausieren'));
    await tester.pumpAndSettle();
    expect(fake.lastBody('PUT /api/standing-orders/*')['paused'], isTrue);
  });

  testWidgets('A08 Sondertilgung: Vorschau und Buchung', (tester) async {
    final fake = FakeBackend({
      'POST /api/loans/*/extra-payment/preview': (o) => {
            'amountCents': 100000, 'remainingBeforeCents': 1971031, 'remainingAfterCents': 1871031,
            'installmentsBefore': 59, 'installmentsAfter': 56, 'endBefore': '2031-10-12', 'endAfter': '2031-07-12',
            'interestSavedCents': 32150, 'availableCents': 523450,
          },
      'POST /api/loans/*/extra-payment': (o) => FakeBackend.loanDetail(extraPaid: 100000),
    });
    await pumpScreen(tester, Launcher(ExtraPaymentScreen(loan: Loan.fromJson(FakeBackend.loan()))), backend: fake);
    await open(tester);
    await tester.enterText(find.byKey(const Key('extra_amount')), '1000');
    await tester.tap(find.byKey(const Key('extra_preview')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/loans/*/extra-payment/preview')['amountCents'], 100000);
    await scrollTo(tester, find.text('Du sparst Zinsen'));
    expect(find.text('321,50 €'), findsOneWidget);
    expect(find.text('3 Raten weniger'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('extra_pay')));
    await tester.tap(find.byKey(const Key('extra_pay')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/loans/*/extra-payment')['amountCents'], 100000);
    expect(find.byKey(const Key('launcher_open')), findsOneWidget);       // Seite geschlossen
    expect(find.textContaining('Sondertilgung über 1.000,00 €'), findsOneWidget);
  });

  testWidgets('A09 Sondertilgung: zu wenig Geld → Knopf gesperrt', (tester) async {
    final fake = FakeBackend({
      'POST /api/loans/*/extra-payment/preview': (o) => {
            'amountCents': 900000, 'remainingBeforeCents': 1971031, 'remainingAfterCents': 1071031,
            'installmentsBefore': 59, 'installmentsAfter': 30, 'endBefore': '2031-10-12', 'endAfter': '2029-04-12',
            'interestSavedCents': 150000, 'availableCents': 1000,
          },
    });
    await pumpScreen(tester, Launcher(ExtraPaymentScreen(loan: Loan.fromJson(FakeBackend.loan()))), backend: fake);
    await open(tester);
    await tester.enterText(find.byKey(const Key('extra_amount')), '9000');
    await tester.tap(find.byKey(const Key('extra_preview')));
    await tester.pumpAndSettle();
    await scrollTo(tester, find.byKey(const Key('extra_pay')));
    final button = tester.widget<ButtonStyleButton>(find.byKey(const Key('extra_pay')));
    expect(button.onPressed, isNull);
    expect(find.textContaining('nur 10,00 €'), findsOneWidget);
  });

  testWidgets('A10 Zwei-Faktor einrichten: QR-Code und Bestätigung', (tester) async {
    final fake = FakeBackend({
      'POST /api/me/2fa/setup': (o) => {
            'secret': 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
            'otpauthUri': 'otpauth://totp/Projektbank%3Aerika%40example.org?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
          },
      'POST /api/me/2fa/enable': (o) => const FakeResponse(204),
      'GET /api/me': (o) => FakeBackend.user(twoFactor: true),
    });
    await pumpScreen(tester, const Launcher(TwoFactorScreen()), backend: fake);
    await open(tester);
    await tester.tap(find.byKey(const Key('totp_start')));
    await tester.pumpAndSettle();
    expect(find.byType(QrImageView), findsOneWidget);
    expect(find.text('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('totp_code')), '123 456');
    await scrollTo(tester, find.byKey(const Key('totp_confirm')));
    await tester.tap(find.byKey(const Key('totp_confirm')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/me/2fa/enable')['code'], '123456');
    expect(fake.calls('GET /api/me'), isNotEmpty);
    expect(find.text('Zwei-Faktor-Anmeldung ist jetzt aktiv'), findsOneWidget);
  });

  testWidgets('A11 CSV-Import: Beispieldatei und Fehlermeldung', (tester) async {
    const csv = 'Datum;Betrag;Verwendungszweck\n01.09.2026;-10,00;Kino\n';
    var fail = false;
    final fake = FakeBackend({
      'GET /api/imports/sample': (o) => csv,
      'POST /api/imports': (o) => fail
          ? const FakeResponse(400, {'code': 'INVALID_CSV', 'message': 'Keine Kopfzeile mit Datum und Betrag gefunden.'})
          : {
              'importId': 1, 'format': 'CSV (Semikolon)', 'imported': 33, 'skipped': 2, 'errors': ['Zeile 9: Datum nicht lesbar'],
              'firstDate': '2026-07-01', 'lastDate': '2026-09-27', 'incomeCents': 720000, 'expenseCents': 368868,
            },
    });
    await pumpScreen(tester, const CsvImportScreen(), backend: fake);
    await tester.tap(find.byKey(const Key('csv_sample')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/imports')['content'], csv);
    expect(find.text('33 Umsätze importiert'), findsOneWidget);
    expect(find.text('2 schon vorhanden und übersprungen'), findsOneWidget);
    expect(find.text('Zeile 9: Datum nicht lesbar'), findsOneWidget);
    fail = true;
    await tester.tap(find.byKey(const Key('csv_sample')));
    await tester.pumpAndSettle();
    expect(find.text('Keine Kopfzeile mit Datum und Betrag gefunden.'), findsOneWidget);
  });

  testWidgets('A12 Unterkonto teilen (Besitzer)', (tester) async {
    final fake = FakeBackend({'POST /api/subaccounts/*/members': (o) => const FakeResponse(200, [])});
    await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: fake);
    expect(find.text('Ben Beispiel'), findsOneWidget);
    expect(find.text('Erika Mustermann (du)'), findsOneWidget);
    expect(find.text('Unterkonto auflösen'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('share_add')));
    await tester.tap(find.byKey(const Key('share_add')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('share_recipient')), 'mia@example.org');
    await tester.tap(find.text('Teilen').last);
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/subaccounts/*/members')['recipient'], 'mia@example.org');
  });

  testWidgets('A13 Geteiltes fremdes Unterkonto: kein Auflösen, aber Austreten', (tester) async {
    final fake = FakeBackend({
      'GET /api/subaccounts/*/members': (o) => [
            {'userId': 5, 'name': 'Mia Musterfrau-Langname', 'owner': true, 'you': false},
            {'userId': 1, 'name': 'Erika Mustermann', 'owner': false, 'you': true},
          ],
    });
    await pumpScreen(tester, const SubaccountScreen(accountId: 12), backend: fake);
    expect(find.text('Geteilt von Mia Musterfrau-Langname'), findsOneWidget);
    expect(find.text('Unterkonto auflösen'), findsNothing);
    expect(find.text('Name und Symbol ändern'), findsNothing);
    await scrollTo(tester, find.text('Austreten'));
    expect(find.text('Austreten'), findsOneWidget);
  });

  testWidgets('A14 Startseite zeigt geteilte Unterkonten', (tester) async {
    await pumpScreen(tester, const AccountHomeScreen(), settle: false);
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.text('Hallo Erika!'), findsOneWidget);
    expect(find.text('Geteilt mit 2 Personen'), findsOneWidget);
    expect(find.text('Geteilt von Mia Musterfrau-Langname'), findsOneWidget);
  });

  testWidgets('A15 Sparplan: Erinnerung auf 7 Tage stellen', (tester) async {
    final fake = FakeBackend({'PUT /api/plans/*': (o) => FakeBackend.planDetail()});
    await pumpScreen(tester, const PlanDetailScreen(planId: 5), backend: fake, settle: false);
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    await scrollTo(tester, find.byKey(const Key('plan_reminder')));
    await tester.tap(find.byKey(const Key('plan_reminder')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('7 Tage vorher'));
    await tester.pumpAndSettle();
    expect(fake.lastBody('PUT /api/plans/*')['reminderDays'], 7);
  });

  test('FakeBackend liefert 404 für unbekannte Routen', () async {
    final dio = Dio()..httpClientAdapter = FakeBackend();
    expect(() => dio.get<dynamic>('http://x/api/unbekannt'), throwsA(isA<DioException>()));
  });
}
