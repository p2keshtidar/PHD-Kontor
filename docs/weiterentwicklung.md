# 🧭 Weiterentwicklung – Plan und Stand

[← Übersicht](../README.md) · [Scrum](scrum/README.md)

Am 15.10.2026 hat das Team 22 Vorschläge zur Weiterentwicklung aufgenommen. Arbeitsregel: **erst den vorhandenen Code
prüfen**, dann gezielt ergänzen – funktionierende Teile werden nicht neu geschrieben. Die Tabelle zeigt für jeden
Vorschlag, was es schon gibt und was als Aufgabe (Issue) in Sprint 6 bzw. 7 der Projektwoche eingeplant ist.

**Grundsatz für alle Erweiterungen:** Alles läuft mit **simulierten Anbietern und Spielgeld**, deutlich gekennzeichnet.
Echte Bankverbindungen, Zahlungen oder Angebote nur mit ausdrücklicher Freigabe, Verträgen und Zugangsdaten.

Legende: ✅ vorhanden · 🟡 teilweise vorhanden · 🆕 neu geplant

| # | Vorschlag | Bestand (vor dem 15.10.) | Stand | Aufgabe |
|---|---|---|---|---|
| 1 | Zentrales Finanz-Dashboard | Gesamtvermögen (Konten, Depot, Kredite, verbundene Banken), Finanzüberblick mit Monaten und Kategorien | ✅ | 🆕 [#66](https://github.com/p2keshtidar/girokonto/issues/66) Dashboard: Cashflow, Schulden, Sparziele, Vermögensverlauf, verfügbar/reserviert |
| 2 | Multi-Banking | mehrere echte Banken verbinden (Sandbox, nur lesend), Gesamtsaldo der verbundenen Konten | ✅ | 🆕 [#67](https://github.com/p2keshtidar/girokonto/issues/67) weitere Projektbank-Konten (Tagesgeld, zweites Girokonto), konsolidierte Umsätze, Umbuchung zwischen eigenen Konten |
| 3 | Transaktionsmanagement | Suche, Filter, Kategorien ändern, automatische Kategorien, Daueraufträge, Einnahmen/Ausgaben (regelmäßig), CSV-Import mit Duplikatschutz | ✅ | 🆕 [#68](https://github.com/p2keshtidar/girokonto/issues/68) Notizen, CSV-Export, doppelte Umsätze erkennen |
| 4 | Budgetplanung | Spartipps aus Kategorien | ✅ | 🆕 [#61](https://github.com/p2keshtidar/girokonto/issues/61) Budgets je Kategorie, Warnungen, ungewöhnliche Ausgaben, Prognose Monatsende |
| 5 | Finanz-Marktplatz | Kreditvergleich (fiktive Banken) | ✅ | 🆕 [#69](https://github.com/p2keshtidar/girokonto/issues/69) Girokonten, Kreditkarten, Tagesgeld, Festgeld, Depots, Kredite – nur simulierte Anbieter |
| 6 | Kreditvergleich und Finanzierungsrechner | effektiver Jahreszins, Rate, Gesamtkosten, Tilgungsplan, Sondertilgung/vollständige Rückzahlung, Auto/Konsum/Immobilie, getrennte Schritte (Sprint 5) | ✅ | – |
| 7 | Sparziele | digitale Konten mit Ziel, nötiger Rate, Fortschritt, Prognose; Sparpläne; Sparregeln (Sprint 5) | ✅ | – |
| 8 | Virtuelle Unterkonten | digitale Konten, Umbuchungen auch zwischen Töpfen | ✅ | 🆕 [#66](https://github.com/p2keshtidar/girokonto/issues/66) verfügbar und reserviert getrennt; weitere Zwecke (Miete, Lebensmittel, Rücklagen) |
| 9 | Gemeinsame Finanzen und WG | geteilte digitale Konten mit Rechten | ✅ | 🆕 [#71](https://github.com/p2keshtidar/girokonto/issues/71) gemeinsame Ausgaben, Aufteilung, Forderungen, Ausgleich |
| 10 | Kreditkartenverwaltung | virtuelle Debitkarte: Limit, sperren, Daten nur mit Passwort, Demo-Zahlung | ✅ | 🆕 [#60](https://github.com/p2keshtidar/girokonto/issues/60) Kreditkarte (Simulation) mit Abrechnung und Gebühren; Kartenangebote im Marktplatz [#69](https://github.com/p2keshtidar/girokonto/issues/69) |
| 11 | Finanzielle Gesundheit | – | ✅ | [#60](https://github.com/p2keshtidar/girokonto/issues/60) interner Finanzscore (kein SCHUFA), Verschuldungsquote, Notgroschen |
| 12 | Persönlicher Finanzassistent | Spartipps, Machbarkeits-Ampel | ✅ | 🆕 [#72](https://github.com/p2keshtidar/girokonto/issues/72) Fragen zu Ausgaben, Monatsbericht, Szenarien – nur mit echten Daten der App |
| 13 | Vertrags- und Dokumentenverwaltung | – | ✅ | [#73](https://github.com/p2keshtidar/girokonto/issues/73) Verträge, Fristen, Kosten, Erinnerungen, sicherer Upload |
| 14 | Angebots- und Antragsverwaltung | Kreditanfragen mit Status (Sprint 5) | ✅ | 🆕 [#69](https://github.com/p2keshtidar/girokonto/issues/69) Favoriten, Vergleichslisten, Anträge mit Unterlagen, Entscheidungen festhalten |
| 15 | Finanzprognosen | Prognose für Spar-/Investpläne und Sparregeln | ✅ | 🆕 [#61](https://github.com/p2keshtidar/girokonto/issues/61) Kontostand nach wiederkehrenden Buchungen, Liquiditätswarnung, Best/Worst Case |
| 16 | Benutzerfreundlichkeit | Deutsch/Englisch komplett (Tests L01–L07), heller/dunkler Modus, 4 Bildschirmformate getestet | ✅ | 🆕 [#74](https://github.com/p2keshtidar/girokonto/issues/74) Bildschirmleser, einheitliche Lade-/Fehler-/Leerzustände; #25 Feinschliff |
| 17 | Backend und Datenmodell | Beträge als ganze Cent (`long`), Zinsen `NUMERIC`, Flyway, einheitliche Fehler, Validierung, Swagger | ✅ | [#62](https://github.com/p2keshtidar/girokonto/issues/62) Prüfung des Datenmodells und der API-Validierung |
| 18 | Architektur für echte Anbieter | Schnittstellen für Banken (Open Banking) und Kreditangebote, Sandbox getrennt | ✅ | [#69](https://github.com/p2keshtidar/girokonto/issues/69) Marktplatz nach demselben Muster; [#63](https://github.com/p2keshtidar/girokonto/issues/63) Übersicht aller Simulationen |
| 19 | Sicherheit | Hash, JWT, Rate-Limit, Sicherheitsmatrix (IDOR), Secrets-Prüfung, gitleaks/Trivy/CodeQL | ✅ | [#62](https://github.com/p2keshtidar/girokonto/issues/62) ✅ Prüfprotokoll erweitert, API-Version v1, Lasttest T137, IDOR-Tests S11/S12 |
| 20 | Tests und Qualität | 232 Backend- und 293 App-Tests, Ende-zu-Ende auf 12 Systemen, CI | ✅ | je Aufgabe neue Tests ([#70](https://github.com/p2keshtidar/girokonto/issues/70)) |
| 21 | Performance und Wartbarkeit | Seiten in Umsatzlisten, Indizes | ✅ | 🆕 [#75](https://github.com/p2keshtidar/girokonto/issues/75) langsame Endpunkte messen, Indizes, strukturierte Logs |
| 22 | Dokumentation | Anleitungen, Architektur, Testbericht, Open Banking, Swagger | ✅ | 🆕 [#63](https://github.com/p2keshtidar/girokonto/issues/63) Ubuntu/Docker, Umgebungsvariablen, Datenbankschema, Einschränkungen, simulierte Funktionen |

## Reihenfolge der Umsetzung

| Schritt | Inhalt | Aufgaben |
|---|---|---|
| 1 | Repository analysieren, vorhandene Funktionen prüfen | diese Tabelle |
| 2 | Fehler, Sicherheitsprobleme, unvollständige Funktionen beheben | laufend |
| 3 | Dashboard und Datenkonsistenz | [#66](https://github.com/p2keshtidar/girokonto/issues/66) |
| 4 | Multi-Banking und Transaktionsmanagement | [#67](https://github.com/p2keshtidar/girokonto/issues/67), [#68](https://github.com/p2keshtidar/girokonto/issues/68) |
| 5 | Budgets, Sparziele und Finanzprognosen | [#61](https://github.com/p2keshtidar/girokonto/issues/61) |
| 6 | Kreditrechner und Finanz-Marktplatz | [#69](https://github.com/p2keshtidar/girokonto/issues/69), [#59](https://github.com/p2keshtidar/girokonto/issues/59) |
| 7 | Karten, WG-Funktionen, Dokumentenverwaltung | [#60](https://github.com/p2keshtidar/girokonto/issues/60), [#71](https://github.com/p2keshtidar/girokonto/issues/71), [#73](https://github.com/p2keshtidar/girokonto/issues/73) |
| 8 | Finanzassistent und Finanzscore | [#72](https://github.com/p2keshtidar/girokonto/issues/72), [#60](https://github.com/p2keshtidar/girokonto/issues/60) |
| 9 | Bedienung, Übersetzungen, Barrierefreiheit | [#74](https://github.com/p2keshtidar/girokonto/issues/74), #25 |
| 10 | Tests, Sicherheit, Performance, Dokumentation | [#70](https://github.com/p2keshtidar/girokonto/issues/70), [#62](https://github.com/p2keshtidar/girokonto/issues/62), [#75](https://github.com/p2keshtidar/girokonto/issues/75), [#63](https://github.com/p2keshtidar/girokonto/issues/63) |

Nach jedem Schritt laufen alle Tests (CI) – der Stand steht im [Testbericht](technik/testbericht.md).
