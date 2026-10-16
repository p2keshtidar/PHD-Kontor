// Ende-zu-Ende-Test gegen ein laufendes Backend: läuft im Browser, im Android-Emulator
// und im iOS-Simulator (siehe .github/workflows/e2e.yml).
//
// Lokal: Backend starten, dann z. B.
//   flutter test integration_test/app_test.dart -d <Gerät>
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/config.dart';
import 'package:girokonto_app/core/key_value_store.dart';
import 'package:girokonto_app/core/messenger.dart';
import 'package:girokonto_app/core/token_storage.dart';
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

/// Text in ein Feld eintragen und prüfen, dass er wirklich drinsteht (sonst wiederholen).
Future<void> enterKey(WidgetTester tester, Key key, String text) async {
  for (var i = 0; i < 5; i++) {
    await tester.enterText(find.byKey(key), text);
    await pumpFor(tester, 300);
    final field = tester.widget<EditableText>(find.descendant(of: find.byKey(key), matching: find.byType(EditableText)));
    if (field.controller.text == text) return;
  }
  throw TestFailure('Eingabe "$text" kam im Feld $key nicht an');
}

/// Scrollt in der aktuellen Liste, bis [target] sichtbar ist (wichtig auf kleinen Bildschirmen,
/// weil ListView unsichtbare Einträge gar nicht erst baut).
Future<void> scrollTo(WidgetTester tester, Finder target) async {
  final lists = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable));
  if (lists.evaluate().isEmpty) {
    // Seite ohne ListView (z. B. Anmeldung mit SingleChildScrollView)
    await pumpUntil(tester, target);
    await tester.ensureVisible(target.first);
  } else {
    try {
      await tester.scrollUntilVisible(target, 250, scrollable: lists.first, maxScrolls: 80);
    } catch (_) {
      // Zur Fehlersuche: was ist zu sehen?
      final texts = find.byType(Text).evaluate().map((e) => (e.widget as Text).data ?? '').where((t) => t.isNotEmpty);
      // ignore: avoid_print
      print('SICHTBAR (scrollTo $target): ${texts.take(60).join(' | ')}');
      rethrow;
    }
  }
  await pumpFor(tester, 300);
}

/// Tippt einen Knopf an. Vorher werden Hinweisleisten (Live-Meldungen) geschlossen,
/// die sonst unten über dem Knopf liegen und den Tipp abfangen können.
Future<void> tapButton(WidgetTester tester, Finder button) async {
  rootMessengerKey.currentState?.clearSnackBars();
  await pumpFor(tester, 500);
  await scrollTo(tester, button);
  await pumpUntil(tester, button);
  await tester.tap(button.first);
  await tester.pump();
}

/// Zurück zur vorigen Seite. (tester.pageBack() sucht den Tooltip "Back" – mit deutscher Oberfläche heißt er
/// "Zurück", darum direkt über den Zurück-Knopf bzw. den Navigator.)
Future<void> goBack(WidgetTester tester) async {
  final back = find.byType(BackButton);
  if (back.evaluate().isNotEmpty) {
    await tester.tap(back.last);
  } else {
    await tester.state<NavigatorState>(find.byType(Navigator).last).maybePop();
  }
  await tester.pump();
}

/// Springt in der aktuellen Liste ganz nach oben.
Future<void> scrollToTop(WidgetTester tester) async {
  final scrollable = find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first;
  tester.state<ScrollableState>(scrollable).position.jumpTo(0);
  await pumpFor(tester, 300);
}

Finder navItem(String label) =>
    find.descendant(of: find.byType(NavigationBar), matching: find.text(label));

/// Zweiter Nutzer direkt über die Server-Schnittstelle (ohne Oberfläche).
class ApiUser {
  ApiUser._(this.dio, this.email);
  final Dio dio;
  final String email;

  static Future<ApiUser> register(String firstName, String email, String phone) async {
    final dio = Dio(BaseOptions(baseUrl: AppConfig.apiBaseUrl, contentType: Headers.jsonContentType));
    final r = await dio.post<Map<String, dynamic>>('/api/auth/register', data: {
      'firstName': firstName, 'lastName': 'Zwei', 'email': email, 'phone': phone,
      'birthDate': '1995-03-01', 'password': 'geheim12345',
    });
    dio.options.headers['Authorization'] = 'Bearer ${r.data!['accessToken']}';
    return ApiUser._(dio, email);
  }

  Future<int> mainBalance() async {
    final r = await dio.get<List<dynamic>>('/api/accounts');
    final main = r.data!.cast<Map<String, dynamic>>().firstWhere((a) => a['type'] == 'MAIN');
    return main['balanceCents'] as int;
  }

  Future<void> transfer(String to, int cents, String text) => dio.post<dynamic>('/api/transfers',
      data: {'recipient': to, 'amountCents': cents, 'description': text},
      options: Options(headers: {'Idempotency-Key': 'e2e-${DateTime.now().microsecondsSinceEpoch}'}));

  Future<List<dynamic>> standingOrders() async => (await dio.get<List<dynamic>>('/api/standing-orders')).data!;
}

bool _surfaceConverted = false;

/// Bildschirmfoto für den Versions-Vergleich (Android/iOS). Die Bilddaten werden als Base64 in
/// das Testprotokoll geschrieben; der Workflow setzt sie wieder zu PNG-Dateien zusammen.
/// Fehler hier lassen den Test nicht scheitern.
Future<void> snap(WidgetTester tester, String name) async {
  if (kIsWeb) return;
  try {
    final binding = IntegrationTestWidgetsFlutterBinding.instance;
    if (defaultTargetPlatform == TargetPlatform.android && !_surfaceConverted) {
      await binding.convertFlutterSurfaceToImage();
      _surfaceConverted = true;
    }
    await tester.pump(const Duration(milliseconds: 300));
    final bytes = await binding.takeScreenshot(name);
    final b64 = base64Encode(bytes);
    for (var i = 0; i < b64.length; i += 800) {
      // ignore: avoid_print
      print('SCREENSHOT:$name:${b64.substring(i, i + 800 > b64.length ? b64.length : i + 800)}');
    }
  } catch (e) {
    // ignore: avoid_print
    print('Bildschirmfoto $name nicht möglich: $e');
  }
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Registrieren, Startseite, alle Bereiche, Beispielmonate', (tester) async {
    final stamp = DateTime.now().millisecondsSinceEpoch;
    // abgemeldet starten (falls ein früherer Lauf auf diesem Gerät angemeldet blieb)
    await TokenStorage().clear();
    // Die Testgeräte sind auf Englisch eingestellt; die Abläufe prüfen die deutschen Texte
    await KeyValueStore.instance.write('language', 'de');
    app.main();

    // Login-Seite -> Registrierung (erster Start auf alten Simulatoren kann lange dauern)
    await pumpUntil(tester, find.text('Willkommen zurück'), timeout: const Duration(seconds: 120));
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

    // /K040/ CSV-Import: Finanzüberblick öffnen, Beispieldatei importieren, zurück
    await tapButton(tester, find.text('Jeden Monat übrig'));
    await pumpUntil(tester, find.byKey(const Key('csv_import_open')));
    await pumpFor(tester, 1500); // Seitenübergang abwarten
    await tester.tap(find.byKey(const Key('csv_import_open')));
    await pumpUntil(tester, find.byKey(const Key('csv_sample')));
    await pumpFor(tester, 1000);
    await tapButton(tester, find.byKey(const Key('csv_sample')));
    await pumpUntil(tester, find.text('33 Umsätze importiert'), timeout: const Duration(seconds: 30));
    await goBack(tester);
    await pumpFor(tester, 800);
    await goBack(tester);
    await pumpUntil(tester, find.text('Kredit'));

    // Kreditplaner öffnen und Angebote vergleichen
    await tapButton(tester, find.text('Kredit'));
    await pumpUntil(tester, find.text('Kredit planen'));
    await pumpFor(tester, 1000);
    await scrollTo(tester, find.text('Angebote vergleichen'));
    await tester.tap(find.text('Angebote vergleichen'));
    // Angebote laden (auf langsamen Rechnern mehr als 3 s), dann hinscrollen
    for (var attempt = 0;; attempt++) {
      await pumpFor(tester, 2000);
      try {
        await scrollTo(tester, find.text('Nordlicht Kredit'));
        break;
      } catch (_) {
        if (attempt >= 10) rethrow;
      }
    }
    expect(find.text('Nordlicht Kredit'), findsOneWidget);

    // Getrennte Schritte: Angebot öffnen → unverbindlich anfragen → Vertrag abschließen → Auszahlung anfordern
    await tester.tap(find.text('Nordlicht Kredit'));
    // oben auf der Angebotsseite stehen die Schritte; der Tilgungsplan liegt weit unten (wird erst beim Scrollen gebaut)
    await pumpUntil(tester, find.byKey(const Key('loan_steps')));
    await pumpFor(tester, 1500);
    await scrollTo(tester, find.text('Tilgungsplan'));
    await tapButton(tester, find.byKey(const Key('loan_consent')));
    await pumpFor(tester, 400);
    await tapButton(tester, find.byKey(const Key('loan_request')));
    await pumpUntil(tester, find.byKey(const Key('loan_confirm')), timeout: const Duration(seconds: 30));
    await pumpFor(tester, 1500);
    await tapButton(tester, find.byKey(const Key('loan_confirm')));
    await pumpFor(tester, 400);
    await tapButton(tester, find.byKey(const Key('loan_accept')));
    await pumpUntil(tester, find.byKey(const Key('loan_payout')), timeout: const Duration(seconds: 30));
    await pumpFor(tester, 1500);
    await tapButton(tester, find.byKey(const Key('loan_payout')));
    await pumpUntil(tester, find.text('Restschuld'), timeout: const Duration(seconds: 30));

    // /K020/ Sondertilgung: Vorschau berechnen und buchen
    await tapButton(tester, find.byKey(const Key('loan_extra_payment')));
    await pumpUntil(tester, find.byKey(const Key('extra_amount')));
    await pumpFor(tester, 1500); // Seitenübergang abwarten, sonst geht die Eingabe auf langsamen Geräten verloren
    await enterKey(tester, const Key('extra_amount'), '1000');
    // Tastatur schließen (verdeckt auf dem Handy sonst die Knöpfe)
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 800);
    await tapButton(tester, find.byKey(const Key('extra_preview')));
    // Die Vorschau erscheint unter dem Knopf – auf kleinen Displays außerhalb des sichtbaren Bereichs
    await pumpUntil(tester, find.byKey(const Key('extra_pay'), skipOffstage: false));
    await scrollTo(tester, find.text('Du sparst Zinsen'));
    await tapButton(tester, find.byKey(const Key('extra_pay')));
    // zurück im Kreditdetail: neue Zeile "Sondertilgungen" mit dem Betrag
    await pumpUntil(tester, find.byKey(const Key('loan_extra_payment')));
    await pumpFor(tester, 1500);
    await scrollToTop(tester);
    await scrollTo(tester, find.text('Sondertilgungen'));
    expect(find.text('1.000,00 €'), findsWidgets);

    // Echte Bankkonten (am Ende, weil verbundene Umsätze den Finanzüberblick füllen):
    // Sandbox-Bank verbinden (Testdaten, keine echte Bank), Umsätze ansehen
    for (var i = 0; i < 6 && find.byType(NavigationBar).evaluate().isEmpty; i++) {
      await goBack(tester);
      await pumpFor(tester, 800);
    }
    await tester.tap(navItem('Konto'));
    await pumpUntil(tester, find.text('Hallo Erika!'));
    await scrollToTop(tester);
    await tapButton(tester, find.byKey(const Key('home_bank_connect')));
    await pumpUntil(tester, find.byKey(const Key('institution_SANDBOX_MAINBANK')));
    await pumpFor(tester, 1000);
    await tapButton(tester, find.byKey(const Key('institution_SANDBOX_MAINBANK')));
    await pumpUntil(tester, find.byKey(const Key('sandbox_approve')));
    await tester.tap(find.byKey(const Key('sandbox_approve')));
    await pumpUntil(tester, find.textContaining('Bank verbunden'), timeout: const Duration(seconds: 60));
    await pumpFor(tester, 1500);
    await scrollToTop(tester);
    await pumpUntil(tester, find.text('Hallo Erika!'));
    await scrollTo(tester, find.text('Tagesgeld'));
    await snap(tester, 'bankkonten');
    await tapButton(tester, find.text('Tagesgeld'));
    await pumpUntil(tester, find.byKey(const Key('bank_tx_search')));
    await pumpUntil(tester, find.textContaining('Umbuchung'));
    await goBack(tester);
    await pumpUntil(tester, find.text('Bankkonten'));

    // Finanz-Dashboard: alles auf einen Blick (Beispielmonate, Kredit, verbundene Bank)
    await scrollToTop(tester);
    await tapButton(tester, find.byKey(const Key('home_dashboard')));
    await pumpUntil(tester, find.byKey(const Key('dash_networth')), timeout: const Duration(seconds: 30));
    Finder inNetWorth(String t) => find.descendant(of: find.byKey(const Key('dash_networth')), matching: find.text(t));
    expect(inNetWorth('Andere Banken'), findsOneWidget);
    expect(inNetWorth('Kredite'), findsOneWidget);
    await scrollTo(tester, find.byKey(const Key('dash_debts')));
    await scrollTo(tester, find.byKey(const Key('dash_notes')));
    expect(find.textContaining('Simulation mit Spielgeld'), findsOneWidget);
    await snap(tester, 'dashboard');
    await goBack(tester);
    await pumpUntil(tester, find.text('Bankkonten'));

    // Multi-Banking: Tagesgeld der Projektbank eröffnen (Simulation, fiktiver Zinssatz), alle Umsätze an einem Ort
    await scrollToTop(tester);
    await tapButton(tester, find.byKey(const Key('home_open_account')));
    await pumpUntil(tester, find.byKey(const Key('open_type_CALL')));
    await pumpFor(tester, 800);
    await tester.tap(find.byKey(const Key('open_submit')));
    await pumpUntil(tester, find.textContaining('eröffnet (Simulation)'));
    await pumpFor(tester, 800);
    await scrollTo(tester, find.textContaining('Tagesgeld · 2,00 % p. a.'));
    await tapButton(tester, find.byKey(const Key('home_all_transactions')));
    await pumpUntil(tester, find.byKey(const Key('tx_sums')));
    await pumpUntil(tester, find.textContaining('Sandbox-Hausbank'), timeout: const Duration(seconds: 30));
    await goBack(tester);
    await pumpUntil(tester, find.text('Bankkonten'));
  });

  testWidgets('Zwei Nutzer: Überweisung, Live-Meldung, Dauerauftrag, geteiltes Unterkonto', (tester) async {
    final stamp = DateTime.now().millisecondsSinceEpoch;
    String phone(int n) => '0170${((stamp + n) % 10000000).toString().padLeft(7, '0')}';
    // Abmelden, falls der erste Test noch angemeldet ist
    await TokenStorage().clear();
    await AppConfig.load();

    // Nutzer A (bedient die App) und B (nur über die Schnittstelle)
    final a = await ApiUser.register('Anna', 'e2e-a-$stamp@example.org', phone(1));
    final b = await ApiUser.register('Ben', 'e2e-b-$stamp@example.org', phone(2));

    // B legt ein Unterkonto an und teilt es mit A (/K060/)
    final sub = await b.dio.post<Map<String, dynamic>>('/api/subaccounts', data: {'name': 'WG-Kasse E2E', 'icon': 'home'});
    await b.dio.post<dynamic>('/api/subaccounts/${sub.data!['id']}/members', data: {'recipient': a.email});

    // Die Testgeräte sind auf Englisch eingestellt; die Abläufe prüfen die deutschen Texte
    await KeyValueStore.instance.write('language', 'de');
    app.main();
    await pumpUntil(tester, find.byKey(const Key('login_email')), timeout: const Duration(seconds: 120));
    await tester.enterText(find.byKey(const Key('login_email')), a.email);
    await tester.enterText(find.byKey(const Key('login_password')), 'geheim12345');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tapButton(tester, find.byKey(const Key('login_submit')));
    await pumpUntil(tester, find.text('Hallo Anna!'));
    await pumpFor(tester, 1500);
    await snap(tester, '1-startseite');

    // Geteiltes Unterkonto von Ben ist auf der Startseite
    await scrollTo(tester, find.text('WG-Kasse E2E'));
    expect(find.text('Geteilt von Ben Zwei'), findsOneWidget);
    await scrollToTop(tester);

    // Live-Meldung: B überweist an A, während A die App offen hat
    await b.transfer(a.email, 1234, 'Pizza E2E');
    // Kontostand ändert sich ohne Neuladen (Meldung kommt über die WebSocket-Verbindung)
    await pumpUntil(tester, find.text('1.012,34 €'), timeout: const Duration(seconds: 30));

    // A überweist an B über die Oberfläche
    rootMessengerKey.currentState?.clearSnackBars();
    await tester.tap(navItem('Überweisen'));
    await pumpUntil(tester, find.byKey(const Key('transfer_recipient')));
    await tester.enterText(find.byKey(const Key('transfer_recipient')), b.email);
    await tester.enterText(find.byKey(const Key('transfer_amount')), '25,50');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tapButton(tester, find.byKey(const Key('transfer_next')));
    await pumpUntil(tester, find.text('Jetzt senden'));
    await pumpFor(tester, 800);
    await snap(tester, '2-ueberweisung-pruefen');
    expect(find.text('Ben Zwei'), findsWidgets);
    await tapButton(tester, find.text('Jetzt senden'));
    await pumpUntil(tester, find.text('Geld gesendet'), timeout: const Duration(seconds: 30));
    expect(await b.mainBalance(), 100000 - 1234 + 2550);
    expect(await a.mainBalance(), 100000 + 1234 - 2550);

    // Erfolgsseite schließen, dann Dauerauftrag an B anlegen (/K030/)
    await tapButton(tester, find.text('Fertig'));
    await pumpUntil(tester, navItem('Überweisen'));
    await tester.tap(navItem('Überweisen'));
    await pumpUntil(tester, find.text('Daueraufträge'));
    await tester.tap(find.text('Daueraufträge').first);
    await pumpUntil(tester, find.byKey(const Key('standing_order_new')));
    await pumpFor(tester, 1000);
    await tester.tap(find.byKey(const Key('standing_order_new')));
    await pumpUntil(tester, find.byKey(const Key('so_recipient')));
    await tester.enterText(find.byKey(const Key('so_recipient')), b.email);
    await tester.enterText(find.byKey(const Key('so_amount')), '50');
    await tester.enterText(find.byKey(const Key('so_reference')), 'Miete Anteil');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tapButton(tester, find.byKey(const Key('so_save')));
    await pumpUntil(tester, find.text('Miete Anteil'), timeout: const Duration(seconds: 30));
    await pumpFor(tester, 1500);
    await snap(tester, '3-dauerauftraege');
    final orders = await a.standingOrders();
    expect(orders, hasLength(1));
    expect(orders.first['amountCents'], 5000);

    // Eigene Einnahme (monatliches Gehalt) und einmalige Ausgabe (Urlaub) eintragen
    final balanceBefore = await a.mainBalance();
    await goBack(tester);
    await pumpFor(tester, 800);
    await tester.tap(navItem('Konto'));
    await pumpUntil(tester, find.byKey(const Key('home_add_income')));
    await tapButton(tester, find.byKey(const Key('home_add_income')));
    await pumpUntil(tester, find.byKey(const Key('entry_suggest_Gehalt')));
    await pumpFor(tester, 1000);
    await tester.tap(find.byKey(const Key('entry_suggest_Gehalt')));
    await pumpFor(tester, 300);
    await enterKey(tester, const Key('entry_amount'), '2650');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tapButton(tester, find.byKey(const Key('entry_save')));
    await pumpUntil(tester, find.byKey(const Key('home_add_expense')), timeout: const Duration(seconds: 30));
    await pumpFor(tester, 1000);
    expect(await a.mainBalance(), balanceBefore + 265000);

    await tapButton(tester, find.byKey(const Key('home_add_expense')));
    await pumpUntil(tester, find.byKey(const Key('entry_title')));
    await pumpFor(tester, 1000);
    await enterKey(tester, const Key('entry_title'), 'Urlaub E2E');
    await enterKey(tester, const Key('entry_amount'), '450');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tapButton(tester, find.byKey(const Key('entry_save')));
    await pumpUntil(tester, find.byKey(const Key('home_add_expense')), timeout: const Duration(seconds: 30));
    expect(await a.mainBalance(), balanceBefore + 265000 - 45000);

    // Übersicht zeigt beide Einträge
    await tapButton(tester, find.byKey(const Key('home_entries')));
    await pumpUntil(tester, find.text('Urlaub E2E'));
    expect(find.text('Gehalt'), findsWidgets);
    await pumpFor(tester, 800);
    await snap(tester, '4-einnahmen-ausgaben');

    // Sparregel für das geteilte digitale Konto von Ben: wöchentlich 20 € vom eigenen Girokonto
    // (Umbuchung innerhalb der Projektbank; Start heute = sofort ausgeführt)
    final subId = sub.data!['id'] as int;
    await goBack(tester);
    await pumpFor(tester, 800);
    // die Startseite ist noch nach unten gescrollt (zum Knopf "Einnahmen & Ausgaben verwalten")
    await pumpUntil(tester, find.byKey(const Key('home_entries')));
    await tapButton(tester, find.byKey(Key('digital_tile_$subId')));
    await pumpUntil(tester, find.byKey(const Key('digital_deposit')));
    await pumpFor(tester, 1500);
    await tapButton(tester, find.byKey(const Key('alloc_new')));
    await pumpUntil(tester, find.byKey(const Key('alloc_amount')));
    await pumpFor(tester, 1000);
    await enterKey(tester, const Key('alloc_amount'), '20');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    final weekly = find.byKey(const Key('alloc_interval_WEEKLY'));
    await tester.ensureVisible(weekly);
    await pumpFor(tester, 300);
    await tester.tap(weekly);
    await pumpFor(tester, 300);
    await tester.tap(find.byKey(const Key('alloc_save')));
    await pumpUntil(tester, find.text('20,00 € · wöchentlich'), timeout: const Duration(seconds: 30));
    await snap(tester, '5-sparregel');
    final rules = (await a.dio.get<List<dynamic>>('/api/allocations/rules')).data!;
    expect(rules, hasLength(1));
    expect(((rules.first as Map)['lastRun'] as Map)['status'], 'COMPLETED');
  });

  testWidgets('Weiterentwicklung: WG mit Ausgleich, Vertrag, Assistent, Planer, Kreditkarte, Finanz-Check',
      (tester) async {
    final stamp = DateTime.now().millisecondsSinceEpoch;
    String phone(int n) => '0171${((stamp + n) % 10000000).toString().padLeft(7, '0')}';
    await TokenStorage().clear();
    await AppConfig.load();
    final a = await ApiUser.register('Wanda', 'e2e-w-$stamp@example.org', phone(1));
    final b = await ApiUser.register('Bruno', 'e2e-x-$stamp@example.org', phone(2));
    final aId = (await a.dio.get<Map<String, dynamic>>('/api/me')).data!['id'] as int;
    // Beispielmonate, damit Assistent, Planer und Finanz-Check echte Zahlen haben
    await a.dio.post<dynamic>('/api/demo/sample-months', data: {'profile': 'EINSTEIGER'});

    await KeyValueStore.instance.write('language', 'de');
    app.main();
    await pumpUntil(tester, find.byKey(const Key('login_email')), timeout: const Duration(seconds: 120));
    await tester.enterText(find.byKey(const Key('login_email')), a.email);
    await tester.enterText(find.byKey(const Key('login_password')), 'geheim12345');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tapButton(tester, find.byKey(const Key('login_submit')));
    await pumpUntil(tester, find.text('Hallo Wanda!'));
    await pumpFor(tester, 1500);

    // Gemeinsame Ausgaben: Gruppe anlegen, Bruno hinzufügen, 30 € Einkauf je zur Hälfte
    await tester.tap(navItem('Sparen'));
    await pumpUntil(tester, find.text('Jeden Monat übrig'));
    await pumpFor(tester, 800);
    await tapButton(tester, find.byKey(const Key('savings_groups')));
    await pumpUntil(tester, find.byKey(const Key('groups_create')));
    await pumpFor(tester, 1000);
    await tester.tap(find.byKey(const Key('groups_create')));
    await pumpUntil(tester, find.byKey(const Key('group_name_field')));
    await enterKey(tester, const Key('group_name_field'), 'WG E2E');
    await tester.tap(find.byKey(const Key('group_name_save')));
    await pumpUntil(tester, find.byKey(const Key('group_add_member')), timeout: const Duration(seconds: 30));
    await pumpFor(tester, 1000);
    await tester.tap(find.byKey(const Key('group_add_member')));
    await pumpUntil(tester, find.byKey(const Key('group_member_field')));
    await enterKey(tester, const Key('group_member_field'), b.email);
    await tester.tap(find.byKey(const Key('group_member_save')));
    await pumpUntil(tester, find.text('Bruno Zwei'), timeout: const Duration(seconds: 30));
    await pumpFor(tester, 800);
    rootMessengerKey.currentState?.clearSnackBars();
    await tester.tap(find.byKey(const Key('group_add_expense')));
    await pumpUntil(tester, find.byKey(const Key('expense_description')));
    await enterKey(tester, const Key('expense_description'), 'Einkauf E2E');
    await enterKey(tester, const Key('expense_amount'), '30');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tester.tap(find.byKey(const Key('expense_save')));
    await pumpUntil(tester, find.text('Du bekommst 15,00 €'), timeout: const Duration(seconds: 30));
    await snap(tester, '6-gemeinsame-ausgaben');

    // Bruno gleicht per Überweisung aus (Spielgeld); Wandas Gruppe aktualisiert sich über die Live-Meldung
    final groupId = ((await a.dio.get<List<dynamic>>('/api/groups')).data!.first as Map)['id'] as int;
    await b.dio.post<dynamic>('/api/groups/$groupId/settlements',
        data: {'toUserId': aId, 'amountCents': 1500},
        options: Options(headers: {'Idempotency-Key': 'e2e-wg-$stamp'}));
    await pumpUntil(tester, find.byKey(const Key('group_settled'), skipOffstage: false),
        timeout: const Duration(seconds: 30));
    expect(await b.mainBalance(), 100000 - 1500);

    // Vertrag anlegen: Übersicht zeigt die monatlichen Kosten
    for (var i = 0; i < 4 && find.byType(NavigationBar).evaluate().isEmpty; i++) {
      await goBack(tester);
      await pumpFor(tester, 800);
    }
    await tester.tap(navItem('Sparen'));
    await pumpFor(tester, 1500);
    await scrollToTop(tester);    // die Liste steht noch weiter unten (bei „Gemeinsame Ausgaben“)
    await pumpUntil(tester, find.text('Jeden Monat übrig'));
    await tapButton(tester, find.byKey(const Key('savings_contracts')));
    await pumpUntil(tester, find.byKey(const Key('contracts_add')));
    await pumpFor(tester, 1000);
    await tester.tap(find.byKey(const Key('contracts_add')));
    await pumpUntil(tester, find.byKey(const Key('contract_name')));
    await pumpFor(tester, 1000);
    await enterKey(tester, const Key('contract_name'), 'Handy E2E');
    await enterKey(tester, const Key('contract_cost'), '29,99');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tapButton(tester, find.byKey(const Key('contract_save')));
    await pumpUntil(tester, find.text('29,99 € pro Monat'), timeout: const Duration(seconds: 30));
    // Übergang abwarten: bis dahin zählt das Eingabefeld der schließenden Formularseite mit
    await pumpFor(tester, 1500);
    expect(find.descendant(of: find.byType(Card), matching: find.text('Handy E2E')), findsOneWidget);
    await snap(tester, '7-vertraege');
    await goBack(tester);
    await pumpFor(tester, 800);

    // Finanzassistent: Vorschlag antippen, Antwort mit Zahlen; Monatsbericht
    await scrollToTop(tester);
    await tapButton(tester, find.byKey(const Key('savings_assistant')));
    await pumpUntil(tester, find.byKey(const Key('assistant_suggestion_0')), timeout: const Duration(seconds: 30));
    await pumpFor(tester, 800);
    await tester.tap(find.byKey(const Key('assistant_suggestion_0')));
    await pumpUntil(tester, find.byKey(const Key('assistant_answer_0'), skipOffstage: false),
        timeout: const Duration(seconds: 30));
    await scrollTo(tester, find.byKey(const Key('assistant_answer_0')));
    expect(find.textContaining('keine Finanzberatung'), findsWidgets);
    await tester.tap(find.byKey(const Key('assistant_tab_report')));
    await pumpUntil(tester, find.byKey(const Key('report_month')), timeout: const Duration(seconds: 30));
    await snap(tester, '8-assistent');
    await goBack(tester);
    await pumpFor(tester, 800);

    // Vorhaben planen: Auto, drei Wege mit Empfehlung
    await scrollToTop(tester);
    await tapButton(tester, find.byKey(const Key('savings_planner')));
    await pumpUntil(tester, find.byKey(const Key('planner_goal_AUTO')));
    await pumpFor(tester, 800);
    await tester.tap(find.byKey(const Key('planner_goal_AUTO')));
    await pumpFor(tester, 300);
    await enterKey(tester, const Key('planner_price'), '20000');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tapButton(tester, find.byKey(const Key('planner_compare')));
    await pumpUntil(tester, find.byKey(const Key('planner_recommendation'), skipOffstage: false),
        timeout: const Duration(seconds: 30));
    await scrollTo(tester, find.byKey(const Key('planner_option_FINANCE')));
    await snap(tester, '9-planer');
    await goBack(tester);
    await pumpFor(tester, 800);

    // Kreditkarte (Simulation): einrichten, Demo-Zahlung
    await tester.tap(navItem('Karte'));
    await pumpUntil(tester, find.text('Projektbank'));
    await pumpFor(tester, 1000);
    await tapButton(tester, find.byKey(const Key('cc_create')));
    await pumpUntil(tester, find.byKey(const Key('cc_limit_save')));
    await tester.tap(find.byKey(const Key('cc_limit_save')));
    await pumpUntil(tester, find.byKey(const Key('cc_pay'), skipOffstage: false), timeout: const Duration(seconds: 30));
    await pumpFor(tester, 800);
    await tapButton(tester, find.byKey(const Key('cc_pay')));
    await pumpUntil(tester, find.byKey(const Key('cc_pay_merchant')));
    await enterKey(tester, const Key('cc_pay_merchant'), 'Café E2E');
    await enterKey(tester, const Key('cc_pay_amount'), '12');
    FocusManager.instance.primaryFocus?.unfocus();
    await pumpFor(tester, 500);
    await tester.tap(find.byKey(const Key('cc_pay_submit')));
    await pumpUntil(tester, find.text('Café E2E', skipOffstage: false), timeout: const Duration(seconds: 30));
    final cc = (await a.dio.get<Map<String, dynamic>>('/api/credit-card')).data!;
    expect(cc['openCents'], 1200);

    // Finanz-Check auf dem Dashboard: erst nach Einwilligung
    await tester.tap(navItem('Konto'));
    await pumpUntil(tester, find.text('Hallo Wanda!'));
    await scrollToTop(tester);
    await tapButton(tester, find.byKey(const Key('home_dashboard')));
    await pumpUntil(tester, find.byKey(const Key('dash_networth')), timeout: const Duration(seconds: 30));
    await scrollTo(tester, find.byKey(const Key('health_consent')));
    await tester.tap(find.byKey(const Key('health_consent')));
    await pumpUntil(tester, find.byKey(const Key('health_score_value'), skipOffstage: false),
        timeout: const Duration(seconds: 30));
    await scrollTo(tester, find.byKey(const Key('health_score_value')));
    await snap(tester, '10-finanz-check');
  });
}
