package de.frauas.girokonto.insights;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finanzüberblick: Einnahmen, Ausgaben, Kategorien und freier Betrag je Monat (/F570/, /F580/, Kap. 8.4).
 * Ausgewertet wird nur das Girokonto; Kategorien vom Typ EXCLUDED zählen nicht mit.
 */
@Service
public class InsightsService {

    public static final int AVERAGE_MONTHS = 3;
    /** Anteil von "verfügbar", der höchstens als Kreditrate eingeplant wird (/F1020/). */
    public static final double LOAN_SHARE = 0.70;

    private final JdbcTemplate jdbc;
    private final AccountService accountService;
    private final DemoDataService demoData;
    private final Clock clock;

    public InsightsService(JdbcTemplate jdbc, AccountService accountService, DemoDataService demoData, Clock clock) {
        this.jdbc = jdbc;
        this.accountService = accountService;
        this.demoData = demoData;
        this.clock = clock;
    }

    /** Die letzten [count] Monate, der älteste zuerst, der laufende Monat zuletzt. */
    @Transactional(readOnly = true)
    public List<MonthSummaryDto> months(Long userId, int count) {
        int n = Math.min(Math.max(count, 1), 24);
        Account main = accountService.mainAccount(userId);
        YearMonth current = YearMonth.now(clock);
        YearMonth first = current.minusMonths(n - 1L);

        Map<YearMonth, Map<Category, Long>> sums = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            sums.put(first.plusMonths(i), new EnumMap<>(Category.class));
        }
        var from = first.atDay(1).atStartOfDay(clock.getZone()).toInstant();
        var to = current.plusMonths(1).atDay(1).atStartOfDay(clock.getZone()).toInstant();
        jdbc.query("""
                SELECT b.booked_at, l.category, l.amount_cents
                  FROM booking_line l JOIN booking b ON b.id = l.booking_id
                 WHERE l.account_id = ? AND b.booked_at >= ? AND b.booked_at < ?
                """, rs -> {
            YearMonth ym = YearMonth.from(rs.getTimestamp("booked_at").toInstant().atZone(clock.getZone()));
            String c = rs.getString("category");
            Category cat = c == null ? Category.SONSTIGES : Category.valueOf(c);
            Map<Category, Long> m = sums.get(ym);
            if (m != null) {
                m.merge(cat, rs.getLong("amount_cents"), Long::sum);
            }
        }, main.getId(), Timestamp.from(from), Timestamp.from(to));

        // /K040/: importierte Umsätze anderer Banken (CSV) zählen mit
        jdbc.query("""
                SELECT booked_on, category, amount_cents FROM imported_transaction
                 WHERE user_id = ? AND booked_on >= ? AND booked_on < ?
                """, rs -> {
            YearMonth ym = YearMonth.from(rs.getDate("booked_on").toLocalDate());
            Map<Category, Long> m = sums.get(ym);
            if (m != null) {
                m.merge(Category.valueOf(rs.getString("category")), rs.getLong("amount_cents"), Long::sum);
            }
        }, userId, java.sql.Date.valueOf(first.atDay(1)), java.sql.Date.valueOf(current.plusMonths(1).atDay(1)));

        List<MonthSummaryDto> result = new ArrayList<>();
        for (var e : sums.entrySet()) {
            result.add(summarize(e.getKey(), e.getKey().isBefore(current), e.getValue()));
        }
        return result;
    }

    static MonthSummaryDto summarize(YearMonth ym, boolean complete, Map<Category, Long> byCategory) {
        long income = 0;
        long expense = 0;
        long fixed = 0;
        List<MonthSummaryDto.CategoryAmount> cats = new ArrayList<>();
        for (var e : byCategory.entrySet()) {
            Category c = e.getKey();
            long net = e.getValue();
            if (c.isIncome()) {
                income += net;
                cats.add(new MonthSummaryDto.CategoryAmount(c, c.label(), c.kind(), net));
            } else if (c.isExpense()) {
                long spent = -net;
                expense += spent;
                if (c.isFixed()) {
                    fixed += spent;
                }
                cats.add(new MonthSummaryDto.CategoryAmount(c, c.label(), c.kind(), spent));
            }
        }
        cats.sort((a, b) -> Long.compare(b.cents(), a.cents()));
        boolean hasData = income != 0 || expense != 0;
        return new MonthSummaryDto(ym.toString(), complete, hasData, income, expense, fixed, income - expense, cats);
    }

    /** Durchschnitt der letzten drei vollen Monate mit Daten; planRatesCents kommt vom Aufrufer. */
    @Transactional(readOnly = true)
    public OverviewDto overview(Long userId, long planRatesCents) {
        List<MonthSummaryDto> recent = months(userId, 7).stream()
                .filter(MonthSummaryDto::complete)
                .filter(MonthSummaryDto::hasData)
                .toList();
        List<MonthSummaryDto> used = recent.subList(Math.max(0, recent.size() - AVERAGE_MONTHS), recent.size());
        String profile = demoData.profileOf(userId);
        if (used.isEmpty()) {
            return new OverviewDto(false, 0, 0, 0, 0, 0, planRatesCents, 0, 0, profile);
        }
        long income = used.stream().mapToLong(MonthSummaryDto::incomeCents).sum() / used.size();
        long expense = used.stream().mapToLong(MonthSummaryDto::expenseCents).sum() / used.size();
        long fixed = used.stream().mapToLong(MonthSummaryDto::fixedCents).sum() / used.size();
        long free = income - expense;
        long available = free - planRatesCents;
        long maxLoanRate = Math.max(0, Math.round(available * LOAN_SHARE));
        return new OverviewDto(true, used.size(), income, expense, fixed, free, planRatesCents, available,
                maxLoanRate, profile);
    }

    /** Durchschnittliche Ausgaben je Kategorie (letzte drei volle Monate) und Ausgaben im letzten vollen Monat. */
    @Transactional(readOnly = true)
    public Map<Category, long[]> categoryTrend(Long userId) {
        List<MonthSummaryDto> recent = months(userId, 4).stream().filter(MonthSummaryDto::complete).toList();
        Map<Category, long[]> result = new EnumMap<>(Category.class);
        if (recent.isEmpty()) {
            return result;
        }
        MonthSummaryDto last = recent.get(recent.size() - 1);
        for (MonthSummaryDto m : recent) {
            for (var c : m.categories()) {
                long[] v = result.computeIfAbsent(c.category(), k -> new long[2]);
                v[0] += c.cents() / recent.size();             // Durchschnitt
                if (m == last) {
                    v[1] = c.cents();                          // letzter voller Monat
                }
            }
        }
        return result;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
