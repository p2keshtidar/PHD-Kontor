package de.frauas.girokonto.loan;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fiktive Banken für den Angebotsvergleich (/F1030/, Kap. 8.5). Es sind keine echten Kreditinstitute; ihre
 * Konditionen sind unverbindliche Rechenbeispiele der Projektbank (Simulation).
 *
 * @param tolerance  höchster Anteil der Rate am frei verfügbaren Betrag, den die Bank akzeptiert
 * @param minClass   schwächste Bonitätsklasse, mit der die Bank noch anbietet
 */
public enum Bank {
    MAINUFER("Mainufer Bank", "-0.20", 0.60, CreditClass.GUT),
    NORDLICHT("Nordlicht Kredit", "0.00", 0.70, CreditClass.MITTEL),
    TAUNUS("Taunus Finanz", "0.35", 0.80, CreditClass.SCHWACH),
    PROJEKTBANK("Projektbank", "0.10", 0.70, CreditClass.MITTEL);

    private final String label;
    private final BigDecimal adjustment;
    private final double tolerance;
    private final CreditClass minClass;

    Bank(String label, String adjustment, double tolerance, CreditClass minClass) {
        this.label = label;
        this.adjustment = new BigDecimal(adjustment);
        this.tolerance = tolerance;
        this.minClass = minClass;
    }

    public String label() { return label; }
    public BigDecimal adjustment() { return adjustment; }
    public double tolerance() { return tolerance; }
    public CreditClass minClass() { return minClass; }

    /** Voraussetzungen, wie sie die (fiktive) Bank nennt. */
    public List<String> requirements() {
        return List.of("Bonität mindestens „" + minClass.label() + "“ (Selbsteinschätzung)",
                "Regelmäßige Einnahmen im Girokonto",
                "Rate höchstens " + Math.round(tolerance * 100) + " % des frei verfügbaren Betrags",
                "Volljährig, Wohnsitz in Deutschland");
    }
}
