package de.frauas.girokonto.transfer;

/** Zusammenfassung vor dem Absenden (/F240/): Name des Empfängers zur Kontrolle. */
public record TransferCheckDto(
        String recipientName,
        String recipientIban,
        long amountCents,
        String description,
        long availableCents,
        long remainingDailyLimitCents) {
}
