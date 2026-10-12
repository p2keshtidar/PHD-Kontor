package de.frauas.girokonto.savings;

/**
 * Risikostufen (/F710/, Pflichtenheft Kap. 8.2).
 *
 * @param goalDepotShare Anteil der Sparrate, der beim Sparplan ins Depot geht (Investplan: immer 100 %)
 * @param equityWeight   Anteil Aktienfonds im Depot, Rest Anleihenfonds
 */
public enum RiskLevel {
    VORSICHTIG("Vorsichtig", 0.0, 0.2,
            "Sicherheit zuerst: Beim Sparplan bleibt alles auf dem Sparkonto, beim Investplan überwiegend Anleihen."),
    AUSGEWOGEN("Ausgewogen", 0.5, 0.5,
            "Halb Aktien, halb Anleihen: mittlere Schwankung, mittlere Chance."),
    MUTIG("Mutig", 0.8, 0.8,
            "Überwiegend Aktienfonds: die größte Chance, aber der Wert kann deutlich schwanken.");

    private final String label;
    private final double goalDepotShare;
    private final double equityWeight;
    private final String explanation;

    RiskLevel(String label, double goalDepotShare, double equityWeight, String explanation) {
        this.label = label;
        this.goalDepotShare = goalDepotShare;
        this.equityWeight = equityWeight;
        this.explanation = explanation;
    }

    public String label() { return label; }
    public double equityWeight() { return equityWeight; }
    public String explanation() { return explanation; }

    public double depotShare(PlanType type) {
        return type == PlanType.INVEST ? 1.0 : goalDepotShare;
    }

    /** Erwartete Rendite des Depots p. a. (Mischung aus beiden Fonds). */
    public double depotReturn() {
        return equityWeight * Fund.WELT_AKTIEN.annualReturn() + (1 - equityWeight) * Fund.EURO_ANLEIHEN.annualReturn();
    }

    /** Erwartete Rendite des ganzen Plans p. a. */
    public double expectedReturn(PlanType type) {
        return depotShare(type) * depotReturn();
    }
}
