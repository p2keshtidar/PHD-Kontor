# 🔔 Benachrichtigungen bei geschlossener App (/K070/)

[← Architektur](architektur.md) · [Übersicht](../../README.md)

## Was ist umgesetzt?

| Situation | Wie kommt die Meldung an? | Verzögerung |
|---|---|---|
| App ist offen | Live über WebSocket (STOMP), Hinweis unten im Bild | sofort |
| App im Hintergrund (noch nicht beendet) | Live über WebSocket → Systembenachrichtigung | sofort |
| **App geschlossen – Android** | **Hintergrund-Abfrage** mit WorkManager: holt neue ungelesene Meldungen und zeigt sie als Systembenachrichtigung | ca. 15 Minuten (Android bestimmt den genauen Zeitpunkt) |
| App geschlossen – iOS | nicht möglich ohne Apple-Push (siehe unten) | – |
| Browser geschlossen | nicht möglich ohne Web-Push über HTTPS (siehe unten) | – |

Code: `app/lib/features/notifications/background_check.dart`

**So funktioniert die Hintergrund-Abfrage:**

1. Nach dem Anmelden plant die App die Aufgabe `girokonto-benachrichtigungen` ein (alle 15 Minuten, nur mit Internet).
2. Android startet die Aufgabe auch, wenn die App geschlossen ist – sogar nach einem Neustart des Handys.
3. Die Aufgabe liest die gespeicherte Server-Adresse und das Anmelde-Token, ruft `GET /api/notifications` ab und zeigt neue ungelesene Meldungen an.
4. Welche Meldung zuletzt angezeigt wurde, merkt sich die App (`last_notified_id`) – so kommt keine Meldung doppelt. Beim allerersten Lauf werden alte Meldungen bewusst nicht angezeigt.
5. Beim Abmelden wird die Aufgabe gelöscht.

**Ausprobieren:** App auf dem Android-Handy öffnen und anmelden → App ganz schließen (aus der Liste der letzten Apps wischen) → von einem zweiten Konto Geld überweisen → nach spätestens ca. 15 Minuten erscheint „Geld erhalten“.

> Manche Hersteller (Xiaomi, Huawei, Samsung im Energiesparmodus) verzögern oder stoppen Hintergrundaufgaben. Dann in den Akku-Einstellungen für „PHD Kontor“ „Nicht optimieren“ wählen.

---

## Ausbaustufe: echtes Push (sofort, auch iOS)

Für sofortige Meldungen bei geschlossener App braucht man den Push-Dienst des Betriebssystems. Das erfordert Konten, die das Projekt bewusst nicht voraussetzt:

| Plattform | Dienst | Voraussetzung | Kosten |
|---|---|---|---|
| Android | Firebase Cloud Messaging (FCM) | Google-Konto, Firebase-Projekt | kostenlos |
| iOS | Apple Push Notification Service (APNs) | **Apple Developer Program** | 99 €/Jahr |
| Browser | Web Push (VAPID) | Server unter **HTTPS** | kostenlos |

### Schritt für Schritt: Android mit Firebase

1. https://console.firebase.google.com → **Projekt hinzufügen** → Name „girokonto“.
2. **Android-App hinzufügen** → Paketname `de.frauas.girokonto_app` → `google-services.json` herunterladen → nach `app/android/app/` legen.
3. In `app/pubspec.yaml`: `firebase_core` und `firebase_messaging` hinzufügen, `flutter pub get`.
4. In der App nach dem Login: `FirebaseMessaging.instance.getToken()` → Token an das Backend schicken (neuer Endpunkt, z. B. `POST /api/me/push-token`).
5. Backend: Tabelle `push_token (user_id, token, platform)`; in `NotificationService.send(...)` zusätzlich über die FCM-HTTP-v1-API senden (Dienstkonto-Schlüssel aus Firebase → *Projekteinstellungen → Dienstkonten*).
6. Testen: App schließen, Überweisung empfangen → Meldung erscheint sofort.

### iOS mit APNs

1. Apple Developer Program beitreten.
2. In Xcode: *Signing & Capabilities* → **Push Notifications** und **Background Modes → Remote notifications**.
3. Im Apple-Developer-Portal einen **APNs-Schlüssel (.p8)** erzeugen und in Firebase hochladen (*Projekteinstellungen → Cloud Messaging*). Danach funktioniert derselbe FCM-Weg wie bei Android.

> Für das Projekt empfehlen wir: Hintergrund-Abfrage (umgesetzt) in der Präsentation zeigen und echtes Push als „nächster Schritt“ erklären.
