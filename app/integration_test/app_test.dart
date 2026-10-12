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
  // Zur Fehlersuche: alle sichtbaren Texte ausgeben
  final texts = find.byType(Text).evaluate().map((e) => (e.widget as Text).data ?? '').where((t) => t.isNotEmpty);
  // ignore: avoid_print
  print('SICHTBAR: ${texts.take(60).join(' | ')}');
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

/// Scrollt in der aktuellen Liste, bis [target] sichtbar ist (wichtig auf kleinen Bildschirmen,
/// weil ListView unsichtbare Einträge gar nicht erst baut).
Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final scrollable = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  await tester.scrollUntilVisible(target, 250, scrollable: scrollable, maxScrolls: 80);
  await pumpFor(tester, 300);
}

/// Springt in der aktuellen Liste ganz nach oben.
Future<void> scrollToTop(WidgetTester tester) async {
  final scrollable = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  tester.state<ScrollableState>(scrollable).position.jumpTo(0);
  await pumpFor(tester, 300);
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
    await pumpUntil(tester, find.text('Kredit planen'));
    await pumpFor(tester, 1000);
    await scrollTo(tester, find.text('Angebote vergleichen'));
    await tester.tap(find.text('Angebote vergleichen'));
    await pumpFor(tester, 3000);
    await scrollTo(tester, find.text('Nordlicht Kredit'));
    expect(find.text('Nordlicht Kredit'), findsOneWidget);

    // Angebot öffnen und Kredit abschließen
    await tester.tap(find.text('Nordlicht Kredit'));
    await pumpUntil(tester, find.text('Tilgungsplan'));
    await pumpFor(tester, 1500);
    await scrollTo(tester, find.byType(CheckboxListTile));
    await tester.tap(find.byType(CheckboxListTile));
    await pumpFor(tester, 400);
    await scrollTo(tester, find.text('Kredit abschließen'));
    await tester.tap(find.text('Kredit abschließen'));
    await pumpUntil(tester, find.text('Restschuld'), timeout: const Duration(seconds: 30));

    // /K020/ Sondertilgung: Vorschau berechnen und buchen
    await scrollTo(tester, find.byKey(const Key('loan_extra_payment')));
    await tester.tap(find.byKey(const Key('loan_extra_payment')));
    await pumpUntil(tester, find.byKey(const Key('extra_amount')));
    await tester.enterText(find.byKey(const Key('extra_amount')), '1000');
    await tester.pump();
    // Tastatur schließen (verdeckt auf dem Handy sonst die Knöpfe)
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 800);
    await scrollTo(tester, find.byKey(const Key('extra_preview')));
    await tester.tap(find.byKey(const Key('extra_preview')));
    // Die Vorschau erscheint unter dem Knopf – auf kleinen Displays außerhalb des sichtbaren Bereichs
    await pumpUntil(tester, find.byKey(const Key('extra_pay'), skipOffstage: false));
    await scrollTo(tester, find.text('Du sparst Zinsen'));
    await scrollTo(tester, find.byKey(const Key('extra_pay')));
    await tester.tap(find.byKey(const Key('extra_pay')));
    // zurück im Kreditdetail: neue Zeile "Sondertilgungen" mit dem Betrag
    await pumpUntil(tester, find.byKey(const Key('loan_extra_payment'), skipOffstage: false));
    await pumpFor(tester, 1500);
    await scrollToTop(tester);
    await scrollTo(tester, find.text('Sondertilgungen'));
    expect(find.text('1.000,00 €'), findsWidgets);
  });
}
