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

## 4. Umsätze

Startseite → **„Alle anzeigen“** → suchen (Name, Zweck), filtern (Symbol oben rechts), Umsatz antippen → Details und **Kategorie ändern**.

## 5. Karte

1. Unten **„Karte“**.
2. **„Kartendaten zeigen“** → Passwort → Nummer und CVC sind 30 Sekunden sichtbar.
3. **„Demo: Kartenzahlung simulieren“** → Händler wählen → Bezahlen → Umsatz erscheint.
4. Karte **sperren** → nochmal bezahlen → wird **abgelehnt** (mit Benachrichtigung).
5. Tageslimit auf 50 € stellen → Zahlung über 50 € → abgelehnt.

## 6. Unterkonten („Töpfe“)

Startseite → **„Neues Unterkonto“** → Name + Symbol → antippen → **Einzahlen**. Das Gesamtvermögen bleibt gleich. **Auflösen** bucht alles zurück.

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

---

✅ Damit hast du alle Musskriterien und die Kannkriterien /K010/ und /K020/ aus dem [Pflichtenheft](../pflichtenheft/Pflichtenheft_Girokonto_v0.2.pdf) einmal gesehen.
