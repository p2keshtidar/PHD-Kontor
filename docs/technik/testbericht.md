# ✅ Testbericht

[← Architektur](architektur.md) · [Übersicht](../../README.md)

Stand: 13.10.2026 · alle Tests laufen automatisch bei jedem Push ([Actions](https://github.com/p2keshtidar/girokonto/actions)).

## Zusammenfassung

| Ebene | Anzahl | Ergebnis | Testabdeckung (Zeilen) |
|---|---|---|---|
| Backend: Unit- und Integrationstests (echte PostgreSQL) | **136** | ✅ alle grün | **93,2 %** |
| App: Unit- und Widget-Tests (simulierter Server) | **159** | ✅ alle grün | **59,4 %** |
| Ende-zu-Ende: echte App + echter Server | 2 Abläufe × 4 Geräte | E2E_ERGEBNIS | – |

Die Abdeckung steht nach jedem Lauf oben in der Zusammenfassung des CI-Laufs, inklusive der 10 am wenigsten getesteten Dateien.

## Backend (`backend/src/test`)

| Gruppe | Klassen | Was wird geprüft |
|---|---|---|
| Pflichtenheft T01–T25 | `AuthFlowTest`, `TransferFlowTest`, `CardFlowTest`, `SubaccountFlowTest`, `PlanningFlowTest`, `NotificationFlowTest`, `ContactFlowTest` | alle Testfälle aus dem Pflichtenheft |
| Kannkriterien T26–T40 | `Phase5FlowTest`, `StandingOrderFlowTest`, `TwoFactorFlowTest`, `CsvImportFlowTest`, `SharedSubaccountFlowTest` | Sondertilgung, Erinnerung, Dauerauftrag, Zwei-Faktor, CSV-Import, Teilen |
| **Sicherheit S01–S10** | `SecurityMatrixTest` | *jeder* `/api`-Endpunkt ohne Anmeldung → 401 (automatisch aufgezählt) · gefälschte/abgelaufene Tokens · Abmelden/Refresh-Wiederverwendung · Admin-Bereich → 403 · fremde Konten, Karten, Pläne, Kredite, Daueraufträge, Unterkonten, Importe → 403/404 · keine Passwort-Hashes oder Kartendaten in Antworten · SQL-Injection in der Suche · CORS · Sperre nach Fehlversuchen |
| **Robustheit R01–R08** | `RobustnessTest` | *Fuzzing*: jeder POST/PUT/GET-Endpunkt mit kaputtem JSON, falschen Typen, riesigen Zahlen, leeren Feldern → nie ein Serverfehler (5xx) · 25 gleichzeitige Überweisungen → genau so viele wie Guthaben · gleicher Idempotenz-Schlüssel parallel → nur einmal gebucht · Grenzwerte (Tageslimit, 10 Unterkonten, Ausführungstag 28/29, Investplan 10 € bis 5.000 €, Alter genau 18) · **160 Zufallsaktionen** mit Zeitvorlauf und Prüfung der Buchhaltung: Summe aller Konten = 0, kein Konto negativ, jede Buchung ausgeglichen · Sonderzeichen, Emojis, sehr lange Texte |
| Profil, Finanzüberblick | `ProfileAndInsightsTest` | Profil ändern, Passwort ändern, Übersicht, Kategorien |
| Live-Meldungen | `StompAuthInterceptorTest`, `WebSocketDeliveryTest` | WebSocket nur mit gültigem Token · Meldung kommt beim richtigen Nutzer an, nicht beim falschen |
| Rechenkerne | `LoanMathTest`, `PlanMathTest`, `TotpTest` (RFC-6238-Testwerte), `CsvParserTest`, `StandingOrderTest`, `SimulationClockTest`, `IbanGeneratorTest`, `CategoryServiceTest`, `BookingServiceValidateTest`, `CardUnitTest`, `PhoneNormalizeTest` | Tilgungsplan, Zinsen, Codes, Dateiformate, Kalender inkl. Sommerzeit, IBAN-Prüfziffer |

## App (`app/test`)

| Datei | Was wird geprüft |
|---|---|
| `screens_layout_test.dart` | **23 Bildschirme × 4 Formate** (kleines Handy 320×568, großes Handy, Tablet, Schrift 150 %): kein Überlauf, kein Absturz |
| `flows_widget_test.dart` | Abläufe A01–A15 mit simuliertem Server: Anmeldung inkl. Zwei-Faktor-Code, Server-Adresse, Überweisung mit Idempotenz-Schlüssel, ungültige Beträge, Dauerauftrag anlegen/pausieren, Sondertilgung (Vorschau, Buchung, zu wenig Geld), Zwei-Faktor einrichten (QR-Code), CSV-Import mit Fehler, Unterkonto teilen/austreten, Startseite mit geteilten Konten, Spar-Erinnerung |
| übrige `*_test.dart` | Geldformat, Eingabeprüfung, JSON-Modelle, CSV-Zeichensätze, Hintergrund-Abfrage |

Simulierter Server: `test/support/fake_backend.dart` (ersetzt die HTTP-Schicht), Testrahmen: `test/support/harness.dart`.

## Ende-zu-Ende (`app/integration_test`, Workflow **E2E**)

Echte App gegen echten Server mit echter Datenbank, im **Browser (Chrome)**, **Android-Emulator** (Pixel 5 oder sehr kleines Display) und **iOS-Simulator**:

1. Registrieren → Startseite → Karte → Überweisen → Sparen → Beispielmonate → CSV-Import → Kreditvergleich → Kredit abschließen → Sondertilgung.
2. **Zwei Nutzer:** zweiter Nutzer teilt ein Unterkonto (erscheint auf der Startseite) · überweist Geld, während die App offen ist (**Live-Meldung** und Kontostand ohne Neuladen) · Überweisung über die Oberfläche (Kontostände beider Nutzer werden über den Server nachgeprüft) · Dauerauftrag anlegen.

## Durch die Tests gefundene und behobene Fehler

| Fehler | Gefunden durch | Behebung |
|---|---|---|
| Simulationsuhr verrechnete sich bei der Zeitumstellung (Tag = 24 h) | Backend-Test um Mitternacht | Vorspulen in Kalendertagen |
| Falsche Zeichen (Umlaute) in Antworten bei Tests | T26 | UTF-8 überall |
| CSV-Kopfzeile wurde in Vorspann-Zeilen der ING-Datei erkannt | CSV-Tests | Wortgrenzen beim Erkennen |
| Typfehler in Pfaden/Parametern (z. B. `/api/plans/abc`) gaben 500 statt 400 | Fuzzing R01/R02 | eigene Fehlerbehandlung für Typ- und Validierungsfehler |
| Doppelte Daten (z. B. gleichzeitige Anfragen) gaben 500 statt 409 | Nebenläufigkeit R03/R04 | Datenbank-Konflikt → 409 |
| Code „123 456“ aus der Authenticator-App wurde auf „12345“ abgeschnitten | Widget-Test A02/A10 | Leerzeichen werden beim Eingeben entfernt |
| Diagramm-Legende lief bei großer Schrift über den Rand | Layout-Test 150 % | Legende bricht um |
| iOS-Absturz beim Start (Kontakte-Plugin) | E2E iOS | klassischer App-Start |

## Selbst ausführen

```bash
cd backend && ./gradlew test          # Bericht: build/reports/tests/test/index.html
                                       # Abdeckung: build/reports/jacoco/test/html/index.html
cd app && flutter test --coverage     # Abdeckung: coverage/lcov.info
```

Ende-zu-Ende: GitHub → **Actions → E2E → Run workflow** → Plattform wählen.
