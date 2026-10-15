import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/groups/group_screen.dart';
import 'package:girokonto_app/features/groups/groups_screen.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Gemeinsame Ausgaben (WG): G01–G05.
void main() {
  testWidgets('G01 Gruppenliste mit eigenem Saldo, antippen öffnet die Gruppe', (tester) async {
    await pumpScreen(tester, const GroupsScreen());
    expect(find.text('WG Berger'), findsOneWidget);
    expect(find.text('Du schuldest 38,33 €'), findsOneWidget);
    await tester.tap(find.text('WG Berger'));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/gruppen/1'), findsOneWidget);
  });

  testWidgets('G02 Schuldner gleicht per Überweisung aus (mit Idempotency-Key)', (tester) async {
    final fake = await pumpScreen(tester, const GroupScreen(groupId: 1), backend: FakeBackend({
      'POST /api/groups/1/settlements': (_) => FakeBackend.group(),
    }));
    expect(find.byKey(const Key('group_my_balance')), findsOneWidget);
    expect(find.text('Du schuldest 38,33 €'), findsOneWidget);
    expect(find.text('Ben Muster → Anna Muster: 38,33 €'), findsOneWidget);
    expect(find.byKey(const Key('group_cash_3')), findsNothing);
    await tester.tap(find.byKey(const Key('group_settle_2')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Projektbank (Spielgeld)'), findsWidgets);
    await tester.tap(find.byKey(const Key('group_settle_confirm')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/groups/1/settlements'), {'toUserId': 2, 'amountCents': 3833});
    expect(fake.calls('POST /api/groups/1/settlements').single.headers['Idempotency-Key'], isNotEmpty);
  });

  testWidgets('G03 Ausgabe eintragen: nur auf Beteiligte aufteilen', (tester) async {
    final fake = await pumpScreen(tester, const GroupScreen(groupId: 1), backend: FakeBackend({
      'POST /api/groups/1/expenses': (_) => FakeBackend.group(),
    }));
    await tester.tap(find.byKey(const Key('group_add_expense')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('expense_description')), 'Pizza');
    await tester.enterText(find.byKey(const Key('expense_amount')), '30');
    await tester.tap(find.byKey(const Key('expense_participant_2')));
    await tester.pumpAndSettle();
    expect(find.text('Je Person etwa 15,00 €'), findsOneWidget);
    await tester.tap(find.byKey(const Key('expense_save')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/groups/1/expenses'),
        {'description': 'Pizza', 'amountCents': 3000, 'payerId': 3, 'participants': [3, 4]});
    expect(find.text('Ausgabe eingetragen'), findsOneWidget);
  });

  testWidgets('G04 Empfänger bestätigt Barzahlung', (tester) async {
    final fake = await pumpScreen(tester, const GroupScreen(groupId: 1), backend: FakeBackend({
      'GET /api/groups/*': (_) => FakeBackend.group(me: 2),
      'POST /api/groups/1/settlements/cash': (_) => FakeBackend.group(me: 2),
    }));
    expect(find.text('Du bekommst 106,66 €'), findsOneWidget);
    expect(find.byKey(const Key('group_settle_2')), findsNothing);
    await tester.tap(find.byKey(const Key('group_cash_4')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('group_cash_confirm')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/groups/1/settlements/cash'), {'fromUserId': 4, 'amountCents': 6833});
  });

  testWidgets('G05 ohne Gruppen: anlegen öffnet die neue Gruppe', (tester) async {
    final fake = await pumpScreen(tester, const GroupsScreen(), backend: FakeBackend({
      'GET /api/groups': (_) => [],
      'POST /api/groups': (_) => FakeResponse(201, FakeBackend.group(me: 2)),
    }));
    expect(find.byKey(const Key('groups_empty')), findsOneWidget);
    await tester.tap(find.byKey(const Key('groups_create')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('group_name_field')), 'WG Berger');
    await tester.tap(find.byKey(const Key('group_name_save')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/groups'), {'name': 'WG Berger'});
    expect(find.text('ROUTE:/gruppen/1'), findsOneWidget);
  });
}
