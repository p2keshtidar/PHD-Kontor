# 🐷 Sparregeln und 🚗 Kreditvergleich

[← Architektur](architektur.md) · [Übersicht](../../README.md)

Zwei Erweiterungen aus Sprint 5 (16.10.2026): **Sparregeln** weisen digitalen Konten regelmäßig Geld zu, der
**Kreditvergleich** zeigt Angebote mehrerer Anbieter und trennt Vergleich, Anfrage, Vertrag und Auszahlung.

> **Stand der Umsetzung:** Alles läuft in der **Simulation der Projektbank** mit Spielgeld. Es gibt **keine echten
> Zahlungen**, **keine echten Kreditangebote** und **keine Abfrage bei einer Auskunftei**. Verbundene Bankkonten werden
> nur gelesen – Geld wird dort nie bewegt. Die Durchschnittszinsen der Deutschen Bundesbank sind eingebaut, der
> Abruf wurde aber nur gegen nachgebildete Antworten getestet (K06–K11), in dieser Entwicklungsumgebung war die
> Schnittstelle nicht erreichbar.

---

## 1. Sparregeln (Geldzuweisungen)

Eine Sparregel weist einem **digitalen Konto** regelmäßig einen festen Betrag zu – **wöchentlich, alle 2 Wochen,
monatlich oder vierteljährlich**, mit Startdatum und optionalem Enddatum. Zusätzlich gibt es Einzahlungen von Hand
(wie bisher) und einmalige Vormerkungen.

### Finanzierungsquellen

| Quelle | Was passiert | Anzeige in der App |
|---|---|---|
| **Girokonto der Projektbank** | echte Umbuchung im Hauptbuch der Simulation (Buchungsart `INTERNAL`, keine Überweisung) | „Umbuchung innerhalb der Projektbank (Simulation) – keine echte Überweisung.“ |
| **Verbundenes Bankkonto** (Open Banking, nur Lesezugriff) | **nur Vormerkung**: Das Geld bleibt bei der Bank, es wird **keine Zahlung ausgelöst**. Der Betrag zählt zum Ziel, wird aber getrennt ausgewiesen. | „Nur Vormerkung: Das Geld bleibt bei … Es wird keine Zahlung ausgelöst.“ |

Ein echter Geldtransfer von einer anderen Bank bräuchte einen **Zahlungsauslösedienst** (PSD2) mit Erlaubnis und
Vertrag – das ist nicht Teil des Projekts (siehe [Open Banking](open-banking.md)).

### Prüfungen vor jeder Ausführung

| Prüfung | Girokonto | Verbundenes Bankkonto |
|---|---|---|
| Berechtigung | Besitzer oder Mitglied mit Recht „einzahlen“ (geteilte digitale Konten) – bei **jeder** Ausführung neu | ebenso |
| Kontostatus | Girokonto und digitales Konto aktiv | Verbindung aktiv (nicht abgelaufen, nicht widerrufen), Konto in Euro |
| Deckung | Guthaben ≥ Betrag (unter Sperre des Girokontos) | letzter Kontostand **nicht älter als 48 h** (sonst wird er abgerufen, Abruf-Grenzen der Bank bleiben gewahrt) **und** frei = Kontostand − alle Vormerkungen ≥ Betrag |

### Status der Vorgänge

| Status | Bedeutung |
|---|---|
| Ausstehend | Termin angelegt, noch nicht versucht (auch: nächste Termine der Regel) |
| Umgebucht / Vormerkung erfasst | erledigt |
| Neuer Versuch am … | fehlgeschlagen (z. B. keine Deckung), nächster Versuch am Folgetag – höchstens 3 Versuche |
| Fehlgeschlagen | alle Versuche aufgebraucht oder dauerhaftes Hindernis; Benachrichtigung; „Erneut versuchen“ in der App |
| Abgebrochen | Regel pausiert oder beendet, bevor der Vorgang erledigt war |

Dauerhafte Hindernisse (Bankkonto gelöscht, Recht entzogen, digitales Konto aufgelöst) **pausieren die Regel** mit
Grund. Beim Fortsetzen werden pausierte Termine **nicht nachgeholt**. Wird ein digitales Konto aufgelöst, enden
seine Regeln.

### Keine doppelten Abbuchungen

1. **Ein Vorgang je Termin:** `UNIQUE (rule_id, due_date)` in `allocation_run`.
2. **Sperre:** Ausführung unter `SELECT … FOR UPDATE` auf dem Vorgang; ein erledigter Vorgang wird nie erneut ausgeführt.
3. **Idempotenz-Schlüssel** der Buchung `alloc-<Regel>-<Datum>`; gibt es die Buchung schon, wird sie übernommen
   statt neu gebucht.
4. **Vormerkungen** einer Quelle laufen nacheinander (Sperre auf dem verbundenen Konto), damit dieselbe Deckung
   nicht zweimal vergeben wird.

Getestet in T70: vier gleichzeitige Tagesläufe, ein nachträglich wiederholter Vorgang und der echte Tageslauf
danach ergeben genau **eine** Buchung.

### Prognose

Mit Zielbetrag zeigt die App, wann das Ziel **voraussichtlich** erreicht ist – berechnet aus allen aktiven Regeln
des Kontos (höchstens 30 Jahre) und gekennzeichnet als „wenn alle Sparregeln wie geplant laufen“.

### Schnittstelle

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/api/allocations/sources` | Quellen mit Kontostand, Vormerkungen, frei, Hinweis |
| GET | `/api/allocations/accounts/{id}` | Regeln, Vorgänge, anstehende Termine, Vormerkungen, Prognose |
| GET/POST | `/api/allocations/rules` | eigene Regeln / neue Regel (Start heute = sofort ausgeführt) |
| PUT | `/api/allocations/rules/{id}` | Betrag, Rhythmus, Enddatum |
| POST | `/api/allocations/rules/{id}/pause`, `/resume` | pausieren, fortsetzen |
| DELETE | `/api/allocations/rules/{id}` | beenden |
| GET | `/api/allocations/runs` | eigene Vorgänge |
| POST | `/api/allocations/runs/{id}/retry` | erneut versuchen |
| POST | `/api/allocations/earmarks`, `/earmarks/release` | vormerken, Vormerkung aufheben |

Einstellungen (`app.allocations`): `max-rules-per-user` (20), `max-attempts` (3), `balance-max-age-hours` (48).

---

## 2. Kreditvergleich mit mehreren Anbietern

### Anbieter

| Anbieter | Art | Zustand |
|---|---|---|
| **Fiktive Banken (Simulation)** – Mainufer Bank, Nordlicht Kredit, Projektbank, Taunus Finanz | Angebote (unverbindliche Rechenbeispiele) | immer verfügbar |
| **Deutsche Bundesbank** – MFI-Zinsstatistik, Neugeschäft | **Referenz**: Durchschnittszinsen, **keine Angebote** | „Verfügbar“, „Daten veraltet“, „Gestört“ oder „Abgeschaltet“ |
| **Echte Kreditangebote** | – | **nicht angebunden** |

Neue Anbieter setzen die Schnittstelle `loan/provider/LoanOfferProvider` um. Echte Angebote dürfen nur mit Vertrag
(Bank oder Kreditvermittler mit Partner-Schnittstelle), den nötigen Erlaubnissen und Zugangsdaten eingebunden werden –
bis dahin zeigt die App diesen Anbieter als „nicht angebunden“ und **erfindet keine Angebote**.

### Rechenbeispiele der fiktiven Banken

- **Sollzins** = Basiszins des Zwecks (Laufzeit, Eigenkapital) + Aufschlag der Bank + **Aufschlag nach Bonität**
  (Selbsteinschätzung: sehr gut −0,50 / gut ±0 / mittel +1,50 / schwach +4,00 Prozentpunkte; bei Immobilien
  −0,20 / ±0 / +0,60 / +1,50).
- **Effektiver Jahreszins** = (1 + Sollzins/12)^12 − 1 (monatliche Raten, keine weiteren Kosten).
- **Kosten:** keine Bearbeitungsgebühr (bei Verbraucherkrediten nach BGH-Rechtsprechung von 2014 unzulässig),
  Sondertilgungen kostenlos – so rechnet auch der simulierte Kredit.
- **Voraussetzungen** je Bank: Mindest-Bonität, regelmäßige Einnahmen, höchster Anteil der Rate am frei verfügbaren
  Betrag (60–80 %), volljährig mit Wohnsitz in Deutschland.
- Jede Zeile trägt den Text „Unverbindliches Rechenbeispiel (Simulation, fiktive Bank): Nettodarlehensbetrag …,
  Laufzeit …, gebundener Sollzins …, effektiver Jahreszins …, monatliche Rate …, Gesamtbetrag …“.

**Filter:** höchste Monatsrate, Budget (höchstens insgesamt zurückzahlen), Bonität, nur Angebote mit erfüllten
Voraussetzungen. Ausgeblendete Angebote werden gezählt („2 Angebote durch Filter ausgeblendet“).

### Referenzzinsen der Bundesbank

Abruf über den öffentlichen SDMX-Webservice `https://api.statistiken.bundesbank.de/rest/data/BBIM1/<Schlüssel>`
(ohne Zugangsdaten), alle 24 h. Verwendete Zeitreihen:

| Kennung | Schlüssel | Inhalt |
|---|---|---|
| CONSUMER_ALL | `M.DE.B.A2B.A.C.A.2250.EUR.N` | Konsumentenkredite, effektiver Jahreszins inkl. Kosten |
| CONSUMER_TO_1Y / 1_5Y / OVER_5Y | `…A2B.F.R…`, `…A2B.I.R…`, `…A2B.J.R…` | nach anfänglicher Zinsbindung |
| HOUSING_ALL | `M.DE.B.A2C.A.C.A.2250.EUR.N` | Wohnungsbaukredite, effektiver Jahreszins inkl. Kosten |
| HOUSING_TO_1Y / 1_5Y / 5_10Y / OVER_10Y | `…A2C.F.R…`, `…A2C.I.R…`, `…A2C.O.R…`, `…A2C.P.R…` | nach Zinsbindung |

- Gespeichert wird nur der **letzte erfolgreiche** Wert je Reihe (Tabelle `market_rate`).
- **Veraltet**, wenn der letzte erfolgreiche Abruf älter als 7 Tage ist oder der Berichtsmonat mehr als 4 Monate
  zurückliegt – dann zeigt die App „möglicherweise veraltet“.
- **Ohne erfolgreichen Abruf keine Werte** – es wird nichts geschätzt.
- Im Vergleich: „x Prozentpunkte über/unter dem Bundesbank-Durchschnitt“ für jedes Rechenbeispiel.
- Abschalten mit `LOAN_MARKET_RATES=false` (in den Tests abgeschaltet).

### Getrennte Schritte

```
1. Vergleich      POST /api/loans/offers                    nichts gespeichert, nichts beantragt
2. Anfrage        POST /api/loans/applications              nur mit Einwilligung; Zusage (14 Tage gültig) oder Ablehnung
                  POST /api/loans/applications/{id}/withdraw zurückziehen
3. Vertrag        POST /api/loans/applications/{id}/accept  {"confirm": true} – Kreditkonto, Status CONTRACTED, KEINE Buchung
                  POST /api/loans/{id}/cancel               Widerruf vor der Auszahlung (kein Geld geflossen)
4. Auszahlung     POST /api/loans/{id}/payout               genau eine Buchung (Idempotenz-Schlüssel), Status ACTIVE,
                                                            erste Rate einen Monat später
```

| Status | Bedeutung |
|---|---|
| Anfrage: Zusage / Abgelehnt / Zurückgezogen / Abgelaufen / Vertrag geschlossen | Ergebnis der Anfrage; abgelaufene Zusagen werden im Tageslauf markiert und gemeldet |
| Kredit: Vertrag geschlossen – Auszahlung ausstehend | Vertrag, aber kein Geld geflossen; keine Raten, keine Sondertilgung |
| Kredit: Ausgezahlt – Raten laufen / Zurückgezahlt / Widerrufen | wie bisher bzw. Widerruf vor der Auszahlung |

Jeder Schritt wird im Prüfprotokoll (`audit_log`) festgehalten. `POST /api/loans` bleibt als **Abkürzung** für den
Demo-Modus erhalten und führt die Schritte 2–4 nacheinander aus (jeder Schritt einzeln gespeichert). Die App nutzt
die getrennten Schritte.

**Bewusste Vereinfachungen:** Die (fiktive) Bank entscheidet sofort. Nach der Auszahlung ist ein Widerruf in der
Simulation nicht möglich (in der Wirklichkeit gilt das Widerrufsrecht nach § 495 BGB auch danach; der Kredit wäre dann
samt Zinsen zurückzuzahlen) – stattdessen gibt es die kostenlose Sondertilgung.

### Voraussetzungen für echte Kreditangebote (nicht Teil des Projekts)

- Vertrag mit einer Bank oder einem Kreditvermittler, der Angebote über eine Partner-Schnittstelle liefert.
- Erlaubnis für die Vermittlung (in Deutschland in der Regel nach Gewerbeordnung) und vollständige
  vorvertragliche Informationen für Verbraucherkredite.
- Echte Bonitätsprüfung nur mit ausdrücklicher Einwilligung und über den Anbieter.
- Datenschutz (DSGVO), Einwilligungstexte, Löschkonzept; Zugangsdaten nur über Umgebungsvariablen.

*Hinweis: keine Rechtsberatung – die rechtlichen Anforderungen müssen vor einem echten Betrieb fachlich geprüft werden.*

---

## Tests

| Bereich | Tests |
|---|---|
| Sparregeln (Backend, PostgreSQL) | T68–T77: sofort und wöchentlich, Wiederholen/Fehlschlagen/von Hand nachholen, parallele Läufe ohne doppelte Buchung, pausieren/fortsetzen/ändern/beenden, Vormerkung mit Deckungsprüfung, gelöschte Bankverbindung, Rechte in geteilten Konten, Eingaben, Ziel mit Vormerkung, Englisch |
| Termine und Prognose | Z01–Z05 (Monatsende, Rhythmen, Prognose über mehrere Regeln, Enddatum) |
| Kreditvergleich (Backend) | T78–T84: Kennzeichnung und Anbieterstatus, Bonität und Filter, Anfrage → Vertrag → Auszahlung, Ablehnung/Rückzug/Ablauf, Widerruf vor der Auszahlung, fremde Anfragen, Englisch |
| Anbieter | K01–K05 fiktive Banken (Konditionen, effektiver Jahreszins, Bonität, Kennzeichnung); K06–K11 Bundesbank gegen nachgebildeten Server (CSV-Formate, unbrauchbare Antworten, Abruf, keine erfundenen Werte, veraltete Werte, abgeschaltet) |
| Sicherheit | Sicherheitsmatrix S05 um alle neuen Endpunkte erweitert (fremde Regeln, Vorgänge, Vormerkungen, Anfragen, Verträge) |
| App | AL01–AL09 (Sparregeln), LV01–LV09 (Kreditschritte), neue Bildschirme im Layout-Test (4 Formate + Englisch) |
| Ende-zu-Ende | Kredit in vier Schritten (Ablauf 1), Sparregel im geteilten digitalen Konto (Ablauf 2) |
