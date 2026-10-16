# 📖 Anleitung 1: Installieren und starten

[← zurück zur Übersicht](../../README.md) · weiter: [App ausprobieren →](02-app-benutzen.md)

Diese Anleitung bringt die App in ca. **10 Minuten** auf deinen PC, dein Android-Handy und dein iPhone.
Du brauchst **keine** Programmierkenntnisse und musst nichts selbst bauen.

```
 ┌────────────┐      WLAN       ┌──────────────────────────────┐
 │ Android /  │ ──────────────▶ │  dein PC                     │
 │ iPhone     │  :8080          │  girokonto-server.jar        │
 └────────────┘                 │   ├─ Server (Spring Boot)    │
 ┌────────────┐                 │   └─ Web-App                 │
 │ Browser    │ ──────────────▶ │  PostgreSQL (Docker)         │
 └────────────┘                 └──────────────────────────────┘
```

Die Apps auf dem Handy sind nur die Oberfläche – alle Daten liegen auf dem **Server auf deinem PC**.
Darum startest du zuerst den Server (Teil A), danach die Apps (Teil B–D).

---

## Inhalt

- [Teil A – Server auf dem PC starten](#teil-a--server-auf-dem-pc-starten) (immer nötig)
- [Teil B – Im Browser benutzen](#teil-b--im-browser-benutzen)
- [Teil C – Android-Handy](#teil-c--android-handy)
- [Teil D – iPhone](#teil-d--iphone)
- [Teil E – Alles wieder beenden](#teil-e--alles-wieder-beenden)

---

## Teil A – Server auf dem PC starten

### A1. Programme installieren (nur einmal)

| Programm | Download | Prüfen (im Terminal) |
|---|---|---|
| **Docker Desktop** | https://www.docker.com/products/docker-desktop/ | `docker --version` |
| **Java 21** (Temurin) | https://adoptium.net/de/temurin/releases/?version=21 | `java -version` → muss `21` zeigen |

> **Terminal öffnen:**
> Windows: `Win`-Taste → „PowerShell“ eingeben → Enter.
> macOS: `Cmd + Leertaste` → „Terminal“ → Enter.

Nach der Installation von Docker Desktop: **Docker Desktop starten** und warten, bis unten links „Engine running“ steht.

### A2. Server-Datei herunterladen

1. Öffne **[girokonto-server.jar](https://github.com/p2keshtidar/girokonto/releases/latest/download/girokonto-server.jar)** – der Download startet sofort.
2. Lege die Datei in einen eigenen Ordner, z. B. `Dokumente\girokonto`.

### A3. Datenbank starten

Im Terminal eingeben (eine Zeile, alles kopieren):

```bash
docker run -d --name girokonto-db -p 5432:5432 -e POSTGRES_DB=girokonto -e POSTGRES_USER=girokonto -e POSTGRES_PASSWORD=girokonto -v girokonto-data:/var/lib/postgresql/data postgres:16-alpine
```

✅ Prüfen: `docker ps` zeigt eine Zeile mit `girokonto-db`.

> Beim **nächsten Mal** reicht: `docker start girokonto-db`

### A4. Server starten

```bash
cd Dokumente/girokonto
java -jar girokonto-server.jar
```

Nach ca. 15 Sekunden erscheint `Started GirokontoApplication` ✅.
**Dieses Fenster offen lassen** – solange es offen ist, läuft der Server.

> **Windows fragt nach der Firewall?** → „Zugriff zulassen“ (auch für **private Netzwerke**), sonst kann das Handy den Server nicht erreichen.

### A5. IP-Adresse deines PCs herausfinden (für das Handy)

| System | Befehl | Wo steht die IP? |
|---|---|---|
| Windows | `ipconfig` | bei „WLAN“ → „IPv4-Adresse“, z. B. `192.168.178.20` |
| macOS | `ipconfig getifaddr en0` | wird direkt ausgegeben |

Deine **Server-Adresse** fürs Handy ist dann: `http://192.168.178.20:8080` (mit deiner IP).

---

## Teil B – Im Browser benutzen

1. Browser öffnen (Chrome, Edge, Safari, Firefox).
2. **http://localhost:8080** eingeben.
3. Fertig – weiter mit dem [Rundgang](02-app-benutzen.md).

Von einem anderen Gerät im WLAN: `http://<IP-deines-PCs>:8080`.

---

## Teil C – Android-Handy

> Voraussetzung: **Android 7.0 oder neuer** (Einstellungen → Über das Telefon → Android-Version). [Welche Versionen getestet sind](../technik/versionen.md)

1. Handy und PC müssen im **selben WLAN** sein.
2. Auf dem Handy **[girokonto.apk](https://github.com/p2keshtidar/girokonto/releases/latest/download/girokonto.apk)** öffnen (Link z. B. per Mail/WhatsApp an dich schicken).
3. Datei antippen → Android fragt: *„Apps aus dieser Quelle zulassen?“* → **Einstellungen → Zulassen** → zurück → **Installieren**.
4. Falls Google Play Protect warnt: **„Trotzdem installieren“** (die App ist nicht im Play Store, darum die Warnung).
5. App **„PHD Kontor“** öffnen.
6. Ganz unten auf der Login-Seite auf **„Server: …“** tippen.
7. Server-Adresse aus Schritt A5 eintragen, z. B. `http://192.168.178.20:8080` → **Speichern**.
8. **„Jetzt registrieren“** → fertig ✅

> **Android-Emulator** (Android Studio): Die App nutzt automatisch `http://10.0.2.2:8080` – das ist im Emulator der eigene PC. Schritt 6–7 entfällt.

---

## Teil D – iPhone

> Voraussetzung: **iOS 15 oder neuer** (ab iPhone 6s/SE). Für die Web-App (Weg 1) reicht Safari 15.6 oder neuer.

Apple erlaubt Apps außerhalb des App Stores nur mit Signatur. Es gibt zwei Wege:

### Weg 1 (empfohlen, 1 Minute): als Web-App auf den Home-Bildschirm

1. iPhone und PC im **selben WLAN**.
2. **Safari** öffnen → `http://<IP-deines-PCs>:8080` eingeben.
3. Unten auf **Teilen** (Quadrat mit Pfeil) → **„Zum Home-Bildschirm“** → **Hinzufügen**.
4. Auf dem Home-Bildschirm ist jetzt das Symbol **PHD Kontor** – startet ohne Safari-Leiste wie eine echte App ✅

### Weg 2: echte iOS-App mit Sideloadly (kostenlose Apple-ID)

1. Auf dem PC **Sideloadly** installieren: https://sideloadly.io (unter Windows zusätzlich iTunes und iCloud von apple.com, **nicht** aus dem Microsoft Store).
2. **[girokonto-ios-unsigned.ipa](https://github.com/p2keshtidar/girokonto/releases/latest/download/girokonto-ios-unsigned.ipa)** herunterladen.
3. iPhone per Kabel anschließen, „Diesem Computer vertrauen“ bestätigen.
4. Sideloadly öffnen → IPA-Datei hineinziehen → deine Apple-ID eintragen → **Start**.
5. Auf dem iPhone: **Einstellungen → Allgemein → VPN und Geräteverwaltung** → deine Apple-ID → **Vertrauen**.
6. Ab iOS 16: **Einstellungen → Datenschutz & Sicherheit → Entwicklermodus** einschalten (Neustart).
7. App öffnen → unten **„Server: …“** → `http://<IP-deines-PCs>:8080` → **Speichern** ✅

> Mit kostenloser Apple-ID läuft die Signatur nach **7 Tagen** ab – dann Schritt 4 wiederholen.

> **iOS-Simulator** (nur Mac mit Xcode): siehe [Entwickeln](03-entwickeln.md) – dort läuft die App mit `http://localhost:8080`.

---

## Teil E – Alles wieder beenden

| Was | Wie |
|---|---|
| Server stoppen | im Server-Fenster `Strg + C` |
| Datenbank stoppen | `docker stop girokonto-db` |
| Alles neu starten | `docker start girokonto-db` → `java -jar girokonto-server.jar` |
| **Alle Daten löschen** (frisch anfangen) | `docker rm -f girokonto-db` und `docker volume rm girokonto-data`, danach wieder A3 |

---

## Alternative: alles mit einem Befehl (Docker Compose)

Für alle, die den Code ohnehin heruntergeladen haben (`git clone` oder **Code → Download ZIP**):

```bash
cd girokonto
docker compose up -d --build
```

Beim ersten Mal ca. 10 Minuten (baut App + Server). Danach: **http://localhost:8080**.
Beenden: `docker compose down`.

---

Probleme? → **[Häufige Probleme](04-probleme.md)**
