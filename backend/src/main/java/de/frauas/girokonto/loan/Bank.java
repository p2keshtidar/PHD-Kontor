package de.frauas.girokonto.loan;

import java.math.BigDecimal;

/**
 * Fiktive Banken für den Angebotsvergleich (/F1030/, Kap. 8.5).
 *
 * @param tolerance höchster Anteil der Rate am verfügbaren Betrag, den die Bank akzeptiert
 */
public enum Bank {
    MAINUFER("Mainufer Bank", "-0.20", 0.60),
    NORDLICHT("Nordlicht Kredit", "0.00", 0.70),
    TAUNUS("Taunus Finanz", "0.35", 0.80),
    PROJEKTBANK("Projektbank", "0.10", 0.70);

    private final String label;
    private final BigDecimal adjustment;
    private final double tolerance;

    Bank(String label, String adjustment, double tolerance) {
        this.label = label;
        this.adjustment = new BigDecimal(adjustment);
        this.tolerance = tolerance;
    }

    public String label() { return label; }
    public BigDecimal adjustment() { return adjustment; }
    public double tolerance() { return tolerance; }
}
