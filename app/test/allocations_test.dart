import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/accounts/account.dart';
import 'package:girokonto_app/features/allocations/allocation_models.dart';
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

/// Hinweise (SnackBar) schließen, damit sie keine Knöpfe verdecken.
Future<void> clearSnackBars(WidgetTester tester) async {
  tester.state<ScaffoldMessengerState>(find.byType(ScaffoldMessenger).first).clearSnackBars();
  await tester.pumpAndSettle();
}

/// Element im Dialog sichtbar machen und antippen.
Future<void> tapInDialog(WidgetTester tester, Finder f) async {
  await tester.ensureVisible(f);
  await tester.pumpAndSettle();
  await tester.tap(f);
  await tester.pumpAndSettle();
}

/// Sparregeln und Geldzuweisungen im digitalen Konto: AL01–AL09.
void main() {
  testWidgets('AL01 Bereich "Automatisch sparen": Regeln, Prognose, ausstehend, Vorgänge mit Status', (tester) async {
    await pumpScreen(tester, const SubaccountScreen(accountId: 11));
    await scrollTo(tester, find.byKey(const Key('alloc_rule_31')));
    expect(find.text('Automatisch sparen'), findsOneWidget);
    expect(find.text('50,00 € · wöchentlich'), findsOneWidget);
    expect(find.textContaining('Nächste Ausführung: 16.10.2026'), findsOneWidget);
    expect(find.text('150,00 € · monatlich'), findsOneWidget);
    expect(find.textContaining('Nur Vormerkung – das Geld bleibt bei der Bank'), findsOneWidget);
    expect(find.text('Weitere Sparregeln anderer Mitglieder: 1'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('alloc_projection')));
    expect(find.textContaining('Voraussichtlich erreicht am 12.03.2027'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('alloc_run_42')));
    expect(find.textContaining('Fehlgeschlagen'), findsWidgets);
    expect(find.textContaining('Nicht genug Guthaben auf „Girokonto“'), findsOneWidget);
    expect(find.byKey(const Key('alloc_retry_42')), findsOneWidget);
    expect(find.byKey(const Key('alloc_retry_41')), findsNothing);   // ausgeführt: nichts zu wiederholen
    expect(find.text('Ausstehend'), findsOneWidget);
  });

  testWidgets('AL02 Sparregel vom Girokonto anlegen (wöchentlich)', (tester) async {
    final fake = await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: FakeBackend({
      'POST /api/allocations/rules': (_) => FakeResponse(201, FakeBackend.allocationRule()),
    }));
    await scrollTo(tester, find.byKey(const Key('alloc_new')));
    await tester.tap(find.byKey(const Key('alloc_new')));
    await tester.pumpAndSettle();
    expect(find.text('Girokonto · Projektbank (Simulation)'), findsOneWidget);
    expect(find.textContaining('keine echte Überweisung'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('alloc_amount')), '25');
    await tapInDialog(tester, find.byKey(const Key('alloc_interval_WEEKLY')));
    await tester.tap(find.byKey(const Key('alloc_save')));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/allocations/rules');
    expect(body['targetAccountId'], 11);
    expect(body['sourceType'], 'PROJEKTBANK');
    expect(body.containsKey('sourceId'), isFalse);
    expect(body['amountCents'], 2500);
    expect(body['interval'], 'WEEKLY');
    expect(body.containsKey('startDate'), isFalse);   // Start heute (Datum des Servers)
    expect(find.text('Sparregel eingerichtet'), findsOneWidget);
  });

  testWidgets('AL03 verbundenes Bankkonto als Quelle: nur Vormerkung, gesperrte Quelle mit Grund', (tester) async {
    final fake = await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: FakeBackend({
      'POST /api/allocations/rules': (_) => FakeResponse(201, FakeBackend.allocationRule(source: 'EXTERNAL')),
    }));
    await scrollTo(tester, find.byKey(const Key('alloc_new')));
    await tester.tap(find.byKey(const Key('alloc_new')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Die Freigabe für Sandbox-Onlinebank (Testdaten) ist abgelaufen'), findsOneWidget);
    await tapInDialog(tester, find.byKey(const Key('alloc_source_EXTERNAL_5')));
    await tester.ensureVisible(find.textContaining('Es wird kein Geld bewegt'));
    expect(find.textContaining('Es wird kein Geld bewegt'), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('alloc_amount')));
    await tester.enterText(find.byKey(const Key('alloc_amount')), '150');
    await tester.tap(find.byKey(const Key('alloc_save')));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/allocations/rules');
    expect(body['sourceType'], 'EXTERNAL');
    expect(body['sourceId'], 5);
    expect(body['interval'], 'MONTHLY');
    expect(body['amountCents'], 15000);
  });

  testWidgets('AL04 ohne Betrag keine Anfrage; Fehler vom Server wird angezeigt', (tester) async {
    final fake = await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: FakeBackend({
      'POST /api/allocations/rules': (_) => const FakeResponse(400,
          {'code': 'START_IN_PAST', 'message': 'Das Startdatum darf nicht in der Vergangenheit liegen.'}),
    }));
    await scrollTo(tester, find.byKey(const Key('alloc_new')));
    await tester.tap(find.byKey(const Key('alloc_new')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('alloc_save')));
    await tester.pumpAndSettle();
    expect(find.text('Bitte gültigen Betrag eingeben'), findsOneWidget);
    expect(fake.calls('POST /api/allocations/rules'), isEmpty);
    await tester.enterText(find.byKey(const Key('alloc_amount')), '10');
    await tester.tap(find.byKey(const Key('alloc_save')));
    await tester.pumpAndSettle();
    expect(find.text('Das Startdatum darf nicht in der Vergangenheit liegen.'), findsOneWidget);
  });

  testWidgets('AL05 fehlgeschlagenen Vorgang erneut versuchen: Ergebnis wird gemeldet', (tester) async {
    final fake = await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: FakeBackend({
      'POST /api/allocations/runs/*/retry': (_) => FakeResponse(200, FakeBackend.allocationRun(id: 42)),
    }));
    await scrollTo(tester, find.byKey(const Key('alloc_retry_42')));
    await tester.tap(find.byKey(const Key('alloc_retry_42')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/allocations/runs/*/retry').single.path, '/api/allocations/runs/42/retry');
    expect(find.text('Ausgeführt: 50,00 €'), findsOneWidget);
  });

  testWidgets('AL06 Sparregel pausieren und beenden', (tester) async {
    final fake = await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: FakeBackend({
      'POST /api/allocations/rules/*/pause': (_) => FakeResponse(200, FakeBackend.allocationRule(status: 'PAUSED')),
      'DELETE /api/allocations/rules/*': (_) => const FakeResponse(204),
    }));
    await scrollTo(tester, find.byKey(const Key('alloc_menu_31')));
    await tester.tap(find.byKey(const Key('alloc_menu_31')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Pausieren'));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/allocations/rules/*/pause').single.path, '/api/allocations/rules/31/pause');
    await clearSnackBars(tester);
    await scrollTo(tester, find.byKey(const Key('alloc_menu_31')));
    await tester.tap(find.byKey(const Key('alloc_menu_31')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Beenden').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('alloc_end_confirm')));
    await tester.pumpAndSettle();
    expect(fake.calls('DELETE /api/allocations/rules/*').single.path, '/api/allocations/rules/31');
  });

  testWidgets('AL07 Vormerkungen: Betrag sichtbar, aufheben und neu vormerken', (tester) async {
    final fake = await pumpScreen(tester, const SubaccountScreen(accountId: 11), backend: FakeBackend({
      'POST /api/allocations/earmarks/release': (_) => FakeResponse(200, FakeBackend.allocationRun(kind: 'RELEASE')),
      'POST /api/allocations/earmarks': (_) => FakeResponse(201, FakeBackend.allocationRun(kind: 'EARMARK')),
    }));
    await scrollTo(tester, find.byKey(const Key('alloc_release_5')));
    expect(find.text('Vorgemerkt: 150,00 €'), findsOneWidget);
    expect(find.textContaining('150,00 € – das Geld bleibt dort'), findsOneWidget);
    await tester.tap(find.byKey(const Key('alloc_release_5')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/allocations/earmarks/release'), {'targetAccountId': 11, 'externalAccountId': 5});

    await clearSnackBars(tester);
    await scrollTo(tester, find.byKey(const Key('alloc_earmark')));
    await tester.tap(find.byKey(const Key('alloc_earmark')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Es wird keine Zahlung ausgelöst'), findsWidgets);   // Dialog und Erklärung
    await tester.ensureVisible(find.byKey(const Key('alloc_earmark_amount')));
    await tester.enterText(find.byKey(const Key('alloc_earmark_amount')), '40');
    await tester.tap(find.byKey(const Key('alloc_earmark_save')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/allocations/earmarks'),
        {'targetAccountId': 11, 'externalAccountId': 5, 'amountCents': 4000});
  });

  testWidgets('AL08 Englisch: Bereich und Knöpfe übersetzt', (tester) async {
    L10n.language.value = 'en';
    await pumpScreen(tester, const SubaccountScreen(accountId: 11));
    await scrollTo(tester, find.byKey(const Key('alloc_new')));
    expect(find.text('Save automatically'), findsOneWidget);
    expect(find.text('New savings rule'), findsOneWidget);
    expect(find.text('Earmark'), findsWidgets);
  });

  test('AL09 Modelle: vollständige und ältere Antworten', () {
    final a = AccountAllocations.fromJson(FakeBackend.accountAllocations());
    expect(a.rules, hasLength(2));
    expect(a.rules.first.movesMoney, isTrue);
    expect(a.rules.last.sourceLabel, 'Tagesgeld · Sandbox-Hausbank (Testdaten)');
    expect(a.runs.first.canRetry, isTrue);
    expect(a.runs[1].canRetry, isFalse);
    expect(a.earmarkedCents, 15000);
    expect(a.projectedGoalDate, DateTime(2027, 3, 12));
    final empty = AccountAllocations.fromJson({'accountId': 3});
    expect(empty.rules, isEmpty);
    expect(empty.canAllocate, isFalse);
    final sources = FakeBackend.allocationSources().map(AllocationSource.fromJson).toList();
    expect(sources.first.isProjektbank, isTrue);
    expect(sources.last.usable, isFalse);
    expect(allocationIntervals.keys, ['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY']);
    final d = Account.fromJson({
      'id': 1, 'type': 'SUB', 'name': 'X', 'balanceCents': 100,
      'digital': {...FakeBackend.digital('SPARZIEL'), 'earmarkedCents': 500, 'savedCents': 600},
    }).digital!;
    expect(d.earmarkedCents, 500);
    expect(d.savedCents, 600);
  });
}
