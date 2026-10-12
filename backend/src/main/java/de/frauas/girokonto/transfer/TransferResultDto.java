package de.frauas.girokonto.transfer;

import java.time.Instant;

public record TransferResultDto(
        Long bookingId,
        String recipientName,
        String recipientIban,
        Long recipientUserId,
        long amountCents,
        String description,
        Instant bookedAt,
        long newBalanceCents) {
}
