# Smartphone-Girokonto mit Sparzielen

Projekt Informatik WiSe 26/27, FRA-UAS (Prof. Dr.-Ing. Ruth Schorr)
Plattformübergreifende App mit **Flutter** (iOS, Android, Web), Backend mit **Spring Boot**, Datenbank **PostgreSQL**.

> Alle Konten, IBANs und Beträge sind simuliert. Es wird kein echtes Geld bewegt.

**Stand: Phase 4.** Umgesetzt sind alle Musskriterien aus dem Pflichtenheft **Version 0.2** (`docs/Pflichtenheft_Girokonto_v0.2.pdf`): /F010/ bis /F950/ und /F1010/ bis /F1070/.

| Phase | Backend | App |
|---|---|---|
| 1 Grundgerüst | Registrierung mit fiktiver IBAN und 1.000 € Startguthaben, Login mit JWT, Refresh-Token, Abmeldung, Sperre nach 5 Fehlversuchen, Profil, Kontoliste, doppelte Buchführung | Login, Registrierung, automatische Anmeldung, Navigation mit 4 Bereichen, Startseite, Profil, heller/dunkler Modus |
| 2 Konto | Umsätze mit Filter (Zeitraum, Richtung, Betrag, Art), Freitextsuche und Seiten; Überweisung per IBAN, E-Mail oder Telefon mit Prüfung von Deckung, Tageslimit 5.000 €, Selbstüberweisung; Zeilensperre gegen parallele Überweisungen; Idempotenz-Schlüssel gegen Doppelbuchungen; Adressbuch und Abgleich mit Telefonnummern | Letzte Umsätze auf der Startseite, Umsatzliste mit Suche, Filter, Tagesgruppen und Nachladen, Umsatzdetail, Überweisung in 3 Schritten (Eingabe, Zusammenfassung, Senden), Erfolgsseite, Adressbuch mit Schnellauswahl und Gerätekontakten |
| 3 Karte, Töpfe, Meldungen | Virtuelle Karte (Luhn-gültige Nummer, AES-GCM-verschlüsselt), Details nur mit Passwort, sperren/entsperren, Tageslimit bis 2.000 €, Online-Schalter, Händler-Simulator; Unterkonten anlegen, umbenennen, umbuchen, auflösen (max. 10); Benachrichtigungen bei jeder Buchung, Live-Versand per WebSocket (STOMP, JWT) nach dem Commit | Bereich „Karte“ mit Kartenbild, Daten 30 s sichtbar, Sperr-Schalter, Limit-Regler, Demo-Zahlung; Unterkonten auf der Startseite mit Detailseite (einzahlen, auszahlen, bearbeiten, auflösen); Glocke mit Zähler, Benachrichtigungsliste, Live-Meldung als Hinweis bzw. Systembenachrichtigung |

| Tests | Inhalt |
|---|---|
| Backend | IBAN, Buchungsregel, Telefonnummern, Luhn-Prüfziffer, Verschlüsselung, Kategorien, Prognose/Ratenvorschlag, Fondspreise, Annuität/Tilgungsplan; Integrationstests mit echter Datenbank für T01 bis T25 (Login, Überweisung, Karte, Unterkonten, Beispielmonate, Kategorie ändern, Machbarkeit, Spar- und Investplan mit Vorspulen, Auszahlen, Kreditangebote, Abschluss, Kreditrate, Ablehnung, Admin-Recht) |
| App | Geldformat und -eingabe, Eingabeprüfungen, Filter-Parameter, Idempotenz-Schlüssel, Kartendaten, Benachrichtigungen, Monatsübersicht, Pläne, Kredite, Login-Formular |

---

## 1. Ordnerstruktur

```
girokonto/
├── backend/              Spring-Boot-Backend (Java 21, Gradle)
├── app/                  Flutter-App (nur lib/, test/, pubspec.yaml; Plattform-Ordner erzeugt Schritt 4)
├── docs/                 Pflichtenheft (Version 0.2)
├── docker-compose.yml    PostgreSQL-Datenbank
└── .github/workflows/    automatische Tests bei jedem Push
```

---

## 2. Einmalig installieren

| Programm | Version | Prüfen mit |
|---|---|---|
| Git | aktuell | `git --version` |
| Docker Desktop | aktuell, muss **laufen** | `docker --version` |
| Java JDK | **21** (z. B. Eclipse Temurin 21) | `java -version` |
| Flutter SDK | 3.24 oder neuer | `flutter --version` |
| Android Studio | für Android-Emulator | – |
| Xcode | nur auf dem Mac, für iOS | – |
| Google Chrome | für die Web-Version | – |

Danach `flutter doctor` ausführen und alles beheben, was rot markiert ist (für Web reicht Chrome).

Gradle muss **nicht** installiert werden, das Projekt bringt den Gradle-Wrapper (`gradlew`) mit.

---

## 3. Backend starten

Alle Befehle im Terminal, ausgehend vom Ordner `girokonto`.

**Schritt 3.1: Datenbank starten**

```bash
docker compose up -d
```

Prüfen: `docker ps` zeigt einen Container `girokonto-db`.

**Schritt 3.2: Backend starten**

macOS / Linux:
```bash
cd backend
chmod +x gradlew
./gradlew bootRun
```

Windows (PowerShell):
```powershell
cd backend
.\gradlew.bat bootRun
```

Der erste Start dauert einige Minuten (Abhängigkeiten werden geladen). Fertig, wenn im Log `Started GirokontoApplication` steht. Flyway legt die Tabellen dabei automatisch an.

**Schritt 3.3: Prüfen**

Im Browser öffnen: <http://localhost:8080/swagger-ui.html>
Dort sind alle Endpunkte sichtbar und können direkt ausprobiert werden (zuerst `POST /api/auth/register`, dann das `accessToken` oben rechts unter „Authorize“ eintragen).

Das Terminal mit dem Backend offen lassen. Beenden mit `Strg + C`.

---

## 4. App einrichten (einmalig)

Die Plattform-Ordner (`android/`, `ios/`, `web/`) erzeugt Flutter selbst. Bereits vorhandene Dateien werden dabei **nicht** überschrieben.

```bash
cd app
flutter create . --platforms=android,ios,web --org de.frauas --project-name girokonto_app
flutter pub get
```

**Nur für Android:** Weil das Backend in der Entwicklung über `http` (ohne s) läuft, muss Android das erlauben.
Datei `app/android/app/src/main/AndroidManifest.xml` öffnen und im Tag `<application ...>` ergänzen:

```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

**Nur für Android, Adressbuch und Benachrichtigungen (ab Phase 2/3):** in derselben Datei **oberhalb** von `<application` einfügen:

```xml
<uses-permission android:name="android.permission.READ_CONTACTS"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

**Nur für Android, Systembenachrichtigungen (ab Phase 3):** Das Paket `flutter_local_notifications` braucht „Core Library Desugaring“. Öffne `app/android/app/build.gradle.kts` (neuere Flutter-Versionen) **oder** `app/android/app/build.gradle` (ältere) und ergänze:

Variante `build.gradle.kts`:
```kotlin
android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        // die vorhandenen Zeilen sourceCompatibility/targetCompatibility bleiben
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
```

Variante `build.gradle`:
```groovy
android {
    compileOptions {
        coreLibraryDesugaringEnabled true
    }
}

dependencies {
    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.1.4'
}
```

Gibt es `compileOptions { ... }` oder `dependencies { ... }` schon, nur die jeweilige Zeile in den vorhandenen Block einfügen.

**Nur für iOS:** in `app/ios/Runner/Info.plist` vor dem letzten `</dict>` einfügen. Der erste Eintrag ist für das Adressbuch nötig, der zweite nur, falls die Verbindung zum Backend blockiert wird:

```xml
<key>NSContactsUsageDescription</key>
<string>Damit du Freunde findest, die diese App nutzen, und ihnen Geld senden kannst.</string>
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsLocalNetworking</key>
    <true/>
</dict>
```

Nach einem Update auf eine neue Phase immer `flutter pub get` ausführen.

---

## 5. App starten

Das Backend aus Schritt 3 muss laufen.

| Ziel | Befehl (im Ordner `app`) |
|---|---|
| Browser (Chrome) | `flutter run -d chrome` |
| Android-Emulator | `flutter run --dart-define=API_URL=http://10.0.2.2:8080` |
| iOS-Simulator (Mac) | `flutter run` |
| Echtes Handy im gleichen WLAN | `flutter run --dart-define=API_URL=http://<IP-deines-PCs>:8080` |

Hinweise:
* Im Android-Emulator ist `10.0.2.2` die Adresse des eigenen PCs.
* Die IP des PCs findest du mit `ipconfig` (Windows) bzw. `ipconfig getifaddr en0` (Mac).
* Mit `flutter devices` siehst du alle verfügbaren Geräte.

**Ausprobieren:**
1. „Jetzt registrieren“ → Formular ausfüllen (mind. 18 Jahre, Passwort mind. 10 Zeichen) → Startseite mit 1.000,00 € und IBAN.
2. Abmelden (Profil) und einen **zweiten Nutzer** registrieren (andere E-Mail und Telefonnummer). Am einfachsten: zweites Browserfenster im Inkognito-Modus.
3. Im Bereich „Überweisen“ die E-Mail, Telefonnummer oder IBAN des anderen Nutzers eingeben, Betrag z. B. `25,50` → „Weiter“ → Name prüfen → „Jetzt senden“.
4. Auf der Erfolgsseite den Empfänger im Adressbuch speichern; danach erscheint er oben in der Schnellauswahl.
5. Startseite → „Alle anzeigen“: Umsätze durchsuchen (z. B. Name oder Verwendungszweck) und über das Filtersymbol filtern. Tippen öffnet die Details.
6. Fehlerfälle testen: mehr Geld als vorhanden, an sich selbst, unbekannte E-Mail.
7. **Live-Meldungen:** beide Nutzer gleichzeitig angemeldet lassen (zwei Fenster). Überweist Nutzer A, erscheint bei B sofort ein Hinweis, die Glocke zählt hoch und der Kontostand aktualisiert sich ohne Neuladen.
8. **Karte:** Bereich „Karte“ → „Kartendaten zeigen“ (Passwort) → Nummer und CVC sind 30 Sekunden sichtbar. „Demo: Kartenzahlung simulieren“ → Händler wählen → Bezahlen. Danach Karte sperren und erneut bezahlen: die Zahlung wird abgelehnt. Tageslimit z. B. auf 50 € stellen und eine Zahlung über 50 € versuchen.
9. **Phase 4 – Finanzassistent (am besten in dieser Reihenfolge):**
   1. Bereich „Sparen“ → „Beispielmonate erzeugen“ → Profil „Berufseinsteiger“. Die App bucht 6 Monate Gehalt, Miete, Einkäufe usw.
   2. Karte „Jeden Monat übrig“ antippen → **Finanzüberblick** mit Balkendiagramm und Kategorien. Unter „Konto → Alle anzeigen“ einen Umsatz öffnen und die Kategorie ändern.
   3. **Sparplan:** „Sparplan“ → Ziel (z. B. Urlaub, 3.000 €, in 18 Monaten) → Risikostufe → die App schlägt die Rate vor und zeigt die Ampel. Mit einem sehr hohen Ziel (z. B. 50.000 € in 12 Monaten) erscheint Rot mit einem Alternativvorschlag.
   4. **Investplan:** „Investplan“ → 200 € → „Ausgewogen“ → anlegen.
   5. **Kredit:** „Kredit“ → Auto, 20.000 €, 60 Monate → „Angebote vergleichen“ → empfohlenes Angebot öffnen → Tilgungsplan ansehen → abschließen. Der Betrag ist sofort auf dem Girokonto.
   6. **Zeit vorspulen:** abmelden und als Administrator anmelden (`admin@projektbank.de`, Passwort `admin12345`) → Profil → „Demo-Modus“ → „+3 Monate“. Danach wieder als Nutzer anmelden: Sparraten, Investraten und Kreditraten sind gebucht, das Depot hat einen Verlauf, die Restschuld ist gesunken, und es gibt Benachrichtigungen.
10. **Unterkonten:** Startseite → „Neues Unterkonto“ → Name und Symbol → antippen → „Einzahlen“. Das Gesamtvermögen bleibt gleich. „Unterkonto auflösen“ bucht das Guthaben zurück.

---

## 6. Tests ausführen

Backend (Docker muss laufen, weil der Integrationstest eine echte PostgreSQL-Datenbank startet):
```bash
cd backend
./gradlew test
```
Bericht danach unter `backend/build/reports/tests/test/index.html`.

App:
```bash
cd app
flutter test
flutter analyze
```

---

## 7. GitHub einrichten

```bash
cd girokonto
git init
git add .
git commit -m "Phase 1: Grundgerüst Backend und App (F010-F030, F110)"
# nach dem Einspielen von Phase 2:
# git add . && git commit -m "Phase 2: Umsätze, Überweisung, Adressbuch (F120-F250)"
# git add . && git commit -m "Phase 3: Karte, Unterkonten, Benachrichtigungen (F310-F520)"
# git add . && git commit -m "Phase 4: Finanzüberblick, Spar-/Investplan, Kreditplan, Demo-Modus (F560-F1070)"
git branch -M main
git remote add origin https://github.com/<team>/girokonto.git
git push -u origin main
```

Arbeitsweise im Team (Pflichtenheft Kap. 13.3):
* Pro Aufgabe ein Branch, z. B. `feature/F210-ueberweisung`
* Commit-Nachrichten beginnen mit der Anforderungs-ID, z. B. `F230: Deckung prüfen`
* Änderungen über Pull-Request, Review durch ein anderes Teammitglied
* GitHub Actions (`.github/workflows/ci.yml`) führt bei jedem Push alle Tests aus

---

## 8. Wie der Code funktioniert

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
| `subaccount` | Unterkonten anlegen, umbenennen, umbuchen, auflösen |
| `notification` | Benachrichtigungen speichern, auflisten, gelesen markieren; WebSocket-Konfiguration (`WebSocketConfig`) |
| `insights` | Kategorien (`CategoryService`), Monatsübersicht und Durchschnitte (`InsightsService`), Demo-Profile und Beispielmonate (`DemoDataService`) |
| `savings` | Spar- und Investpläne (`SavingsPlanService`), Risikostufen, fiktive Fonds (`Fund`), Prognoseformeln (`PlanMath`) |
| `loan` | Kreditzwecke und Zinsregeln (`LoanPurpose`), fiktive Banken (`Bank`), Annuitätenrechnung (`LoanMath`), Angebote, Abschluss, Raten (`LoanService`) |
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

**Kredit (`LoanService`):** Angebote: Zins = Basiszins des Zwecks + Laufzeitaufschlag − Eigenkapitalrabatt + Aufschlag der Bank; Rate nach Annuitätenformel (`LoanMath`). Eine Bank lehnt ab, wenn die Rate über ihrer Grenze liegt (60–80 % von „verfügbar“). Abschluss: Kreditkonto (Typ LOAN) −Betrag, Girokonto +Betrag. Jede Rate: Girokonto −Rate, Kreditkonto +Tilgung, System-Konto „ZINSEN“ +Zins. Die Restschuld ist der negative Saldo des Kreditkontos.

**Live-Benachrichtigungen:**
1. Jede Buchung ruft `NotificationService.send(...)` auf. Die Meldung wird in der Tabelle `notification` gespeichert.
2. Der Versand per WebSocket passiert erst **nach dem Commit** (`TransactionSynchronization.afterCommit`). Wird die Buchung zurückgerollt, geht keine Meldung raus.
3. Die App verbindet sich mit `ws://<server>:8080/ws` (STOMP) und schickt beim CONNECT das Access-Token. `WebSocketConfig` prüft es und merkt sich die Nutzer-ID. Danach abonniert die App `/user/queue/notifications` und erhält nur eigene Meldungen.
4. Bricht die Verbindung ab, verbindet die App nach 5 Sekunden mit frischem Token neu (`RealtimeService`).
5. Ist die App im Vordergrund, erscheint ein Hinweis unten im Bild; im Hintergrund eine Systembenachrichtigung (iOS/Android).

**Umsatzliste (`BookingQueryService`):** SQL-Abfrage über `booking_line` des Kontos. Die Gegenpartei („an/von wem“) ist die andere Zeile derselben Buchung. Filter werden nur angehängt, wenn sie gesetzt sind; Werte gehen immer als Parameter in die Abfrage (Schutz vor SQL-Injection). Es wird eine Zeile mehr geladen als angezeigt, um zu wissen, ob es weitere Seiten gibt.

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
| `features/subaccounts/` | Unterkonto anlegen/bearbeiten (Dialog), Detailseite mit Ein-/Auszahlen und Auflösen |
| `features/notifications/` | Benachrichtigungsliste, Zähler, Live-Verbindung starten (`realtime_provider.dart`), Systembenachrichtigungen |
| `features/insights/` | Finanzüberblick (Diagramme, Kategorien, Durchschnitt), Beispielmonate |
| `features/savings/` | Bereich „Sparen“, Plan-Assistent, Plan-Detail mit Depot und Spartipps |
| `features/loans/` | Kreditplaner, Angebotsvergleich, Tilgungsplan, Kreditdetail |
| `core/charts.dart` | Einfache Diagramme ohne Zusatzpaket (Balken, Linie, Kategorien, Ampel) |
| `core/realtime_service.dart` | WebSocket/STOMP-Verbindung mit automatischem Neuverbinden |
| `features/profile/` | Profil, Daten und Passwort ändern, Abmelden |
| `features/shell/` | Navigationsleiste unten, Platzhalter für spätere Phasen |

Zustandsverwaltung mit **Riverpod**: Bildschirme lesen Daten über Provider (`ref.watch(accountsProvider)`), die Provider holen sie über Repositories vom Backend.

---

## 9. Häufige Probleme

| Problem | Lösung |
|---|---|
| `Connection refused` beim Backend-Start | Datenbank läuft nicht → `docker compose up -d` |
| Port 5432 belegt | Lokales PostgreSQL beenden oder in `docker-compose.yml` z. B. `"5433:5432"` und in `application.yml` die URL anpassen |
| `Unsupported class file major version` / Java-Fehler | JDK 21 installieren, `java -version` prüfen |
| App: „Server nicht erreichbar“ | Backend läuft? Richtige `API_URL` (Android: `10.0.2.2`)? Android: `usesCleartextTraffic` gesetzt? |
| Web: CORS-Fehler in der Browser-Konsole | Backend neu starten; erlaubt sind `http://localhost:*` (siehe `application.yml`) |
| Integrationstest schlägt mit Docker-Fehler fehl | Docker Desktop starten |
| Datenbank komplett zurücksetzen | `docker compose down -v` und danach `docker compose up -d` |
| „Kontakte vom Handy“ fehlt | Erscheint nur auf iOS/Android, nicht im Browser |
| Android: Absturz beim Kontakte-Abgleich | `READ_CONTACTS` im AndroidManifest fehlt (Abschnitt 4) |
| Machbarkeit „Unbekannt“, Banken lehnen ab | Es gibt noch keine Einnahmen: Beispielmonate erzeugen (Bereich „Sparen“ oder Profil) |
| Plan zeigt noch keine Rate | Raten werden am Ausführungstag gebucht, frühestens am nächsten passenden Tag. Zum Testen als Admin die Zeit vorspulen |
| „+3 Monate“ fehlt im Profil | Nur das Admin-Konto sieht es (`admin@projektbank.de` / `admin12345`, änderbar in `application.yml`) |
| Simulationszeit zurücksetzen | `docker compose down -v` und neu starten (löscht alle Daten) |
| Android-Build: „requires core library desugaring“ | Desugaring in `build.gradle(.kts)` ergänzen (Abschnitt 4) |
| Keine Live-Meldungen | Backend erreichbar? In der Browser-Konsole bzw. im Log nach `ws://…/ws` suchen; die Liste unter der Glocke funktioniert auch ohne Live-Verbindung |
| Überweisung: „Empfänger nicht gefunden“ | Empfänger muss in der App registriert sein; Telefonnummer so wie bei der Registrierung, `+49` wird erkannt |

---

## 10. Nächste Phasen

| Phase | Inhalt |
|---|---|
| 5 | Kannkriterien, Tests, Feinschliff |
