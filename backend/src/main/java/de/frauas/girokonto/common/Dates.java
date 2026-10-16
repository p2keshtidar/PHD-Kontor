package de.frauas.girokonto.common;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Datum für Texte an den Nutzer (Meldungen, Fehlermeldungen): deutsches Format 01.11.2026. */
public final class Dates {

    private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Deutsche Monatsnamen (Texte an den Nutzer; die Übersetzung ins Englische macht {@code ServerTexts}). */
    public static final List<String> MONTHS_DE = List.of("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
            "August", "September", "Oktober", "November", "Dezember");

    private Dates() {
    }

    public static String de(LocalDate date) {
        return date == null ? "" : DE.format(date);
    }

    /** „Oktober 2026“ */
    public static String monthYear(YearMonth month) {
        return month == null ? "" : MONTHS_DE.get(month.getMonthValue() - 1) + " " + month.getYear();
    }
}
