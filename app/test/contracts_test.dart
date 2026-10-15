import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/contracts/contracts_screen.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Verträge & Dokumente: CT01–CT05.
void main() {
  testWidgets('CT01 Übersicht: Kosten, Frist bald, Vertrag öffnen', (tester) async {
    await pumpScreen(tester, const ContractsScreen());
    expect(find.text('29,99 € pro Monat'), findsOneWidget);
    expect(find.text('359,88 € pro Jahr'), findsOneWidget);
    expect(find.byKey(const Key('contracts_due_soon')), findsOneWidget);
    expect(find.textContaining('Kündigen bis 24.10.2026'), findsOneWidget);
    expect(find.textContaining('Kündigen musst du selbst'), findsOneWidget);
    await tester.tap(find.text('Handy'));
    await tester.pumpAndSettle();
    expect(find.text('ROUTE:/vertraege/5'), findsOneWidget);
  });

  testWidgets('CT02 Vertrag anlegen mit Laufzeit, Frist und Erinnerung', (tester) async {
    final fake = await pumpScreen(tester, const ContractsScreen(), size: const Size(400, 1400), backend: FakeBackend({
      'POST /api/contracts': (_) => FakeResponse(201, FakeBackend.contract()),
    }));
    await tester.tap(find.byKey(const Key('contracts_add')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('contract_name')), 'Fitnessstudio');
    await tester.enterText(find.byKey(const Key('contract_cost')), '29,90');
    await tester.enterText(find.byKey(const Key('contract_start')), '01.01.2026');
    await tester.enterText(find.byKey(const Key('contract_min_term')), '12');
    await tester.enterText(find.byKey(const Key('contract_renewal')), '12');
    await tester.enterText(find.byKey(const Key('contract_notice')), '3');
    await tester.enterText(find.byKey(const Key('contract_reminder')), '7');
    await tester.tap(find.byKey(const Key('contract_save')));
    await tester.pumpAndSettle();
    expect(fake.lastBody('POST /api/contracts'), {
      'name': 'Fitnessstudio', 'provider': null, 'kind': 'HANDY_INTERNET', 'costCents': 2990, 'billing': 'MONTHLY',
      'startDate': '2026-01-01', 'minTermMonths': 12, 'renewalMonths': 12, 'noticeValue': 3, 'noticeUnit': 'MONTHS',
      'reminderDays': 7, 'note': null,
    });
    expect(find.byKey(const Key('contracts_summary')), findsOneWidget);   // zurück in der Übersicht
  });

  testWidgets('CT03 Ungültiges Datum wird nicht gesendet', (tester) async {
    final fake = await pumpScreen(tester, const ContractsScreen(), size: const Size(400, 1400));
    await tester.tap(find.byKey(const Key('contracts_add')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('contract_name')), 'Strom');
    await tester.enterText(find.byKey(const Key('contract_cost')), '80');
    await tester.enterText(find.byKey(const Key('contract_start')), '31.02.2026');
    await tester.tap(find.byKey(const Key('contract_save')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('contract_error')), findsOneWidget);
    expect(fake.calls('POST /api/contracts'), isEmpty);
  });

  testWidgets('CT04 Detail: Fristen, Dokument, Kündigung vermerken', (tester) async {
    final fake = await pumpScreen(tester, const ContractDetailScreen(contractId: 5), size: const Size(400, 1200),
        backend: FakeBackend({
          'POST /api/contracts/5/cancel': (_) => FakeBackend.contract(status: 'CANCELLED'),
          'DELETE /api/documents/3': (_) => FakeResponse(204, null),
        }));
    expect(find.byKey(const Key('contract_hint')), findsOneWidget);
    expect(find.text('24.10.2026'), findsOneWidget);
    expect(find.text('Vertrag 2024'), findsOneWidget);
    await tester.tap(find.byKey(const Key('contract_cancel')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Die Kündigung selbst schickst du an den Anbieter'), findsOneWidget);
    await tester.tap(find.byKey(const Key('contract_cancel_confirm')));
    await tester.pumpAndSettle();
    expect(fake.calls('POST /api/contracts/5/cancel'), hasLength(1));
    await tester.tap(find.byKey(const Key('document_delete_3')));
    await tester.pumpAndSettle();
    expect(fake.calls('DELETE /api/documents/3'), hasLength(1));
  });

  testWidgets('CT05 ohne Verträge: Hinweis', (tester) async {
    await pumpScreen(tester, const ContractsScreen(), backend: FakeBackend({
      'GET /api/contracts': (_) => {
            'contracts': [], 'monthlyTotalCents': 0, 'yearlyTotalCents': 0, 'dueSoon': 0,
            'note': 'Die App merkt sich Verträge und Fristen nur. Kündigen musst du selbst beim Anbieter.',
          },
    }));
    expect(find.byKey(const Key('contracts_empty')), findsOneWidget);
    expect(find.byKey(const Key('contracts_due_soon')), findsNothing);
  });
}
