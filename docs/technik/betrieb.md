# 🖥️ Betrieb: Server, Umgebungsvariablen, Datenbank, Backup, Monitoring

[← Übersicht](../../README.md) · [Architektur](architektur.md) · [Grenzen und Simulationen](grenzen-und-simulationen.md)

Diese Seite beschreibt, wie man Girokonto auf einem eigenen Server betreibt. Für den Start auf dem eigenen PC reicht
[Anleitung 1](../anleitung/01-installieren.md). **Hinweis:** Girokonto ist ein Studienprojekt mit Spielgeld – kein
zugelassenes Bankprodukt (siehe [Grenzen](grenzen-und-simulationen.md)).

## 1. Ubuntu-Server mit Docker

Getestet ist der Docker-Build im CI (Workflow „CI“, Job „Docker-Image“). Die Befehle unten sind für Ubuntu 22.04/24.04.

```bash
# 1) Docker installieren (offizielles Paket von Docker, enthält Compose ≥ 2.24)
sudo apt-get update && sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update && sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 2) Code holen
git clone https://github.com/p2keshtidar/girokonto.git && cd girokonto

# 3) Geheimnisse anlegen (nie ins Repository!)
cp .env.example .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" .env
sed -i "s|^CARD_KEY=.*|CARD_KEY=$(openssl rand -base64 32)|" .env
sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')|" .env
sed -i "s|^ADMIN_PASSWORD=.*|ADMIN_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')|" .env
nano .env                       # ADMIN_EMAIL und BANKING_REDIRECT_URL anpassen
chmod 600 .env

# 4) Starten (Datenbank + Backend + Web-App in einem Image)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose logs -f app      # warten auf "Started GirokontoApplication"
curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
```

Was `docker-compose.prod.yml` gegenüber der Entwicklung ändert:

| | Entwicklung (`docker-compose.yml`) | Betrieb (`+ docker-compose.prod.yml`) |
|---|---|---|
| Geheimnisse | Entwicklungswerte aus `application.yml` (nur Warnung im Log) | aus `.env`; `APP_ENV=production` → Start **bricht ab**, wenn noch Entwicklungs-Schlüssel oder das Entwicklungs-Datenbankpasswort gesetzt sind (`SecretsGuard`) |
| Datenbank | Port 5432 offen | nicht von außen erreichbar |
| App | Port 8080 auf allen Adressen | nur `127.0.0.1:8080` – davor ein HTTPS-Proxy |

**HTTPS** (Pflicht im Betrieb, Tokens und Passwörter gehen über die Leitung), z. B. mit Caddy, das Zertifikate
automatisch holt:

```bash
sudo apt-get install -y caddy
echo 'girokonto.example.org {
  reverse_proxy 127.0.0.1:8080
}' | sudo tee /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

Danach in `.env` `BANKING_REDIRECT_URL=https://girokonto.example.org/api/banking/callback` setzen und die erlaubte
Herkunft für die Web-App (`app.cors.allowed-origin-patterns` in `application.yml`) prüfen.

**Aktualisieren:** `git pull && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build` –
Flyway spielt neue Datenbank-Migrationen beim Start automatisch ein (vorher Backup, siehe 4).

## 2. Umgebungsvariablen

| Variable | Standard (Entwicklung) | Bedeutung |
|---|---|---|
| `APP_ENV` | `development` | `production` verhindert den Start mit Entwicklungs-Schlüsseln |
| `DB_URL` | `jdbc:postgresql://localhost:5432/girokonto` | Datenbank |
| `DB_USER` / `DB_PASSWORD` | `girokonto` / `girokonto` | Zugang zur Datenbank; im Betrieb eigenes Passwort |
| `PORT` | `8080` | Port des Servers |
| `JWT_SECRET` | Entwicklungswert | Signatur der Anmelde-Tokens (Base64, mind. 32 Byte) – **geheim** |
| `CARD_KEY` | Entwicklungswert | AES-256-Schlüssel (Base64, genau 32 Byte) für Kartendaten, Zwei-Faktor-Schlüssel, Bankfreigaben, Dokumente – **geheim**, sicher aufbewahren (ohne ihn sind diese Daten verloren) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@projektbank.de` / Entwicklungswert | Admin für die Simulationszeit, wird beim ersten Start angelegt |
| `RATE_LIMIT_ENABLED` | `true` | Begrenzung der Anfragen je Minute |
| `SLOW_REQUEST_MS` | `1000` | ab dieser Dauer `WARN event=slow_request` im Log |
| `LOAN_MARKET_RATES` / `LOAN_MARKET_RATES_URL` | `true` / Bundesbank-API | öffentliche Durchschnittszinsen als Referenz im Kreditvergleich |
| `CALL_INTEREST_PERCENT` | `2.00` | fiktiver Tagesgeldzins der Simulation |
| `BANKING_SANDBOX` | `true` | Sandbox-Banken mit Testdaten anbieten |
| `BANKING_AUTO_SYNC` | `true` | verbundene Banken alle 6 Stunden abrufen |
| `BANKING_REDIRECT_URL` | `http://localhost:8080/api/banking/callback` | Rücksprung nach der Freigabe bei der Bank |
| `GOCARDLESS_BASE_URL` / `GOCARDLESS_SECRET_ID` / `GOCARDLESS_SECRET_KEY` | leer | echte Bankverbindungen – nur mit Vertrag und eigenen Zugangsdaten |

Geheimnisse gehören in `.env` (Rechte 600) oder einen Secret-Manager, **nie** ins Repository. Das CI prüft jeden
Commit mit gitleaks auf versehentlich eingecheckte Geheimnisse.

## 3. Datenbankschema

PostgreSQL 16; das Schema entsteht ausschließlich über Flyway-Migrationen (`backend/src/main/resources/db/migration`).
Alle Beträge sind ganze Cent (`BIGINT`), Zinssätze `NUMERIC`, Zeitpunkte `TIMESTAMPTZ`.

| Migration | Tabellen | Inhalt |
|---|---|---|
| V1 | `app_user`, `refresh_token`, `account`, `booking`, `booking_line` | Nutzer, Anmeldung, Konten, doppelte Buchführung (jede Buchung hat Zeilen, die sich zu 0 summieren) |
| V2 | `contact` | Adressbuch |
| V3 | `card`, `notification` | virtuelle Debitkarte (Nummer verschlüsselt), Benachrichtigungen |
| V4 | `sim_clock`, `job_run`, `demo_profile`, `savings_plan`, `depot_position`, `plan_snapshot`, `loan` | Simulationszeit, Tageslauf, Spar-/Investpläne, Kredite |
| V5 | `loan_extra_payment` | Sondertilgungen |
| V6 | `standing_order` | Daueraufträge |
| V7 | – | Zwei-Faktor-Felder an `app_user` |
| V8 | `csv_import`, `imported_transaction` | CSV-Import anderer Banken |
| V9 | `account_member` | geteilte digitale Konten |
| V10 | `entry` | Einnahmen und Ausgaben (regelmäßig/einmalig) |
| V11 | – | Sprache an `app_user` |
| V12 | `bank_connection`, `external_account`, `external_transaction`, `external_transaction_edit`, `audit_log` | verbundene Banken (Open Banking), Prüfprotokoll |
| V13 | – | Zwecke und Ziele digitaler Konten |
| V14 | `allocation_rule`, `allocation_run` | Sparregeln |
| V15 | `loan_application`, `market_rate` | Kreditanfragen in Schritten, Referenzzinsen |
| V16 | – | weitere Konten (Tagesgeld, zweites Girokonto) |
| V17 | `booking_note`, `duplicate_dismissal` | Notizen, Duplikate |
| V18 | `budget`, `budget_alert` | Budgets |
| V19 | `market_favorite`, `market_application`, `market_application_document` | Marktplatz (Simulation) |
| V20 | `credit_card`, `credit_card_statement`, `credit_card_tx` | Kreditkarte (Simulation) |
| V21 | `expense_group`, `expense_group_member`, `group_expense`, `group_expense_share`, `group_settlement` | gemeinsame Ausgaben |
| V22 | `contract`, `document` | Verträge, Dokumente (Inhalt verschlüsselt) |
| V23 | – | zusätzliche Indizes |
| V24 | – | Einwilligung zum Finanz-Check an `app_user` |
| V25 | `subaccount_card` | Demo-Karten für digitale Konten |

Grafisch ansehen: `docker compose exec db psql -U girokonto -d girokonto -c '\dt'` oder ein Werkzeug wie DBeaver.

## 4. Backup und Wiederherstellung

```bash
# Sicherung (täglich per cron, z. B. 03:00): komprimiert, mit Datum
mkdir -p ~/backups
docker compose exec -T db pg_dump -U girokonto -d girokonto -Fc > ~/backups/girokonto-$(date +%F).dump
find ~/backups -name 'girokonto-*.dump' -mtime +14 -delete      # 14 Tage aufheben

# Wiederherstellen (überschreibt die Datenbank!)
docker compose stop app
docker compose exec -T db pg_restore -U girokonto -d girokonto --clean --if-exists < ~/backups/girokonto-2026-10-15.dump
docker compose start app
```

Wichtig: **`.env` (vor allem `CARD_KEY` und `JWT_SECRET`) getrennt und sicher sichern** – ohne `CARD_KEY` sind
Kartendaten, Bankfreigaben und Dokumente im Backup nicht mehr lesbar. Backups enthalten persönliche Daten:
verschlüsselt und nur für Berechtigte ablegen. Die Wiederherstellung einmal ausprobieren, bevor man sich darauf verlässt.

## 5. Monitoring und Logs

| Was | Wo |
|---|---|
| Lebt der Server? | `GET /actuator/health` (auch `/actuator/health/liveness`, `/readiness` für Container-Plattformen) |
| Kennzahlen | `GET /actuator/prometheus` – u. a. `http_server_requests_seconds` (Antwortzeiten je Endpunkt mit Histogramm für p95/p99), JVM, Datenbank-Pool |
| Logs | `docker compose logs app` – je API-Aufruf eine Zeile `event=request method=… path=… status=… ms=… requestId=…`, langsame Aufrufe als `WARN event=slow_request` |
| Fehler zuordnen | die App zeigt Fehler; der Antwort-Header `X-Request-Id` findet die passenden Logzeilen |

Logs enthalten keine Passwörter, Tokens, vollständigen IBANs/Kartennummern, Query-Parameter oder Dokumentinhalte.
`/actuator/prometheus` sollte im Betrieb nur intern erreichbar sein (z. B. im Proxy sperren).

## 6. CI/CD

| Workflow | Wann | Was |
|---|---|---|
| CI (`ci.yml`) | jeder Push/PR | Backend-Tests mit echter PostgreSQL (Testcontainers), App-Analyse und -Tests, Web-Build, Android-APK, iOS-Build ohne Signatur, Docker-Image |
| Security (`security.yml`) | Push auf `main`, montags, manuell | gitleaks (Geheimnisse, ganze Historie), Trivy (Abhängigkeiten: Gradle, pub, Dockerfile), CodeQL (Java) |
| E2E (`e2e.yml`) | manuell | Ende-zu-Ende-Tests auf Chrome, Android-Emulator, iOS-Simulator gegen echten Server |
| Release (`release.yml`) | Push auf `main`, manuell | `girokonto-server.jar` (Backend + Web-App), `girokonto.apk`, iOS ohne Signatur als Release-Dateien |

## 7. Tests ausführen

Siehe [Anleitung 3, Schritt 7](../anleitung/03-entwickeln.md) und den [Testbericht](testbericht.md).
