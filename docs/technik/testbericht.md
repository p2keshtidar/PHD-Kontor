# ✅ Testbericht

[← Architektur](architektur.md) · [Übersicht](../../README.md)

Stand: 16.10.2026 (Sprint 5 und Weiterentwicklung, CI-Lauf auf `5685660`) · alle Tests laufen automatisch bei jedem Push ([Actions](https://github.com/p2keshtidar/girokonto/actions)).

## Zusammenfassung

| Ebene | Anzahl | Ergebnis | Testabdeckung (Zeilen) |
|---|---|---|---|
| Backend: Unit- und Integrationstests (echte PostgreSQL) | **316** | ✅ alle grün | **92,0 %** |
| App: Unit- und Widget-Tests (simulierter Server) | **437** | ✅ alle grün | **77,5 %** |
| Ende-zu-Ende: echte App + echter Server | **3 Abläufe**, Stand 16.10.: **Chrome, Android 11, iOS 26** grün (Ablauf 3 = Weiterentwicklung; Android brauchte einen zweiten Anlauf, weil das NDK-Paket auf dem Runner beschädigt ankam; ein einmaliger Aussetzer in Ablauf 2 auf iOS trat im nächsten Lauf nicht wieder auf) · alle 12 Systeme (Android 7/8/9/11/13/15/16, iOS 16/17/18/26, sehr kleines Display) zuletzt am 13.10. mit den Abläufen 1–2 | ✅ grün | – |

Die Abdeckung steht nach jedem Lauf oben in der Zusammenfassung des CI-Laufs, inklusive der 10 am wenigsten getesteten Dateien.

## Backend (`backend/src/test`)

| Gruppe | Klassen | Was wird geprüft |
|---|---|---|
| Pflichtenheft T01–T25 | `AuthFlowTest`, `TransferFlowTest`, `CardFlowTest`, `SubaccountFlowTest`, `PlanningFlowTest`, `NotificationFlowTest`, `ContactFlowTest` | alle Testfälle aus dem Pflichtenheft |
| Einnahmen & Ausgaben T41–T45 | `EntryFlowTest`, `EntryTest` | einmalig und regelmäßig, Nachbuchen bis 12 Monate, Tageslauf (Gehalt vor Miete), nicht ins Minus, Pausieren, Enddatum, Löschen, fremde Einträge, Kategorien |
| **Sicherheit SEC01–SEC04, Betrieb M01–M02** | `RateLimitAndSecretsTest`, `MonitoringTest` | Anfragebegrenzung (429) bei Anmeldung und API · Start-Sperre für Entwicklungs-Schlüssel im Betrieb · Health öffentlich, Kennzahlen nur für Admins |
| **Deutsch/Englisch I01–I05, T46–T48** | `ServerTextsTest`, `I18nFlowTest` | jede feste Meldung im Code hat eine Übersetzung · Beträge/Daten/Prozente englisch · Sprache pro Nutzer · englische Fehler und Benachrichtigungen |
| **Bankverbindungen B01–B23, T49–T60** | `SandboxBankingProviderTest`, `GoCardlessProviderTest` (nachgebildeter Server), `BankingHelpersTest`, `BankingFlowTest` | gleiche Daten → keine doppelten Umsätze · Vormerkung → Buchung bzw. Storno · Rücklastschrift · eigene Umbuchung · eigene Angaben getrennt von Originaldaten · fremde Daten 404 · Wiederholung bei Störung, Fehlerstatus · Freigabe läuft ab und wird erneuert · Widerruf mit/ohne Löschen · Freigabe und Originaldaten nur verschlüsselt · Ablauf-Erinnerung · Rücksprung-Seite |
| **Digitale Konten T61–T67** | `DigitalAccountFlowTest` | Zweck, Ziel, Fortschritt, nötige Monatsrate · Kennzeichnung „kein eigenes Bankkonto“ · „Ziel erreicht“ nur einmal · Rollen (einzahlen + auszahlen / nur einzahlen / nur ansehen) · Gesamtvermögen mit verbundenen Banken |
| **Sparregeln T68–T77, Z01–Z05** | `AllocationFlowTest`, `AllocationScheduleTest` | sofort und wöchentlich · keine Deckung → Wiederholung → fehlgeschlagen → von Hand nachholen (genau eine Buchung) · **vier parallele Tagesläufe und nachträgliche Wiederholung ohne doppelte Abbuchung** · pausieren (ohne Nachholen), ändern, Enddatum, beenden · verbundenes Bankkonto nur als Vormerkung mit Deckungsprüfung und Warnung · gelöschte/veraltete Bankverbindung · Rechte in geteilten Konten bei jeder Ausführung · Eingaben und Höchstzahl · Ziel erreicht mit Vormerkung · Termine am Monatsende, Prognose |
| **Kreditvergleich T78–T84, K01–K11** | `LoanComparisonFlowTest`, `SimulatedBanksProviderTest`, `MarketRateServiceTest` (nachgebildeter Server) | Kennzeichnung als Rechenbeispiel fiktiver Banken, Anbieterstatus · Bonität und Filter (Monatsrate, Budget, nur erfüllbare) · **Anfrage → Vertrag → Auszahlung getrennt** (Einwilligung, Bestätigung, Vertrag ohne Buchung, Auszahlung genau einmal, keine Raten vor der Auszahlung) · Ablehnung, Rückzug, Ablauf · Widerruf vor der Auszahlung · fremde Anfragen 404 · effektiver Jahreszins · Bundesbank: CSV-Formate, **keine erfundenen Werte ohne Abruf**, veraltete Werte gekennzeichnet, abgeschaltet ohne Aufrufe |
| **Dashboard, Multi-Banking, Umsätze T85–T101** | `DashboardFlowTest`, `MultiBankingFlowTest`, `TransactionManagementFlowTest` | Gesamtvermögen, verfügbar/zurückgelegt, Vermögensverlauf, Cashflow, Schulden · Tagesgeld/zweites Girokonto, Zinsen, Umbuchung · konsolidierte Umsätze, Notizen, CSV-Export, Duplikate |
| **Budgets und Prognose T102–T107, Q01–Q04** | `BudgetForecastFlowTest`, `PlanningMathTest` | Budgets mit Warnung/Überschreitung, ungewöhnliche Ausgaben, Prognose bis Monatsende mit günstigem/ungünstigem Fall |
| **Marktplatz T108–T112** | `MarketFlowTest` | nur fiktive Anbieter, Vergleich, Favoriten, simulierte Anträge mit Unterlagen, Rückzug |
| **Kreditkarte und Finanz-Check T113–T118, T140** | `CreditCardAndScoreFlowTest` | Limit, Sperre, Auslandseinsatz, Monatsabrechnung genau einmal, kein Dispo · Finanz-Check nur mit Einwilligung, kein SCHUFA-Bezug, Englisch |
| **Gemeinsame Ausgaben T119–T123, W01–W02** | `GroupFlowTest`, `GroupMathTest` | Aufteilung mit Rest-Cent, feste Anteile, Salden = 0, Ausgleich per Überweisung genau einmal, Barzahlung nur vom Empfänger, fremde Gruppen 404 |
| **Finanzassistent T124–T128, F01–F02** | `AssistantFlowTest`, `AssistantParsingTest` | Antworten mit genau den Zahlen der Monatsübersicht, Monatsbericht, Szenarien, ohne Daten keine Zahlen, Englisch |
| **Verträge und Dokumente T129–T133, K01–K04** | `ContractFlowTest`, `ContractMathTest` | Fristen und Kosten, Erinnerung genau einmal, Kündigung nur als Vermerk · Dokumente verschlüsselt, nur für den Besitzer, nur PDF/PNG/JPEG am Inhalt erkannt, 5 MB, sichere Dateinamen |
| **API-Version, Prüfprotokoll T134–T136, Lasttest T137** | `ApiVersionAndAuditTest`, `LoadTest` | `/api/v1` = Kurzform, Header · Protokoll ohne Passwörter/IBAN · 12 Nutzer, 8 Endpunkte parallel: keine Fehler, p95 < 3 s |
| **Planer T138–T139, T143, Karten digitaler Konten T141–T142** | `PlannerFlowTest`, `SubaccountCardFlowTest` | drei Wege mit Empfehlung, Eingabeprüfung, Werte für den Kreditvergleich (gleiche günstigste Rate) · Zahlung vom digitalen Konto, Limit, Sperre, nur volle Rechte |
| **Gemeinsame Helfer H01–H03** | `SharedHelpersTest` | Datum/Monatsnamen, Durchschnitt nur über volle Monate mit Daten, Idempotency-Key |
| **Sicherheit neue Funktionen S11–S12, Logs P01–P04** | `SecurityNewFeaturesTest`, `RequestLogFilterTest` | fremde Gruppen/Verträge/Dokumente/Anträge → 403/404 · keine Geheimnisse in Antworten · Logzeile ohne Query/Token, Request-ID, Versionspfade, Datenbankpasswort im Betrieb |
| Kannkriterien T26–T40 | `Phase5FlowTest`, `StandingOrderFlowTest`, `TwoFactorFlowTest`, `CsvImportFlowTest`, `SharedSubaccountFlowTest` | Sondertilgung, Erinnerung, Dauerauftrag, Zwei-Faktor, CSV-Import, Teilen |
| **Sicherheit S01–S10** | `SecurityMatrixTest` | *jeder* `/api`-Endpunkt ohne Anmeldung → 401 (automatisch aufgezählt) · gefälschte/abgelaufene Tokens · Abmelden/Refresh-Wiederverwendung · Admin-Bereich → 403 · fremde Konten, Karten, Pläne, Kredite, Kreditanfragen, Daueraufträge, Unterkonten, Sparregeln, Vormerkungen, Importe → 403/404 · keine Passwort-Hashes oder Kartendaten in Antworten · SQL-Injection in der Suche · CORS · Sperre nach Fehlversuchen |
| **Robustheit R01–R08** | `RobustnessTest` | *Fuzzing*: jeder POST/PUT/GET-Endpunkt mit kaputtem JSON, falschen Typen, riesigen Zahlen, leeren Feldern → nie ein Serverfehler (5xx) · 25 gleichzeitige Überweisungen → genau so viele wie Guthaben · gleicher Idempotenz-Schlüssel parallel → nur einmal gebucht · Grenzwerte (Tageslimit, 10 Unterkonten, Ausführungstag 28/29, Investplan 10 € bis 5.000 €, Alter genau 18) · **160 Zufallsaktionen** mit Zeitvorlauf und Prüfung der Buchhaltung: Summe aller Konten = 0, kein Konto negativ, jede Buchung ausgeglichen · Sonderzeichen, Emojis, sehr lange Texte |
| Profil, Finanzüberblick | `ProfileAndInsightsTest` | Profil ändern, Passwort ändern, Übersicht, Kategorien |
| Live-Meldungen | `StompAuthInterceptorTest`, `WebSocketDeliveryTest` | WebSocket nur mit gültigem Token · Meldung kommt beim richtigen Nutzer an, nicht beim falschen |
| Rechenkerne | `LoanMathTest`, `PlanMathTest`, `TotpTest` (RFC-6238-Testwerte), `CsvParserTest`, `StandingOrderTest`, `SimulationClockTest`, `IbanGeneratorTest`, `CategoryServiceTest`, `BookingServiceValidateTest`, `CardUnitTest`, `PhoneNormalizeTest` | Tilgungsplan, Zinsen, Codes, Dateiformate, Kalender inkl. Sommerzeit, IBAN-Prüfziffer |

## App (`app/test`)

| Datei | Was wird geprüft |
|---|---|
| `screens_layout_test.dart` | **44 Bildschirme × 4 Formate** + alle auf Englisch (kleines Handy 320×568, großes Handy, Tablet, Schrift 150 %): kein Überlauf, kein Absturz |
| `flows_widget_test.dart` | Abläufe A01–A15 mit simuliertem Server: Anmeldung inkl. Zwei-Faktor-Code, Server-Adresse, Überweisung mit Idempotenz-Schlüssel, ungültige Beträge, Dauerauftrag anlegen/pausieren, Sondertilgung (Vorschau, Buchung, zu wenig Geld), Zwei-Faktor einrichten (QR-Code), CSV-Import mit Fehler, Unterkonto teilen/austreten, Startseite mit geteilten Konten, Spar-Erinnerung, A16 Überweisung auf 320×480 mit Schrift 130 %, A17–A22 Einnahmen & Ausgaben (Schnellauswahl, einmalig/monatlich, Tag 29–31, Serverfehler, Pausieren, Löschen, Startseite) |
| `l10n_test.dart` | L01–L08: jeder Text hat eine englische Übersetzung, keine fest codierten Texte, Platzhalter, Beträge/Daten je Sprache, Sprachwechsel schließt Hinweise in der alten Sprache |
| `font_coverage_test.dart` | FC01–FC03: jedes Zeichen der App- und Server-Texte steckt in einer mitgelieferten Schrift (Roboto oder Symbolschrift) – die Web-App lädt nichts von Google nach |
| `banking_test.dart` | BK01–BK12: Startseite mit/ohne verbundene Bank · Sandbox-Freigabe bestätigen/ablehnen · echte Bank: Bankseite öffnen, Freigabe abschließen · Status, Berechtigungen, Abruf, Widerruf mit Löschen · abgelaufene Freigabe erneuern · Umsätze mit Status, Filter, Suche · eigene Angaben speichern · Englisch |
| `digital_accounts_test.dart` | DA01–DA08: zwei getrennte Bereiche, Girokonto als Simulation gekennzeichnet · neues digitales Konto mit Zweck und Ziel · Fortschritt und Hinweis zur Verwahrung · nur ansehen: Ein-/Auszahlen gesperrt · Rechte ändern · teilen mit Rolle · Englisch |
| `allocations_test.dart` | AL01–AL09: Bereich „Automatisch sparen“ (Regeln, Prognose, ausstehend, Vorgänge mit Status) · Sparregel vom Girokonto und vom verbundenen Bankkonto (nur Vormerkung, gesperrte Quelle mit Grund) · Eingabefehler und Serverfehler · erneut versuchen · pausieren und beenden · Vormerkung aufheben und neu vormerken · Englisch · Modelle |
| `loan_steps_test.dart` | LV01–LV09: Vergleich mit Bonität und Filtern, Kennzeichnung, Bundesbank-Referenz, Anbieter · Anfrage nur mit Einwilligung · Vertrag nur nach Bestätigung · Ablehnung · Rückzug · Auszahlung anfordern · Widerruf vor der Auszahlung · Englisch · Modelle (auch ältere Antworten) |
| `dashboard_test.dart`, `multibank_test.dart`, `transaction_management_test.dart`, `planning_test.dart`, `market_test.dart` | Dashboard, weitere Konten, Umsätze, Budgets/Prognose, Marktplatz (Weiterentwicklung Schritt 3–6) |
| `credit_card_score_test.dart` | CS01–CS07: Kreditkarte einrichten, Demo-Zahlung (Ergebnis ersetzt offene Meldung), Abrechnungen · Finanz-Check mit Einwilligung, Bildschirmleser |
| `groups_test.dart`, `assistant_test.dart`, `contracts_test.dart`, `planner_test.dart`, `subaccount_card_test.dart` | G01–G05, AS01–AS05, CT01–CT05, PL01–PL06, SC01–SC02: gemeinsame Ausgaben, Finanzassistent, Verträge, Planer (mit Übergabe an den Kreditvergleich), Karten digitaler Konten |
| `dark_mode_test.dart` | DM01–DM03: neue Bildschirme im dunklen Modus ohne Fehler und mit genug Textkontrast, Plus-/Warnfarben je Modus ≥ 4,5 : 1 |
| `accessibility_test.dart` | BF01–BF04: Bedienelemente beschriftet und 48 × 48, Fehlerzustand mit „Erneut versuchen“, Live-Region |
| übrige `*_test.dart` | Geldformat, Eingabeprüfung, JSON-Modelle, CSV-Zeichensätze, Hintergrund-Abfrage |

Simulierter Server: `test/support/fake_backend.dart` (ersetzt die HTTP-Schicht), Testrahmen: `test/support/harness.dart`.

## Ende-zu-Ende (`app/integration_test`, Workflow **E2E**)

Echte App gegen echten Server mit echter Datenbank, im **Browser (Chrome)**, auf **Android-Emulatoren von Android 7.0 bis 16** (Pixel 5 oder sehr kleines Display) und auf **iPhone-Simulatoren mit iOS 16, 17, 18 und 26** – Details: [Unterstützte Versionen](versionen.md):

1. Registrieren → Startseite → Karte → Überweisen → Sparen → Beispielmonate → CSV-Import → Kreditvergleich → **Anfrage → Vertrag → Auszahlung** → Sondertilgung → **Sandbox-Bank verbinden** (Test-Freigabe) → Umsätze des Tagesgeldkontos.
2. **Zwei Nutzer:** zweiter Nutzer teilt ein digitales Konto (erscheint auf der Startseite) · überweist Geld, während die App offen ist (**Live-Meldung** und Kontostand ohne Neuladen) · Überweisung über die Oberfläche (Kontostände beider Nutzer werden über den Server nachgeprüft) · Dauerauftrag anlegen · Einnahme und Ausgabe eintragen · **Sparregel im geteilten digitalen Konto** (sofort ausgeführt, über den Server nachgeprüft).
3. **Weiterentwicklung:** gemeinsame Ausgaben (Gruppe anlegen, zweiten Nutzer hinzufügen, Ausgabe teilen; der zweite Nutzer gleicht per Überweisung aus, die Gruppe zeigt das über die Live-Meldung) · Vertrag anlegen (Kosten pro Monat) · Finanzassistent (Frage, Monatsbericht) · Vorhaben planen (Auto) und weiter zum Kreditvergleich mit übernommenem Betrag, Eigenkapital und Laufzeit · Kreditkarte (Simulation) einrichten und Demo-Zahlung (offener Betrag über den Server geprüft) · Finanz-Check nach Einwilligung.

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
| Sehr kleines Handy: Knopf „Jetzt senden“ war unter der Navigationsleiste nicht erreichbar | E2E Nexus One | Zusammenfassung scrollbar (auch Dauerauftrag-Menü) |
| Meldungen zeigten das Datum technisch („2026-11-01“) | Bildschirmfotos der Versions-Tests | deutsches Format „01.11.2026“, Test prüft alle Meldungen |
| Langsame Antwort wurde als „Server nicht erreichbar“ gemeldet | E2E iOS (langsamer Runner) | eigene Meldung, Wartezeit 30 s für langsame Mobilnetze |
| Web-App blieb ohne Internet leer (lud Grafik und Schrift von Google) | Bildschirmfotos | alles wird mitgeliefert |
| iOS: Live-Meldungen kamen erst an, nachdem die Frage nach der Benachrichtigungs-Erlaubnis beantwortet war | E2E zwei Nutzer | Live-Verbindung startet sofort |

| `pageBack()` fand den Zurück-Knopf nicht mehr (Tooltip auf Deutsch „Zurück“) | E2E Browser | eigener Zurück-Schritt über den Knopf |
| Ladeanzeige lief während der Bank-Freigabe weiter | Widget-Test BK03 | Anzeige nur während der Anfrage |
| Zweite Meldung wurde hinter der ersten angestellt und erschien erst Sekunden später | Widget-Test BK06 | neue Meldung ersetzt die alte |
| Verbundene Bank füllte den Finanzüberblick, „Beispielmonate erzeugen“ war nicht mehr sichtbar | E2E Browser | Ablauf angepasst; gewollt: echte Umsätze zählen mit |
| SQL-Abfrage der Sparregel-Quellen kaputt („WHERE“ ohne Leerzeichen am Ende eines Java-Textblocks) | lokaler Rauchtest vor dem Commit | Leerzeichen ausdrücklich (`\s`) |
| „Vorgemerkt“ wurde für Sparregeln mit „Pending“ übersetzt (gleiches Wort wie bei Bankumsätzen) | Prüfung der Übersetzungstabelle | eigener Begriff „Vormerkung erfasst“ |
| Test T02 (unter 18) maß das Alter am echten statt am simulierten Datum und schlug fehl, sobald andere Tests die Zeit weit genug vorspulten | CI nach den Sparregel-Tests | Test nutzt das Serverdatum |
| Kopfzeile „Meine Anträge“ lief bei 320 px bzw. 150 % Schrift über | Layout-Test | Status-Chip und Knopf umbrechen |
| Budget-Status rechnete mit gerundeten Prozent (Grenzfall „überschritten“ zu früh) | Q01 | Vergleich in Cent |
| Dokument abrufen gab 500: Prüfprotokoll schrieb in einer nur-lesenden Transaktion | T132 | Abruf als schreibende Transaktion |
| Antwortvorlage „Für {0} hast du {1} {2} ausgegeben“ war mehrdeutig – Englisch vertauschte Teile | lokaler Rauchtest Englisch | eindeutige Vorlage („insgesamt“) |
| Planer: Ansparen einer Immobilie scheiterte an der Sparplan-Obergrenze (100.000 €) | lokaler Rauchtest | Ansparrate direkt berechnet |
| Grün für Plus-Beträge (#2E7D32) und Gelb für „knapp“ (#E0A100) hatten als Text zu wenig Kontrast – Grün im dunklen, Gelb sogar im hellen Modus | Prüfung Dark Mode (#74) | Farben je Modus (DM03) |
| E2E Ablauf 3: Karten weiter unten in „Sparen“ wurden auf Handys nicht gefunden (Liste baut nur Sichtbares) | E2E Android/iOS | Test scrollt erst nach oben und sucht dann gezielt |
| Spartipps teilten durch alle abgeschlossenen Monate, auch solche ohne Umsätze (Schnitt zu klein) | Aufräumen #75 | ein gemeinsamer Durchschnitt nur über Monate mit Daten (H02) |
| Web-App ohne Internet: „→“ (WG-Ausgleich, Umbuchungen), „✓“ und „⚠“ als Kästchen – Roboto hat diese Zeichen nicht, Flutter lud eine Ersatzschrift von Google | Aufnahme des Demo-Videos ohne Internet | eigene Symbolschrift (Teilmenge von DejaVu Sans) als Ersatzschrift mitgeliefert, Emoji entfernt (FC01–FC03) |
| Planer: „Kredit vergleichen“ öffnete den Kreditvergleich mit Standardwerten (20.000 €, 60 Monate) statt mit den Werten des Vorhabens | Aufnahme des Demo-Videos | Server liefert Betrag und Eigenkapital je Weg, die App übergibt sie mit Zweck und Laufzeit (T143, PL04–PL06, E2E Ablauf 3) |
| Meldung der Kreditkarten-Demo-Zahlung stand hinter der Live-Meldung an und erschien erst nach dem Sprachwechsel – noch auf Deutsch | Aufnahme des Demo-Videos | neue Meldung ersetzt die offene; Sprachwechsel schließt offene und wartende Hinweise (CS07, L08) |

## Selbst ausführen

```bash
cd backend && ./gradlew test          # Bericht: build/reports/tests/test/index.html
                                       # Abdeckung: build/reports/jacoco/test/html/index.html
cd app && flutter test --coverage     # Abdeckung: coverage/lcov.info
```

Ende-zu-Ende: GitHub → **Actions → E2E → Run workflow** → Plattform wählen, *Systemversionen: alle* für alle Android- und iOS-Versionen.
