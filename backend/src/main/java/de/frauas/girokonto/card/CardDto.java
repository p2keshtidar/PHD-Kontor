package de.frauas.girokonto.card;

/** Kartenansicht ohne volle Nummer (/F310/). */
public record CardDto(
        Long id,
        String holderName,
        String maskedNumber,
        String last4,
        String expiry,
        CardStatus status,
        long dailyLimitCents,
        long maxDailyLimitCents,
        boolean onlineEnabled,
        long spentTodayCents) {
}
