package de.frauas.girokonto.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Zentrales Finanz-Dashboard: alles auf einen Blick, nur aus vorhandenen Daten berechnet. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * Gesamtvermögen nach Bestandteilen (Cent).
     *
     * @param accountsCents Girokonto und weitere Konten der Projektbank (ohne digitale Konten und Sparpläne)
     * @param digitalCents  eigene digitale Konten (Töpfe)
     * @param plansCents    Sparkonten der Spar-/Investpläne (ohne Depot)
     * @param depotCents    Musterdepot zum aktuellen Kurs
     * @param externalCents verbundene Banken (letzter abgerufener Kontostand, nur Euro)
     * @param loansCents    Restschulden (negativ)
     */
    public record NetWorth(long accountsCents, long digitalCents, long plansCents, long depotCents, long externalCents,
                           long loansCents, long totalCents) {}

    /**
     * Was frei verfügbar ist und was für Ziele zurückgelegt wurde.
     *
     * @param availableCents  Girokonto-Guthaben der Projektbank (frei verfügbar)
     * @param reservedCents   in digitalen Konten und Sparplänen zurückgelegt
     * @param earmarkedCents  bei verbundenen Banken für Ziele vorgemerkt (bleibt dort)
     */
    public record Liquidity(long availableCents, long reservedCents, long earmarkedCents, long externalCents) {}

    /**
     * Ein Konto in der Übersicht.
     *
     * @param kind PROJEKTBANK, DIGITAL, PLAN, EXTERNAL, LOAN
     */
    public record AccountLine(String kind, Long id, String name, String institution, long balanceCents,
                              boolean simulation, boolean stale, String note) {}

    public record CardLine(Long id, String label, String maskedNumber, String status, long dailyLimitCents,
                           long spentTodayCents, boolean simulation) {}

    public record LoanLine(Long id, String name, String status, long remainingCents, long monthlyRateCents,
                           LocalDate nextDueDate) {}

    /** @param debtRatioPercent Kreditraten im Verhältnis zu den durchschnittlichen Einnahmen (null ohne Einnahmen) */
    public record Debts(long totalCents, long monthlyRatesCents, Double debtRatioPercent, List<LoanLine> loans) {}

    public record CashflowMonth(String month, boolean complete, long incomeCents, long expenseCents, long netCents) {}

    public record Cashflow(long incomeCents, long expenseCents, long netCents, List<CashflowMonth> months) {}

    /** @param kind DIGITAL oder PLAN */
    public record Goal(String kind, Long id, String name, long savedCents, Long targetCents, Double progressPercent,
                       LocalDate targetDate, boolean reached) {}

    public record CategoryShare(String category, String label, long cents, double percent) {}

    /**
     * Vermögen am Monatsende.
     *
     * @param externalIncluded false, solange für verbundene Banken noch keine Umsätze vorliegen (Wert unbekannt)
     */
    public record HistoryPoint(String month, long totalCents, long projektbankCents, long depotCents,
                               Long externalCents, boolean externalIncluded) {}

    public record Dashboard(LocalDate date, NetWorth netWorth, Liquidity liquidity, List<AccountLine> accounts,
                            List<CardLine> cards, Debts debts, Cashflow cashflow, List<Goal> goals,
                            List<CategoryShare> topExpenses, String topExpensesMonth, List<HistoryPoint> history,
                            List<String> notes, Instant generatedAt) {}
}
