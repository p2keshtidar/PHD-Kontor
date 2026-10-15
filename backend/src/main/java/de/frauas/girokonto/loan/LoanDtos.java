package de.frauas.girokonto.loan;

import de.frauas.girokonto.loan.provider.MarketRateService.ReferenceRate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LoanDtos {

    private LoanDtos() {
    }

    /**
     * Finanzierungswunsch (/F1010/) mit Filtern für den Vergleich.
     *
     * @param maxMonthlyRateCents höchste Monatsbelastung
     * @param maxTotalCents       Budget: höchstens so viel insgesamt zurückzahlen (Kredit + Zinsen + Kosten)
     * @param creditClass         Bonität (Selbsteinschätzung): SEHR_GUT, GUT (Standard), MITTEL, SCHWACH
     * @param onlyEligible        nur Angebote, deren Voraussetzungen erfüllt sind
     */
    public record OfferRequest(
            @NotNull(message = "Zweck fehlt.") LoanPurpose purpose,
            @NotNull(message = "Kreditbetrag fehlt.") Long principalCents,
            @PositiveOrZero(message = "Eigenkapital darf nicht negativ sein.") Long equityCents,
            @NotNull(message = "Laufzeit fehlt.") Integer termMonths,
            @Positive Long maxMonthlyRateCents,
            @Positive Long maxTotalCents,
            String creditClass,
            Boolean onlyEligible) {}

    /**
     * Schritt 2: unverbindliche Kreditanfrage bei einer (fiktiven) Bank.
     *
     * @param consent Einwilligung, dass die Angaben für diese Anfrage verwendet werden
     */
    public record ApplicationRequest(
            String provider,
            @NotNull(message = "Bank fehlt.") Bank bank,
            @NotNull(message = "Zweck fehlt.") LoanPurpose purpose,
            @NotNull(message = "Kreditbetrag fehlt.") Long principalCents,
            @PositiveOrZero Long equityCents,
            @NotNull(message = "Laufzeit fehlt.") Integer termMonths,
            String creditClass,
            Boolean consent) {}

    /** Schritt 3: Vertrag abschließen (ausdrücklich bestätigen). */
    public record AcceptRequest(Boolean confirm) {}

    /**
     * Kreditanfrage und ihr Ergebnis.
     *
     * @param status APPROVED (Zusage, befristet), DECLINED, ACCEPTED (Vertrag geschlossen), WITHDRAWN, EXPIRED
     */
    public record ApplicationDto(Long id, String provider, boolean simulation, String bank, String bankName,
                                 LoanPurpose purpose, String purposeLabel, long principalCents, long equityCents,
                                 int termMonths, String creditClass, String creditClassLabel, BigDecimal interestRate,
                                 BigDecimal effectiveRate, Long monthlyRateCents, Long totalCents, String status,
                                 String statusLabel, String decisionReason, LocalDate validUntil, Long loanId,
                                 Instant createdAt, String nextStepText) {}

    /**
     * Anbieter im Vergleich und sein Zustand.
     *
     * @param kind  OFFERS (liefert Angebote), REFERENCE (nur Durchschnittszinsen), PARTNER (echte Angebote)
     * @param state OK, STALE, UNAVAILABLE, DISABLED, NOT_CONNECTED
     */
    public record ProviderDto(String id, String name, String kind, boolean simulation, String state,
                              String stateLabel, String message, Instant updatedAt) {}

    public record CreateRequest(
            @NotNull(message = "Bank fehlt.") Bank bank,
            @NotNull(message = "Zweck fehlt.") LoanPurpose purpose,
            @NotNull(message = "Kreditbetrag fehlt.") Long principalCents,
            @PositiveOrZero Long equityCents,
            @NotNull(message = "Laufzeit fehlt.") Integer termMonths) {}

    public record ScheduleRequest(
            @NotNull Long principalCents,
            @NotNull BigDecimal interestRate,
            @NotNull Integer termMonths) {}

    public record Affordability(boolean hasData, long availableCents, long maxRateCents) {}

    /**
     * Ein Angebot im Vergleich (bei fiktiven Banken: unverbindliches Rechenbeispiel).
     *
     * @param interestRate       gebundener Sollzins p. a.
     * @param effectiveRate      effektiver Jahreszins
     * @param marketDeltaPercent Abstand des effektiven Jahreszinses zum Bundesbank-Durchschnitt (falls vorhanden)
     */
    public record Offer(
            String bank,
            String bankName,
            BigDecimal interestRate,
            long monthlyRateCents,
            long totalInterestCents,
            long totalCostCents,
            boolean affordable,
            boolean declined,
            String declineReason,
            boolean recommended,
            String providerId,
            boolean simulation,
            BigDecimal effectiveRate,
            long feesCents,
            String minCreditClass,
            String minCreditClassLabel,
            boolean eligible,
            List<String> requirements,
            List<String> conditions,
            String exampleText,
            Double marketDeltaPercent) {

        Offer recommend() {
            return new Offer(bank, bankName, interestRate, monthlyRateCents, totalInterestCents, totalCostCents,
                    affordable, declined, declineReason, true, providerId, simulation, effectiveRate, feesCents,
                    minCreditClass, minCreditClassLabel, eligible, requirements, conditions, exampleText,
                    marketDeltaPercent);
        }
    }

    public record OfferResult(
            LoanPurpose purpose,
            String purposeLabel,
            long principalCents,
            long equityCents,
            int termMonths,
            boolean hasData,
            long availableCents,
            long maxRateCents,
            long maxPrincipalCents,
            List<Offer> offers,
            String creditClass,
            String creditClassLabel,
            int hiddenCount,
            List<ReferenceRate> referenceRates,
            List<ProviderDto> providers,
            String disclaimerText) {}

    public record ScheduleRow(int month, LocalDate dueDate, long rateCents, long interestCents,
                              long principalCents, long remainingCents, boolean paid, long extraCents) {}

    /** @param status ACTIVE (ausgezahlt, Raten laufen), CONTRACTED (Vertrag, noch nicht ausgezahlt), PAID_OFF, CANCELLED */
    public record LoanDto(
            Long id,
            Bank bank,
            String bankName,
            LoanPurpose purpose,
            String purposeLabel,
            long principalCents,
            long equityCents,
            BigDecimal interestRate,
            int termMonths,
            long monthlyRateCents,
            LocalDate startDate,
            LocalDate nextDueDate,
            int paidInstallments,
            long remainingCents,
            double progressPercent,
            Loan.Status status,
            Long accountId,
            int remainingInstallments,
            LocalDate expectedEndDate,
            long extraPaidCents,
            String statusLabel,
            BigDecimal effectiveRate,
            boolean simulation,
            Long applicationId,
            Instant contractedAt,
            Instant paidOutAt) {}

    public record LoanDetail(LoanDto loan, long totalInterestCents, long interestSavedCents, List<ScheduleRow> schedule,
                             List<ExtraPaymentDto> extraPayments) {}

    /** /K020/ */
    public record ExtraPaymentRequest(
            @NotNull(message = "Betrag fehlt.") Long amountCents) {}

    public record ExtraPaymentDto(Long id, long amountCents, LocalDate paidOn, int afterInstallment) {}

    /** Vorschau einer Sondertilgung: was ändert sich? */
    public record ExtraPreview(
            long amountCents,
            long remainingBeforeCents,
            long remainingAfterCents,
            int installmentsBefore,
            int installmentsAfter,
            LocalDate endBefore,
            LocalDate endAfter,
            long interestSavedCents,
            long availableCents) {}
}
