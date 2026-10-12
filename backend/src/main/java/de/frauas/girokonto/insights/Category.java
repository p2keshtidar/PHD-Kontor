package de.frauas.girokonto.insights;

/** Kategorien für den Finanzüberblick (/F560/, Pflichtenheft Kap. 8.4). */
public enum Category {
    GEHALT("Gehalt", Kind.INCOME),
    SONSTIGE_EINNAHMEN("Sonstige Einnahmen", Kind.INCOME),
    WOHNEN("Wohnen", Kind.FIXED),
    VERTRAEGE("Verträge & Abos", Kind.FIXED),
    KREDIT("Kredit", Kind.FIXED),
    LEBENSMITTEL("Lebensmittel", Kind.VARIABLE),
    MOBILITAET("Mobilität", Kind.VARIABLE),
    FREIZEIT("Freizeit", Kind.VARIABLE),
    SHOPPING("Shopping", Kind.VARIABLE),
    SONSTIGES("Sonstiges", Kind.VARIABLE),
    // zählen weder als Einnahme noch als Ausgabe
    STARTGUTHABEN("Startguthaben", Kind.EXCLUDED),
    UMBUCHUNG("Umbuchung", Kind.EXCLUDED),
    SPAREN("Sparen & Investieren", Kind.EXCLUDED),
    KREDIT_AUSZAHLUNG("Kreditauszahlung", Kind.EXCLUDED);

    public enum Kind { INCOME, FIXED, VARIABLE, EXCLUDED }

    private final String label;
    private final Kind kind;

    Category(String label, Kind kind) {
        this.label = label;
        this.kind = kind;
    }

    public String label() { return label; }
    public Kind kind() { return kind; }
    public boolean isIncome() { return kind == Kind.INCOME; }
    public boolean isExpense() { return kind == Kind.FIXED || kind == Kind.VARIABLE; }
    public boolean isFixed() { return kind == Kind.FIXED; }

    /** Kategorien, die der Nutzer selbst wählen darf. */
    public boolean isUserSelectable() { return kind != Kind.EXCLUDED; }
}
