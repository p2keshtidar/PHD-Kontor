package de.frauas.girokonto.booking;

import java.time.Instant;

/**
 * Ein Umsatz aus Sicht eines Kontos (/F120/, /F140/).
 * amountCents: positiv = Eingang, negativ = Ausgang.
 */
public record BookingItemDto(
        Long bookingId,
        BookingType type,
        String description,
        Instant bookedAt,
        long amountCents,
        String counterpartyName,
        String counterpartyIban,
        String category,
        String categoryLabel) {
}
