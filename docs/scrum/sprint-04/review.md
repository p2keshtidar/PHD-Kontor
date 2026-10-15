# Sprint 4 – Sprint Review (Do 15.10.)

[← Scrum-Übersicht](../README.md) · Sprint 4: [Planning](planning.md) · [Daily Scrum](standups.md) · [Review](review.md) · [Retro](retro.md)

| | |
|---|---|
| **Datum** | Do, 15.10.2026, 18:00–18:30 |
| **Teilnehmer** | Pouria Keshtidar, David Eslami Amirabadi, Mohamed Hedi Harbaoui |
| **Sprintziel** | Finanzassistent I (Finanzüberblick, Sparplan, Erinnerung) und Erweiterung Teil 1: Sicherheit, Deutsch/Englisch, mehrere echte Bankkonten, getrennte Bereiche für Bankkonten und digitale Konten |
| **Erreicht?** | ✅ ja – alle Aufgaben erledigt |

## Ergebnisse (Demo)

| # | Aufgabe | Ergebnis | Fertig? |
|---|---|---|---|
| 1 | Code-Walkthrough Phase 4a: Kategorien, Finanzüberblick, Spar-/Investplan | Issue geschlossen | ✅ |
| 2 | /K010/ Erinnerung an die Sparrate – Backend (Tageslauf, Benachrichtigung) | Test T26 | ✅ |
| 3 | /K010/ Erinnerung – Einstellung in der App | Issue geschlossen | ✅ |
| 4 | Tests für Erinnerung + Testfälle T09–T18 prüfen | Issue geschlossen | ✅ |
| 5 | Sicherheit: Anfragebegrenzung, Start-Sperre für Entwicklungs-Schlüssel, Security-Workflow (gitleaks, Trivy, CodeQL), Dependabot, Monitoring | Commits 46f3352, aec7397 · Tests SEC01–SEC04, M01–M02 | ✅ |
| 6 | Zweisprachigkeit Deutsch/Englisch in der App: Sprachumschalter, Übersetzungen, Beträge und Daten je Sprache | Commit 400f2c8 · Tests L01–L07, alle Bildschirme auf Englisch | ✅ |
| 7 | Zweisprachigkeit im Server: Sprache pro Nutzer, englische Meldungen und Benachrichtigungen | Commit 90a8818 · Tests I01–I05, T46–T48 | ✅ |
| 8 | Mehrere echte Bankkonten verbinden (Open Banking/PSD2, nur Lesezugriff, Sandbox) – Backend | Commit 0ec7ade · [Open Banking](../../technik/open-banking.md) | ✅ |
| 9 | Bereich „Bankkonten“ in der App: verbinden, verwalten, Umsätze, eigene Angaben | Commit 1d6dd60 | ✅ |
| 10 | Zwei getrennte Kontenbereiche und digitale Konten (Zweck, Ziel, Fortschritt, Rollen) | Commits 58d8658, 8e3a44e | ✅ |
| 11 | Tests für Bankverbindungen und digitale Konten (Backend, Widget, Ende-zu-Ende) | Tests B01–B23, T49–T67, BK01–BK12, DA01–DA08 | ✅ |

## Feedback des Product Owners

- 📝

## Übernahme in den nächsten Sprint

- keine – alles erledigt
