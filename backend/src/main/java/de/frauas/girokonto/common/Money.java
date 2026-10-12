package de.frauas.girokonto.common;

import java.util.Locale;

/** Formatiert Cent-Beträge für Texte, z. B. 123456 -> "1.234,56 €". Gerechnet wird immer in Cent. */
public final class Money {

    private Money() {
    }

    public static String format(long cents) {
        return String.format(Locale.GERMANY, "%,.2f €", cents / 100.0);
    }
}
