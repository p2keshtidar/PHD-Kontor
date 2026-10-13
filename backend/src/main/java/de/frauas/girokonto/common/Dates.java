package de.frauas.girokonto.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Datum für Texte an den Nutzer (Meldungen, Fehlermeldungen): deutsches Format 01.11.2026. */
public final class Dates {

    private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private Dates() {
    }

    public static String de(LocalDate date) {
        return date == null ? "" : DE.format(date);
    }
}
