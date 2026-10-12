package de.frauas.girokonto.booking;

import java.time.LocalDate;

/**
 * Filter für die Umsatzliste (/F130/). Alle Felder sind optional und frei kombinierbar.
 *
 * @param direction IN (Eingänge) oder OUT (Ausgänge)
 * @param q         Freitext: sucht in Verwendungszweck und Name der Gegenpartei
 */
public record BookingFilter(
        LocalDate from,
        LocalDate to,
        Direction direction,
        Long minCents,
        Long maxCents,
        BookingType type,
        String q) {

    public enum Direction { IN, OUT }
}
