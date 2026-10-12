# 🆘 Häufige Probleme

[← Entwickeln](03-entwickeln.md) · [Übersicht](../../README.md)

Problem nicht dabei? → [Issue anlegen](https://github.com/p2keshtidar/girokonto/issues/new/choose) (Vorlage „Fehler melden“).

| Problem | Lösung |
|---|---|
| `Connection refused` beim Backend-Start | Datenbank läuft nicht → `docker compose up -d db` bzw. `docker start girokonto-db` |
| Port 5432 belegt | Lokales PostgreSQL beenden oder in `docker-compose.yml` z. B. `"5433:5432"` und in `application.yml` die URL anpassen |
| `Unsupported class file major version` / Java-Fehler | JDK 21 installieren, `java -version` prüfen |
| App: „Server nicht erreichbar“ | Läuft der Server (Fenster mit `java -jar` offen)? Login-Seite → „Server: …“ → richtige Adresse `http://<IP-PC>:8080`? Handy im **selben WLAN**? |
| Web: CORS-Fehler in der Browser-Konsole | Backend neu starten; erlaubt sind `http://localhost:*` (siehe `application.yml`) |
| Integrationstest schlägt mit Docker-Fehler fehl | Docker Desktop starten |
| Datenbank komplett zurücksetzen | `docker compose down -v` und danach `docker compose up -d db` bzw. `docker start girokonto-db` |
| „Kontakte vom Handy“ fehlt | Erscheint nur auf iOS/Android, nicht im Browser |
| Android: Absturz beim Kontakte-Abgleich | `READ_CONTACTS` im AndroidManifest fehlt (`dart run tool/setup_platforms.dart` ausführen) |
| Machbarkeit „Unbekannt“, Banken lehnen ab | Es gibt noch keine Einnahmen: Beispielmonate erzeugen (Bereich „Sparen“ oder Profil) |
| Plan zeigt noch keine Rate | Raten werden am Ausführungstag gebucht, frühestens am nächsten passenden Tag. Zum Testen als Admin die Zeit vorspulen |
| „+3 Monate“ fehlt im Profil | Nur das Admin-Konto sieht es (`admin@projektbank.de` / `admin12345`, änderbar in `application.yml`) |
| Simulationszeit zurücksetzen | `docker compose down -v` und neu starten (löscht alle Daten) |
| Android-Build: „requires core library desugaring“ | Desugaring in `build.gradle(.kts)` ergänzen (`dart run tool/setup_platforms.dart` ausführen) |
| Keine Live-Meldungen | Backend erreichbar? In der Browser-Konsole bzw. im Log nach `ws://…/ws` suchen; die Liste unter der Glocke funktioniert auch ohne Live-Verbindung |
| Überweisung: „Empfänger nicht gefunden“ | Empfänger muss in der App registriert sein; Telefonnummer so wie bei der Registrierung, `+49` wird erkannt |
| Handy erreicht den PC nicht, Browser am PC geht | Windows-Firewall: *Windows-Sicherheit → Firewall → App durch Firewall zulassen* → **Java(TM) Platform** für *Privat* erlauben. WLAN in Windows als **privates** Netzwerk einstellen. Gäste-WLAN/Uni-WLAN (eduroam) blockiert oft Geräte untereinander → Handy-Hotspot benutzen |
| Testen, ob das Handy den Server sieht | Im Handy-Browser `http://<IP-PC>:8080/swagger-ui.html` öffnen – erscheint die Seite, stimmt die Verbindung |
| Android: „App nicht installiert“ | Alte Version zuerst deinstallieren (gleicher Name, andere Signatur) |
| iPhone: App startet nicht (Sideloadly) | Einstellungen → Allgemein → VPN und Geräteverwaltung → Apple-ID **vertrauen**; Entwicklermodus an; nach 7 Tagen neu installieren |
| iPhone/Browser: nach Neuladen abgemeldet | Normal, wenn privates Surfen aktiv ist (kein Speicher) |
| `java` wird nicht gefunden / falsche Version | Java 21 installieren, Terminal neu öffnen, `java -version` |
| Port 8080 belegt | Anderes Programm beenden oder Server mit `java -jar girokonto-server.jar --server.port=8081` starten (dann überall `:8081`) |

