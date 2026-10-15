# 🏗️ Architektur

[← Übersicht](../../README.md)

## Überblick

```mermaid
flowchart LR
  subgraph Geräte
    A[📱 Android-App]
    I[🍏 iOS-App]
    W[🌐 Web-App]
  end
  subgraph Server["☕ Spring Boot (Port 8080)"]
    R[REST-API /api/**]
    S[WebSocket /ws – STOMP]
    J[Tagesläufe: Sparraten, Kreditraten]
    B[BookingService – doppelte Buchführung]
  end
  DB[(🐘 PostgreSQL)]
  A & I & W -- HTTP/JSON + JWT --> R
  A & I & W -- Live-Meldungen --> S
  R --> B --> DB
  J --> B
```

| Schicht | Technik |
|---|---|
| App | Flutter (Dart), Riverpod (Zustand), go_router (Navigation), dio (HTTP), STOMP (Live-Meldungen) |
| Server | Java 21, Spring Boot 3, Spring Security + JWT, JPA/JDBC, Flyway, springdoc (Swagger) |
| Datenbank | PostgreSQL 16 |
| Qualität | JUnit 5 + Testcontainers, flutter_test, integration_test, GitHub Actions |

## Umfang nach Phasen

| Phase | Backend | App |
|---|---|---|
| 1 Grundgerüst | Registrierung mit fiktiver IBAN und 1.000 € Startguthaben, Login mit JWT, Refresh-Token, Abmeldung, Sperre nach 5 Fehlversuchen, Profil, Kontoliste, doppelte Buchführung | Login, Registrierung, automatische Anmeldung, Navigation mit 4 Bereichen, Startseite, Profil, heller/dunkler Modus |
| 2 Konto | Umsätze mit Filter (Zeitraum, Richtung, Betrag, Art), Freitextsuche und Seiten; Überweisung per IBAN, E-Mail oder Telefon mit Prüfung von Deckung, Tageslimit 5.000 €, Selbstüberweisung; Zeilensperre gegen parallele Überweisungen; Idempotenz-Schlüssel gegen Doppelbuchungen; Adressbuch und Abgleich mit Telefonnummern | Letzte Umsätze auf der Startseite, Umsatzliste mit Suche, Filter, Tagesgruppen und Nachladen, Umsatzdetail, Überweisung in 3 Schritten (Eingabe, Zusammenfassung, Senden), Erfolgsseite, Adressbuch mit Schnellauswahl und Gerätekontakten |
| 3 Karte, Töpfe, Meldungen | Virtuelle Karte (Luhn-gültige Nummer, AES-GCM-verschlüsselt), Details nur mit Passwort, sperren/entsperren, Tageslimit bis 2.000 €, Online-Schalter, Händler-Simulator; Unterkonten anlegen, umbenennen, umbuchen, auflösen (max. 10); Benachrichtigungen bei jeder Buchung, Live-Versand per WebSocket (STOMP, JWT) nach dem Commit | Bereich „Karte“ mit Kartenbild, Daten 30 s sichtbar, Sperr-Schalter, Limit-Regler, Demo-Zahlung; Unterkonten auf der Startseite mit Detailseite (einzahlen, auszahlen, bearbeiten, auflösen); Glocke mit Zähler, Benachrichtigungsliste, Live-Meldung als Hinweis bzw. Systembenachrichtigung |
| 4 Finanzassistent | Kategorien je Buchungszeile, Monatsübersicht und Durchschnitt, Demo-Profile mit Beispielmonaten; Spar- und Investpläne mit Prognose, Ratenvorschlag, Ampel, fiktiven Fonds und Musterdepot; Kreditangebote von 4 fiktiven Banken (Annuität, Tilgungsplan), Abschluss und monatliche Raten; Simulationsuhr mit Tagesläufen und Admin-Vorspulen | Finanzüberblick mit Diagrammen, Kategorie ändern, Bereich „Sparen“ mit Plan-Assistent und Plan-Detail, Kreditplaner, Angebotsvergleich, Tilgungsplan, Kreditdetail, Demo-Modus im Profil |
| 5 Kannkriterien | /K010/ Erinnerung an die Sparrate: Spalte `reminder_days` (Standard 3, 0 = aus), Tageslauf meldet X Tage vor der Abbuchung, warnt bei zu wenig Guthaben, höchstens einmal pro Monat; /K020/ Sondertilgung: Tabelle `loan_extra_payment`, Buchungsart `LOAN_EXTRA`, Vorschau und Buchung, Tilgungsplan mit fester Rate und Sondertilgungen (`LoanMath.schedule(…, extras)`), Laufzeit und Zinsen sinken | Einstellung „Erinnerung vor der Abbuchung“ im Plan, Seite „Sondertilgung“ mit Schnellbeträgen, Vorher-/Nachher-Vergleich und Zinsersparnis; Kreditdetail zeigt voraussichtliches Ende, Sondertilgungen und gesparte Zinsen; Dialoge auf kleinen Bildschirmen scrollbar |
| 6 Weitere Kannkriterien | /K030/ Daueraufträge (`standingorder`, Tageslauf Nr. 25, Ausführung über `TransferService` mit Idempotenz-Schlüssel); /K050/ Zwei-Faktor-Anmeldung (`Totp` nach RFC 6238 ohne Zusatzbibliothek, Schlüssel AES-GCM-verschlüsselt, Prüfung in echter Zeit, Schutz vor Wiederverwendung); /K040/ CSV-Import (`csvimport`, erkennt Spalten deutscher Banken, Duplikatschutz per SHA-256, eigene Tabelle – zählt im Finanzüberblick, ändert keine Kontostände); /K060/ geteilte Unterkonten (`account_member`, Zugriff über `AccountService.requireAccess`); Simulationsuhr rechnet in Kalendertagen (Zeitumstellung) | Daueraufträge (Liste, Anlegen, Ändern, Pausieren); Zwei-Faktor-Einrichtung mit QR-Code, Code-Feld beim Login; CSV-Import-Seite mit Beispieldatei; Teilen-Bereich im Unterkonto; Android-Hintergrundabfrage für Meldungen bei geschlossener App (WorkManager) |

| Tests | Inhalt |
|---|---|
| Backend | IBAN, Buchungsregel, Telefonnummern, Luhn-Prüfziffer, Verschlüsselung, Kategorien, Prognose/Ratenvorschlag, Fondspreise, Annuität/Tilgungsplan, Tilgungsplan mit Sondertilgung; Integrationstests mit echter Datenbank für T01 bis T40 (Login, Überweisung, Karte, Unterkonten, Beispielmonate, Kategorie ändern, Machbarkeit, Spar- und Investplan mit Vorspulen, Auszahlen, Kreditangebote, Abschluss, Kreditrate, Ablehnung, Admin-Recht, Erinnerung, Sondertilgung, ungültige Sondertilgung, Komplett-Rückzahlung, Daueraufträge, Zwei-Faktor-Anmeldung, CSV-Import, geteilte Unterkonten) |
| App | Geldformat und -eingabe, Eingabeprüfungen, Filter-Parameter, Idempotenz-Schlüssel, Kartendaten, Benachrichtigungen, Monatsübersicht, Pläne, Kredite, Login-Formular |

---

### Backend (`backend/src/main/java/de/frauas/girokonto`)

| Paket | Aufgabe |
|---|---|
| `auth` | Registrierung, Login, JWT, Refresh-Token, Sicherheitskonfiguration |
| `user` | Benutzer-Entität, Profil anzeigen/ändern, Passwort ändern |
| `account` | Konten; beim Registrieren wird das Girokonto mit IBAN angelegt; Endpunkt für Umsätze |
| `booking` | **Einzige Stelle, die Geld bewegt** (`BookingService`). Jede Buchung hat mind. zwei Zeilen mit Summe 0. `BookingQueryService` liest Umsätze mit Filtern |
| `transfer` | Überweisungen: Empfänger finden (`RecipientResolver`), prüfen und buchen (`TransferService`) |
| `contact` | Adressbuch und Abgleich mit Telefonnummern aus dem Handy |
| `card` | Virtuelle Karte: Nummer erzeugen (Luhn), verschlüsseln (`CardCrypto`, AES-256-GCM), sperren, Limits, Händler-Simulator |
| `subaccount` | Digitale Konten (früher Unterkonten): anlegen mit Zweck/Ziel, ändern, umbuchen, teilen mit Rollen, auflösen |
| `banking` | Echte Bankkonten über Open Banking/PSD2 (nur lesen): Anbieter-Schnittstelle, Sandbox, GoCardless, Abgleich, Widerruf – siehe [Open Banking](open-banking.md) |
| `audit` | Prüfprotokoll sicherheitsrelevanter Aktionen (`GET /api/me/audit-log`) |
| `i18n` | Englische Server-Texte (`ServerTexts`, `en.tsv`), Übersetzung der Antworten bei `Accept-Language: en` |
| `notification` | Benachrichtigungen speichern, auflisten, gelesen markieren; WebSocket-Konfiguration (`WebSocketConfig`) |
| `insights` | Kategorien (`CategoryService`), Monatsübersicht und Durchschnitte (`InsightsService`), Demo-Profile und Beispielmonate (`DemoDataService`) |
| `savings` | Spar- und Investpläne (`SavingsPlanService`), Risikostufen, fiktive Fonds (`Fund`), Prognoseformeln (`PlanMath`) |
| `loan` | Kreditzwecke und Zinsregeln (`LoanPurpose`), fiktive Banken (`Bank`) mit Mindest-Bonität, Bonitätsklassen (`CreditClass`), Annuitätenrechnung (`LoanMath`), Vergleich mit Filtern, getrennte Schritte Anfrage → Vertrag → Auszahlung, Raten (`LoanService`) |
| `loan/provider` | Anbieter-Schnittstelle `LoanOfferProvider`, fiktive Banken (`SimulatedBanksProvider`), Referenzzinsen der Bundesbank (`MarketRateService`) – siehe [Sparregeln und Kredite](sparregeln-und-kredite.md) |
| `allocation` | Sparregeln für digitale Konten: Rhythmen, Quellen (Girokonto = Umbuchung, verbundenes Bankkonto = Vormerkung), Vorgänge mit Status und Wiederholung, Tageslauf Nr. 35 |
| `simulation` | Verschiebbare Uhr (`SimulationClock`), Tagesläufe (`SimulationService`, `DailyJob`), nächtlicher Lauf (`DailyScheduler`), Admin-Endpunkt zum Vorspulen |
| `common` | IBAN-Generator, einheitliche Fehlermeldungen |
| `config` | Einstellungen aus `application.yml`, zentrale Uhr (später für den Demo-Modus) |

**Ablauf Registrierung:** `AuthController.register` → `AuthService.register` (prüft Alter, doppelte E-Mail/Telefon) → Benutzer speichern → `AccountService.openMainAccount` (nächste Kontonummer aus der Datenbank-Sequenz, IBAN nach ISO 13616) → `BookingService.post` bucht 1.000 € vom System-Konto `BANK` auf das neue Konto → `TokenService.issue` gibt Access- und Refresh-Token zurück. Alles läuft in **einer** Datenbanktransaktion: schlägt ein Schritt fehl, wird nichts gespeichert.

**Kontostand:** wird nie als Zahl gespeichert, sondern immer als Summe aller Buchungszeilen des Kontos berechnet (`BookingLineRepository.balanceOf`). Die Summe aller Buchungszeilen im System ist immer 0 (Test T15).

**Anmeldung und Sicherheit:**
* Passwörter nur als BCrypt-Hash.
* Access-Token (JWT) 15 Minuten gültig, enthält Nutzer-ID, Rolle und `ver` (Token-Version).
* Refresh-Token 7 Tage gültig, in der Datenbank nur als SHA-256-Hash, jedes nur einmal nutzbar.
* Abmelden erhöht die Token-Version, dadurch sind alte Access-Tokens sofort ungültig.
* Nach 5 falschen Passwörtern ist die Anmeldung 5 Minuten gesperrt (HTTP 423).
* `JwtAuthFilter` prüft bei jeder Anfrage das Token; Controller erhalten den Nutzer über `@AuthenticationPrincipal AuthUser`. Daten werden immer nur für diese Nutzer-ID geladen.

**Ablauf Überweisung (`TransferService.execute`):**
1. Girokonto des Senders mit `SELECT ... FOR UPDATE` sperren. Kommen zwei Überweisungen gleichzeitig, wartet die zweite, bis die erste fertig ist. So kann das Konto nicht durch parallele Anfragen überzogen werden.
2. Idempotenz: Die App schickt im Header `Idempotency-Key` eine zufällige UUID, die pro Zusammenfassung einmal erzeugt wird. Wurde dieser Schlüssel schon gebucht (z. B. doppeltes Tippen, Netzwerk-Wiederholung), kommt das alte Ergebnis zurück, ohne erneut zu buchen.
3. Empfänger suchen (IBAN, E-Mail oder Telefonnummer; Telefonnummern werden vereinheitlicht, z. B. `+49 151 …` = `0151…`).
4. Prüfungen: Betrag > 0, nicht an sich selbst, Deckung, Tageslimit (Summe der heutigen Überweisungs-Ausgänge + Betrag ≤ 5.000 €).
5. `BookingService.post` bucht −Betrag beim Sender und +Betrag beim Empfänger in derselben Transaktion.

**Kartenzahlung (`CardService.pay`):** Girokonto sperren → prüfen in dieser Reihenfolge: Karte gesperrt? Online-Zahlung erlaubt? Tageslimit (heutige Kartenzahlungen + Betrag)? Deckung? Bei Ablehnung gibt es keine Buchung, aber eine Benachrichtigung mit Grund. Bei Erfolg bucht der `BookingService` vom Girokonto auf das System-Konto `HAENDLER`. Die volle Kartennummer und CVC liegen nur verschlüsselt in der Datenbank; angezeigt werden sie nur nach Passwortprüfung.

> Abweichung vom Pflichtenheft-Entwurf: Der Händler-Simulator liegt unter `POST /api/simulator/card-payment` und zahlt mit der Karte des angemeldeten Nutzers („Demo-Taste“ in der App), statt nur für Administratoren.

**Simulationszeit und Tagesläufe (Phase 4):** Alle Klassen fragen die Zeit über eine gemeinsame `Clock` ab. Das ist eine `SimulationClock`, die die echte Zeit um eine Anzahl Tage verschiebt (gespeichert in `sim_clock`). Für jedes Datum führt `SimulationService.runDay` genau einmal (Tabelle `job_run`) alle `DailyJob`s aus: 1. Beispielbuchungen der Demo-Profile, 2. fällige Spar-/Investraten, 3. fällige Kreditraten. Normal läuft das jede Nacht um 00:05; beim Vorspulen wird die Uhr Tag für Tag weitergestellt und für jeden Tag `runDay` aufgerufen. Anmelde-Tokens laufen bewusst in **echter** Zeit, damit Nutzer nach dem Vorspulen nicht abgemeldet werden.

**Finanzüberblick:** Ausgewertet wird nur das Girokonto. Jede Buchungszeile hat eine Kategorie (Spalte `booking_line.category`), vergeben vom `CategoryService` nach Buchungstyp und Schlüsselwörtern. Einnahmen = Summe der Einnahme-Kategorien, Ausgaben = Summe der Ausgabe-Kategorien; Umbuchungen, Sparraten, Startguthaben und Kreditauszahlungen zählen nicht mit. „Verfügbar für Pläne“ = Ø freier Betrag der letzten 3 vollen Monate − Raten laufender Pläne.

**Sparrate (`SavingsPlanService.execute`):** Hauptkonto −Rate; Sparplan-Unterkonto +Sparkonto-Anteil; System-Konto „DEPOT“ +Depot-Anteil. Mit dem Depot-Anteil werden Fondsanteile zum Preis des Monats gekauft (`depot_position`). Wert des Plans = Unterkonto + Σ Anteile × aktueller Preis. Die Formeln für Prognose und Ratenvorschlag stehen in `PlanMath` und im Pflichtenheft Kap. 8.3.

**Sondertilgung (`LoanService.extraPayment`, /K020/):** Girokonto −Betrag, Kreditkonto +Betrag (Buchungsart `LOAN_EXTRA`, Kategorie „Sondertilgung“, zählt nicht als Ausgabe). Gespeichert wird, nach welcher Rate gezahlt wurde (`loan_extra_payment.after_installment`). Der Tilgungsplan wird mit **fester Monatsrate** neu berechnet: Sondertilgungen senken die Restschuld sofort, dadurch sinken die folgenden Zinsen und der Kredit endet früher. Deckt der Betrag die ganze Restschuld, ist der Kredit sofort abbezahlt.

**Erinnerung (`SavingsPlanService.remind`, /K010/):** Läuft im Tageslauf direkt nach den Sparraten. Ist heute + `reminder_days` der Ausführungstag, gibt es eine Benachrichtigung; `last_reminded_month` verhindert doppelte Erinnerungen.

**Kredit (`LoanService`):** Angebote: Zins = Basiszins des Zwecks + Laufzeitaufschlag − Eigenkapitalrabatt + Aufschlag der Bank + Aufschlag nach Bonität (Selbsteinschätzung); Rate nach Annuitätenformel (`LoanMath`), effektiver Jahreszins = (1 + Sollzins/12)^12 − 1. Eine Bank lehnt ab, wenn die Bonität unter ihrer Mindestklasse oder die Rate über ihrer Grenze liegt (60–80 % von „verfügbar“). Die Schritte sind getrennt gespeichert: Anfrage (`loan_application`, Zusage 14 Tage gültig) → Vertrag (`loan.status = CONTRACTED`, Kreditkonto ohne Buchung) → Auszahlung (Kreditkonto −Betrag, Girokonto +Betrag, Idempotenz-Schlüssel `loan-payout:<id>`, Status `ACTIVE`). Jede Rate: Girokonto −Rate, Kreditkonto +Tilgung, System-Konto „ZINSEN“ +Zins. Die Restschuld ist der negative Saldo des Kreditkontos. Details: [Sparregeln und Kredite](sparregeln-und-kredite.md).

**Sparregeln (`AllocationService`):** Tageslauf Nr. 35 (nach Daueraufträgen und Kreditraten). Für jeden fälligen Termin entsteht genau ein Vorgang (`allocation_run`, eindeutig je Regel und Datum); ausgeführt wird unter Zeilensperre. Quelle Girokonto: Umbuchung `INTERNAL` mit Idempotenz-Schlüssel `alloc-<Regel>-<Datum>`. Quelle verbundenes Bankkonto: nur Vormerkung (keine Buchung), vorher Verbindung, Aktualität des Kontostands (≤ 48 h) und freie Deckung geprüft. Fehlschläge werden an den Folgetagen wiederholt (höchstens 3 Versuche). Vormerkungen zählen zum Ziel eines digitalen Kontos (`digital.earmarkedCents`, `digital.savedCents`); „Ziel erreicht“ prüft der gemeinsame `GoalTracker`.

**Live-Benachrichtigungen:**
1. Jede Buchung ruft `NotificationService.send(...)` auf. Die Meldung wird in der Tabelle `notification` gespeichert.
2. Der Versand per WebSocket passiert erst **nach dem Commit** (`TransactionSynchronization.afterCommit`). Wird die Buchung zurückgerollt, geht keine Meldung raus.
3. Die App verbindet sich mit `ws://<server>:8080/ws` (STOMP) und schickt beim CONNECT das Access-Token. `WebSocketConfig` prüft es und merkt sich die Nutzer-ID. Danach abonniert die App `/user/queue/notifications` und erhält nur eigene Meldungen.
4. Bricht die Verbindung ab, verbindet die App nach 5 Sekunden mit frischem Token neu (`RealtimeService`).
5. Ist die App im Vordergrund, erscheint ein Hinweis unten im Bild; im Hintergrund eine Systembenachrichtigung (iOS/Android).

**Umsatzliste (`BookingQueryService`):** SQL-Abfrage über `booking_line` des Kontos. Die Gegenpartei („an/von wem“) ist die andere Zeile derselben Buchung. Filter werden nur angehängt, wenn sie gesetzt sind; Werte gehen immer als Parameter in die Abfrage (Schutz vor SQL-Injection). Es wird eine Zeile mehr geladen als angezeigt, um zu wissen, ob es weitere Seiten gibt.

**Zwei Kontenbereiche:** Die Startseite trennt **Bankkonten** (Girokonto der Projektbank – ausdrücklich als
Simulation gekennzeichnet – und verbundene Konten echter Banken, nur lesend) von **digitalen Konten**. Ein digitales
Konto ist eine eigene Zeile im Hauptbuch der Projektbank (Typ `SUB`): Ein- und Auszahlungen sind echte Umbuchungen
innerhalb der Projektbank (Buchungsart `INTERNAL`), es ist aber kein eigenes Bankkonto und hat keine eigene
Einlagensicherung – das steht in jeder Antwort (`digital.custodyText`) und in der App. Zweck, Zielbetrag und Zieldatum
ergeben Fortschritt, Restbetrag und die nötige Monatsrate; „Ziel erreicht“ wird einmal gemeldet (erneut erst, wenn der
Stand zwischendurch unter das Ziel fiel). Geteilte digitale Konten haben Rollen: einzahlen + auszahlen, nur einzahlen,
nur ansehen – geprüft bei jeder Umbuchung. Das Gesamtvermögen enthält die Euro-Salden verbundener Banken als eigenen Posten.

**Datenbank:** Schema nur über Flyway-Dateien in `src/main/resources/db/migration`. Neue Tabellen in späteren Phasen kommen als `V2__...sql`, `V3__...sql` usw. Bestehende Migrationsdateien niemals ändern.

### App (`app/lib`)

| Datei / Ordner | Aufgabe |
|---|---|
| `main.dart` | Start, Theme, Router |
| `core/api_client.dart` | HTTP-Client (dio): hängt Token an, erneuert es bei 401 automatisch einmal |
| `core/token_storage.dart` | Tokens sicher speichern |
| `core/router.dart` | Alle Seiten; nicht angemeldet → Login, angemeldet → Startseite |
| `core/money.dart` | Beträge in Cent als „1.234,56 €“ anzeigen |
| `features/auth/` | Login, Registrierung, `AuthController` (wer ist angemeldet?) |
| `features/accounts/` | Startseite, Umsatzliste mit Suche/Filter/Nachladen, Umsatzdetail |
| `features/transfer/` | Überweisung: Formular, Zusammenfassung (prüft per `/api/transfers/check`), Senden mit Idempotenz-Schlüssel, Erfolgsseite |
| `features/contacts/` | Adressbuch, Hinzufügen, Löschen (wischen), Gerätekontakte abgleichen |
| `features/card/` | Kartenbild, Kartendaten mit Passwort, Sperren, Limits, Händler-Simulator |
| `features/subaccounts/` | Digitales Konto anlegen/bearbeiten (Zweck, Ziel, Datum), Detail mit Fortschritt, Ein-/Auszahlen, Teilen mit Rollen, Auflösen |
| `features/banking/` | Echte Bankkonten: verbinden (Sandbox-Test-Freigabe bzw. Bankseite), Verwalten, Umsätze mit Suche/Filter, eigene Angaben |
| `l10n/` | Deutsch/Englisch: `tr('Deutscher Text')`, `strings_en.dart`, Sprachwahl |
| `features/notifications/` | Benachrichtigungsliste, Zähler, Live-Verbindung starten (`realtime_provider.dart`), Systembenachrichtigungen |
| `features/insights/` | Finanzüberblick (Diagramme, Kategorien, Durchschnitt), Beispielmonate |
| `features/savings/` | Bereich „Sparen“, Plan-Assistent, Plan-Detail mit Depot und Spartipps |
| `features/loans/` | Kreditplaner mit Bonität und Filtern, Angebotsvergleich (Rechenbeispiele, Bundesbank-Referenz, Anbieterstatus), Angebot → Anfrage → Vertrag → Auszahlung, Tilgungsplan, Kreditdetail |
| `features/allocations/` | „Automatisch sparen“ im digitalen Konto: Sparregeln anlegen/ändern/pausieren/beenden, Vorgänge mit Status und „erneut versuchen“, Vormerkungen |
| `core/charts.dart` | Einfache Diagramme ohne Zusatzpaket (Balken, Linie, Kategorien, Ampel) |
| `core/realtime_service.dart` | WebSocket/STOMP-Verbindung mit automatischem Neuverbinden |
| `features/profile/` | Profil, Daten und Passwort ändern, Abmelden |
| `features/shell/` | Navigationsleiste unten, Platzhalter für spätere Phasen |

Zustandsverwaltung mit **Riverpod**: Bildschirme lesen Daten über Provider (`ref.watch(accountsProvider)`), die Provider holen sie über Repositories vom Backend.
