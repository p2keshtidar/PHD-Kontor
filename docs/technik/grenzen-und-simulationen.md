# ⚠️ Grenzen und Übersicht der Simulationen

[← Übersicht](../../README.md) · [Betrieb](betrieb.md) · [Architektur](architektur.md)

Girokonto ist ein **Studienprojekt** (Projekt Informatik, Frankfurt UAS). Es ist **keine Bank**, hat keine Erlaubnis
der BaFin, keine Einlagensicherung und bewegt **kein echtes Geld**. Alles, was nach Bank aussieht, ist eine
Simulation mit Spielgeld – in der App jeweils gekennzeichnet.

## Was ist echt, was ist simuliert?

| Funktion | Art | Erklärung |
|---|---|---|
| Girokonto, IBAN, Startguthaben 1.000 € | 🎮 Simulation | Konto der „Projektbank“ in der App-Datenbank; IBAN nur im Format echt, Spielgeld |
| Überweisungen, Daueraufträge | 🎮 Simulation | nur zwischen Nutzern dieser App, keine SEPA-Zahlung |
| Weitere Konten (Tagesgeld, zweites Girokonto) | 🎮 Simulation | fiktiver Zinssatz (`CALL_INTEREST_PERCENT`) |
| Digitale Konten („Töpfe“), Sparregeln | 🎮 Simulation | Unterteilung des Projektbank-Kontos – **keine eigenen Bankkonten, keine eigene Einlagensicherung**; bei verbundenen echten Banken nur Vormerkung, keine Zahlung |
| Debitkarte | 🎮 Simulation | virtuelle Karte mit generierter Nummer, Demo-Zahlungen bei erfundenen Händlern; es wird keine Karte bestellt oder ausgegeben |
| Karten für digitale Konten | 🎮 Simulation | virtuelle Demo-Karte ohne Kartennummer, zahlt Spielgeld vom digitalen Konto |
| Kreditkarte | 🎮 Simulation | virtuelle Spielgeld-Kreditkarte mit Abrechnung; keine Kreditprüfung, keine Bestellung |
| Spar- und Investpläne, Depot | 🎮 Simulation | fiktive Fonds mit simulierter Wertentwicklung |
| Kreditvergleich, Kreditanfrage, Kredit | 🎮 Simulation | vier fiktive Banken, Zusage nach festen Regeln; als Referenz echte öffentliche Durchschnittszinsen der Deutschen Bundesbank (wenn erreichbar) |
| Finanz-Marktplatz, Anträge | 🎮 Simulation | fiktive Anbieter und Angebote; Anträge erzeugen keine Verträge, Konten oder Karten |
| Finanz-Check | 📊 interne Auswertung | Punktwert aus den eigenen App-Daten – **keine SCHUFA-Auskunft**, keine Bonitätsbewertung |
| Finanzassistent | 📊 interne Auswertung | regelbasiert, nur Zahlen aus der App; Szenarien sind Rechenbeispiele, keine Beratung |
| Budgets, Prognose, Dashboard | 📊 interne Auswertung | aus den vorhandenen Umsätzen; Prognose mit günstigem/ungünstigem Fall |
| Gemeinsame Ausgaben | 🎮 + 📝 | Ausgleich per Spielgeld-Überweisung; Barzahlungen nur als Vermerk |
| Verträge | 📝 Vermerk | Fristen werden berechnet und erinnert; **gekündigt wird nicht über die App** |
| Verbundene Banken – Sandbox | 🎮 Simulation | „Sandbox-Banken“ mit Testdaten |
| Verbundene Banken – echt (GoCardless) | ✅ echt, nur lesend | technisch vorbereitet; nur mit Vertrag und eigenen Zugangsdaten (`GOCARDLESS_*`), im Projekt **nicht freigeschaltet** |
| CSV-Import | ✅ echte Daten | Umsätze, die der Nutzer selbst aus seiner Bank exportiert |
| Dokumente | ✅ echte Dateien | vom Nutzer hochgeladen, verschlüsselt gespeichert |
| Benachrichtigungen | ✅ echt | in der App live (WebSocket); bei geschlossener App nur Android |
| Zeit vorspulen | 🧪 Demo | Admin-Funktion, um Monate in Minuten zu zeigen |

## Bekannte Grenzen

| Bereich | Grenze |
|---|---|
| Rechtliches | keine Identitätsprüfung (KYC), keine Geldwäsche-Prüfung, keine Zahlungsdiensterichtlinie – für echtes Geld wäre eine Banklizenz bzw. ein lizenzierter Partner nötig |
| Uploads | Typprüfung am Inhalt (nur PDF/PNG/JPEG, 5 MB), aber **keine Virenprüfung**; PDFs lassen sich in den Handy-Apps noch nicht öffnen (Web-App: Herunterladen) |
| Export | CSV-Export auf dem Handy über die Zwischenablage (kein Teilen-Dialog) |
| Verträge | Fristberechnung vereinfacht (Mindestlaufzeit, feste Verlängerung, Frist in Tagen/Wochen/Monaten); keine Sonderkündigungsrechte |
| Finanzassistent | versteht nur bestimmte Fragen (Schlüsselwörter für Kategorien, Monate, Themen) |
| Betrieb | eine Server-Instanz: Rate-Limit und Tageslauf sind nicht für mehrere Instanzen ausgelegt |
| Externe Dienste | Bundesbank-Zinsen nur, wenn die API erreichbar ist (sonst Hinweis „veraltet“) |
| Lasttest | einfacher Test im Prozess (T137); kein verteilter Lasttest gegen eine Produktionsumgebung |
| iOS | kein App-Store-Build (nur Web-App auf dem Home-Bildschirm oder unsignierte IPA) |
| Push | bei geschlossener App nur Android (Hintergrundabruf), kein Apple Push |
