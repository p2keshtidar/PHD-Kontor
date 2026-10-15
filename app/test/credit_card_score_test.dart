import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/creditcard/credit_card_section.dart';
import 'package:girokonto_app/features/health/health_score_card.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

Widget _scroll(Widget child) => ListView(children: [child]);

/// Kreditkarte (Simulation) und Finanz-Check: CS01–CS05.
void main() {
  testWidgets('CS01 ohne Karte: einrichten mit Limit', (tester) async {
    final fake = await pumpScreen(tester, _scroll(const CreditCardSection()), backend: FakeBackend({
      'GET /api/credit-card': (_) => FakeBackend.creditCard(exists: false),
      'POST /api/credit-card': (_) => FakeResponse(201, FakeBackend.creditCard()),
    }));
    expect(find.textContaining('keine echte Karte'), findsOneWidget);
    await tester.tap(find.byKey(const Key('cc_create')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('cc_limit_field')), '1500');
    await tester.tap(find.byKey(const Key('cc_limit_save')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/credit-card'), {'limitCents': 150000});
  });

  testWidgets('CS02 Karte mit offenen Umsätzen, Auslandseinsatz und Abrechnung', (tester) async {
    await pumpScreen(tester, _scroll(const CreditCardSection()));
    expect(find.text('Offen: 407,00 € · frei: 593,00 € von 1.000,00 €'), findsOneWidget);
    expect(find.text('Hotel Roma'), findsOneWidget);
    expect(find.textContaining('+ 7,00 € Auslandseinsatz'), findsOneWidget);
    expect(find.byKey(const Key('cc_statement_2026-09')), findsOneWidget);
    expect(find.text('vom Girokonto eingezogen'), findsOneWidget);
  });

  testWidgets('CS03 sperren und Demo-Zahlung in Fremdwährung', (tester) async {
    final fake = await pumpScreen(tester, _scroll(const CreditCardSection()), backend: FakeBackend({
      'POST /api/credit-card/lock': (_) => FakeBackend.creditCard(status: 'BLOCKED'),
      'POST /api/credit-card/payment': (_) =>
          {'approved': true, 'message': 'Zahlung erfolgreich (Simulation)', 'amountCents': 2000, 'feeCents': 35},
    }));
    await tester.tap(find.byKey(const Key('cc_lock')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/credit-card/lock'), hasLength(1));
    await tester.tap(find.byKey(const Key('cc_pay')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('cc_pay_merchant')), 'Café Paris');
    await tester.enterText(find.byKey(const Key('cc_pay_amount')), '20');
    await tester.tap(find.byKey(const Key('cc_pay_foreign')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('cc_pay_submit')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/credit-card/payment'),
        {'merchant': 'Café Paris', 'amountCents': 2000, 'foreignCurrency': true});
    expect(find.text('Zahlung erfolgreich (Simulation)'), findsOneWidget);
  });

  testWidgets('CS04 Finanz-Check mit Bestandteilen, Tipp und Hinweis „keine SCHUFA“', (tester) async {
    await pumpScreen(tester, _scroll(const HealthScoreCard()));
    expect(find.byKey(const Key('health_score_value')), findsOneWidget);
    expect(find.text('62'), findsOneWidget);
    expect(find.text('Ausbaufähig'), findsOneWidget);
    expect(find.text('Notgroschen · 1,8 Monate'), findsOneWidget);
    expect(find.text('15/25'), findsOneWidget);
    expect(find.textContaining('keine SCHUFA-Auskunft'), findsOneWidget);
  });

  testWidgets('CS05 Bildschirmleser: Punkte als Text', (tester) async {
    final semantics = tester.ensureSemantics();
    await pumpScreen(tester, _scroll(const HealthScoreCard()));
    expect(find.bySemanticsLabel('Finanz-Check: 62 von 100 Punkten'), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('CS06 Finanz-Check erst nach Einwilligung', (tester) async {
    var consented = false;
    final fake = await pumpScreen(tester, _scroll(const HealthScoreCard()), backend: FakeBackend({
      'GET /api/health-score': (_) => consented
          ? FakeBackend.healthScore()
          : {
              ...FakeBackend.healthScore(),
              'consented': false, 'score': 0, 'level': 'NICHT_AKTIVIERT', 'levelLabel': 'Nicht aktiviert',
              'components': [], 'tips': [],
            },
      'POST /api/health-score/consent': (_) {
        consented = true;
        return FakeResponse(200, FakeBackend.healthScore());
      },
    }));
    expect(find.byKey(const Key('health_consent_text')), findsOneWidget);
    expect(find.byKey(const Key('health_score_value')), findsNothing);
    await tester.tap(find.byKey(const Key('health_consent')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/health-score/consent'), hasLength(1));
    expect(find.byKey(const Key('health_score_value')), findsOneWidget);
  });
}
