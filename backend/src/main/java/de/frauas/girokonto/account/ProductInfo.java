package de.frauas.girokonto.account;

import java.math.BigDecimal;

/**
 * Art eines Bankkontos der Projektbank (alles Simulation mit Spielgeld).
 *
 * @param product           GIRO (Haupt-Girokonto), CURRENT (weiteres Girokonto), CALL (Tagesgeld)
 * @param interestPercent   nur Tagesgeld: fiktiver Zinssatz der Simulation in Prozent pro Jahr
 * @param receivesTransfers Überweisungen anderer Nutzer per IBAN kommen an
 * @param sendsTransfers    Überweisungen an andere Nutzer möglich (nur vom Haupt-Girokonto)
 * @param closable          kann aufgelöst werden (Restguthaben geht aufs Girokonto)
 */
public record ProductInfo(String product, String productLabel, BigDecimal interestPercent, boolean receivesTransfers,
                          boolean sendsTransfers, boolean closable, String note) {

    public static final String CALL_NOTE = "Fiktiver Zinssatz der Simulation, Zinsen am Monatsende. "
            + "Ein- und Auszahlungen nur über deine eigenen Konten.";
    public static final String CURRENT_NOTE = "Eigene IBAN für Eingänge. Überweisungen an andere nur vom Girokonto.";

    /** null für Konten, die keine Bankkonten sind (digitale Konten, Sparpläne, Kredite). */
    public static ProductInfo of(Account a) {
        return switch (a.getType()) {
            case MAIN -> new ProductInfo("GIRO", "Girokonto", null, true, true, false, null);
            case CURRENT -> new ProductInfo("CURRENT", "Weiteres Girokonto", null, true, false, true, CURRENT_NOTE);
            case CALL -> new ProductInfo("CALL", "Tagesgeld", a.getInterestRate(), false, false, true, CALL_NOTE);
            default -> null;
        };
    }
}
