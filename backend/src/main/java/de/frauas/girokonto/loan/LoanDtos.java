package de.frauas.girokonto.loan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LoanDtos {

    private LoanDtos() {
    }

    /** Finanzierungswunsch (/F1010/) */
    public record OfferRequest(
            @NotNull(message = "Zweck fehlt.") LoanPurpose purpose,
            @NotNull(message = "Kreditbetrag fehlt.") Long principalCents,
            @PositiveOrZero(message = "Eigenkapital darf nicht negativ sein.") Long equityCents,
            @NotNull(message = "Laufzeit fehlt.") Integer termMonths) {}

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

    public record Offer(
            Bank bank,
            String bankName,
            BigDecimal interestRate,
            long monthlyRateCents,
            long totalInterestCents,
            long totalCostCents,
            boolean affordable,
            boolean declined,
            String declineReason,
            boolean recommended) {}

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
            List<Offer> offers) {}

    public record ScheduleRow(int month, LocalDate dueDate, long rateCents, long interestCents,
                              long principalCents, long remainingCents, boolean paid, long extraCents) {}

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
            long extraPaidCents) {}

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
