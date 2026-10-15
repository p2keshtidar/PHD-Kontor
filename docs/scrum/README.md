# 🏃 Scrum – Projektwoche

[← Übersicht](../../README.md)

Wir haben das Projekt in **einer intensiven Woche** umgesetzt: **Mo 12.10. – So 18.10.2026**, **jeder Tag ein Sprint** mit Planning, Daily Scrum, Review und Retrospektive.

## 👥 Team und Rollen

| Name | Scrum-Rolle | Schwerpunkt |
|---|---|---|
| **Pouria Keshtidar** | Product Owner | Backend (Spring Boot), Pflichtenheft |
| **David Eslami Amirabadi** | Scrum Master | App (Flutter), Design |
| **Mohamed Hedi Harbaoui** | Entwicklungsteam | Tests, CI/GitHub Actions, Plattformen (Android/iOS) |

Alle drei sind Teil des Entwicklungsteams.

| Rolle | Aufgabe |
|---|---|
| Product Owner | pflegt das Backlog, legt Prioritäten fest, nimmt Ergebnisse im Review ab |
| Scrum Master | moderiert die Meetings, schreibt Protokolle, räumt Hindernisse aus dem Weg |
| Entwicklungsteam | setzt die Aufgaben um, schätzt Aufwand, testet und reviewt gegenseitig |

## 🔁 Ablauf eines Sprinttags

```
 09:00            13:00            18:00            18:30
 ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
 │ Planning │ ──▶ │  Daily   │ ──▶ │  Review  │ ──▶ │  Retro   │
 │  30 Min  │     │  15 Min  │     │  30 Min  │     │  20 Min  │
 └──────────┘     └──────────┘     └──────────┘     └──────────┘
   Ziel + Aufgaben  Stand mittags    Demo, Abnahme    Start/Stop/Continue
```

| Zeit | Meeting | Dauer | Ergebnis |
|---|---|---|---|
| 09:00 | **Sprint Planning** | 30 Min. | Sprintziel des Tages, Aufgaben verteilen |
| 13:00 | **Daily Scrum** | 15 Min. | Stand am Mittag: geschafft, als Nächstes, Hindernisse |
| 18:00 | **Sprint Review** | 30 Min. | Demo der fertigen Aufgaben, Abnahme durch den Product Owner |
| 18:30 | **Retrospektive** | 20 Min. | Start · Stop · Continue für den nächsten Tag |

## 📅 Die Projektwoche

| Sprint | Tag | Sprintziel | Aufgaben | Protokolle |
|---|---|---|---|---|
| ✅ [Sprint 1](sprint-01/planning.md) | Mo, 12.10.2026 | Fundament: Team steht, Pflichtenheft abgestimmt, die App läuft bei allen auf Browser, Android und iPhone | 7 | [Planning](sprint-01/planning.md) · [Daily](sprint-01/standups.md) · [Review](sprint-01/review.md) · [Retro](sprint-01/retro.md) |
| ✅ [Sprint 2](sprint-02/planning.md) | Di, 13.10.2026 | Konto und Überweisung: alle verstehen den Code, Usability-Test, die App läuft auf allen unterstützten Android- und iOS-Versionen, eigene Einnahmen und Ausgaben eintragen | 7 | [Planning](sprint-02/planning.md) · [Daily](sprint-02/standups.md) · [Review](sprint-02/review.md) · [Retro](sprint-02/retro.md) |
| ✅ [Sprint 3](sprint-03/planning.md) | Mi, 14.10.2026 | Karte, Unterkonten, Live-Meldungen und Daueraufträge verstanden und geprüft, eigenes App-Design | 5 | [Planning](sprint-03/planning.md) · [Daily](sprint-03/standups.md) · [Review](sprint-03/review.md) · [Retro](sprint-03/retro.md) |
| 🗓️ [Sprint 4](sprint-04/planning.md) | Do, 15.10.2026 | Finanzassistent I (Finanzüberblick, Sparplan, Erinnerung) und Erweiterung Teil 1: Sicherheit, Deutsch/Englisch, mehrere echte Bankkonten, getrennte Bereiche für Bankkonten und digitale Konten | 11 | [Planning](sprint-04/planning.md) · [Daily](sprint-04/standups.md) · [Review](sprint-04/review.md) · [Retro](sprint-04/retro.md) |
| 🗓️ [Sprint 5](sprint-05/planning.md) | Fr, 16.10.2026 | Finanzassistent II (Kredit, Sondertilgung) und Erweiterung Teil 2: Sparpläne mit Geldzuweisungen, Kreditvergleich mit mehreren Anbietern | 7 | [Planning](sprint-05/planning.md) · [Daily](sprint-05/standups.md) · [Review](sprint-05/review.md) · [Retro](sprint-05/retro.md) |
| 🗓️ [Sprint 6](sprint-06/planning.md) | Sa, 17.10.2026 | Qualität auf allen Plattformen und Erweiterung Teil 3: Angebote mit Finanzierung, Karten und Score, persönliche Finanzplanung | 7 | [Planning](sprint-06/planning.md) · [Daily](sprint-06/standups.md) · [Review](sprint-06/review.md) · [Retro](sprint-06/retro.md) |
| 🗓️ [Sprint 7](sprint-07/planning.md) | So, 18.10.2026 | Abschluss: Präsentation und Abgabe vorbereitet, Tests, Dokumentation und Betrieb vollständig | 6 | [Planning](sprint-07/planning.md) · [Daily](sprint-07/standups.md) · [Review](sprint-07/review.md) · [Retro](sprint-07/retro.md) |

## 🧑‍💻 Aufgabenverteilung der Woche

| Tag | Pouria | David | Mohamed |
|---|---|---|---|
| **Mo 12.10.**<br>Sprint 1 | • Kick-off: Rollen, Regeln, Kommunikationskanal festlegen<br>• Pflichtenheft v0.2 gemeinsam durchgehen und abgeben<br>• David und Mohamed als Collaborators ins GitHub-Repo einladen<br>• Entwicklungsumgebung bei allen einrichten (Anleitung 3)<br>• Code-Walkthrough Phase 1: Registrierung, Login, Konto, Buchung | • Kick-off: Rollen, Regeln, Kommunikationskanal festlegen<br>• Entwicklungsumgebung bei allen einrichten (Anleitung 3)<br>• Code-Walkthrough Phase 1: Registrierung, Login, Konto, Buchung | • Kick-off: Rollen, Regeln, Kommunikationskanal festlegen<br>• Entwicklungsumgebung bei allen einrichten (Anleitung 3)<br>• App auf Browser, Android und iPhone installieren und testen (Anleitung 1)<br>• CI/CD: automatische Tests, Builds für Web, Android und iOS, Release-Workflow, Docker |
| **Di 13.10.**<br>Sprint 2 | • Code-Walkthrough Phase 2: Umsätze, Überweisung, Adressbuch<br>• Gefundene Fehler aus dem Usability-Test beheben<br>• Einnahmen & Ausgaben eintragen (einmalig oder regelmäßig): Backend, App, Tests | • Code-Walkthrough Phase 2: Umsätze, Überweisung, Adressbuch<br>• Gefundene Fehler aus dem Usability-Test beheben<br>• Bildschirmfotos der App für README und Präsentation<br>• Einnahmen & Ausgaben eintragen (einmalig oder regelmäßig): Backend, App, Tests | • Usability-Test mit 3 Testpersonen (Kommiliton:innen) durchführen und auswerten<br>• Gefundene Fehler aus dem Usability-Test beheben<br>• Testfälle T01–T08 aus dem Pflichtenheft manuell auf dem Handy prüfen<br>• Alle Android- (ab 7.0) und iOS-Versionen (ab 15) unterstützen, Ende-zu-Ende-Tests auf 12 Systemen<br>• Einnahmen & Ausgaben eintragen (einmalig oder regelmäßig): Backend, App, Tests |
| **Mi 14.10.**<br>Sprint 3 | • Code-Walkthrough Phase 3: Karte, Unterkonten, WebSocket<br>• /K030/ Daueraufträge – Backend | • Code-Walkthrough Phase 3: Karte, Unterkonten, WebSocket<br>• App-Icon und Startbildschirm (Splash) gestalten<br>• /K030/ Daueraufträge – App | • Tests für Daueraufträge |
| **Do 15.10.**<br>Sprint 4 | • Code-Walkthrough Phase 4a: Kategorien, Finanzüberblick, Spar-/Investplan<br>• /K010/ Erinnerung an die Sparrate – Backend (Tageslauf, Benachrichtigung)<br>• Sicherheit: Anfragebegrenzung, Start-Sperre für Entwicklungs-Schlüssel, Security-Workflow (gitleaks, Trivy, CodeQL), Dependabot, Monitoring<br>• Zweisprachigkeit im Server: Sprache pro Nutzer, englische Meldungen und Benachrichtigungen<br>• Mehrere echte Bankkonten verbinden (Open Banking/PSD2, nur Lesezugriff, Sandbox) – Backend<br>• Zwei getrennte Kontenbereiche und digitale Konten (Zweck, Ziel, Fortschritt, Rollen) | • Code-Walkthrough Phase 4a: Kategorien, Finanzüberblick, Spar-/Investplan<br>• /K010/ Erinnerung – Einstellung in der App<br>• Zweisprachigkeit Deutsch/Englisch in der App: Sprachumschalter, Übersetzungen, Beträge und Daten je Sprache<br>• Bereich „Bankkonten“ in der App: verbinden, verwalten, Umsätze, eigene Angaben<br>• Zwei getrennte Kontenbereiche und digitale Konten (Zweck, Ziel, Fortschritt, Rollen) | • Tests für Erinnerung + Testfälle T09–T18 prüfen<br>• Sicherheit: Anfragebegrenzung, Start-Sperre für Entwicklungs-Schlüssel, Security-Workflow (gitleaks, Trivy, CodeQL), Dependabot, Monitoring<br>• Tests für Bankverbindungen und digitale Konten (Backend, Widget, Ende-zu-Ende) |
| **Fr 16.10.**<br>Sprint 5 | • Code-Walkthrough Kredit: Angebote, Annuität, Tilgungsplan<br>• /K020/ Sondertilgung – Backend<br>• Sparpläne und Geldzuweisungen: wöchentlich oder monatlich, Finanzierungsquelle, Status, keine doppelten Abbuchungen<br>• Kreditvergleich mit mehreren Anbietern: Anbieter-Schnittstelle, unverbindliche Beispiele, Filter, getrennte Schritte | • /K020/ Sondertilgung – App (Kreditdetail)<br>• Sparpläne und Geldzuweisungen: wöchentlich oder monatlich, Finanzierungsquelle, Status, keine doppelten Abbuchungen<br>• Kreditvergleich mit mehreren Anbietern: Anbieter-Schnittstelle, unverbindliche Beispiele, Filter, getrennte Schritte | • Tests für Sondertilgung + Testfälle T19–T25 prüfen<br>• Tests für Sparpläne, Zuweisungen und Kreditvergleich |
| **Sa 17.10.**<br>Sprint 6 | • Pflichtenheft auf Version 1.0 bringen (Abweichungen, Kannkriterien)<br>• README und Anleitungen prüfen – jeder installiert einmal nach Anleitung<br>• Angebote (Reise, Auto, Immobilie) mit Finanzierung und Sparplan, zentraler Planer<br>• Karten für digitale Konten (als Demo gekennzeichnet, solange kein Kartenherausgeber angebunden ist) und interner Score (transparent, kein SCHUFA, mit Einwilligung)<br>• Persönliche Finanzplanung: Budgets, wiederkehrende Zahlungen erkennen, Prognose, gekennzeichnete Empfehlungen | • Feinschliff: Texte, Dunkelmodus, große Schrift, Fehlermeldungen<br>• README und Anleitungen prüfen – jeder installiert einmal nach Anleitung<br>• Angebote (Reise, Auto, Immobilie) mit Finanzierung und Sparplan, zentraler Planer<br>• Karten für digitale Konten (als Demo gekennzeichnet, solange kein Kartenherausgeber angebunden ist) und interner Score (transparent, kein SCHUFA, mit Einwilligung)<br>• Persönliche Finanzplanung: Budgets, wiederkehrende Zahlungen erkennen, Prognose, gekennzeichnete Empfehlungen | • Ende-zu-Ende-Tests auf Browser, Android, iOS grün<br>• README und Anleitungen prüfen – jeder installiert einmal nach Anleitung<br>• Persönliche Finanzplanung: Budgets, wiederkehrende Zahlungen erkennen, Prognose, gekennzeichnete Empfehlungen |
| **So 18.10.**<br>Sprint 7 | • Präsentationsfolien: Problem, Lösung, Architektur, Demo, Scrum, Fazit<br>• Demo-Ablauf festlegen (Anleitung 2) und zweimal proben<br>• Kolloquium-Vorbereitung: jeder kann jeden Teil des Codes erklären<br>• Architektur und Tests: Prüfprotokoll, API-Versionierung, Last- und Sicherheitstests | • Präsentationsfolien: Problem, Lösung, Architektur, Demo, Scrum, Fazit<br>• Demo-Ablauf festlegen (Anleitung 2) und zweimal proben<br>• Kolloquium-Vorbereitung: jeder kann jeden Teil des Codes erklären | • Präsentationsfolien: Problem, Lösung, Architektur, Demo, Scrum, Fazit<br>• Demo-Ablauf festlegen (Anleitung 2) und zweimal proben<br>• Kolloquium-Vorbereitung: jeder kann jeden Teil des Codes erklären<br>• Finale Abgabe: Release, Tag v1.0, Repo aufgeräumt<br>• Architektur und Tests: Prüfprotokoll, API-Versionierung, Last- und Sicherheitstests<br>• Betrieb: Dokumentation, CI/CD, Monitoring, Backup und Wiederherstellung |

## 🗓️ Alle Termine

| Datum | Sprint | 09:00 | 13:00 | 18:00 | 18:30 |
|---|---|---|---|---|---|
| Mo, 12.10.2026 | Sprint 1 | Planning | Daily Scrum | Review | Retro |
| Di, 13.10.2026 | Sprint 2 | Planning | Daily Scrum | Review | Retro |
| Mi, 14.10.2026 | Sprint 3 | Planning | Daily Scrum | Review | Retro |
| Do, 15.10.2026 | Sprint 4 | Planning | Daily Scrum | Review | Retro |
| Fr, 16.10.2026 | Sprint 5 | Planning | Daily Scrum | Review | Retro |
| Sa, 17.10.2026 | Sprint 6 | Planning | Daily Scrum | Review | Retro |
| So, 18.10.2026 | Sprint 7 | Planning | Daily Scrum | Review | Retro |

## Nach der Projektwoche

Laut Modulbeschreibung läuft das Projekt bis **27.01.2027**. Bis dahin:

| Was | Wann |
|---|---|
| Wöchentliches Treffen mit Prof. Schorr: Was ist passiert? Welche Schwierigkeiten? Was kommt als Nächstes? Welche Entscheidungen und warum? | wöchentlich, nach Absprache |
| Präsentation (mind. 20 Min.) und Kolloquium mit jedem Teammitglied | bis 27.01.2027 |

## ✅ Definition of Done

Eine Aufgabe ist **fertig**, wenn:

- [ ] der Code über einen **Pull Request** in `main` ist und **ein anderes Teammitglied** ihn reviewt hat
- [ ] alle **automatischen Prüfungen (CI) grün** sind
- [ ] es für neue Logik mindestens **einen Test** gibt
- [ ] die Funktion auf **mindestens einem Handy** (Android oder iPhone) und im **Browser** ausprobiert wurde
- [ ] Anleitung/Pflichtenheft angepasst sind, falls sich etwas Sichtbares geändert hat
- [ ] das Issue geschlossen ist

## 🧰 Werkzeuge

| Was | Wo |
|---|---|
| Product Backlog | [GitHub Issues](https://github.com/p2keshtidar/girokonto/issues) |
| Sprints | [Milestones](https://github.com/p2keshtidar/girokonto/milestones) – ein Milestone pro Sprinttag |
| Board (optional) | GitHub → **Projects → New project → Board**, Issues hinzufügen (Spalten: Todo / In Arbeit / Review / Fertig) |
| Protokolle | dieser Ordner, je Sprint ein Unterordner |
| Vorlagen | [vorlagen/](vorlagen/) |

## ✍️ Protokoll schreiben – so geht's

1. Datei des Meetings öffnen (z. B. `sprint-04/standups.md`).
2. Auf GitHub oben rechts auf ✏️ **Edit** klicken.
3. Die 📝-Stellen ausfüllen.
4. Unten **Commit changes** → Nachricht z. B. `Scrum: Daily 15.10.` → fertig.
