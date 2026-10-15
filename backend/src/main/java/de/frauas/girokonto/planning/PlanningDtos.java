package de.frauas.girokonto.planning;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;

/** Budgets, ungewöhnliche Ausgaben und Prognose des Girokontos. */
public final class PlanningDtos {

    private PlanningDtos() {
    }

    public record BudgetRequest(@Min(value = 100, message = "Ein Budget muss mindestens 1,00 € betragen.")
                                @Max(value = 10_000_000, message = "Ein Budget darf höchstens 100.000 € betragen.")
                                long monthlyCents) {}

    /**
     * Ein Budget im laufenden Monat.
     *
     * @param status         OK (unter 80 %), WARN (ab 80 %), OVER (über 100 %)
     * @param projectedCents Ausgaben bis Monatsende, wenn es im selben Tempo weitergeht (null in den ersten Tagen)
     */
    public record BudgetItem(String category, String label, long limitCents, long spentCents, long remainingCents,
                             double percent, String status, Long projectedCents, String projectionText) {}

    /** Ausgaben in einer Kategorie ohne Budget (Anregung). */
    public record Unbudgeted(String category, String label, long spentCents) {}

    /**
     * Eine auffällige Ausgabe.
     *
     * @param kind SINGLE (einzelne Ausgabe deutlich über dem Üblichen) oder CATEGORY (Monat deutlich über dem Schnitt)
     */
    public record Unusual(String kind, String category, String label, LocalDate date, String text, long amountCents,
                          long usualCents, String reason) {}

    public record BudgetOverview(String month, int daysLeft, List<BudgetItem> items, long totalLimitCents,
                                 long totalSpentCents, List<Unbudgeted> unbudgeted, List<Unusual> unusual,
                                 List<String> notes) {}

    /** Ein Tag der Prognose: erwarteter, günstiger und ungünstiger Kontostand am Tagesende. */
    public record ForecastPoint(LocalDate date, long expectedCents, long bestCents, long worstCents) {}

    /**
     * Eine bekannte künftige Buchung.
     *
     * @param source ENTRY, STANDING_ORDER, PLAN, LOAN, ALLOCATION
     */
    public record ForecastEvent(LocalDate date, String label, long amountCents, String source) {}

    public record MonthEnd(String month, long expectedCents, long bestCents, long worstCents) {}

    /**
     * Prognose für das Girokonto.
     *
     * @param historyMonths Monate, aus denen die übrigen Umsätze (Gehalt, Karte, Lastschriften …) übernommen wurden
     */
    public record Forecast(LocalDate date, long startBalanceCents, int days, List<ForecastPoint> points,
                           List<ForecastEvent> events, List<MonthEnd> monthEnds, ForecastPoint lowest,
                           List<String> warnings, List<String> assumptions, int historyMonths) {}
}
