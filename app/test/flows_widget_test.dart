import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/config.dart';
import 'package:girokonto_app/l10n/l10n.dart';
import 'package:girokonto_app/features/accounts/account_home_screen.dart';
import 'package:girokonto_app/features/auth/login_screen.dart';
import 'package:girokonto_app/features/entries/entries_screen.dart';
import 'package:girokonto_app/features/entries/entry_form_screen.dart';
import 'package:girokonto_app/features/insights/csv_import_screen.dart';
import 'package:girokonto_app/features/loans/extra_payment_screen.dart';
import 'package:girokonto_app/features/loans/loan_models.dart';
import 'package:girokonto_app/features/profile/profile_screen.dart';
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

  testWidgets('A16 Überweisung auf sehr kleinem Display mit großer Schrift: "Jetzt senden" erreichbar', (tester) async {
    final fake = FakeBackend({
      'POST /api/transfers/check': (o) => {
            'recipientName': 'Ben Beispiel mit einem sehr langen Namen', 'recipientIban': 'DE12000100010000000042',
            'amountCents': 2550, 'description': 'Pizza und Getränke vom Wochenende', 'availableCents': 523450,
            'remainingDailyLimitCents': 497450,
          },
      'POST /api/transfers': (o) => {
            'bookingId': 900, 'recipientName': 'Ben Beispiel', 'recipientIban': 'DE12000100010000000042',
            'recipientUserId': 2, 'amountCents': 2550, 'description': 'Pizza', 'bookedAt': '2026-10-13T08:00:00Z',
            'newBalanceCents': 520900,
          },
    });
    await pumpScreen(tester, const TransferScreen(), backend: fake, size: const Size(320, 480), textScale: 1.3);
    await tester.enterText(find.byKey(const Key('transfer_recipient')), 'ben@example.org');
    await tester.enterText(find.byKey(const Key('transfer_amount')), '25,50');
    await tester.ensureVisible(find.byKey(const Key('transfer_next')));
    await tester.tap(find.byKey(const Key('transfer_next')));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull); // kein Überlauf in der Zusammenfassung
    await tester.ensureVisible(find.text('Jetzt senden'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Jetzt senden'));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/transfers'), hasLength(1));
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

  testWidgets('A12 digitales Konto teilen (Besitzer)', (tester) async {
    final fake = FakeBackend({'POST /api/subaccounts/*/members': (o) => const FakeResponse(200, [])});
    await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: fake);
    // Ziel-Karte steht oben, Teilen und Auflösen weiter unten
    await scrollTo(tester, find.byKey(const Key('share_add')));
    expect(find.text('Ben Beispiel'), findsOneWidget);
    expect(find.text('Erika Mustermann (du)'), findsOneWidget);
    await scrollTo(tester, find.text('Digitales Konto auflösen'));
    expect(find.text('Digitales Konto auflösen'), findsOneWidget);
    tester
        .state<ScrollableState>(find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first)
        .position
        .jumpTo(0);
    await tester.pumpAndSettle();
    await scrollTo(tester, find.byKey(const Key('share_add')));
    await tester.tap(find.byKey(const Key('share_add')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('share_recipient')), 'mia@example.org');
    await tester.tap(find.text('Teilen').last);
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/subaccounts/*/members')['recipient'], 'mia@example.org');
    expect(fake.lastBody('POST /api/subaccounts/*/members')['role'], 'FULL');
  });

  testWidgets('A13 geteiltes fremdes digitales Konto: kein Auflösen, aber Austreten', (tester) async {
    final fake = FakeBackend({
      'GET /api/subaccounts/*/members': (o) => [
            {'userId': 5, 'name': 'Mia Musterfrau-Langname', 'owner': true, 'you': false},
            {'userId': 1, 'name': 'Erika Mustermann', 'owner': false, 'you': true},
          ],
    });
    await pumpScreen(tester, const SubaccountScreen(accountId: 12), backend: fake);
    expect(find.text('Geteilt von Mia Musterfrau-Langname'), findsOneWidget);
    expect(find.text('Digitales Konto auflösen'), findsNothing);
    expect(find.text('Name, Zweck und Ziel ändern'), findsNothing);
    await scrollTo(tester, find.text('Austreten'));
    expect(find.text('Austreten'), findsOneWidget);
  });

  testWidgets('A14 Startseite zeigt geteilte digitale Konten', (tester) async {
    await pumpScreen(tester, const AccountHomeScreen(), settle: false);
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.text('Hallo Erika!'), findsOneWidget);
    // digitale Konten stehen unter den Bankkonten
    final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
    await tester.scrollUntilVisible(find.text('Geteilt von Mia Musterfrau-Langname'), 200, scrollable: list);
    expect(find.text('Geteilt mit 2 Personen'), findsOneWidget);
    expect(find.text('Geteilt von Mia Musterfrau-Langname'), findsOneWidget);
    // Ziehen über die antippbare Vermögenskarte startet einen kurzen Timer – auslaufen lassen
    await tester.pump(const Duration(seconds: 1));
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

  group('Sprache', () {
    testWidgets('A23 Sprachumschalter: sofort Englisch, beim Konto gespeichert, zurück auf Deutsch', (tester) async {
      final fake = await pumpScreen(tester, const LoginScreen());
      expect(find.text('Willkommen zurück'), findsOneWidget);
      await tester.tap(find.byKey(const Key('language_switch')));
      await tester.pumpAndSettle();
      expect(L10n.lang, 'en');
      expect(find.text('Welcome back'), findsOneWidget);
      expect(find.text('Log in'), findsOneWidget);
      expect(find.text('Willkommen zurück'), findsNothing);
      // noch nicht angemeldet: nur lokal gespeichert
      expect(fake.calls('PUT /api/me/language'), isEmpty);
      await tester.tap(find.byKey(const Key('login_submit')));
      await tester.pumpAndSettle();
      expect(find.text('Please enter your password'), findsOneWidget);   // Prüfmeldung übersetzt
      await tester.tap(find.byKey(const Key('language_switch')));
      await tester.pumpAndSettle();
      expect(find.text('Willkommen zurück'), findsOneWidget);
    });

    testWidgets('A25 Profil: Sprache wählen wird beim Konto gespeichert', (tester) async {
      final fake = await pumpScreen(tester, const ProfileScreen());
      await tester.tap(find.text('EN').last);
      await tester.pumpAndSettle();
      expect(L10n.lang, 'en');
      expect(fake.lastBody('PUT /api/me/language')['language'], 'en');
      expect(find.text('Language'), findsOneWidget);
      expect(find.text('Profile'), findsOneWidget);
    });

    testWidgets('A24 Startseite und Überweisung auf Englisch, Accept-Language wird gesendet', (tester) async {
      L10n.language.value = 'en';
      final fake = await pumpScreen(tester, const AccountHomeScreen(), settle: false);
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      expect(find.text('Hello Erika!'), findsOneWidget);
      expect(find.text('Net worth'), findsOneWidget);
      expect(find.text('€5,234.50'), findsWidgets);
      expect(fake.requests.first.headers['Accept-Language'], 'en');
    });
  });

  group('Einnahmen & Ausgaben', () {
    Map<String, Object? Function(RequestOptions)> created(Map<String, dynamic> entry, int n, int cents) => {
          'POST /api/entries': (o) => {'entry': entry, 'bookedNow': n, 'bookedNowCents': cents},
        };

    testWidgets('A17 Gehalt monatlich über die Schnellauswahl', (tester) async {
      final fake = FakeBackend(created(FakeBackend.entry(), 1, 265000));
      await pumpScreen(tester, const Launcher(EntryFormScreen(kind: 'INCOME')), backend: fake);
      await open(tester);
      await tester.tap(find.byKey(const Key('entry_suggest_Gehalt')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('entry_amount')), '2.650,00');
      await scrollTo(tester, find.byKey(const Key('entry_save')));
      expect(find.textContaining('Wird sofort gebucht'), findsOneWidget);
      await tester.tap(find.byKey(const Key('entry_save')));
      await tester.pumpAndSettle();
      final body = fake.lastBody('POST /api/entries');
      expect(body['kind'], 'INCOME');
      expect(body['title'], 'Gehalt');
      expect(body['category'], 'GEHALT');
      expect(body['amountCents'], 265000);
      expect(body['intervalMonths'], 1);
      expect(body['date'], '2026-10-13');      // Datum der Simulation, nicht des Rechners
      expect(body.containsKey('endDate'), isFalse);
      expect(find.byKey(const Key('launcher_open')), findsOneWidget);   // Seite geschlossen
      expect(find.text('Gebucht: +2.650,00 €'), findsOneWidget);
    });

    testWidgets('A18 einmalige Ausgabe, Kategorie automatisch; ohne Betrag keine Anfrage', (tester) async {
      final fake = FakeBackend(created(FakeBackend.entry(kind: 'EXPENSE', title: 'Urlaub', interval: 0), 1, -145000));
      await pumpScreen(tester, const Launcher(EntryFormScreen()), backend: fake);
      await open(tester);
      expect(find.text('Ausgabe eintragen'), findsOneWidget);
      await tester.enterText(find.byKey(const Key('entry_title')), 'Urlaub Mallorca');
      await scrollTo(tester, find.byKey(const Key('entry_save')));
      await tester.tap(find.byKey(const Key('entry_save')));
      await tester.pumpAndSettle();
      expect(find.text('Bitte gültigen Betrag eingeben'), findsOneWidget);
      expect(fake.calls('POST /api/entries'), isEmpty);

      await tester.enterText(find.byKey(const Key('entry_amount')), '1450');
      await scrollTo(tester, find.byKey(const Key('entry_save')));
      await tester.tap(find.byKey(const Key('entry_save')));
      await tester.pumpAndSettle();
      final body = fake.lastBody('POST /api/entries');
      expect(body['kind'], 'EXPENSE');
      expect(body['intervalMonths'], 0);
      expect(body.containsKey('category'), isFalse);
      expect(body['amountCents'], 145000);
    });

    testWidgets('A19 Wechsel Einnahme/Ausgabe und Fehlermeldung vom Server', (tester) async {
      final fake = FakeBackend({
        'POST /api/entries': (o) => const FakeResponse(400, {
              'code': 'INSUFFICIENT_FUNDS',
              'message': 'Nicht genug Guthaben auf dem Girokonto für „Auto“ am 13.10.2026 (verfügbar: 5.234,50 €).',
            }),
      });
      await pumpScreen(tester, const Launcher(EntryFormScreen(kind: 'INCOME')), backend: fake);
      await open(tester);
      await tester.tap(find.byKey(const Key('entry_kind_expense')));
      await tester.pumpAndSettle();
      expect(find.text('Ausgabe eintragen'), findsOneWidget);
      expect(find.byKey(const Key('entry_suggest_Miete')), findsOneWidget);
      await tester.enterText(find.byKey(const Key('entry_title')), 'Auto');
      await tester.enterText(find.byKey(const Key('entry_amount')), '20000');
      await scrollTo(tester, find.byKey(const Key('entry_save')));
      await tester.tap(find.byKey(const Key('entry_save')));
      await tester.pumpAndSettle();
      expect(fake.lastBody('POST /api/entries')['kind'], 'EXPENSE');
      expect(find.textContaining('Nicht genug Guthaben'), findsOneWidget);
      expect(find.byKey(const Key('launcher_open')), findsNothing);    // Seite bleibt offen
    });

    testWidgets('A20 monatlich am 30.: Hinweis, keine Anfrage', (tester) async {
      final fake = FakeBackend({'GET /api/clock': (_) => {'date': '2026-10-30', 'offsetDays': 0}});
      await pumpScreen(tester, const Launcher(EntryFormScreen(kind: 'EXPENSE')), backend: fake);
      await open(tester);
      await tester.tap(find.byKey(const Key('entry_suggest_Miete')));
      await tester.pumpAndSettle();
      await tester.enterText(find.byKey(const Key('entry_amount')), '900');
      await scrollTo(tester, find.byKey(const Key('entry_save')));
      expect(find.textContaining('zwischen 1. und 28.'), findsOneWidget);
      await tester.tap(find.byKey(const Key('entry_save')));
      await tester.pumpAndSettle();
      expect(fake.calls('POST /api/entries'), isEmpty);
    });

    testWidgets('A21 Übersicht: Monatswerte, pausieren, löschen', (tester) async {
      final fake = FakeBackend({
        'PUT /api/entries/*': (o) => FakeBackend.entry(id: 22, kind: 'EXPENSE', title: 'Miete', status: 'PAUSED'),
        'DELETE /api/entries/*': (o) => const FakeResponse(204),
      });
      await pumpScreen(tester, const EntriesScreen(), backend: fake);
      expect(find.text('2.650,00 €'), findsWidgets);
      expect(find.text('1.750,00 €'), findsOneWidget);
      expect(find.text('Regelmäßig'), findsOneWidget);
      expect(find.text('Einmalig'), findsOneWidget);
      expect(find.textContaining('nächste: 01.11.2026'), findsWidgets);
      expect(find.textContaining('pausiert'), findsOneWidget);

      await tester.tap(find.text('Miete'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Pausieren'));
      await tester.pumpAndSettle();
      expect(fake.lastBody('PUT /api/entries/*')['paused'], isTrue);
      expect(fake.calls('PUT /api/entries/*').single.path, '/api/entries/22');

      await scrollTo(tester, find.textContaining('Urlaub Mallorca'));
      await tester.tap(find.textContaining('Urlaub Mallorca'));
      await tester.pumpAndSettle();
      expect(find.text('Betrag ändern'), findsNothing);   // schon gebucht und erledigt
      await tester.tap(find.text('Löschen'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Bereits gebuchte Beträge bleiben'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilledButton, 'Löschen'));
      await tester.pumpAndSettle();
      expect(fake.calls('DELETE /api/entries/*').single.path, '/api/entries/24');
    });

    testWidgets('A22 Startseite: Knöpfe für Einnahme und Ausgabe', (tester) async {
      await pumpScreen(tester, const AccountHomeScreen(), settle: false);
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      await tester.tap(find.byKey(const Key('home_add_income')));
      await tester.pumpAndSettle();
      expect(find.text('ROUTE:/eintraege/neu?art=einnahme'), findsOneWidget);
    });
  });

  test('FakeBackend liefert 404 für unbekannte Routen', () async {
    final dio = Dio()..httpClientAdapter = FakeBackend();
    expect(() => dio.get<dynamic>('http://x/api/unbekannt'), throwsA(isA<DioException>()));
  });
}
