// Ende-zu-Ende-Test gegen ein laufendes Backend: läuft im Browser, im Android-Emulator
// und im iOS-Simulator (siehe .github/workflows/e2e.yml).
//
// Lokal: Backend starten, dann z. B.
//   flutter test integration_test/app_test.dart -d <Gerät>
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/main.dart' as app;
import 'package:integration_test/integration_test.dart';

/// Pumpt, bis [finder] etwas findet (statt pumpAndSettle, weil Ladeanzeigen und
/// die Live-Verbindung dauerhaft Frames erzeugen können).
Future<void> pumpUntil(WidgetTester tester, Finder finder, {Duration timeout = const Duration(seconds: 40)}) async {
  final end = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(end)) {
    await tester.pump(const Duration(milliseconds: 200));
    if (finder.evaluate().isNotEmpty) return;
  }
  throw TestFailure('Nicht gefunden nach ${timeout.inSeconds} s: $finder');
}

Future<void> pumpFor(WidgetTester tester, [int ms = 800]) async {
  for (var i = 0; i < ms ~/ 100; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> enter(WidgetTester tester, String label, String text) async {
  final field = find.widgetWithText(TextFormField, label);
  await tester.ensureVisible(field);
  await tester.enterText(field, text);
  await tester.pump();
}

Finder navItem(String label) =>
    find.descendant(of: find.byType(NavigationBar), matching: find.text(label));

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Registrieren, Startseite, alle Bereiche, Beispielmonate', (tester) async {
    final stamp = DateTime.now().millisecondsSinceEpoch;
    app.main();

    // Login-Seite -> Registrierung
    await pumpUntil(tester, find.text('Willkommen zurück'));
    await tester.tap(find.text('Noch kein Konto? Jetzt registrieren'));
    await pumpUntil(tester, find.widgetWithText(TextFormField, 'Vorname'));

    await enter(tester, 'Vorname', 'Erika');
    await enter(tester, 'Nachname', 'Test');
    await enter(tester, 'E-Mail', 'e2e-$stamp@example.org');
    await enter(tester, 'Telefonnummer', '0160${(stamp % 10000000).toString().padLeft(7, '0')}');

    // Geburtsdatum über den Kalender (Vorauswahl: vor 20 Jahren)
    final birth = find.widgetWithText(TextFormField, 'Geburtsdatum');
    await tester.ensureVisible(birth);
    await tester.tap(birth);
    await pumpUntil(tester, find.text('OK'));
    await tester.tap(find.text('OK'));
    await pumpFor(tester);

    await enter(tester, 'Passwort', 'geheim12345');
    await enter(tester, 'Passwort wiederholen', 'geheim12345');
    final submit = find.widgetWithText(FilledButton, 'Konto eröffnen');
    await tester.ensureVisible(submit);
    await tester.tap(submit);

    // Startseite mit 1.000 € Startguthaben
    await pumpUntil(tester, find.text('Hallo Erika!'));
    await pumpUntil(tester, find.text('1.000,00 €'));
    expect(find.text('Girokonto'), findsWidgets);

    // Bereich Karte
    await tester.tap(navItem('Karte'));
    await pumpUntil(tester, find.text('Projektbank'));
    expect(find.textContaining('••••'), findsOneWidget);

    // Bereich Überweisen
    await tester.tap(navItem('Überweisen'));
    await pumpUntil(tester, find.text('Weiter'));
    expect(find.text('Adressbuch'), findsWidgets);

    // Bereich Sparen -> Beispielmonate erzeugen -> Überblick erscheint
    await tester.tap(navItem('Sparen'));
    await pumpUntil(tester, find.text('Beispielmonate erzeugen'));
    await tester.tap(find.text('Beispielmonate erzeugen').first);
    await pumpUntil(tester, find.text('Erzeugen'));
    await tester.tap(find.text('Erzeugen'));
    await pumpUntil(tester, find.text('Jeden Monat übrig'), timeout: const Duration(seconds: 60));

    // Kreditplaner öffnen und Angebote vergleichen
    await tester.tap(find.text('Kredit'));
    await pumpUntil(tester, find.text('Angebote vergleichen'));
    await tester.ensureVisible(find.text('Angebote vergleichen'));
    await pumpFor(tester, 400);
    await tester.tap(find.text('Angebote vergleichen'));
    await pumpFor(tester, 3000);
    final list = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
    await tester.scrollUntilVisible(find.text('Nordlicht Kredit'), 300, scrollable: list, maxScrolls: 30);
    expect(find.text('Nordlicht Kredit'), findsOneWidget);
  });
}
