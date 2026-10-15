import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/subaccounts/subaccount_card_section.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

Widget _scroll(Widget child) => ListView(children: [child]);

/// Demo-Karten für digitale Konten: SC01–SC02.
void main() {
  testWidgets('SC01 Karte vorhanden: Limit, Demo-Zahlung vom digitalen Konto', (tester) async {
    final fake = await pumpScreen(tester, _scroll(const SubaccountCardSection(accountId: 11)), backend: FakeBackend({
      'POST /api/subaccounts/11/card/payment': (_) =>
          {'approved': true, 'message': 'Zahlung erfolgreich', 'amountCents': 1250, 'merchant': 'Bäckerei'},
    }));
    expect(find.text('•••• 4711'), findsOneWidget);
    expect(find.text('Heute bezahlt: 25,00 € von 200,00 €'), findsOneWidget);
    expect(find.textContaining('keine echte Karte'), findsOneWidget);
    await tester.tap(find.byKey(const Key('subcard_pay')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('subcard_merchant')), 'Bäckerei');
    await tester.enterText(find.byKey(const Key('subcard_amount')), '12,50');
    await tester.tap(find.byKey(const Key('subcard_pay_submit')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/subaccounts/11/card/payment'), {'merchant': 'Bäckerei', 'amountCents': 1250});
    expect(find.text('Zahlung erfolgreich'), findsOneWidget);
  });

  testWidgets('SC02 ohne Karte: einrichten mit Tageslimit', (tester) async {
    final fake = await pumpScreen(tester, _scroll(const SubaccountCardSection(accountId: 11)), backend: FakeBackend({
      'GET /api/subaccounts/*/card': (_) => {
            'exists': false, 'last4': null, 'status': null, 'dailyLimitCents': 0, 'spentTodayCents': 0, 'canPay': true,
            'note': 'Demo: virtuelle Karte für dieses digitale Konto – keine echte Karte, Zahlungen nur mit Spielgeld bei Demo-Händlern.',
          },
      'POST /api/subaccounts/11/card': (_) => FakeResponse(201, <String, dynamic>{}),
    }));
    await tester.tap(find.byKey(const Key('subcard_create')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/subaccounts/11/card'), {'dailyLimitCents': 20000});
  });
}
