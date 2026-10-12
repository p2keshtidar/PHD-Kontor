package de.frauas.girokonto.savings;

/** Prognose und Ratenvorschlag (Pflichtenheft Kap. 8.3). Alle Beträge in Cent. */
public final class PlanMath {

    /** Mehr als 50 Jahre gilt als "nicht erreichbar". */
    public static final int MAX_MONTHS = 600;

    private PlanMath() {
    }

    /** i = (1 + r)^(1/12) − 1 */
    public static double monthlyRate(double annualReturn) {
        return Math.pow(1 + annualReturn, 1.0 / 12) - 1;
    }

    /** W(n) = W0 · (1+i)^n + R · ((1+i)^n − 1) / i */
    public static double valueAfter(long w0, long rate, double i, int n) {
        if (Math.abs(i) < 1e-12) {
            return w0 + (double) rate * n;
        }
        double f = Math.pow(1 + i, n);
        return w0 * f + rate * (f - 1) / i;
    }

    /** Kleinstes n mit W(n) ≥ Ziel, oder null, wenn nicht innerhalb von 50 Jahren erreichbar. */
    public static Integer monthsToTarget(long w0, long rate, double i, long target) {
        if (w0 >= target) {
            return 0;
        }
        for (int n = 1; n <= MAX_MONTHS; n++) {
            if (valueAfter(w0, rate, i, n) >= target - 0.5) {
                return n;
            }
        }
        return null;
    }

    /** Nötige Monatsrate, um in n Monaten das Ziel zu erreichen, aufgerundet auf volle Euro. */
    public static long requiredRate(long w0, long target, double i, int n) {
        if (n <= 0) {
            return Math.max(0, target - w0);
        }
        double rest;
        double rate;
        if (Math.abs(i) < 1e-12) {
            rest = target - w0;
            rate = rest / n;
        } else {
            double f = Math.pow(1 + i, n);
            rest = target - w0 * f;
            rate = rest * i / (f - 1);
        }
        if (rate <= 0) {
            return 0;
        }
        return (long) Math.ceil(rate / 100.0) * 100;
    }
}
