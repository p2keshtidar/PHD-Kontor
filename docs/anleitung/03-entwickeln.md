# 🛠️ Anleitung 3: Entwickeln – Projekt aus dem Code starten

[← App ausprobieren](02-app-benutzen.md) · [Übersicht](../../README.md) · weiter: [Häufige Probleme →](04-probleme.md)

Für alle im Team, die am Code arbeiten.

---

## Schritt 1 – Programme installieren (einmalig)

| Programm | Version | Prüfen |
|---|---|---|
| Git | aktuell | `git --version` |
| Docker Desktop | aktuell, muss **laufen** | `docker --version` |
| Java JDK | **21** (Temurin) | `java -version` |
| Flutter SDK | 3.24 oder neuer – https://docs.flutter.dev/get-started/install | `flutter --version` |
| Android Studio | für Android-Emulator | – |
| Xcode | nur auf dem Mac, für iOS | – |
| IDE | IntelliJ IDEA (Backend), VS Code oder Android Studio (App) | – |

Danach `flutter doctor` ausführen und alles Rote beheben. Gradle muss **nicht** installiert werden (`gradlew` ist dabei).

## Schritt 2 – Code holen

```bash
git clone https://github.com/p2keshtidar/girokonto.git
cd girokonto
```

## Schritt 3 – Datenbank starten

```bash
docker compose up -d db
```

✅ `docker ps` zeigt `girokonto-db`.

## Schritt 4 – Backend starten

```bash
cd backend
./gradlew bootRun          # Windows: .\gradlew.bat bootRun
```

✅ Im Log steht `Started GirokontoApplication`. Erster Start dauert einige Minuten.
API ausprobieren: **http://localhost:8080/swagger-ui.html**

Terminal offen lassen. Für die nächsten Schritte ein **zweites Terminal** öffnen.

## Schritt 5 – App einrichten (einmalig)

```bash
cd app
flutter create . --platforms=android,ios,web --org de.frauas --project-name girokonto_app
flutter pub get
dart run tool/setup_platforms.dart
```

`flutter create` erzeugt die Plattform-Ordner (sie sind nicht im Git). `setup_platforms.dart` trägt automatisch alles Nötige ein: Internet-, Kontakte- und Benachrichtigungsrechte, http für die Entwicklung, App-Name „PHD Kontor“, Android-Desugaring, iOS-Texte.

## Schritt 6 – App starten

| Ziel | Befehl (im Ordner `app`) | Server-Adresse |
|---|---|---|
| Browser | `flutter run -d chrome` | automatisch `localhost:8080` |
| Android-Emulator | `flutter run -d emulator-5554` | automatisch `10.0.2.2:8080` |
| iOS-Simulator (Mac) | `flutter run -d iPhone` | automatisch `localhost:8080` |
| Echtes Handy (USB) | `flutter run` | in der App: Login-Seite → „Server: …“ → `http://<IP-PC>:8080` |

`flutter devices` zeigt alle Geräte. Während `flutter run` läuft: `r` = Hot Reload, `R` = Neustart, `q` = Beenden.

## Schritt 7 – Tests

```bash
# Backend (Docker muss laufen – Testcontainers startet eine echte PostgreSQL)
cd backend && ./gradlew test
# Bericht: backend/build/reports/tests/test/index.html

# App
cd app && flutter analyze && flutter test

# Ende-zu-Ende (Server muss laufen, Gerät/Emulator offen)
cd app && flutter test integration_test/app_test.dart -d <gerät>
```

Was alles getestet wird: **[Testbericht](../technik/testbericht.md)**

---

## Arbeitsweise im Team (Git + Scrum)

### Branches und Commits

1. Aufgabe im [Backlog (Issues)](https://github.com/p2keshtidar/girokonto/issues) auswählen, sich selbst zuweisen, Status im Board auf „In Arbeit“.
2. Branch anlegen:
   ```bash
   git checkout main && git pull
   git checkout -b feature/12-sondertilgung      # Issue-Nummer + kurzer Name
   ```
3. Kleine Commits, Nachricht mit Anforderungs-ID oder Issue:
   ```bash
   git commit -m "K020: Sondertilgung im LoanService (#12)"
   ```
4. Pushen und **Pull Request** öffnen: `git push -u origin feature/12-sondertilgung`
5. Ein **anderes** Teammitglied macht das Review. Erst wenn die Prüfungen (CI) grün sind → **Merge**.
6. `Closes #12` in der PR-Beschreibung schließt das Issue automatisch.

### Automatik auf GitHub

| Workflow | Wann | Was |
|---|---|---|
| **CI** | jeder Push/PR | Backend-Tests, `flutter analyze`, App-Tests, Builds für Web/Android/iOS/Docker |
| **Release** | jeder Push auf `main` | baut APK, IPA, Server-JAR, Web-ZIP → [Releases](https://github.com/p2keshtidar/girokonto/releases/tag/latest) |
| **E2E** | manuell (Actions → E2E → Run workflow) | echte App gegen echten Server im Browser, Android-Emulator, iOS-Simulator |

### Regeln

- Nie direkt auf `main` arbeiten.
- Datenbank-Änderungen **nur** als neue Flyway-Datei (`V5__….sql`), alte nie ändern.
- Geld bewegt **nur** der `BookingService`.
- Neue Funktion = mindestens ein Test.

Wie der Code aufgebaut ist: **[Architektur](../technik/architektur.md)**
