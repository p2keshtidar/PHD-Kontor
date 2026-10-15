import 'dart:ui' show SemanticsFlag;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/assistant/assistant_screen.dart';
import 'package:girokonto_app/features/contracts/contracts_screen.dart';
import 'package:girokonto_app/features/groups/group_screen.dart';
import 'package:girokonto_app/features/groups/groups_screen.dart';
import 'package:girokonto_app/features/market/applications_screen.dart';

import 'support/fake_backend.dart';
import 'support/harness.dart';

/// Benutzerfreundlichkeit und Barrierefreiheit: BF01–BF04.
void main() {
  final screens = <String, Widget Function()>{
    'Gemeinsame Ausgaben': () => const GroupsScreen(),
    'Gruppe': () => const GroupScreen(groupId: 1),
    'Verträge': () => const ContractsScreen(),
    'Vertrag': () => const ContractDetailScreen(contractId: 5),
  };

  for (final s in screens.entries) {
    testWidgets('BF01 ${s.key}: alle Bedienelemente beschriftet und mindestens 48 × 48', (tester) async {
      final handle = tester.ensureSemantics();
      await pumpScreen(tester, s.value());
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      handle.dispose();
    });
  }

  testWidgets('BF02 Finanzassistent: alle Bedienelemente beschriftet', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester, const AssistantScreen());
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    handle.dispose();
  });

  testWidgets('BF03 Fehlerzustand: Meldung und „Erneut versuchen“ lädt neu', (tester) async {
    var calls = 0;
    final fake = await pumpScreen(tester, const GroupsScreen(), backend: FakeBackend({
      'GET /api/groups': (_) => ++calls == 1
          ? FakeResponse(503, {'code': 'UNAVAILABLE', 'message': 'Server gerade nicht erreichbar.'})
          : <Object>[],
    }));
    expect(find.byKey(const Key('error_message')), findsOneWidget);
    await tester.tap(find.byKey(const Key('error_retry')));
    await tester.pumpAndSettle();
    expect(fake.calls('GET /api/groups'), hasLength(2));
    expect(find.byKey(const Key('groups_empty')), findsOneWidget);
  });

  testWidgets('BF04 Fehlerzustand wird Bildschirmlesern sofort angesagt', (tester) async {
    final handle = tester.ensureSemantics();
    await pumpScreen(tester, const ApplicationsScreen(), backend: FakeBackend({
      'GET /api/market/applications': (_) => FakeResponse(503, {'code': 'UNAVAILABLE', 'message': 'Server gerade nicht erreichbar.'}),
    }));
    final node = tester.getSemantics(find.byKey(const Key('error_message')));
    expect(node.hasFlag(SemanticsFlag.isLiveRegion), isTrue);
    handle.dispose();
  });
}
