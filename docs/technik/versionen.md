# 📱 Unterstützte Android- und iOS-Versionen

[← Architektur](architektur.md) · [Testbericht](testbericht.md) · [Übersicht](../../README.md)

## Kurz gesagt

| Plattform | Läuft ab | Geräte (Beispiele) | Automatisch getestet auf |
|---|---|---|---|
| 🤖 **Android** | **Android 7.0** (API 24, 2016) | praktisch alle Handys der letzten ~9 Jahre | **Android 7.0, 8.0, 9, 11, 13, 15, 16** |
| 🍏 **iOS** | **iOS 15** (2021) | ab iPhone 6s / iPhone SE (1. Gen.) | ****iOS 16, 17, 18, 26**** |
| 🌐 **Browser** | Chrome/Edge (aktuell), Firefox (aktuell), Safari ab 15.6 | PC, Mac, Tablet, Handy | Chrome |

Das sind die **ältesten Versionen, die Flutter überhaupt unterstützt** ([Flutter: Supported deployment platforms](https://docs.flutter.dev/reference/supported-platforms), Stand Flutter 3.47: Android 24–37, iOS 15–27).
Ältere Systeme (Android 6 und älter, iOS 14 und älter) kann keine aktuelle Flutter-App bedienen – dafür müsste man eine veraltete Flutter-Version ohne Sicherheitsupdates verwenden.
Für solche Geräte gibt es aber die **Web-App** im Browser – solange der Browser aktuell genug ist.

Nach oben ist nichts begrenzt: Die App wird immer mit dem neuesten SDK gebaut (Android `targetSdk` 36, iOS-SDK 26) und auf den neuesten Systemen getestet.

## Wo ist das eingestellt?

`app/tool/setup_platforms.dart` (läuft nach `flutter create`, auch in jedem CI-Lauf):

```dart
const androidMinSdk = 24;      // Android 7.0
const iosMinVersion = '15.0';  // iOS 15
```

Das Skript trägt die Werte in `android/app/build.gradle.kts`, `ios/Podfile`, das Xcode-Projekt und `AppFrameworkInfo.plist` ein.
Alle Plugins kommen damit aus (geprüft mit dem Workflow **Versionen prüfen**):

| Plugin | braucht Android ab | braucht iOS ab |
|---|---|---|
| file_selector | API 24 | 13 |
| shared_preferences | API 24 | 13 |
| workmanager | API 23 | 14 |
| flutter_secure_storage | API 19 | 9 |
| flutter_contacts | API 19 | 8 |
| flutter_local_notifications | API 16 | 8 |

## Unterschiede zwischen den Versionen – und wie die App damit umgeht

| Version | Besonderheit | Umsetzung in der App |
|---|---|---|
| Android 9+ | http ohne Verschlüsselung ist gesperrt | `usesCleartextTraffic` für den Server im lokalen Netz |
| Android 13+ | Benachrichtigungen brauchen eine Erlaubnis | wird nach der Anmeldung gefragt (ältere Versionen: automatisch erlaubt) |
| Android 15+ | App zeichnet bis unter die Systemleisten („edge-to-edge“) | Seiten und Fenster halten unten den Systembereich frei (`safeListPadding`) |
| Android 6+ / iOS | Kontakte nur mit Erlaubnis | Abfrage erst, wenn man im Adressbuch „Kontakte vom Handy durchsuchen“ antippt |
| iPhone mit Home-Indikator | unterer Rand ist belegt | wie Android 15+ |
| iOS | Benachrichtigungs-Erlaubnis kommt als Dialog | Live-Verbindung startet trotzdem sofort (früher wartete sie auf die Antwort) |
| iOS | Hintergrund-Abfrage | nur Android (iOS erlaubt das nicht zuverlässig, siehe [Push](push.md)) |

## Wie wird getestet?

GitHub → **Actions → E2E → Run workflow** → *Systemversionen: alle*.
Derselbe Ende-zu-Ende-Test (Registrieren, Karte, Überweisung, Beispielmonate, CSV-Import, Kredit, Sondertilgung, zwei Nutzer mit Live-Meldung, Dauerauftrag, geteiltes Unterkonto) läuft dann parallel auf:

- Android-Emulatoren mit **API 24, 26, 28, 30, 33, 35, 36**
- iPhone-Simulatoren mit **iOS 16, 17, 18, 26** (ältere werden während des Laufs bei Apple geladen)
- Chrome

Jeder Lauf macht Bildschirmfotos (Startseite, Überweisung prüfen, Daueraufträge) und legt sie in die Branches `ci-shots-android-24` … `ci-shots-ios-26`. So sieht man auf einen Blick, ob die App überall gleich aussieht.

**Nicht automatisch testbar:** iOS 15 – Apple bietet den Simulator für das aktuelle Xcode nicht mehr an. Die App ist für iOS 15 gebaut (Deployment Target 15.0); auf einem echten Gerät mit iOS 15 bitte einmal von Hand prüfen.
