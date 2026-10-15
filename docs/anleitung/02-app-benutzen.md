# 🧭 Anleitung 2: App ausprobieren – geführter Rundgang

[← Installieren](01-installieren.md) · [Übersicht](../../README.md) · weiter: [Entwickeln →](03-entwickeln.md)

Voraussetzung: Der Server läuft ([Anleitung 1, Teil A](01-installieren.md#teil-a--server-auf-dem-pc-starten)).
Dauer des Rundgangs: ca. 15 Minuten. Ideal auch als **Ablauf für die Präsentation**.

---

## 1. Konto eröffnen

1. **„Noch kein Konto? Jetzt registrieren“** antippen.
2. Formular ausfüllen:
   - Vor- und Nachname, E-Mail, Telefonnummer (z. B. `0151 23456789`)
   - Geburtsdatum (mindestens 18 Jahre)
   - Passwort mit **mindestens 10 Zeichen**
3. **Registrieren** → Startseite mit **1.000,00 €** Startguthaben und deiner (fiktiven) IBAN ✅

## 2. Zweiten Nutzer anlegen (für Überweisungen)

- Im Browser ein **Inkognito-Fenster** öffnen (`Strg + Umschalt + N`) → `http://localhost:8080` → zweiten Nutzer registrieren (andere E-Mail und Telefonnummer).
- Oder: zweiter Nutzer auf dem Handy.

## 3. Geld überweisen

1. Unten **„Überweisen“**.
2. Empfänger: E-Mail, Telefonnummer **oder** IBAN des zweiten Nutzers.
3. Betrag z. B. `25,50`, Verwendungszweck eingeben → **Weiter**.
4. Zusammenfassung prüfen (Name des Empfängers wird angezeigt) → **Jetzt senden**.
5. Erfolgsseite → **Im Adressbuch speichern**.

👀 Beim zweiten Nutzer erscheint **sofort** eine Meldung, die 🔔 zählt hoch, der Kontostand ändert sich ohne Neuladen.

**Fehlerfälle zeigen:** mehr als den Kontostand überweisen · an sich selbst · unbekannte E-Mail.

## 4. Einnahmen & Ausgaben eintragen

Damit der Finanzassistent mit deinem echten Leben rechnet, trägst du Gehalt, Miete und andere Ausgaben ein.

1. Startseite → **„+ Einnahme“** oder **„− Ausgabe“** (oder: Sparen → **Einnahmen & Ausgaben**).
2. Schnellauswahl antippen, z. B. **Gehalt** oder **Miete** – Bezeichnung, Kategorie und „monatlich“ sind dann schon gesetzt. Oder selbst eintragen, z. B. „Urlaub Mallorca“.
3. **Betrag** eingeben, **Wie oft?** wählen: einmalig, monatlich, vierteljährlich, halbjährlich oder jährlich.
4. **Datum** (bei regelmäßigen Einträgen: erste Buchung, Tag 1–28). Optional ein Enddatum.
5. **Speichern**.

| Fall | Was passiert |
|---|---|
| Datum heute oder in der Vergangenheit (bis 12 Monate) | wird **sofort gebucht** – bei „monatlich seit Mai“ für jeden Monat seitdem |
| Datum in der Zukunft | wird am Tag gebucht (beim Vorspulen im Demo-Modus sieht man es sofort) |
| Ausgabe höher als das Guthaben | Fehlermeldung beim Speichern; später: Benachrichtigung „Ausgabe nicht gebucht“ |

Die Beträge erscheinen in Kontostand, Umsätzen und im **Finanzüberblick**. Unter **Einnahmen & Ausgaben verwalten** siehst du, was pro Monat regelmäßig rein- und rausgeht, und kannst Einträge ändern, pausieren oder löschen (bereits gebuchte Beträge bleiben in den Umsätzen).

## 4a. Umsätze

Startseite → **„Alle anzeigen“** → suchen (Name, Zweck), filtern (Symbol oben rechts), Umsatz antippen → Details und **Kategorie ändern**.

## 4a+. Weitere Konten, Umbuchen, alle Umsätze

| Schritt | Was tun | Was passiert |
|---|---|---|
| 1 | Startseite → unter den Bankkonten **„Konto eröffnen“** → **Tagesgeld** | neues Konto mit IBAN; Zinssatz der Simulation (fiktiv, Standard 2 % p. a.) |
| 2 | **„Umbuchen“** → von „Girokonto“ nach „Tagesgeld“, z. B. 500 € | sofort gebucht; Umbuchungen gehen zwischen Girokonten, Tagesgeld und digitalen Konten |
| 3 | Admin spult die Zeit bis zum Monatsersten vor | **Zinsen** für den Vormonat werden gutgeschrieben (tagesgenau auf den Stand am Ende jedes Tages) |
| 4 | **„Konto eröffnen“** → **Weiteres Girokonto** | eigene IBAN: andere Nutzer können per IBAN dorthin überweisen. Überweisungen an andere gehen nur vom Girokonto |
| 5 | Konto öffnen → Menü oben rechts → **Umbenennen** / **Auflösen** | beim Auflösen: Zinsen bis gestern, Restguthaben aufs Girokonto |
| 6 | **„Alle Umsätze“** | Projektbank und verbundene Banken zusammen, neueste zuerst; Filter nach Quelle, Suche, Umbuchungen ein-/ausblenden; Summe der Einnahmen und Ausgaben |

Höchstens 3 weitere Konten. Alle Konten der Projektbank sind eine **Simulation mit Spielgeld** – keine echten Bankkonten, keine Einlagensicherung.

**Umsätze verwalten:**

| Was | Wo | Hinweis |
|---|---|---|
| Notiz zu einem Umsatz | Umsatz antippen → **Notiz → Hinzufügen** | nur für dich sichtbar (auch in geteilten digitalen Konten); die Suche findet auch Notizen |
| CSV-Export | **Alle Umsätze** → Symbol ⬇ oben rechts | exportiert die aktuelle Auswahl (Quelle, Suche). Im Browser als Datei, in der Handy-App in die Zwischenablage (zum Einfügen in eine Tabelle) |
| CSV-Importe | **Alle Umsätze** → Quelle **„CSV-Import“** | importierte Umsätze anderer Banken stehen mit in der Liste (ändern keine Kontostände) |
| Doppelte Umsätze | **Alle Umsätze** → Hinweis „mögliche doppelte Umsätze“ oder Symbol ⇄ | gleicher Betrag, höchstens 3 Tage auseinander, gemeinsames Stichwort – z. B. selbst eingetragen *und* von der Bank geliefert. **„Kein Duplikat“** blendet das Paar aus; bei einer verbundenen Bank kann ein Umsatz **nicht mitgezählt** werden |

## 4b. Echte Bankkonten verbinden (Sandbox)

Konten anderer Banken in Girokonto sehen – **nur lesen**, Girokonto kann dort kein Geld bewegen. Im Projekt gibt es
dafür eine **Sandbox**: simulierte Banken mit Testdaten, überall als **„Testdaten“** gekennzeichnet. Echte Banken
brauchen einen Vertrag mit einem zugelassenen Anbieter ([Details](../technik/open-banking.md)).

1. Startseite → Bereich **„Echte Bankkonten“** → **„Konten anderer Banken verbinden“**.
2. **Sandbox-Hausbank** antippen → Dialog **„Test-Freigabe (Sandbox)“** → **„Freigeben (Test)“**.
   (Bei einer echten Bank öffnet sich hier die Anmeldeseite der Bank.)
3. Meldung „Bank verbunden: 2 Konto/Konten, … Umsätze importiert“ – auf der Startseite stehen jetzt **Girokonto**
   und **Tagesgeld** der Sandbox-Bank mit Saldo, getrennt von deinem Projektbank-Konto und den Unterkonten.
4. Konto antippen → **Umsätze**: suchen, nach Status filtern (vorgemerkt, gebucht, storniert, zurückgebucht) und
   Kategorie. Umsatz antippen → oben die Daten der Bank (unverändert), unten **deine Angaben**: Kategorie, Notiz,
   Schlagwörter, „im Finanzüberblick mitzählen“.
5. **„Verwalten“** → je Bank: Status, Berechtigungen, letzter Abruf, Ablauf der Freigabe · **„Jetzt abrufen“** ·
   Menü **⋮ → „Zugriff widerrufen“** (wahlweise mit Löschen aller Umsätze dieser Bank).

| Ausprobieren | Was passiert |
|---|---|
| Zeit 5 Tage vorspulen, dann „Jetzt abrufen“ | Vormerkungen werden gebucht; die Hotel-Reservierung wird **storniert** |
| „Sandbox-Bank nicht erreichbar“ verbinden | Bank verbunden, aber Abruf **gestört** – mit Hinweis |
| „Sandbox-Bank mit 1-Tag-Freigabe“ verbinden, 2 Tage vorspulen | Freigabe **abgelaufen** (Benachrichtigung) → **„Freigabe erneuern“** |

## 5. Karte

1. Unten **„Karte“**.
2. **„Kartendaten zeigen“** → Passwort → Nummer und CVC sind 30 Sekunden sichtbar.
3. **„Demo: Kartenzahlung simulieren“** → Händler wählen → Bezahlen → Umsatz erscheint.
4. Karte **sperren** → nochmal bezahlen → wird **abgelehnt** (mit Benachrichtigung).
5. Tageslimit auf 50 € stellen → Zahlung über 50 € → abgelehnt.

### 5a. Kreditkarte (Simulation)

Unten auf **„Karte“** ganz nach unten scrollen: **„Kreditkarte einrichten (Simulation)“** → Verfügungsrahmen 100 € bis 5.000 €.

| Aktion | Ergebnis |
|---|---|
| **Demo-Zahlung** (optional „In Fremdwährung“) | Betrag erscheint unter „Seit der letzten Abrechnung“; bei Fremdwährung + 1,75 % Auslandseinsatz |
| Zahlung über dem freien Rahmen | abgelehnt |
| **Kreditkarte sperren** | Zahlungen werden abgelehnt, bis du entsperrst |
| **Limit ändern** | nicht unter den offenen Betrag |
| Zeit bis zum Monatsersten vorspulen | Abrechnung wird vom Girokonto eingezogen; reicht das Guthaben nicht, bleibt sie **offen** und wird täglich erneut versucht (kein Dispo) |

Es ist eine **virtuelle Spielgeld-Karte**: keine echte Karte, keine Kreditprüfung, keine Bestellung.

## 6. Digitale Konten („Töpfe“)

Die Startseite hat zwei getrennte Bereiche: **Bankkonten** (dein Girokonto der Projektbank – eine Simulation – und
verbundene Banken) und **Digitale Konten**. Digitale Konten sind Töpfe in deinem Projektbank-Konto: Geld wird dorthin
umgebucht, sie sind aber **keine eigenen Bankkonten** und haben keine eigene Einlagensicherung (ⓘ neben der Überschrift).

1. **„Neues digitales Konto“** → Name, **Zweck** (Urlaub, Auto, Immobilie, Rückzahlung, WG-Kasse, Notgroschen …),
   optional **Zielbetrag** und **Zieldatum** → Speichern.
2. Antippen → **Einzahlen**. Die Karte zeigt **Fortschritt**, **Restbetrag** und – mit Zieldatum – die **nötige Rate pro
   Monat**. Wird das Ziel erreicht, kommt eine Benachrichtigung.
3. **Teilen** → E-Mail/Telefon/IBAN + Rechte: *einzahlen und auszahlen*, *nur einzahlen* oder *nur ansehen*. Rechte
   später über **⋮** beim Mitglied ändern.
4. **Auflösen** bucht das Guthaben aufs Girokonto zurück. Das Gesamtvermögen bleibt beim Umbuchen gleich.
5. **Automatisch sparen** (im digitalen Konto) → **„Sparregel anlegen“** → Quelle, Betrag, Rhythmus (*wöchentlich,
   alle 2 Wochen, monatlich, vierteljährlich*), Start (heute oder später) und optional Ende → Speichern.
   - Quelle **Girokonto**: Startet die Regel heute, wird der Betrag sofort umgebucht, danach automatisch zu jedem Termin.
   - Quelle **verbundenes Bankkonto** (siehe 4b): Das Geld bleibt bei der Bank – es wird nur **vorgemerkt** und dem Ziel
     zugerechnet (kein Geldtransfer). Vorher wird geprüft, ob genug frei ist.
   - Unter **Vorgänge** steht jeder Termin mit Status (*umgebucht, vorgemerkt, neuer Versuch am …, fehlgeschlagen*).
     Fehlt Guthaben, versucht die App es an den nächsten zwei Tagen erneut; danach ↻ **erneut versuchen**.
   - **⋮** an der Regel: pausieren, fortsetzen, Betrag oder Rhythmus ändern, beenden. Mit Zielbetrag zeigt die App, wann
     das Ziel voraussichtlich erreicht ist.

### 6a. Gemeinsame Ausgaben (WG, Reise, Paar)

**„Sparen“ → „Gemeinsame Ausgaben“** → **„+ Gruppe“** → Name (z. B. „WG Berger“). Oben rechts **Person hinzufügen**
(E-Mail, Telefon oder IBAN eines anderen App-Nutzers).

| Aktion | Ergebnis |
|---|---|
| **„+ Ausgabe“**: Wofür, Betrag, bezahlt von, aufteilen auf | gleichmäßig auf die Beteiligten verteilt (Rest-Cent an die Ersten); alle Beteiligten bekommen eine Benachrichtigung mit ihrem Anteil |
| **Salden** | grün: bekommt Geld, rot: schuldet; die Summe ist immer 0 |
| **So wird ausgeglichen** | möglichst wenige Zahlungen, z. B. „Cem → Anna: 68,33 €“ |
| **Überweisen** (wer schuldet) | normale Überweisung der Projektbank (Spielgeld) mit allen Prüfungen und Tageslimit; doppelt tippen bucht nicht doppelt |
| **Bar erhalten** (wer Geld bekommt) | Barzahlung wird festgehalten – bestätigen kann das nur der Empfänger |
| Gruppe verlassen / löschen | nur mit ausgeglichenem Saldo |

Über die Schnittstelle sind auch feste Anteile möglich (`shares`, müssen genau aufgehen).

## 7. Finanzassistent ⭐ (Kernidee des Projekts)

| Schritt | Was tun | Was passiert |
|---|---|---|
| 7.1 | **Sparen → „Beispielmonate erzeugen“** → Profil „Berufseinsteiger“ | 6 Monate Gehalt, Miete, Einkäufe … werden gebucht |
| 7.2 | Karte **„Jeden Monat übrig“** antippen | Finanzüberblick: Balkendiagramm Einnahmen/Ausgaben, Kategorien, Durchschnitt |
| 7.3 | **„Sparplan“** → Ziel „Urlaub“, 3.000 €, in 18 Monaten → Risikostufe | Ratenvorschlag + **Ampel** (grün/gelb/rot), ob es machbar ist |
| 7.4 | Gleiches mit 50.000 € in 12 Monaten | **Rot** + Alternativvorschlag (längere Laufzeit/kleineres Ziel) |
| 7.5 | **„Investplan“** → 200 € → „Ausgewogen“ | monatliche Rate in fiktive Fonds, Musterdepot |
| 7.6 | **„Kredit“** → Auto, 20.000 €, 60 Monate, **Bonität** (Selbsteinschätzung) und optional **Filter** (höchste Monatsrate, Budget, nur erfüllbare Angebote) → **„Angebote vergleichen“** | Rechenbeispiele von 4 **fiktiven** Banken mit Sollzins, effektivem Jahreszins und Gesamtbetrag; günstigstes passendes Angebot ist markiert; „Anbieter und Datenquellen“ zeigt, was angebunden ist |
| 7.7 | Angebot öffnen → Voraussetzungen, Konditionen, Tilgungsplan → Einwilligung → **„Unverbindlich anfragen“** → bei Zusage bestätigen → **„Vertrag abschließen“** → **„Auszahlung anfordern“** | Vier getrennte Schritte: Anfrage (Zusage 14 Tage gültig, noch nichts verbindlich), Vertrag (noch kein Geld; Widerruf möglich), Auszahlung (20.000 € auf dem Girokonto, erste Rate in einem Monat) |
| 7.8 | Kredit öffnen → **Sondertilgung** → z. B. 1.000 € → **Auswirkung berechnen** | Vorschau: Restschuld, offene Raten und Zinsersparnis vorher → nachher. **Jetzt sondertilgen** bucht den Betrag; die Rate bleibt gleich, der Kredit endet früher |
| 7.9 | Sparplan öffnen → **Erinnerung vor der Abbuchung** | Aus / 1 / 3 / 7 Tage vorher. Standard: 3 Tage. Reicht das Guthaben nicht, warnt die Erinnerung |

### 7d. Fragen an den Finanzassistenten, Monatsbericht, „Was wäre, wenn …“

**„Sparen“ → „Finanzassistent“** (oder im Finanzüberblick oben das Assistenten-Symbol). Der Assistent arbeitet
**regelbasiert** im Backend und nutzt **nur deine Zahlen aus der App** (Projektbank, verbundene Banken, CSV-Import) –
keine externe KI, keine erfundenen Werte. Ohne Daten sagt er das.

| Reiter | Beispiele | Ergebnis |
|---|---|---|
| **Fragen** | „Wofür gebe ich am meisten aus?“, „Wie viel habe ich letzten Monat für Lebensmittel ausgegeben?“, „Was habe ich im August für Miete bezahlt?“, „Wo kann ich sparen?“, „Reicht mein Geld bis zum Monatsende?“, „Wie stehen meine Budgets?“ | Antwort mit den Zahlen dahinter (z. B. Betrag und Schnitt der letzten 3 vollen Monate) |
| **Monatsbericht** | ‹ › wechselt den Monat (letzte 12 Monate) | Einnahmen, Ausgaben, übrig, Sparquote, Vergleich zum Vormonat, stark gestiegene Kategorie, größter Posten |
| **Was wäre, wenn …** | „Weniger ausgeben“ (Kategorie, Betrag), „Einnahmen ändern“ (−50 % bis +50 %), „Für ein Ziel sparen“ (Betrag, Ziel) | freier Betrag und Sparquote vorher → nachher, Monate bis zum Ziel, Warnungen; als **Rechenbeispiel** gekennzeichnet |

### 7e. Verträge & Dokumente

**„Sparen“ → „Verträge & Dokumente“** → **„+ Vertrag“**: Name, Anbieter, Art, Kosten (monatlich, vierteljährlich,
jährlich), Vertragsbeginn, Mindestlaufzeit, Verlängerung, Kündigungsfrist (Tage/Wochen/Monate) und Erinnerung.

| Was | Ergebnis |
|---|---|
| Übersicht | Fixkosten aus Verträgen pro Monat und Jahr; Verträge mit der nächsten Kündigungsfrist zuerst, Fristen in den nächsten 30 Tagen rot |
| Vertrag öffnen | „Kündigen bis … (Vertragsende …), sonst Verlängerung um …“ – berechnet aus Beginn, Mindestlaufzeit, Verlängerung und Frist |
| Erinnerung | Benachrichtigung einmal je Vertragsperiode, z. B. 14 Tage vor Fristende (Zeit vorspulen zum Ausprobieren) |
| **Kündigung vermerken** | nur ein Vermerk in der App – **die Kündigung selbst schickst du an den Anbieter** |
| **Dokumente → Hochladen** | PDF, PNG oder JPEG bis 5 MB; Typ wird am Inhalt geprüft, Inhalt **verschlüsselt** gespeichert, nur für dich abrufbar. Bilder werden in der App angezeigt; PDFs in der Web-App heruntergeladen |

### 7c. Finanz-Marktplatz und Anträge (Simulation)

**Sparen → „Finanz-Marktplatz“**: Girokonten, Kreditkarten, Tagesgeld, Festgeld und Depots **fiktiver Anbieter** vergleichen. Jede Karte zeigt die Konditionen und eine **Beispielrechnung** (Kosten pro Jahr bzw. Zinsertrag für einen frei wählbaren Betrag); „Vor- und Nachteile“ klappt Details auf. ☆ merkt ein Angebot, **„Vergleichen“** legt 2–4 Angebote nebeneinander. **„Antrag (Simulation)“** legt einen simulierten Antrag an (Bestätigung nötig): Unterlagen als *bereitgestellt* markieren → am nächsten Tag simulierte Zusage oder Ablehnung (geprüft wird z. B. „Einnahmen ab …“ mit deinem Finanzüberblick). Es entsteht **kein Vertrag, kein Konto und keine Karte**. Unter **„Meine Anträge“** kannst du eine Entscheidung/Notiz festhalten oder zurückziehen.

### 7b. Budgets & Prognose

Finanzüberblick → Symbol 🎯 oben rechts (oder im Dashboard **„Budgets & Prognose“**).

| Bereich | Was du siehst |
|---|---|
| Prognose Girokonto | Kontostand für die nächsten 90 Tage: erwartet, günstig (20 % weniger übrige Ausgaben), ungünstig (20 % mehr). Bekannte Buchungen (Einnahmen & Ausgaben, Daueraufträge, Raten, Sparregeln) sind genau eingeplant, übrige Umsätze wie im Schnitt der letzten 3 Monate. **Warnung**, wenn das Guthaben voraussichtlich nicht reicht |
| Budgets | Betrag pro Monat je Ausgabenkategorie; Status *im Rahmen / fast aufgebraucht (ab 80 %) / überschritten*, Hochrechnung bis Monatsende. Benachrichtigung bei 80 % und 100 % (einmal je Monat) |
| Ausgaben ohne Budget | Vorschläge: antippen → Budget festlegen |
| Ungewöhnliche Ausgaben | einzelne Ausgaben weit über dem Üblichen oder Kategorien deutlich über dem Monatsschnitt |

Alles ist eine **Schätzung aus deinen eigenen Daten** – keine Zusage.

### 7a. Finanz-Dashboard

Auf der Startseite die Karte **„Gesamtvermögen“** antippen.

| Bereich | Was du siehst |
|---|---|
| Gesamtvermögen | Summe mit allen Bestandteilen: Girokonto, digitale Konten, Sparpläne, Depot, andere Banken, Kredite (negativ) |
| Verfügbar und zurückgelegt | frei verfügbar (Girokonto) und zurückgelegt (digitale Konten, Sparpläne, Depot); bei anderen Banken vorgemerkte Beträge getrennt |
| Vermögensverlauf | Gesamtvermögen an den letzten 12 Monatsenden. Hohle Punkte: Monate ohne verbundene Banken, weil dafür noch keine Umsätze vorliegen |
| Cashflow | Einnahmen, Ausgaben und Saldo dieses Monats, Diagramm der letzten 6 Monate |
| Größte Ausgaben | die 5 größten Kategorien mit Anteil an allen Ausgaben des Monats |
| Schulden | Restschuld, monatliche Raten, Raten im Verhältnis zu den Einnahmen; Kredit antippen öffnet ihn |
| Sparziele | digitale Konten mit Ziel und Sparpläne mit Fortschritt |
| Konten und Karten | alle Konten; Kontostände verbundener Banken, die älter als 2 Tage sind, sind als **veraltet** markiert |

Alle Zahlen kommen aus den vorhandenen Daten – geschätzt wird nichts. Girokonto, Pläne, Depot und Kredite sind eine Simulation mit Spielgeld.

**Finanz-Check:** Auf dem Dashboard zeigt der Finanz-Check 0–100 Punkte aus vier Teilen (je 0–25): Sparquote,
Notgroschen (zurückgelegtes Geld in Monatsausgaben), Kreditraten im Verhältnis zu den Einnahmen und eingehaltene Budgets.
Ohne Umsätze erscheint „ohne Daten“. Das ist eine **interne Orientierung aus deinen App-Daten** – keine SCHUFA-Auskunft,
keine Bonitätsbewertung, keine Finanzberatung.

## 8. Zeit vorspulen (Demo-Modus)

1. **Profil → Abmelden**.
2. Als Admin anmelden: `admin@projektbank.de` / `admin12345`.
3. **Profil → Demo-Modus → „+3 Monate“**.
4. Abmelden und wieder als normaler Nutzer anmelden.

👀 Spar-, Invest- und Kreditraten wurden gebucht, das Depot hat einen Kursverlauf, die Restschuld ist gesunken, und es gibt neue Benachrichtigungen – darunter die **Erinnerungen** „Sparrate in 3 Tagen“.

## 9. Weitere Funktionen (Kannkriterien)

### 9.1 Dauerauftrag (/K030/)
1. **Überweisen** → oben in der Leiste **„Daueraufträge“** → **„Neuer Dauerauftrag“**.
2. Empfänger (E-Mail des zweiten Nutzers), Betrag `50`, Zweck „Miete Anteil“, **monatlich**, Ausführungstag z. B. **5** → **Einrichten**.
3. Antippen → Betrag oder Tag ändern, **pausieren** oder **löschen**.
4. Zum Ausprobieren als Admin vorspulen (Abschnitt 8): Am 5. wird überwiesen, beide bekommen eine Meldung. Reicht das Geld nicht, kommt „Dauerauftrag nicht ausgeführt“.

### 9.2 Zwei-Faktor-Anmeldung (/K050/)
1. Auf dem Handy eine Authenticator-App installieren (Google Authenticator, Microsoft Authenticator, 2FAS …).
2. **Profil → Zwei-Faktor-Anmeldung → Einrichten**.
3. QR-Code mit der Authenticator-App scannen (oder Schlüssel abtippen).
4. Den 6-stelligen Code eingeben → **Bestätigen und einschalten**.
5. Abmelden und neu anmelden: Nach dem Passwort fragt die App nach dem Code.
6. Ausschalten: gleiche Seite → Passwort + Code.

> Die Uhrzeit auf Handy und PC muss stimmen (automatische Zeit einschalten), sonst passen die Codes nicht.

### 9.3 CSV-Import (/K040/)
1. **Sparen → „Jeden Monat übrig“** → oben rechts Symbol **Datei hochladen**.
2. **„Beispieldatei importieren“** – oder im echten Online-Banking die Umsätze als CSV exportieren und **„CSV-Datei auswählen“**.
3. Ergebnis: Anzahl, Zeitraum, Einnahmen, Ausgaben. Zurück im Finanzüberblick sind die Monate gefüllt.
4. Die Kontostände in der App ändern sich **nicht** – es ist Geld bei einer anderen Bank. Ein zweiter Import derselben Datei legt nichts doppelt an. Import löschen: in der Liste „Bisherige Importe“.

### 9.4 Digitales Konto teilen (/K060/)
1. Startseite → Unterkonto öffnen (oder neu anlegen, z. B. „WG-Kasse“).
2. Im Kasten **„Teilen“** → **Person hinzufügen** → E-Mail des zweiten Nutzers.
3. Beim zweiten Nutzer erscheint „WG-Kasse – Geteilt von …“ auf der Startseite. Er kann **einzahlen**, **auszahlen** und die Umsätze sehen. Bei jeder Bewegung bekommt der andere eine Meldung.
4. Nur der Besitzer kann umbenennen, Personen entfernen und auflösen (das Geld geht dann an den Besitzer). Mitglieder können **austreten**.

### 9.5 Meldungen bei geschlossener App (/K070/, Android)
App anmelden, dann komplett schließen. Vom zweiten Konto Geld überweisen. Nach spätestens ca. 15 Minuten erscheint die Systembenachrichtigung. Details: [Push-Benachrichtigungen](../technik/push.md).

---

✅ Damit hast du alle Musskriterien und alle Kannkriterien /K010/–/K070/ aus dem [Pflichtenheft](../pflichtenheft/Pflichtenheft_Girokonto_v0.2.pdf) einmal gesehen.
