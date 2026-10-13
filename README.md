<div align="center">

# 💶 Girokonto – Smartphone-Konto mit Finanzassistent

**Projekt Informatik · WiSe 2026/27 · Frankfurt University of Applied Sciences**
Betreuung: Prof. Dr.-Ing. Ruth Schorr

[![CI](https://github.com/p2keshtidar/girokonto/actions/workflows/ci.yml/badge.svg)](https://github.com/p2keshtidar/girokonto/actions/workflows/ci.yml)
[![Release](https://github.com/p2keshtidar/girokonto/actions/workflows/release.yml/badge.svg)](https://github.com/p2keshtidar/girokonto/releases/tag/latest)
[![E2E](https://github.com/p2keshtidar/girokonto/actions/workflows/e2e.yml/badge.svg)](https://github.com/p2keshtidar/girokonto/actions/workflows/e2e.yml)
![Plattformen](https://img.shields.io/badge/Plattformen-Android%20%7C%20iOS%20%7C%20Browser-0a7cff)
![Flutter](https://img.shields.io/badge/App-Flutter-02569B?logo=flutter)
![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot%203-6DB33F?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/DB-PostgreSQL%2016-336791?logo=postgresql)

</div>

> [!NOTE]
> Alle Konten, IBANs, Banken und Beträge sind **simuliert**. Es wird kein echtes Geld bewegt.

---

## ⬇️ Download – fertige Apps

GitHub baut bei jeder Änderung automatisch alle Apps neu. Einfach anklicken:

| | Datei | Wofür |
|---|---|---|
| 🤖 | **[girokonto.apk](https://github.com/p2keshtidar/girokonto/releases/latest/download/girokonto.apk)** | Android-App fürs Handy |
| 🍏 | **[girokonto-ios-unsigned.ipa](https://github.com/p2keshtidar/girokonto/releases/latest/download/girokonto-ios-unsigned.ipa)** | iPhone-App (ohne Signatur, Installation siehe Anleitung) |
| 🖥️ | **[girokonto-server.jar](https://github.com/p2keshtidar/girokonto/releases/latest/download/girokonto-server.jar)** | Server **mit Web-App** – läuft auf deinem PC |
| 🌐 | [girokonto-web.zip](https://github.com/p2keshtidar/girokonto/releases/latest/download/girokonto-web.zip) | nur die Web-App (statische Dateien) |

Alle Dateien auf einen Blick: **[Releases → Aktuelle Version](https://github.com/p2keshtidar/girokonto/releases/tag/latest)**

> [!IMPORTANT]
> Die Apps brauchen den **Server** auf deinem PC. Wie das in 5 Minuten geht, steht in der
> **[📖 Schritt-für-Schritt-Anleitung](docs/anleitung/01-installieren.md)**.

---

## 🚀 Schnellstart (3 Schritte)

```bash
# 1. Datenbank starten (Docker Desktop muss laufen)
docker run -d --name girokonto-db -p 5432:5432 -e POSTGRES_DB=girokonto -e POSTGRES_USER=girokonto -e POSTGRES_PASSWORD=girokonto postgres:16-alpine

# 2. Server starten (Java 21) – die Datei vorher oben herunterladen
java -jar girokonto-server.jar

# 3. Im Browser öffnen
#    http://localhost:8080
```

Handy verbinden: App öffnen → unten auf der Login-Seite **„Server: …“** antippen → `http://<IP-deines-PCs>:8080` eintragen.

---

## ✨ Was kann die App?

| Bereich | Funktionen |
|---|---|
| 🏦 **Girokonto** | Registrierung mit fiktiver IBAN und 1.000 € Startguthaben · Umsätze mit Suche und Filter · Kategorien |
| 💸 **Überweisen** | per IBAN, E-Mail oder Telefonnummer · Adressbuch · Abgleich mit Handy-Kontakten · Tageslimit · **Daueraufträge** (monatlich bis jährlich) |
| 💳 **Karte** | virtuelle Debitkarte · Daten nur mit Passwort · sperren/entsperren · Limit · Demo-Kartenzahlung |
| 🗂️ **Unterkonten** | „Töpfe“ für Urlaub, Auto … · einzahlen, auszahlen, auflösen · **mit anderen teilen** (z. B. WG-Kasse) |
| 🔔 **Benachrichtigungen** | live per WebSocket bei jeder Buchung · Android: auch bei **geschlossener App** (Hintergrund-Abfrage) |
| 📊 **Finanzüberblick** | Einnahmen/Ausgaben pro Monat · Kategorien · „Jeden Monat übrig“ · **CSV-Import** von Umsätzen echter Banken |
| 🔐 **Sicherheit** | Passwort-Hash, JWT, Sperre nach Fehlversuchen · **Zwei-Faktor-Anmeldung** mit Authenticator-App |
| 🐷 **Sparplan** | Ziel + Datum → Ratenvorschlag · Risikostufe · Ampel „machbar?“ · Spartipps · **Erinnerung vor der Abbuchung** |
| 📈 **Investplan** | z. B. 200 €/Monat in fiktive Fonds · Musterdepot mit Verlauf |
| 🚗🏠 **Kreditplan** | Auto/Immobilie · Angebote von 4 fiktiven Banken vergleichen · Tilgungsplan · Abschluss simulieren · **Sondertilgung** mit Vorschau (Restschuld, Laufzeit, Zinsersparnis) |
| ⏩ **Demo-Modus** | Beispielmonate erzeugen · Admin spult die Zeit vor (Raten werden gebucht) |

Demo-Admin: `admin@projektbank.de` / `admin12345`

---

## 📚 Dokumentation

| Dokument | Inhalt |
|---|---|
| 📖 [Anleitung: Installieren](docs/anleitung/01-installieren.md) | Server starten, Android, iPhone, Browser – Schritt für Schritt |
| 🧭 [Anleitung: App ausprobieren](docs/anleitung/02-app-benutzen.md) | Geführter Rundgang durch alle Funktionen |
| 🛠️ [Anleitung: Entwickeln](docs/anleitung/03-entwickeln.md) | Projekt aus dem Code starten, Tests, Git-Arbeitsweise |
| 🆘 [Häufige Probleme](docs/anleitung/04-probleme.md) | Lösungen für typische Fehler |
| 🏗️ [Architektur](docs/technik/architektur.md) | Wie Backend und App aufgebaut sind |
| ✅ [Testbericht](docs/technik/testbericht.md) | Was getestet wird, Abdeckung, gefundene Fehler |
| 🔔 [Push-Benachrichtigungen](docs/technik/push.md) | Hintergrund-Abfrage und Weg zu echtem Push (Firebase/APNs) |
| 📄 [Pflichtenheft v0.2](docs/pflichtenheft/Pflichtenheft_Girokonto_v0.2.pdf) | Anforderungen (Muss-/Kannkriterien, Tests) |
| 🏃 [Scrum](docs/scrum/README.md) | Team, Rollen, Sprints, Meeting-Protokolle |
| ✅ [Backlog (Issues)](https://github.com/p2keshtidar/girokonto/issues) · [Sprints (Milestones)](https://github.com/p2keshtidar/girokonto/milestones) | Aufgaben und Stand |

---

## 🗂️ Aufbau des Repositorys

```
girokonto/
├── app/                    📱 Flutter-App (Android, iOS, Browser)
│   ├── lib/                   Quellcode (core/ + features/)
│   ├── test/                  Unit- und Widget-Tests
│   ├── integration_test/      Ende-zu-Ende-Test (echte App gegen echten Server)
│   └── tool/                  Hilfsskript für die Plattform-Einstellungen
├── backend/                ☕ Spring-Boot-Server (Java 21, PostgreSQL)
│   └── src/main/…             Code, Datenbank-Migrationen (Flyway)
├── docs/                   📚 Anleitungen, Architektur, Pflichtenheft, Scrum
├── .github/                ⚙️ automatische Tests + Builds (GitHub Actions), Vorlagen für Issues/PRs
├── Dockerfile              🐳 baut Server + Web-App in ein Image
└── docker-compose.yml      🐳 startet Datenbank (+ optional die ganze Anwendung)
```

## 🧪 Qualität

Bei jedem Push prüft GitHub automatisch ([Actions](https://github.com/p2keshtidar/girokonto/actions)):

- **Backend:** 136 Tests mit echter PostgreSQL-Datenbank, **93 % Zeilenabdeckung** – Testfälle T01–T40 aus dem Pflichtenheft, Sicherheitsmatrix über *alle* Endpunkte, Fuzzing, gleichzeitige Überweisungen, 160 Zufallsaktionen mit Prüfung der Buchhaltung
- **App:** `flutter analyze` + 159 Unit-/Widget-Tests – jeder Bildschirm in 4 Formaten (kleines Handy bis Tablet, Schrift 150 %), Abläufe mit simuliertem Server
- **Builds:** Web, Android-APK, iOS, Docker-Image
- **Ende-zu-Ende** (manuell: *Actions → E2E → Run workflow*, Plattform und Android-Gerät wählbar): echte App im Browser (Chrome), Android-Emulator (Pixel 5 oder sehr kleines Display, Android 11) und iOS-Simulator (iPhone, iOS 18) gegen den echten Server – registrieren, Karte, Überweisung, Beispielmonate, Finanzüberblick, Kreditvergleich, Kreditabschluss, Sondertilgung, **zwei Nutzer** (Live-Meldung, Überweisung, Dauerauftrag, geteiltes Unterkonto)

📋 Alle Details: **[Testbericht](docs/technik/testbericht.md)**

## 👥 Team

| Name | Rolle |
|---|---|
| Pouria Keshtidar | Product Owner · Backend |
| David Eslami Amirabadi | Scrum Master · App (Flutter) |
| Mohamed Hedi Harbaoui | Entwicklung · Tests, CI & Plattformen |

Rollenverteilung ist ein Vorschlag aus dem Kick-off – Details und Ablauf: [docs/scrum](docs/scrum/README.md)

<div align="center"><sub>Projekt Informatik · FRA-UAS · 12.10.2026 – 27.01.2027</sub></div>
