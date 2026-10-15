package de.frauas.girokonto.allocation;

import java.time.LocalDate;

/** Rhythmus einer Sparregel. Termine werden immer vom Startdatum aus gerechnet (kein Verrutschen am Monatsende). */
public enum AllocationInterval {
    WEEKLY("wöchentlich"),
    BIWEEKLY("alle 2 Wochen"),
    MONTHLY("monatlich"),
    QUARTERLY("vierteljährlich");

    private final String label;

    AllocationInterval(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** k-ter Termin ab dem Start (k = 0 ist der Start selbst). 31.01. monatlich → 28./29.02., 31.03., 30.04. … */
    public LocalDate at(LocalDate start, long k) {
        return switch (this) {
            case WEEKLY -> start.plusWeeks(k);
            case BIWEEKLY -> start.plusWeeks(2 * k);
            case MONTHLY -> start.plusMonths(k);
            case QUARTERLY -> start.plusMonths(3 * k);
        };
    }

    /** Erster Termin am oder nach dem Datum. */
    public LocalDate onOrAfter(LocalDate start, LocalDate date) {
        if (!date.isAfter(start)) {
            return start;
        }
        long k = switch (this) {
            case WEEKLY -> (date.toEpochDay() - start.toEpochDay()) / 7;
            case BIWEEKLY -> (date.toEpochDay() - start.toEpochDay()) / 14;
            case MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(start, date);
            case QUARTERLY -> java.time.temporal.ChronoUnit.MONTHS.between(start, date) / 3;
        };
        k = Math.max(0, k - 1);
        LocalDate d = at(start, k);
        while (d.isBefore(date)) {
            d = at(start, ++k);
        }
        return d;
    }

    /** Erster Termin nach dem Datum. */
    public LocalDate after(LocalDate start, LocalDate date) {
        return onOrAfter(start, date.plusDays(1));
    }

    public static AllocationInterval parse(String s) {
        if (s == null || s.isBlank()) {
            return MONTHLY;
        }
        for (AllocationInterval i : values()) {
            if (i.name().equals(s.trim())) {
                return i;
            }
        }
        return null;
    }
}
