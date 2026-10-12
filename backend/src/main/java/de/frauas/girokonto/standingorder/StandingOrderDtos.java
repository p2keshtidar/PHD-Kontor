package de.frauas.girokonto.standingorder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class StandingOrderDtos {

    private StandingOrderDtos() {
    }

    /**
     * @param recipient      IBAN, E-Mail oder Telefonnummer des Empfängers
     * @param intervalMonths 1 = monatlich, 3 = vierteljährlich, 6 = halbjährlich, 12 = jährlich
     */
    public record CreateRequest(
            @NotBlank(message = "Empfänger fehlt.") String recipient,
            @NotNull(message = "Betrag fehlt.") @Positive(message = "Der Betrag muss größer als 0 sein.") Long amountCents,
            @Size(max = 140, message = "Der Verwendungszweck darf höchstens 140 Zeichen haben.") String reference,
            @NotNull(message = "Ausführungstag fehlt.")
            @Min(value = 1, message = "Ausführungstag 1 bis 28.") @Max(value = 28, message = "Ausführungstag 1 bis 28.")
            Integer executionDay,
            Integer intervalMonths,
            LocalDate endDate) {}

    public record UpdateRequest(
            @Positive(message = "Der Betrag muss größer als 0 sein.") Long amountCents,
            @Size(max = 140, message = "Der Verwendungszweck darf höchstens 140 Zeichen haben.") String reference,
            @Min(value = 1, message = "Ausführungstag 1 bis 28.") @Max(value = 28, message = "Ausführungstag 1 bis 28.")
            Integer executionDay,
            Boolean paused,
            LocalDate endDate,
            Boolean removeEndDate) {}

    public record StandingOrderDto(
            Long id,
            String recipientName,
            String recipientIban,
            long amountCents,
            String reference,
            int executionDay,
            int intervalMonths,
            String intervalLabel,
            LocalDate startDate,
            LocalDate endDate,
            StandingOrder.Status status,
            LocalDate nextExecutionDate,
            String lastExecutedMonth) {}
}
