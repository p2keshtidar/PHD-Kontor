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

## 7. Finanzassistent ⭐ (Kernidee des Projekts)

| Schritt | Was tun | Was passiert |
|---|---|---|
| 7.1 | **Sparen → „Beispielmonate erzeugen“** → Profil „Berufseinsteiger“ | 6 Monate Gehalt, Miete, Einkäufe … werden gebucht |
| 7.2 | Karte **„Jeden Monat übrig“** antippen | Finanzüberblick: Balkendiagramm Einnahmen/Ausgaben, Kategorien, Durchschnitt |
| 7.3 | **„Sparplan“** → Ziel „Urlaub“, 3.000 €, in 18 Monaten → Risikostufe | Ratenvorschlag + **Ampel** (grün/gelb/rot), ob es machbar ist |
| 7.4 | Gleiches mit 50.000 € in 12 Monaten | **Rot** + Alternativvorschlag (längere Laufzeit/kleineres Ziel) |
| 7.5 | **„Investplan“** → 200 € → „Ausgewogen“ | monatliche Rate in fiktive Fonds, Musterdepot |
| 7.6 | **„Kredit“** → Auto, 20.000 €, 60 Monate → **„Angebote vergleichen“** | 4 Banken; günstigstes passendes Angebot ist markiert |
| 7.7 | Angebot öffnen → **Tilgungsplan** → **Abschließen** | 20.000 € sind sofort auf dem Girokonto, Kredit erscheint im Bereich Sparen |
| 7.8 | Kredit öffnen → **Sondertilgung** → z. B. 1.000 € → **Auswirkung berechnen** | Vorschau: Restschuld, offene Raten und Zinsersparnis vorher → nachher. **Jetzt sondertilgen** bucht den Betrag; die Rate bleibt gleich, der Kredit endet früher |
| 7.9 | Sparplan öffnen → **Erinnerung vor der Abbuchung** | Aus / 1 / 3 / 7 Tage vorher. Standard: 3 Tage. Reicht das Guthaben nicht, warnt die Erinnerung |

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
