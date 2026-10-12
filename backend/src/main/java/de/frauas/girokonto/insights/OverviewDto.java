package de.frauas.girokonto.insights;

/**
 * Durchschnitt der letzten drei vollen Monate und daraus abgeleitete Werte (/F580/, Kap. 8.4).
 *
 * @param availableCents "verfügbar für Pläne" = freier Betrag − Raten laufender Pläne
 * @param maxLoanRateCents tragbare Kreditrate = 70 % von verfügbar (/F1020/)
 */
public record OverviewDto(
        boolean hasData,
        int monthsUsed,
        long avgIncomeCents,
        long avgExpenseCents,
        long avgFixedCents,
        long avgFreeCents,
        long planRatesCents,
        long availableCents,
        long maxLoanRateCents,
        String demoProfile) {
}
