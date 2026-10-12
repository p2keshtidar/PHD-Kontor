package de.frauas.girokonto.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Überweisung (/F210/). Beträge immer in Cent, damit keine Rundungsfehler entstehen.
 *
 * @param recipient   IBAN, E-Mail oder Telefonnummer des Empfängers
 * @param description Verwendungszweck (optional, max. 140 Zeichen)
 */
public record TransferRequest(
        @NotBlank(message = "Empfänger fehlt.") String recipient,
        @Positive(message = "Der Betrag muss größer als 0 sein.") long amountCents,
        @Size(max = 140, message = "Der Verwendungszweck darf höchstens 140 Zeichen haben.") String description) {
}
