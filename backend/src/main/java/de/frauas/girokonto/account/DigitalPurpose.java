package de.frauas.girokonto.account;

/** Zweck eines digitalen Kontos – bestimmt Symbol und Bezeichnung. */
public enum DigitalPurpose {
    SPARZIEL("Sparziel", "savings"),
    URLAUB("Urlaub", "flight"),
    AUTO("Auto", "car"),
    IMMOBILIE("Immobilie", "home"),
    RUECKZAHLUNG("Rückzahlung", "payments"),
    WG("WG-Kasse", "groups"),
    NOTGROSCHEN("Notgroschen", "shield"),
    SONSTIGES("Sonstiges", "savings");

    private final String label;
    private final String icon;

    DigitalPurpose(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public String icon() {
        return icon;
    }

    /** Unbekannte oder alte Werte → SONSTIGES. */
    public static DigitalPurpose of(String s) {
        if (s == null) {
            return SONSTIGES;
        }
        for (DigitalPurpose p : values()) {
            if (p.name().equals(s)) {
                return p;
            }
        }
        return SONSTIGES;
    }
}
