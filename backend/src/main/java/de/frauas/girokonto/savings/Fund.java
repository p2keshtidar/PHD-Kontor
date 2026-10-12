package de.frauas.girokonto.savings;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Die zwei fiktiven Fonds des Musterdepots (/F820/, Pflichtenheft Kap. 8.2).
 *
 * Preis(k) = Preis(k−1) · (1 + μ + σ · z(k)) mit μ = (1 + r)^(1/12) − 1 und z(k) gleichverteilt in [−√3; √3].
 * z(k) entsteht aus einem festen Startwert (Fonds und Monatsnummer): reproduzierbar, aber "börsenähnlich".
 */
public enum Fund {
    WELT_AKTIEN("Welt-Aktien", 0.06, 0.04),
    EURO_ANLEIHEN("Euro-Anleihen", 0.02, 0.008);

    public static final YearMonth BASE_MONTH = YearMonth.of(2026, 1);
    public static final double START_PRICE = 100.0;
    private static final double SQRT3 = Math.sqrt(3);

    private final String label;
    private final double annualReturn;
    private final double monthlyVolatility;
    private final List<Double> prices = new ArrayList<>(List.of(START_PRICE));

    Fund(String label, double annualReturn, double monthlyVolatility) {
        this.label = label;
        this.annualReturn = annualReturn;
        this.monthlyVolatility = monthlyVolatility;
    }

    public String label() { return label; }
    public double annualReturn() { return annualReturn; }

    /** Preis in Euro im Monat [ym]; vor Januar 2026 gilt der Startpreis. */
    public synchronized double price(YearMonth ym) {
        long k = ChronoUnit.MONTHS.between(BASE_MONTH, ym);
        if (k <= 0) {
            return START_PRICE;
        }
        double mu = Math.pow(1 + annualReturn, 1.0 / 12) - 1;
        while (prices.size() <= k) {
            int month = prices.size();
            double z = (new SplittableRandom(ordinal() * 1_000_003L + month * 7_919L).nextDouble() * 2 - 1) * SQRT3;
            double previous = prices.get(month - 1);
            prices.add(previous * (1 + mu + monthlyVolatility * z));
        }
        return prices.get((int) k);
    }
}
