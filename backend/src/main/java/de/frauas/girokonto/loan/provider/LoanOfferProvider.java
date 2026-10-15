package de.frauas.girokonto.loan.provider;

import de.frauas.girokonto.loan.CreditClass;
import de.frauas.girokonto.loan.LoanPurpose;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Anbieter von Kreditangeboten für den Vergleich.
 *
 * <p>Ein echter Anbieter (Bank oder Kreditvermittler mit Partner-Schnittstelle) darf nur mit Vertrag, Erlaubnis und
 * Zugangsdaten angebunden werden. Bis dahin gibt es nur {@link SimulatedBanksProvider}: fiktive Banken mit
 * unverbindlichen Rechenbeispielen – als Simulation gekennzeichnet. Angebote werden nie erfunden und als echt
 * ausgegeben.
 */
public interface LoanOfferProvider {

    /** Kennung, z. B. SIMULATION */
    String id();

    String displayName();

    /** true = fiktive Banken (Simulation), false = echte Angebote eines angebundenen Anbieters */
    boolean simulation();

    /** Darf der Anbieter Kreditanfragen annehmen (Schritt 2)? */
    boolean supportsApplications();

    Status status();

    /** Unverbindliche Beispielrechnungen für den Wunsch (ohne Tragfähigkeitsprüfung, die macht der Aufrufer). */
    List<Quote> quotes(Query query);

    /** Kreditwunsch für den Vergleich. */
    record Query(LoanPurpose purpose, long principalCents, long equityCents, int termMonths, CreditClass creditClass) {}

    /**
     * Ein Rechenbeispiel.
     *
     * @param interestRate   gebundener Sollzins p. a. in Prozent
     * @param effectiveRate  effektiver Jahreszins in Prozent (mit allen Kosten)
     * @param feesCents      Kosten neben den Zinsen (bei Verbraucherkrediten keine Bearbeitungsgebühr)
     * @param eligible       Bonitätsvoraussetzung erfüllt
     * @param maxIncomeShare höchster Anteil der Rate am frei verfügbaren Betrag, den der Anbieter akzeptiert
     */
    record Quote(String providerId, String bankCode, String bankName, BigDecimal interestRate, BigDecimal effectiveRate,
                 long monthlyRateCents, long totalInterestCents, long feesCents, long totalCents,
                 CreditClass minClass, boolean eligible, String ineligibleReason, double maxIncomeShare,
                 List<String> requirements, List<String> conditions, String exampleText) {}

    /**
     * Zustand eines Anbieters für die Anzeige.
     *
     * @param state OK, STALE (Daten veraltet), UNAVAILABLE (gestört), DISABLED (abgeschaltet), NOT_CONNECTED
     */
    record Status(String state, String message, Instant updatedAt) {}
}
