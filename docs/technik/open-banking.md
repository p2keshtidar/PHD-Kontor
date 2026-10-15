# 🏦 Echte Bankkonten verbinden (Open Banking / PSD2)

[← Architektur](architektur.md) · [Übersicht](../../README.md)

Nutzer können Konten **mehrerer Banken** mit Girokonto verbinden. Girokonto bekommt dabei **nur Lesezugriff**
(Kontoinformationsdienst nach PSD2): Kontodetails, Salden und Umsätze. **Es werden keine Zahlungen ausgeführt.**

> **Stand der Umsetzung:** Im Projekt ist nur die **Sandbox** aktiv – simulierte Banken mit Testdaten, deutlich als
> „Testdaten, keine echte Bank“ gekennzeichnet. Der Anbieter für echte Banken (GoCardless Bank Account Data) ist
> eingebaut, bleibt aber **ohne Vertrag und Zugangsdaten abgeschaltet**. Er wurde nur gegen nachgebildete Antworten
> im Format der Schnittstellen-Dokumentation getestet (B10–B17), **nicht** gegen echte Banken.

## Ablauf

```
App                         Server (BankingService)                 Anbieter / Bank
 │ Bank wählen ───────────▶ POST /api/banking/connections ────────▶ Freigabe anlegen (Agreement + Requisition)
 │ ◀── Link zur Bank ─────── (Sandbox: kein Link, Test-Dialog)
 │ Anmeldung bei der Bank ─────────────────────────────────────────▶ Nutzer bestätigt Lesezugriff (90 Tage)
 │ ◀────────────────────────────── Rücksprung /api/banking/callback (feste Seite, ändert nichts)
 │ „Freigabe abschließen“ ▶ POST …/{id}/complete ─────────────────▶ Status prüfen, Konten holen
 │                          erster Import (90 Tage), Kategorien
 │ alle 6 h im Hintergrund  Abgleich ab letzter Buchung − 5 Tage ──▶ Salden + Umsätze
 │ „Zugriff widerrufen“ ──▶ DELETE …/{id}?deleteData= ─────────────▶ Freigabe beim Anbieter löschen
```

## Anbieter

| Kennung | Wann aktiv | Inhalt |
|---|---|---|
| `SANDBOX` | Standard (`BANKING_SANDBOX=true`) | 5 simulierte Banken mit festen Testdaten (siehe unten) |
| `GOCARDLESS` | nur wenn `GOCARDLESS_SECRET_ID` und `GOCARDLESS_SECRET_KEY` gesetzt sind | GoCardless Bank Account Data, Banken im EWR über PSD2 |

Weitere Anbieter (z. B. Tink, finAPI, Yapily) lassen sich ergänzen, indem man `banking/provider/BankingProvider`
umsetzt. Die App zeigt automatisch alle verfügbaren Anbieter.

**Sandbox-Banken** (Daten hängen nur vom Simulationsdatum ab – gleicher Tag, gleiche Daten, gleiche Kennungen):

| Bank | Zweck |
|---|---|
| Sandbox-Hausbank | Girokonto + Tagesgeld: Gehalt, Miete, Lastschriften, Kartenzahlungen, Sparrate ans eigene Tagesgeld (= Umbuchung), jeden 4. Monat eine Rücklastschrift, jeden Tag eine Kartenreservierung, die nie gebucht wird |
| Sandbox-Onlinebank | Nebenjob, Abo, Online-Einkäufe, Tanken |
| Sandbox-Bank mit Störungen | jeder Abruf scheitert zweimal → testet die Wiederholung |
| Sandbox-Bank nicht erreichbar | Abruf scheitert immer → Fehlerstatus und Hinweis |
| Sandbox-Bank mit 1-Tag-Freigabe | Freigabe läuft nach 1 Tag ab → Ablauf und Erneuern |

## Voraussetzungen für echte Banken (nicht Teil des Projekts)

- **Erlaubnis:** Wer Kontoinformationen gewerblich abruft, braucht eine Registrierung als Kontoinformationsdienst
  (§ 34 ZAG, BaFin) – oder nutzt einen zugelassenen Anbieter, der den Zugriff in eigener Verantwortung bereitstellt.
- **Vertrag** mit dem Anbieter; Verfügbarkeit, Preise und Abruf-Grenzen vorher prüfen.
- **Datenschutz (DSGVO):** Auftragsverarbeitungsvertrag, Datenschutzhinweise, Einwilligungstext, Löschkonzept.
- **Betrieb:** Zugangsdaten nur als Umgebungsvariablen bzw. Secret-Manager, öffentliche HTTPS-Adresse für den
  Rücksprung (`BANKING_REDIRECT_URL`), Sandbox im Echtbetrieb ggf. abschalten (`BANKING_SANDBOX=false`).

## Sicherheit und Datenschutz

| Maßnahme | Umsetzung |
|---|---|
| Keine Bank-Passwörter | Anmeldung immer bei der Bank (Weiterleitung). Girokonto speichert nur die Kennung der Freigabe. |
| Freigabe-Kennung verschlüsselt | AES-256-GCM (`CardCrypto`), Schlüssel aus `CARD_KEY`; beim Widerruf gelöscht (`NULL`) |
| Originaldaten verschlüsselt | Die Antwort der Bank je Umsatz wird unverändert, aber verschlüsselt gespeichert (`raw_enc`) |
| IBAN nur gekürzt | Anzeige `DE89 •••• 3000`; zum Wiedererkennen eigener Konten ein HMAC-SHA-256-Prüfwert, keine volle IBAN |
| Nur eigene Daten | Jede Abfrage prüft den Nutzer; fremde Verbindungen und Umsätze liefern „nicht gefunden“ (404) |
| Protokolle ohne Daten | Fehlermeldungen des Anbieters werden in kurze, feste Texte übersetzt (keine Antworten, Tokens, IBANs) |
| Prüfprotokoll | `audit_log`: Freigabe begonnen/erteilt/abgelehnt/abgelaufen, Widerruf (mit/ohne Löschen); einsehbar unter `GET /api/me/audit-log` |
| Widerruf und Löschen | Widerruf löscht die Freigabe bei Anbieter und Girokonto; optional werden alle Konten, Umsätze und eigenen Angaben dieser Bank gelöscht |

## Umsätze: Status, doppelte Umsätze, Bearbeitungen

| Status | Bedeutung | Woran erkannt |
|---|---|---|
| `PENDING` – Vorgemerkt | noch nicht endgültig gebucht | Liste „pending“ der Bank |
| `BOOKED` – Gebucht | endgültig | Liste „booked“ der Bank |
| `SUPERSEDED` – Durch Buchung ersetzt | Vormerkung, aus der eine Buchung wurde | Vormerkung fehlt beim nächsten Abruf, gleiche Buchung (Betrag, Empfänger, bis 7 Tage später) ist da |
| `CANCELLED` – Storniert | Vormerkung ohne Buchung (z. B. aufgehobene Kartenreservierung) | Vormerkung fehlt, keine passende Buchung |
| `REVERSED` – Zurückgebucht | später zurückgebucht (Rücklastschrift, Rücküberweisung) | ISO-20022-Buchungscode `…-UPDD`, `…-RRTN`, `…-RPCR`, `…-ARET` oder eindeutiger Text, soweit die Bank ihn liefert |

- **Keine doppelten Umsätze:** eindeutiger Schlüssel (Konto, Kennung der Bank). Liefert die Bank keine Kennung,
  wird ein Prüfwert aus Datum, Betrag, Empfänger und Verwendungszweck gebildet (gleiche Umsätze am selben Tag
  werden durchnummeriert). Erneute Abrufe ändern nichts (Test T49).
- **Eigene Angaben getrennt:** Kategorie, Notiz, Schlagwörter, „nicht mitzählen“ liegen in
  `external_transaction_edit`. Ein Abruf überschreibt sie nie; die Originaldaten bleiben unverändert (T52).
  Wird eine Vormerkung gebucht, wandern die eigenen Angaben mit.
- **Automatische Kategorien** wie bei eigenen Umsätzen; Überträge zwischen eigenen Konten (IBAN-Prüfwert) werden
  „Umbuchung“ und zählen nicht als Einnahme/Ausgabe.

## Abgleich, Wiederholung, Fehler

- **Erster Import:** 90 Tage. **Danach:** ab der letzten Buchung minus 5 Tage (späte Buchungen der Bank) bzw. ab der
  ältesten offenen Vormerkung.
- **Wiederholung:** vorübergehende Fehler (Zeitüberschreitung, 5xx) bis zu 3 Versuche mit wachsender Wartezeit.
- **Fehlerstatus:** `OK`, `PARTIAL` (einige Konten), `FAILED`; Zähler `failed_syncs`; beim ersten Fehler im
  Hintergrund eine Benachrichtigung. Abgelaufene Freigabe → Status `EXPIRED`, Benachrichtigung, „Freigabe erneuern“
  behält alle bisherigen Daten (kein zweites Konto, keine doppelten Umsätze, T55).
- **Abruf-Grenzen:** Hintergrund-Abruf alle 6 Stunden; beim echten Anbieter höchstens alle 6 Stunden je Verbindung
  (Banken erlauben über PSD2 oft nur 4 Abrufe pro Tag), sonst `429 SYNC_TOO_SOON`.
- **Ablauf-Erinnerung:** 7 Tage vor Ende der Freigabe einmalig.

## Finanzüberblick

Gebuchte Euro-Umsätze verbundener Konten zählen im Finanzüberblick mit – ohne Rückbuchungen (Original und
Rückbuchung heben sich auf), ohne Umbuchungen und ohne als „nicht mitzählen“ markierte Umsätze. Per CSV importierte
Zeilen, die auch über eine verbundene Bank kamen (gleicher Tag, gleicher Betrag), werden nicht doppelt gezählt.
Konten in anderen Währungen werden angezeigt, aber nicht summiert.

## Schnittstelle

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/api/banking/providers` | verfügbare Anbieter |
| GET | `/api/banking/institutions?provider=` | Banken eines Anbieters |
| POST | `/api/banking/connections` | Freigabe beginnen (`provider`, `institutionId`) |
| POST | `/api/banking/connections/{id}/complete` | Freigabe abschließen, Konten + erster Import (Sandbox: `approve`) |
| POST | `/api/banking/connections/{id}/renew` | abgelaufene/abgelehnte Freigabe erneuern |
| POST | `/api/banking/connections/{id}/sync` | jetzt abrufen |
| DELETE | `/api/banking/connections/{id}?deleteData=` | widerrufen, optional Daten löschen |
| GET | `/api/banking/connections` · `/accounts` | Verbindungen mit Status/Berechtigungen · alle Konten mit Summe |
| GET | `/api/banking/transactions?accountId=&q=&category=&status=&tag=&from=&to=` | Umsätze aller Banken, gefiltert |
| GET/PUT | `/api/banking/transactions/{id}` | ein Umsatz · eigene Angaben ändern |
| GET | `/api/banking/transactions/{id}/original` | Originaldaten der Bank (unverändert) |

## Tests

| Ebene | Tests |
|---|---|
| Sandbox | B01–B08: gleiche Daten bei gleichem Abruf, Vormerkung → Buchung mit neuer Kennung, Storno, Rücklastschrift, Saldo, gültige Test-IBAN, Freigabe bestätigt/abgelehnt, Störung/Ausfall, Ablauf |
| GoCardless (nachgebildeter Server) | B10–B17: nur mit Zugangsdaten, Token im Kopf, Freigabe nur lesend, Status und Ablauf, verfügbarer Saldo, Umwandlung der Umsätze, Prüfwert ohne Kennung, Fehler ohne Geheimnisse, Token-Erneuerung |
| Hilfsfunktionen | B20–B23: IBAN kürzen, Prüfwert, Abruf-Abstand, Schlagwörter |
| Abläufe (echte PostgreSQL) | T49–T60: Import ohne Doppelte, Vormerkungen gebucht/storniert, Rückbuchung und Umbuchung im Überblick, eigene Angaben getrennt, fremde Daten, Wiederholung und Fehlerstatus, Ablauf und Erneuern, Widerruf mit/ohne Löschen, nichts im Klartext, Eingaben und Englisch, Ablauf-Erinnerung einmal, Rücksprung-Seite |
| App | BK01–BK12 (Widget-Tests), 4 Bildschirme × 4 Formate + Englisch, Ende-zu-Ende: Sandbox-Bank verbinden und Umsätze öffnen |

## Grenzen

- Keine Zahlungsauslösung (PSD2-Zahlungsauslösedienst bräuchte eine eigene Erlaubnis und einen Anbieter).
- Echte Banken wurden nicht angebunden getestet (kein Vertrag, keine Zugangsdaten).
- Die Rückbuchungs-Erkennung hängt davon ab, ob die Bank Buchungscodes liefert.
