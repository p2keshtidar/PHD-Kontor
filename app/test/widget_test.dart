import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/auth/login_screen.dart';

void main() {
  testWidgets('Login zeigt Fehler bei leeren Feldern', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: LoginScreen())),
    );

    await tester.tap(find.byKey(const Key('login_submit')));
    await tester.pump();

    expect(find.text('Bitte E-Mail eingeben'), findsOneWidget);
    expect(find.text('Bitte Passwort eingeben'), findsOneWidget);
  });

  testWidgets('Login erkennt ungültige E-Mail', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(child: MaterialApp(home: LoginScreen())),
    );

    await tester.enterText(find.byKey(const Key('login_email')), 'kein-at');
    await tester.enterText(find.byKey(const Key('login_password')), 'irgendwas');
    await tester.tap(find.byKey(const Key('login_submit')));
    await tester.pump();

    expect(find.text('E-Mail ist ungültig'), findsOneWidget);
  });
}
