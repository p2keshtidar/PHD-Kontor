package de.frauas.girokonto.multibank;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Zinsen auf dem Tagesgeldkonto: tagesgenau auf den Kontostand am Ende jedes Tages, Zinstage/Jahr
 * nach der tatsächlichen Länge des Jahres (act/act), Gutschrift einmal im Monat, kaufmännisch auf Cent
 * gerundet (Banker's Rounding, HALF_EVEN). Negative Stände bringen keine Zinsen (das Konto kann nicht ins Minus).
 */
public final class InterestMath {

    private InterestMath() {
    }

    /**
     * @param deltas   Summe der Buchungen je Tag (alle Tage bis einschließlich {@code to}, auch vor {@code from})
     * @param from     erster Zinstag
     * @param to       letzter Zinstag (einschließlich)
     * @param percent  Zinssatz in Prozent pro Jahr
     * @return Zinsen in Cent
     */
    public static long interestCents(NavigableMap<LocalDate, Long> deltas, LocalDate from, LocalDate to,
                                     BigDecimal percent) {
        if (percent == null || percent.signum() <= 0 || to.isBefore(from)) {
            return 0;
        }
        long balance = 0;
        for (Map.Entry<LocalDate, Long> e : deltas.headMap(from, false).entrySet()) {
            balance += e.getValue();
        }
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal rate = percent.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            balance += deltas.getOrDefault(d, 0L);
            if (balance > 0) {
                sum = sum.add(BigDecimal.valueOf(balance).multiply(rate)
                        .divide(BigDecimal.valueOf(d.lengthOfYear()), MathContext.DECIMAL64));
            }
        }
        return sum.setScale(0, RoundingMode.HALF_EVEN).longValueExact();
    }
}
