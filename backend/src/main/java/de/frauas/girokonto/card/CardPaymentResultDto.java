package de.frauas.girokonto.card;

/**
 * Ergebnis einer simulierten Kartenzahlung. Abgelehnte Zahlungen sind kein Fehler,
 * sondern ein normales Ergebnis mit approved = false und einem Grund.
 */
public record CardPaymentResultDto(boolean approved, String reasonCode, String message,
                                   Long bookingId, long amountCents, String merchant) {
}
