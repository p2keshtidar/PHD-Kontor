package de.frauas.girokonto.csvimport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liest Umsatz-CSV-Dateien deutscher Banken (Sparkasse, Volksbank, ING, DKB, comdirect, N26 …).
 * Die Formate unterscheiden sich, darum wird die Kopfzeile gesucht und die Spalten werden über
 * typische Namen erkannt ("Buchungstag", "Betrag", "Verwendungszweck" …). Vorspann-Zeilen
 * (z. B. Kontonummer, Zeitraum) vor der Kopfzeile werden übersprungen.
 */
public final class CsvParser {

    public static final int MAX_ROWS = 5000;

    public record Row(int line, LocalDate date, long amountCents, String counterparty, String description) {}

    public record Result(String format, List<Row> rows, List<String> errors) {}

    private static final String[] DATE_COLS = {"buchungstag", "buchungsdatum", "buchung", "datum", "date", "booking date", "wertstellung", "valuta"};
    private static final String[] AMOUNT_COLS = {"betrag", "umsatz", "amount", "betrag (eur)", "betrag (€)"};
    private static final String[] PARTY_COLS = {"beguenstigter/zahlungspflichtiger", "begünstigter/zahlungspflichtiger",
            "name zahlungsbeteiligter", "auftraggeber/empfänger", "auftraggeber / begünstigter", "zahlungsempfänger",
            "empfänger", "payee", "gläubiger", "name", "auftraggeber", "partner"};
    private static final String[] TEXT_COLS = {"verwendungszweck", "buchungstext", "beschreibung", "description",
            "vorgang", "payment reference", "text"};

    private CsvParser() {
    }

    public static Result parse(String content) {
        List<String> errors = new ArrayList<>();
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        String[] lines = text.split("\\r?\\n");

        // Kopfzeile in den ersten 30 Zeilen suchen
        int headerIdx = -1;
        char sep = ';';
        int[] cols = null;
        for (int i = 0; i < Math.min(30, lines.length) && headerIdx < 0; i++) {
            for (char candidate : new char[] {';', ',', '\t'}) {
                List<String> cells = split(lines[i], candidate);
                if (cells.size() < 2) {
                    continue;
                }
                int[] found = findColumns(cells);
                if (found[0] >= 0 && found[1] >= 0) {
                    headerIdx = i;
                    sep = candidate;
                    cols = found;
                    break;
                }
            }
        }
        if (cols == null) {
            throw new IllegalArgumentException("Keine Kopfzeile mit Datum und Betrag gefunden. "
                    + "Unterstützt werden CSV-Exporte mit Spalten wie „Buchungstag“ und „Betrag“.");
        }
        String format = "CSV (" + (sep == ';' ? "Semikolon" : sep == ',' ? "Komma" : "Tabulator") + ")";

        List<Row> rows = new ArrayList<>();
        for (int i = headerIdx + 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                errors.add("Mehr als " + MAX_ROWS + " Zeilen – der Rest wurde ignoriert.");
                break;
            }
            List<String> cells = split(lines[i], sep);
            try {
                LocalDate date = parseDate(cell(cells, cols[0]));
                long cents = parseAmount(cell(cells, cols[1]));
                if (cents == 0) {
                    continue;
                }
                rows.add(new Row(i + 1, date, cents, trim(cell(cells, cols[2]), 200), trim(cell(cells, cols[3]), 400)));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                if (errors.size() < 20) {
                    errors.add("Zeile " + (i + 1) + ": " + (e instanceof DateTimeParseException
                            ? "Datum nicht lesbar" : e.getMessage()));
                }
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("In der Datei wurden keine Umsätze gefunden.");
        }
        return new Result(format, rows, errors);
    }

    /** Spaltennummern für Datum, Betrag, Gegenpartei, Text (-1 = fehlt). */
    static int[] findColumns(List<String> header) {
        List<String> h = header.stream().map(c -> c.trim().toLowerCase(Locale.ROOT)).toList();
        return new int[] {find(h, DATE_COLS), find(h, AMOUNT_COLS), find(h, PARTY_COLS), find(h, TEXT_COLS)};
    }

    /** Erst exakter Name, dann "beginnt mit" – in der Reihenfolge der Liste. */
    private static int find(List<String> header, String[] names) {
        for (String n : names) {
            int i = header.indexOf(n);
            if (i >= 0) {
                return i;
            }
        }
        for (String n : names) {
            for (int i = 0; i < header.size(); i++) {
                if (header.get(i).startsWith(n)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** CSV-Zeile mit Anführungszeichen ("a;b" bleibt zusammen, "" = Anführungszeichen). */
    static List<String> split(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == sep && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    static LocalDate parseDate(String s) {
        String v = s.trim();
        if (v.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return LocalDate.parse(v.substring(0, 10));
        }
        if (v.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) {
            return LocalDate.parse(v, DateTimeFormatter.ofPattern("d.M.yyyy"));
        }
        if (v.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{2}")) {
            return LocalDate.parse(v, DateTimeFormatter.ofPattern("d.M.yy"));
        }
        throw new IllegalArgumentException("Datum „" + v + "“ nicht lesbar");
    }

    /** "-1.234,56", "12,50 €", "+3.000,00", "-12.5" → Cent */
    static long parseAmount(String s) {
        String v = s.replace("€", "").replace("EUR", "").replace(" ", "").replace(" ", "").trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("Betrag fehlt");
        }
        boolean negative = v.startsWith("-") || v.endsWith("-");
        v = v.replace("-", "").replace("+", "");
        if (v.contains(",")) {
            v = v.replace(".", "").replace(',', '.');      // deutsches Format
        } else if (v.chars().filter(c -> c == '.').count() > 1) {
            v = v.replace(".", "");                        // nur Tausenderpunkte
        }
        if (!v.matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("Betrag „" + s.trim() + "“ nicht lesbar");
        }
        long cents = new java.math.BigDecimal(v).movePointRight(2).longValueExact();
        return negative ? -cents : cents;
    }

    private static String cell(List<String> cells, int idx) {
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : "";
    }

    private static String trim(String s, int max) {
        String v = s.trim().replaceAll("\\s+", " ");
        return v.length() > max ? v.substring(0, max) : v;
    }
}
