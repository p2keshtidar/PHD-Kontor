package de.frauas.girokonto.dashboard;

import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.account.EarmarkRepository;
import de.frauas.girokonto.banking.BankingDtos;
import de.frauas.girokonto.banking.BankingService;
import de.frauas.girokonto.card.CardDto;
import de.frauas.girokonto.card.CardService;
import de.frauas.girokonto.common.Dates;
import de.frauas.girokonto.dashboard.DashboardDtos.AccountLine;
import de.frauas.girokonto.dashboard.DashboardDtos.CardLine;
import de.frauas.girokonto.dashboard.DashboardDtos.Cashflow;
import de.frauas.girokonto.dashboard.DashboardDtos.CashflowMonth;
import de.frauas.girokonto.dashboard.DashboardDtos.CategoryShare;
import de.frauas.girokonto.dashboard.DashboardDtos.Dashboard;
import de.frauas.girokonto.dashboard.DashboardDtos.Debts;
import de.frauas.girokonto.dashboard.DashboardDtos.Goal;
import de.frauas.girokonto.dashboard.DashboardDtos.HistoryPoint;
import de.frauas.girokonto.dashboard.DashboardDtos.Liquidity;
import de.frauas.girokonto.dashboard.DashboardDtos.LoanLine;
import de.frauas.girokonto.dashboard.DashboardDtos.NetWorth;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.InsightsService;
import de.frauas.girokonto.insights.MonthSummaryDto;
import de.frauas.girokonto.insights.OverviewDto;
import de.frauas.girokonto.loan.Loan;
import de.frauas.girokonto.loan.LoanDtos.LoanDto;
import de.frauas.girokonto.loan.LoanService;
import de.frauas.girokonto.savings.DepotMath;
import de.frauas.girokonto.savings.Fund;
import de.frauas.girokonto.savings.PlanDtos.Summary;
import de.frauas.girokonto.savings.PlanStatus;
import de.frauas.girokonto.savings.PlanType;
import de.frauas.girokonto.savings.RiskLevel;
import de.frauas.girokonto.savings.SavingsPlanService;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Zentrales Finanz-Dashboard. Alle Zahlen stammen aus den vorhandenen Daten (Hauptbuch der Projektbank, Pläne,
 * Kredite, verbundene Banken) – es wird nichts geschätzt. Wo ein Wert unbekannt ist (z. B. Kontostand einer verbundenen
 * Bank vor dem ersten importierten Umsatz), wird das gekennzeichnet statt aufgefüllt.
 */
@Service
public class DashboardService {

    static final int HISTORY_MONTHS = 12;
    /** Kontostand einer verbundenen Bank gilt danach als nicht mehr aktuell. */
    static final java.time.Duration STALE_AFTER = java.time.Duration.ofDays(2);
    static final String PROJEKTBANK = "Projektbank (Simulation)";

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final EarmarkRepository earmarks;
    private final BankingService banking;
    private final LoanService loans;
    private final SavingsPlanService plans;
    private final InsightsService insights;
    private final CardService cards;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, AccountService accounts, EarmarkRepository earmarks,
                            BankingService banking, LoanService loans, SavingsPlanService plans,
                            InsightsService insights, CardService cards, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.earmarks = earmarks;
        this.banking = banking;
        this.loans = loans;
        this.plans = plans;
        this.insights = insights;
        this.cards = cards;
        this.clock = clock;
    }

    public Dashboard dashboard(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<AccountDto> own = accounts.listForUser(userId);
        BankingDtos.AccountsOverview ext = banking.accounts(userId);
        List<LoanDto> loanList = loans.list(userId);
        List<Summary> planList = plans.list(userId);
        long depot = plans.totalDepotValue(userId);

        long main = sum(own, AccountType.MAIN);
        long digital = sum(own, AccountType.SUB);
        long planCash = sum(own, AccountType.SAVINGS);
        long loanBalance = sum(own, AccountType.LOAN);   // negativ
        long external = ext.totalBalanceCents();
        long earmarked = earmarks.forAccounts(own.stream().filter(a -> a.type() == AccountType.SUB)
                .map(AccountDto::id).toList()).values().stream().mapToLong(Long::longValue).sum();
        NetWorth netWorth = new NetWorth(main, digital, planCash, depot, external, loanBalance,
                main + digital + planCash + depot + external + loanBalance);
        Liquidity liquidity = new Liquidity(main, digital + planCash + depot, earmarked, external);

        List<AccountLine> lines = new ArrayList<>();
        for (AccountDto a : own) {
            if (a.type() == AccountType.MAIN) {
                lines.add(new AccountLine("PROJEKTBANK", a.id(), a.name(), PROJEKTBANK, a.balanceCents(), true, false, null));
            } else if (a.type() == AccountType.SUB) {
                lines.add(new AccountLine("DIGITAL", a.id(), a.name(), PROJEKTBANK, a.balanceCents(), true, false, null));
            }
        }
        for (Summary p : planList) {
            if (p.status() == PlanStatus.ACTIVE || p.status() == PlanStatus.PAUSED) {
                lines.add(new AccountLine("PLAN", p.id(), p.name(), PROJEKTBANK, p.currentValueCents(), true, false, null));
            }
        }
        for (BankingDtos.AccountDto a : ext.accounts()) {
            boolean stale = !"ACTIVE".equals(a.connectionStatus()) || !"OK".equals(a.lastSyncStatus())
                    || a.lastSyncAt() == null || a.lastSyncAt().isBefore(Instant.now(clock).minus(STALE_AFTER));
            lines.add(new AccountLine("EXTERNAL", a.id(), a.name(), a.bankName(),
                    a.balanceCents() == null ? 0 : a.balanceCents(), a.sandbox(), stale,
                    a.lastSyncAt() == null ? null : "Stand " + Dates.de(LocalDate.ofInstant(a.lastSyncAt(), clock.getZone()))));
        }
        List<LoanLine> loanLines = new ArrayList<>();
        long rates = 0;
        long debt = 0;
        for (LoanDto l : loanList) {
            if (l.status() == Loan.Status.ACTIVE) {
                lines.add(new AccountLine("LOAN", l.id(), "Kredit " + l.purposeLabel() + " (" + l.bankName() + ")",
                        PROJEKTBANK, -l.remainingCents(), true, false, null));
                rates += l.monthlyRateCents();
                debt += l.remainingCents();
            }
            if (l.status() == Loan.Status.ACTIVE || l.status() == Loan.Status.CONTRACTED) {
                loanLines.add(new LoanLine(l.id(), l.purposeLabel() + " · " + l.bankName(), l.status().name(),
                        l.remainingCents(), l.monthlyRateCents(), l.nextDueDate()));
            }
        }
        OverviewDto ov = plans.overview(userId);
        Double ratio = ov.hasData() && ov.avgIncomeCents() > 0
                ? Math.round(rates * 1000.0 / ov.avgIncomeCents()) / 10.0 : null;
        Debts debts = new Debts(debt, rates, ratio, loanLines);

        List<CardLine> cardLines = new ArrayList<>();
        CardDto card = cards.view(userId);
        if (card != null) {
            cardLines.add(new CardLine(card.id(), "Debitkarte (Simulation)", card.maskedNumber(), card.status().name(),
                    card.dailyLimitCents(), card.spentTodayCents(), true));
        }

        List<MonthSummaryDto> months = insights.months(userId, 6);
        List<CashflowMonth> cm = months.stream().map(m -> new CashflowMonth(m.month(), m.complete(), m.incomeCents(),
                m.expenseCents(), m.incomeCents() - m.expenseCents())).toList();
        MonthSummaryDto current = months.isEmpty() ? null : months.get(months.size() - 1);
        Cashflow cashflow = current == null ? new Cashflow(0, 0, 0, cm)
                : new Cashflow(current.incomeCents(), current.expenseCents(),
                        current.incomeCents() - current.expenseCents(), cm);

        // größte Ausgaben: laufender Monat, sonst der letzte Monat mit Ausgaben
        MonthSummaryDto expMonth = null;
        for (int i = months.size() - 1; i >= 0 && expMonth == null; i--) {
            if (months.get(i).expenseCents() > 0) {
                expMonth = months.get(i);
            }
        }
        List<CategoryShare> top = new ArrayList<>();
        if (expMonth != null) {
            long total = expMonth.expenseCents();
            expMonth.categories().stream().filter(c -> c.category().isExpense() && c.cents() > 0)
                    .sorted(Comparator.comparingLong(MonthSummaryDto.CategoryAmount::cents).reversed()).limit(5)
                    .forEach(c -> top.add(new CategoryShare(c.category().name(), c.label(), c.cents(),
                            total == 0 ? 0 : Math.round(c.cents() * 1000.0 / total) / 10.0)));
        }

        List<Goal> goals = new ArrayList<>();
        for (AccountDto a : own) {
            if (a.type() == AccountType.SUB && a.digital() != null && a.digital().targetCents() != null) {
                goals.add(new Goal("DIGITAL", a.id(), a.name(), a.digital().savedCents(), a.digital().targetCents(),
                        a.digital().progressPercent(), a.digital().targetDate(), a.digital().goalReached()));
            }
        }
        for (Summary p : planList) {
            if (p.type() == PlanType.GOAL && p.targetCents() != null
                    && (p.status() == PlanStatus.ACTIVE || p.status() == PlanStatus.PAUSED)) {
                goals.add(new Goal("PLAN", p.id(), p.name(), p.currentValueCents(), p.targetCents(), p.progressPercent(),
                        p.targetDate(), p.currentValueCents() >= p.targetCents()));
            }
        }

        List<HistoryPoint> history = history(userId, YearMonth.from(today), ext);
        List<String> notes = new ArrayList<>();
        notes.add("Girokonto, digitale Konten, Pläne, Depot und Kredite der Projektbank sind eine Simulation mit Spielgeld.");
        if (!ext.accounts().isEmpty()) {
            notes.add("Verbundene Banken: letzter abgerufener Kontostand, nur Konten in Euro.");
            if (lines.stream().anyMatch(l -> l.kind().equals("EXTERNAL") && l.stale())) {
                notes.add("Mindestens ein Kontostand einer verbundenen Bank ist älter als 2 Tage oder die Verbindung ist gestört.");
            }
            history.stream().filter(HistoryPoint::externalIncluded).findFirst()
                    .filter(h -> !h.equals(history.get(0)))
                    .ifPresent(h -> notes.add("Verlauf mit verbundenen Banken erst ab " + h.month()
                            + ", weil vorher keine Umsätze vorliegen."));
        }
        return new Dashboard(today, netWorth, liquidity, lines, cardLines, debts, cashflow, goals, top,
                expMonth == null ? null : expMonth.month(), history, notes, Instant.now(clock));
    }

    private static long sum(List<AccountDto> list, AccountType type) {
        return list.stream().filter(a -> a.type() == type).mapToLong(AccountDto::balanceCents).sum();
    }

    // ------------------------------------------------------------------ Vermögensverlauf

    /** Vermögen an jedem Monatsende der letzten 12 Monate (laufender Monat: heute). */
    List<HistoryPoint> history(Long userId, YearMonth now, BankingDtos.AccountsOverview ext) {
        List<YearMonth> months = new ArrayList<>();
        for (int i = HISTORY_MONTHS - 1; i >= 0; i--) {
            months.add(now.minusMonths(i));
        }
        String zone = clock.getZone().getId();

        // Konten der Projektbank (alle eigenen, auch aufgelöste): Summe aller Buchungen bis Monatsende
        TreeMap<String, Long> perMonth = new TreeMap<>();
        jdbc.query("""
                SELECT to_char(b.booked_at AT TIME ZONE ?, 'YYYY-MM') AS m, SUM(l.amount_cents) AS s
                  FROM booking_line l JOIN booking b ON b.id = l.booking_id JOIN account a ON a.id = l.account_id
                 WHERE a.owner_id = ? AND a.type <> 'SYSTEM'
                 GROUP BY 1
                """, rs -> {
            perMonth.put(rs.getString("m"), rs.getLong("s"));
        }, zone, userId);

        Map<YearMonth, Long> depot = depotHistory(userId, months, zone);
        Map<YearMonth, Long[]> external = externalHistory(months, ext, now);

        List<HistoryPoint> points = new ArrayList<>();
        for (YearMonth m : months) {
            long pb = perMonth.headMap(m.toString(), true).values().stream().mapToLong(Long::longValue).sum();
            long dp = depot.getOrDefault(m, 0L);
            Long[] e = external.get(m);
            boolean included = e[1] == 1L;
            long ex = included ? e[0] : 0;   // unvollständig: verbundene Banken ganz weglassen statt teilweise zählen
            points.add(new HistoryPoint(m.toString(), pb + dp + ex, pb, dp,
                    ext.accounts().isEmpty() || !included ? null : ex, included));
        }
        return points;
    }

    /**
     * Depotwert am Monatsende: gekaufte Anteile (aus den Sparraten mit Schlüssel "plan:Plan:Monat", gleiche Aufteilung
     * wie beim Kauf) bis zum Verkauf bei der Auszahlung, bewertet zum Preis des jeweiligen Monats.
     */
    Map<YearMonth, Long> depotHistory(Long userId, List<YearMonth> months, String zone) {
        Long depotAccount = accounts.systemAccount("DEPOT").getId();
        record Buy(Long plan, RiskLevel risk, YearMonth month, long cents) {}
        List<Buy> buys = jdbc.query("""
                SELECT p.id, p.risk_level, b.idempotency_key, l.amount_cents
                  FROM savings_plan p
                  JOIN booking b ON b.idempotency_key LIKE CONCAT('plan:', p.id, ':%')
                  JOIN booking_line l ON l.booking_id = b.id AND l.account_id = ?
                 WHERE p.user_id = ?
                """, (rs, i) -> {
            String key = rs.getString("idempotency_key");
            return new Buy(rs.getLong("id"), RiskLevel.valueOf(rs.getString("risk_level")),
                    YearMonth.parse(key.substring(key.lastIndexOf(':') + 1)), rs.getLong("amount_cents"));
        }, depotAccount, userId);
        Map<Long, YearMonth> sold = new HashMap<>();
        jdbc.query("""
                SELECT p.id, to_char(MIN(b.booked_at) AT TIME ZONE ?, 'YYYY-MM') AS m
                  FROM savings_plan p
                  JOIN booking b ON b.idempotency_key = CONCAT('plan-payout:', p.id)
                       OR (b.idempotency_key IS NULL AND b.type = 'DEPOT' AND b.created_by = p.user_id
                           AND b.description = CONCAT('Verkauf Musterdepot „', p.name, '“'))
                 WHERE p.user_id = ?
                 GROUP BY p.id
                """, rs -> {
            sold.put(rs.getLong("id"), YearMonth.parse(rs.getString("m")));
        }, zone, userId);

        Map<YearMonth, Long> result = new HashMap<>();
        for (YearMonth m : months) {
            Map<Fund, BigDecimal> units = new EnumMap<>(Fund.class);
            for (Buy b : buys) {
                YearMonth s = sold.get(b.plan());
                if (b.month().isAfter(m) || (s != null && !s.isAfter(m))) {
                    continue;
                }
                DepotMath.unitsFor(b.risk(), b.cents(), b.month()).forEach((f, u) -> units.merge(f, u, BigDecimal::add));
            }
            long value = 0;
            for (Map.Entry<Fund, BigDecimal> e : units.entrySet()) {
                value += DepotMath.value(e.getKey(), e.getValue(), m);
            }
            result.put(m, value);
        }
        return result;
    }

    /**
     * Kontostand verbundener Konten am Monatsende: heutiger Stand minus alle danach gebuchten Umsätze. Vor dem ersten
     * importierten Umsatz ist der Stand unbekannt (Wert [Summe, 1 = vollständig / 0 = unvollständig]).
     */
    Map<YearMonth, Long[]> externalHistory(List<YearMonth> months, BankingDtos.AccountsOverview ext, YearMonth now) {
        Map<YearMonth, Long[]> result = new HashMap<>();
        for (YearMonth m : months) {
            result.put(m, new Long[] {0L, 1L});
        }
        for (BankingDtos.AccountDto a : ext.accounts()) {
            if (!"EUR".equals(a.currency()) || a.balanceCents() == null) {
                continue;
            }
            Date first = jdbc.queryForObject("""
                    SELECT MIN(booking_date) FROM external_transaction
                     WHERE external_account_id = ? AND status IN ('BOOKED', 'REVERSED')
                    """, Date.class, a.id());
            for (YearMonth m : months) {
                Long[] r = result.get(m);
                if (m.equals(now)) {
                    r[0] += a.balanceCents();
                    continue;
                }
                LocalDate end = m.atEndOfMonth();
                if (first == null || end.isBefore(first.toLocalDate())) {
                    r[1] = 0L;
                    continue;
                }
                Long later = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(amount_cents), 0) FROM external_transaction
                         WHERE external_account_id = ? AND status IN ('BOOKED', 'REVERSED') AND booking_date > ?
                        """, Long.class, a.id(), Date.valueOf(end));
                r[0] += a.balanceCents() - (later == null ? 0 : later);
            }
        }
        return result;
    }
}
