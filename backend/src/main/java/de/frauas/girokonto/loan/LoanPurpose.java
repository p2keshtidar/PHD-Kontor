package de.frauas.girokonto.loan;

import java.math.BigDecimal;

/**
 * Zwecke eines Kredits mit Grenzen und Zinsregeln (/F1010/, Pflichtenheft Kap. 8.5).
 * Alle Zinsangaben in Prozent p. a.
 */
public enum LoanPurpose {
    AUTO("Auto", 100_000, 10_000_000, 12, 96, "5.49", 12, 48, "0.10", "0.15"),
    IMMOBILIE("Wohnung/Haus", 5_000_000, 100_000_000, 60, 360, "3.79", 60, 120, "0.15", "0.30"),
    SONSTIGES("Sonstiges", 100_000, 5_000_000, 12, 120, "7.49", 12, 48, "0.10", "0.15");

    private final String label;
    private final long minCents;
    private final long maxCents;
    private final int minTerm;
    private final int maxTerm;
    private final BigDecimal baseRate;
    private final int termStepMonths;
    private final int termFreeMonths;
    private final BigDecimal termStepAdd;
    private final BigDecimal equityDiscount;

    LoanPurpose(String label, long minCents, long maxCents, int minTerm, int maxTerm, String baseRate,
                int termStepMonths, int termFreeMonths, String termStepAdd, String equityDiscount) {
        this.label = label;
        this.minCents = minCents;
        this.maxCents = maxCents;
        this.minTerm = minTerm;
        this.maxTerm = maxTerm;
        this.baseRate = new BigDecimal(baseRate);
        this.termStepMonths = termStepMonths;
        this.termFreeMonths = termFreeMonths;
        this.termStepAdd = new BigDecimal(termStepAdd);
        this.equityDiscount = new BigDecimal(equityDiscount);
    }

    public String label() { return label; }
    public long minCents() { return minCents; }
    public long maxCents() { return maxCents; }
    public int minTerm() { return minTerm; }
    public int maxTerm() { return maxTerm; }

    /**
     * Zinssatz ohne Bankaufschlag: Basiszins + Laufzeitaufschlag − Eigenkapitalrabatt (ab 20 % Eigenkapitalquote).
     */
    public BigDecimal rateFor(int termMonths, long principalCents, long equityCents) {
        int steps = Math.max(0, termMonths - termFreeMonths) / termStepMonths;
        BigDecimal rate = baseRate.add(termStepAdd.multiply(BigDecimal.valueOf(steps)));
        double equityRatio = equityCents <= 0 ? 0 : (double) equityCents / (principalCents + equityCents);
        if (equityRatio >= 0.20) {
            rate = rate.subtract(equityDiscount);
        }
        return rate;
    }
}
