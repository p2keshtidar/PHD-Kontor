import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/theme.dart';
import 'package:girokonto_app/features/contracts/contracts_screen.dart';
import 'package:girokonto_app/features/dashboard/dashboard_screen.dart';
import 'package:girokonto_app/features/groups/group_screen.dart';
import 'package:girokonto_app/features/groups/groups_screen.dart';
import 'package:girokonto_app/features/planner/planner_screen.dart';

import 'support/harness.dart';

/// Dunkler Modus: DM01–DM03.
void main() {
  final screens = <String, Widget Function()>{
    'Gemeinsame Ausgaben': () => const GroupsScreen(),
    'Gruppe': () => const GroupScreen(groupId: 1),
    'Verträge': () => const ContractsScreen(),
    'Vertrag': () => const ContractDetailScreen(contractId: 5),
    'Vorhaben planen': () => const PlannerScreen(),
  };

  for (final s in screens.entries) {
    testWidgets('DM01 ${s.key}: dunkel ohne Fehler, Text mit genug Kontrast', (tester) async {
      final handle = tester.ensureSemantics();
      await pumpScreen(tester, s.value(), brightness: Brightness.dark, size: const Size(412, 915));
      expect(tester.takeException(), isNull);
      expect(Theme.of(tester.element(find.byType(Scaffold).first)).brightness, Brightness.dark);
      await expectLater(tester, meetsGuideline(textContrastGuideline));
      handle.dispose();
    });
  }

  testWidgets('DM02 Dashboard dunkel ohne Fehler', (tester) async {
    await pumpScreen(tester, const DashboardScreen(), brightness: Brightness.dark);
    expect(tester.takeException(), isNull);
  });

  testWidgets('DM03 Plus/Warnung: je Modus eigene Farbe mit mindestens 4,5 : 1 Kontrast', (tester) async {
    double lum(Color c) => c.computeLuminance();
    double ratio(Color a, Color b) {
      final x = lum(a), y = lum(b);
      return (x > y ? x + 0.05 : y + 0.05) / (x > y ? y + 0.05 : x + 0.05);
    }

    for (final b in Brightness.values) {
      late BuildContext ctx;
      await tester.pumpWidget(MaterialApp(
        theme: buildTheme(b),
        home: Builder(builder: (c) {
          ctx = c;
          return const SizedBox();
        }),
      ));
      final surface = Theme.of(ctx).colorScheme.surface;
      expect(ratio(positiveColor(ctx), surface), greaterThanOrEqualTo(4.5), reason: 'positiv $b');
      expect(ratio(warningColor(ctx), surface), greaterThanOrEqualTo(4.5), reason: 'Warnung $b');
    }
  });
}
