package de.frauas.girokonto.loan;

import java.math.BigDecimal;

/**
 * Bonitätsklasse für die Beispielrechnung – eine Selbsteinschätzung der Nutzerin bzw. des Nutzers, keine Abfrage
 * bei einer Auskunftei. Der Zins steigt mit schwächerer Bonität (Aufschlag in Prozentpunkten).
 */
public enum CreditClass {
    SEHR_GUT("sehr gut", "-0.50", "-0.20"),
    GUT("gut", "0.00", "0.00"),
    MITTEL("mittel", "1.50", "0.60"),
    SCHWACH("schwach", "4.00", "1.50");

    private final String label;
    private final BigDecimal consumerAddOn;
    private final BigDecimal housingAddOn;

    CreditClass(String label, String consumerAddOn, String housingAddOn) {
        this.label = label;
        this.consumerAddOn = new BigDecimal(consumerAddOn);
        this.housingAddOn = new BigDecimal(housingAddOn);
    }

    public String label() {
        return label;
    }

    /** Zinsaufschlag (Prozentpunkte) für diesen Zweck. */
    public BigDecimal addOn(LoanPurpose purpose) {
        return purpose == LoanPurpose.IMMOBILIE ? housingAddOn : consumerAddOn;
    }

    /** Mindestens so gut wie {@code required}? (Reihenfolge der Konstanten: beste zuerst) */
    public boolean atLeast(CreditClass required) {
        return ordinal() <= required.ordinal();
    }

    public static CreditClass parse(String s) {
        if (s == null || s.isBlank()) {
            return GUT;
        }
        for (CreditClass c : values()) {
            if (c.name().equals(s.trim())) {
                return c;
            }
        }
        return null;
    }
}
