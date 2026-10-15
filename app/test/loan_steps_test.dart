import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/loans/loan_application_screen.dart';
import 'package:girokonto_app/features/loans/loan_detail_screen.dart';
import 'package:girokonto_app/features/loans/loan_models.dart';
import 'package:girokonto_app/features/loans/loan_offer_screen.dart';
import 'package:girokonto_app/features/loans/loan_planner_screen.dart';
import 'package:girokonto_app/l10n/l10n.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 200, scrollable: list);
  await tester.pumpAndSettle();
}

SelectedOffer _selected() {
  final r = OfferResult.fromJson(FakeBackend.offerResult());
  return SelectedOffer(r, r.offers.first);
}

/// Kreditvergleich mit mehreren Anbietern und getrennten Schritten: LV01–LV09.
void main() {
  testWidgets('LV01 Vergleich mit Bonität und Filtern; Kennzeichnung, Referenzzins, Anbieter', (tester) async {
    final fake = await pumpScreen(tester, const LoanPlannerScreen(), backend: FakeBackend({
      'POST /api/loans/offers': (_) => FakeResponse(200, FakeBackend.offerResult(hidden: 1)),
    }));
    await scrollTo(tester, find.byKey(const Key('loan_class_MITTEL')));
    await tester.tap(find.byKey(const Key('loan_class_MITTEL')));
    await tester.pumpAndSettle();
    await scrollTo(tester, find.byKey(const Key('loan_filters')));
    await tester.tap(find.byKey(const Key('loan_filters')));
    await tester.pumpAndSettle();
    await scrollTo(tester, find.byKey(const Key('loan_filter_rate')));
    await tester.enterText(find.byKey(const Key('loan_filter_rate')), '300');
    await scrollTo(tester, find.byKey(const Key('loan_filter_eligible')));
    await tester.tap(find.byKey(const Key('loan_filter_eligible')));
    await tester.pumpAndSettle();
    await scrollTo(tester, find.text('Angebote vergleichen'));
    await tester.tap(find.text('Angebote vergleichen'));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/loans/offers');
    expect(body['creditClass'], 'MITTEL');
    expect(body['maxMonthlyRateCents'], 30000);
    expect(body['onlyEligible'], isTrue);
    expect(body.containsKey('maxTotalCents'), isFalse);

    await scrollTo(tester, find.byKey(const Key('loan_offer_NORDLICHT')));
    expect(find.text('1 Angebote durch Filter ausgeblendet'), findsOneWidget);
    expect(find.text('Fiktive Bank – unverbindliches Rechenbeispiel'), findsWidgets);
    expect(find.text('5,63 %'), findsOneWidget);   // effektiver Jahreszins
    expect(find.textContaining('3,36 Prozentpunkte unter dem Bundesbank-Durchschnitt'), findsWidgets);
    await scrollTo(tester, find.byKey(const Key('loan_reference')));
    expect(find.textContaining('8,99 %'), findsOneWidget);
    expect(find.textContaining('kein Angebot'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('loan_disclaimer')));
    expect(find.textContaining('keine echten Kreditangebote'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('loan_providers')));
    await tester.tap(find.byKey(const Key('loan_providers')));
    await tester.pumpAndSettle();
    expect(find.text('Echte Kreditangebote · Nicht angebunden'), findsOneWidget);
  });

  testWidgets('LV02 Angebot: Anfrage erst mit Einwilligung, dann Weiter zur Anfrage', (tester) async {
    final fake = await pumpScreen(tester, LoanOfferScreen(selected: _selected()), backend: FakeBackend({
      'POST /api/loans/schedule': (_) => FakeResponse(200, [
            {'month': 1, 'dueDate': '2026-11-13', 'rateCents': 23252, 'interestCents': 4575, 'principalCents': 18677,
             'remainingCents': 981323, 'paid': false, 'extraCents': 0},
          ]),
      'POST /api/loans/applications': (_) => FakeResponse(201, FakeBackend.loanApplication()),
    }));
    expect(find.byKey(const Key('loan_simulation_hint')), findsOneWidget);
    expect(find.text('5,63 %'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('loan_request')));
    expect(find.text('Keine Bearbeitungsgebühr'), findsNothing);   // als Aufzählung "• …"
    expect(find.text('• Keine Bearbeitungsgebühr'), findsOneWidget);
    expect(tester.widget<FilledButton>(find.byKey(const Key('loan_request'))).onPressed, isNull);
    await tester.tap(find.byKey(const Key('loan_consent')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('loan_request')));
    await tester.pumpAndSettle();
    final body = fake.lastBody('POST /api/loans/applications');
    expect(body['bank'], 'NORDLICHT');
    expect(body['consent'], isTrue);
    expect(body['creditClass'], 'MITTEL');
    expect(body['principalCents'], 1000000);
    expect(find.text('ROUTE:/kredit/anfrage/51'), findsOneWidget);
  });

  testWidgets('LV03 Zusage: Vertrag erst nach Bestätigung, dann zum Kredit', (tester) async {
    final fake = await pumpScreen(tester, const LoanApplicationScreen(applicationId: 51), backend: FakeBackend({
      'POST /api/loans/applications/*/accept': (_) => FakeResponse(201, FakeBackend.loanDetail(status: 'CONTRACTED')),
    }));
    expect(find.text('Zusage'), findsOneWidget);
    expect(find.textContaining('Noch kein Vertrag'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('loan_accept')));
    expect(tester.widget<FilledButton>(find.byKey(const Key('loan_accept'))).onPressed, isNull);
    await tester.tap(find.byKey(const Key('loan_confirm')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('loan_accept')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/loans/applications/*/accept'), {'confirm': true});
    expect(find.text('ROUTE:/kredit/7'), findsOneWidget);
  });

  testWidgets('LV04 Ablehnung: Grund sichtbar, kein Vertrag möglich', (tester) async {
    await pumpScreen(tester, const LoanApplicationScreen(applicationId: 52), backend: FakeBackend({
      'GET /api/loans/applications/*': (_) => FakeBackend.loanApplication(id: 52, status: 'DECLINED'),
    }));
    expect(find.text('Abgelehnt'), findsOneWidget);
    expect(find.textContaining('Rate zu hoch'), findsOneWidget);
    expect(find.byKey(const Key('loan_accept')), findsNothing);
  });

  testWidgets('LV05 Anfrage zurückziehen', (tester) async {
    final fake = await pumpScreen(tester, const LoanApplicationScreen(applicationId: 51), backend: FakeBackend({
      'POST /api/loans/applications/*/withdraw': (_) => FakeResponse(200, FakeBackend.loanApplication(status: 'WITHDRAWN')),
    }));
    await scrollTo(tester, find.byKey(const Key('loan_withdraw')));
    await tester.tap(find.byKey(const Key('loan_withdraw')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/loans/applications/*/withdraw').single.path, '/api/loans/applications/51/withdraw');
  });

  testWidgets('LV06 Vertrag geschlossen: Auszahlung anfordern', (tester) async {
    final fake = await pumpScreen(tester, const LoanDetailScreen(loanId: 7), backend: FakeBackend({
      'GET /api/loans/*': (_) => FakeBackend.loanDetail(status: 'CONTRACTED'),
      'POST /api/loans/*/payout': (_) => FakeResponse(200, FakeBackend.loanDetail()),
    }));
    expect(find.text('Vertrag geschlossen – Auszahlung ausstehend'), findsOneWidget);
    expect(find.byKey(const Key('loan_extra_payment')), findsNothing);   // Sondertilgung erst nach der Auszahlung
    await scrollTo(tester, find.byKey(const Key('loan_payout')));
    await tester.tap(find.byKey(const Key('loan_payout')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/loans/*/payout').single.path, '/api/loans/7/payout');
    expect(find.text('20.000,00 € aufs Girokonto ausgezahlt'), findsOneWidget);
  });

  testWidgets('LV07 Vertrag vor der Auszahlung widerrufen', (tester) async {
    final fake = await pumpScreen(tester, const LoanDetailScreen(loanId: 7), backend: FakeBackend({
      'GET /api/loans/*': (_) => FakeBackend.loanDetail(status: 'CONTRACTED'),
      'POST /api/loans/*/cancel': (_) => FakeResponse(200, FakeBackend.loanDetail(status: 'CANCELLED')),
    }));
    await scrollTo(tester, find.byKey(const Key('loan_cancel')));
    await tester.tap(find.byKey(const Key('loan_cancel')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Es wurde noch kein Geld ausgezahlt'), findsOneWidget);
    await tester.tap(find.byKey(const Key('loan_cancel_confirm')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/loans/*/cancel').single.path, '/api/loans/7/cancel');
  });

  testWidgets('LV08 Englisch: Schritte und Hinweise übersetzt', (tester) async {
    L10n.language.value = 'en';
    await pumpScreen(tester, const LoanApplicationScreen(applicationId: 51));
    expect(find.text('1. Compare'), findsOneWidget);
    expect(find.text('3. Contract'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('loan_accept')));
    expect(find.text('Conclude contract'), findsOneWidget);
  });

  test('LV09 Modelle: neue Felder und ältere Antworten', () {
    final r = OfferResult.fromJson(FakeBackend.offerResult(hidden: 2));
    expect(r.hiddenCount, 2);
    expect(r.creditClass, 'MITTEL');
    expect(r.referenceRates.single.ratePercent, 8.99);
    expect(r.providers.last.ok, isFalse);
    expect(r.offers.first.effectiveRate, 5.63);
    expect(r.offers.first.conditions, contains('Keine Bearbeitungsgebühr'));
    expect(r.offers.last.eligible, isFalse);
    final old = OfferResult.fromJson({
      'purpose': 'AUTO', 'purposeLabel': 'Auto', 'principalCents': 1, 'equityCents': 0, 'termMonths': 12,
      'hasData': false, 'availableCents': 0, 'maxRateCents': 0, 'maxPrincipalCents': 0,
      'offers': [
        {'bank': 'X', 'bankName': 'X', 'interestRate': 5.0, 'monthlyRateCents': 1, 'totalInterestCents': 0,
         'totalCostCents': 1, 'affordable': false, 'declined': false, 'recommended': false},
      ],
    });
    expect(old.offers.single.simulation, isTrue);
    expect(old.referenceRates, isEmpty);
    final app = LoanApplication.fromJson(FakeBackend.loanApplication());
    expect(app.approved, isTrue);
    expect(app.validUntil, DateTime(2026, 10, 27));
    final contracted = Loan.fromJson(FakeBackend.loan(status: 'CONTRACTED'));
    expect(contracted.isContracted, isTrue);
    expect(contracted.isActive, isFalse);
    expect(creditClasses.keys, ['SEHR_GUT', 'GUT', 'MITTEL', 'SCHWACH']);
  });
}
