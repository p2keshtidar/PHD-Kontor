import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/accounts/account.dart';
import 'package:girokonto_app/features/accounts/account_home_screen.dart';
import 'package:girokonto_app/features/subaccounts/subaccount_screen.dart';
import 'package:girokonto_app/l10n/l10n.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Scrollt in der Seitenliste, bis [target] sichtbar ist.
Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

/// Digitale Konten getrennt von Bankkonten: DA01–DA07.
void main() {
  testWidgets('DA01 Startseite: zwei getrennte Bereiche, Girokonto als Simulation gekennzeichnet', (tester) async {
    await pumpScreen(tester, const AccountHomeScreen());
    expect(find.text('Bankkonten'), findsOneWidget);
    expect(find.textContaining('Projektbank (Simulation) · DE89'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('digital_tile_11')));
    expect(find.text('Digitale Konten'), findsOneWidget);
    expect(find.text('Töpfe für Ziele – umgebucht innerhalb der Projektbank, keine eigenen Bankkonten.'), findsOneWidget);
    expect(find.text('noch 1.800,00 € bis 3.000,00 €'), findsOneWidget);
    expect(find.text('Urlaub'), findsOneWidget);   // Zweck
    await tester.tap(find.byKey(const Key('home_digital_info')));
    await tester.pumpAndSettle();
    expect(find.textContaining('keine eigenen Bankkonten und haben keine eigene Einlagensicherung'), findsOneWidget);
  });

  testWidgets('DA02 neues digitales Konto mit Zweck und Ziel', (tester) async {
    final fake = await pumpScreen(tester, const AccountHomeScreen(), backend: FakeBackend({
      'POST /api/subaccounts': (_) => FakeResponse(201, FakeBackend.accounts()[1]),
    }));
    await scrollTo(tester, find.byKey(const Key('home_new_digital')));
    await tester.tap(find.byKey(const Key('home_new_digital')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('digital_name')), 'Neues Auto');
    await tester.tap(find.byKey(const Key('digital_purpose_AUTO')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('digital_target')), '5000');
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('digital_date')), findsOneWidget);   // Datum erst mit Zielbetrag
    await tester.tap(find.byKey(const Key('digital_save')));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/subaccounts');
    expect(body['name'], 'Neues Auto');
    expect(body['purpose'], 'AUTO');
    expect(body['icon'], 'car');   // Symbol folgt dem Zweck
    expect(body['targetCents'], 500000);
    expect(body.containsKey('targetDate'), isFalse);
  });

  testWidgets('DA03 Detail: Fortschritt, nötige Rate, Hinweis zur Verwahrung', (tester) async {
    await pumpScreen(tester, const SubaccountScreen(accountId: 11));
    expect(find.text('40 % von 3.000,00 € · noch 1.800,00 €'), findsOneWidget);
    expect(find.textContaining('nötig ca. 225,00 € pro Monat'), findsOneWidget);
    expect(find.textContaining('kein eigenes Bankkonto und ohne eigene Einlagensicherung'), findsOneWidget);
    final deposit = tester.widget<FilledButton>(find.byKey(const Key('digital_deposit')));
    expect(deposit.onPressed, isNotNull);
  });

  testWidgets('DA04 nur ansehen: Ein- und Auszahlen gesperrt, Rechte sichtbar', (tester) async {
    await pumpScreen(tester, const SubaccountScreen(accountId: 12), backend: FakeBackend({
      'GET /api/subaccounts/*/members': (o) => [
            {'userId': 5, 'name': 'Mia Musterfrau-Langname', 'owner': true, 'you': false, 'role': 'OWNER'},
            {'userId': 1, 'name': 'Erika Mustermann', 'owner': false, 'you': true, 'role': 'VIEW'},
          ],
    }));
    expect(tester.widget<FilledButton>(find.byKey(const Key('digital_deposit'))).onPressed, isNull);
    expect(find.text('Deine Rechte: Nur ansehen'), findsOneWidget);
    expect(find.text('Kein Zielbetrag festgelegt.'), findsOneWidget);
  });

  testWidgets('DA05 Besitzer ändert die Rechte eines Mitglieds', (tester) async {
    final fake = await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: FakeBackend({
      'PUT /api/subaccounts/*/members/*': (_) => const FakeResponse(200, []),
    }));
    await scrollTo(tester, find.byKey(const Key('member_menu_2')));
    expect(find.text('Einzahlen und auszahlen'), findsOneWidget);
    await tester.tap(find.byKey(const Key('member_menu_2')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Nur einzahlen').last);
    await tester.pumpAndSettle();
    final put = fake.calls('PUT /api/subaccounts/*/members/*').single;
    expect(put.path, '/api/subaccounts/11/members/2');
    expect(fake.lastBody('PUT /api/subaccounts/*/members/*'), {'role': 'DEPOSIT'});
  });

  testWidgets('DA06 teilen mit Rolle "nur einzahlen"', (tester) async {
    final fake = await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: FakeBackend({
      'POST /api/subaccounts/*/members': (o) => const FakeResponse(200, []),
    }));
    await scrollTo(tester, find.byKey(const Key('share_add')));
    await tester.tap(find.byKey(const Key('share_add')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('share_recipient')), 'ben@example.org');
    await tester.tap(find.byKey(const Key('share_role_DEPOSIT')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share_confirm')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/subaccounts/*/members'), {'recipient': 'ben@example.org', 'role': 'DEPOSIT'});
  });

  testWidgets('DA07 Englisch: Bereiche und Zweck übersetzt', (tester) async {
    L10n.language.value = 'en';
    await pumpScreen(tester, const AccountHomeScreen());
    expect(find.text('Bank accounts'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('digital_tile_11')));
    expect(find.text('Digital accounts'), findsOneWidget);
    expect(find.text('Holiday'), findsOneWidget);
  });

  test('DA08 Modell: digitale Angaben und ältere Antworten', () {
    final a = Account.fromJson(FakeBackend.accounts()[1]);
    expect(a.digital!.purposeLabel, 'Urlaub');
    expect(a.digital!.hasTarget, isTrue);
    expect(a.digital!.monthlyNeededCents, 22500);
    final foreign = Account.fromJson(FakeBackend.accounts()[2]);
    expect(foreign.digital!.canDeposit, isFalse);
    expect(foreign.digital!.roleLabel, 'Nur ansehen');
    final old = Account.fromJson({'id': 1, 'type': 'SUB', 'name': 'Alt', 'balanceCents': 0});
    expect(old.digital, isNull);
    expect(digitalPurposes.keys, containsAll(['SPARZIEL', 'URLAUB', 'AUTO', 'IMMOBILIE', 'RUECKZAHLUNG', 'WG']));
  });
}
