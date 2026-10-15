package de.frauas.girokonto.planning;

import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.InsightsService;
import de.frauas.girokonto.insights.MonthSummaryDto;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.planning.PlanningDtos.BudgetItem;
import de.frauas.girokonto.planning.PlanningDtos.BudgetOverview;
import de.frauas.girokonto.planning.PlanningDtos.Unbudgeted;
import de.frauas.girokonto.planning.PlanningDtos.Unusual;
import de.frauas.girokonto.simulation.DailyJob;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budgets je Ausgabenkategorie mit Warnungen bei 80 % und 100 % (Tageslauf Nr. 50) und Erkennung ungewöhnlicher
 * Ausgaben. Gezählt wird wie im Finanzüberblick: Girokonto, verbundene Banken und CSV-Importe.
 */
@Service
public class BudgetService implements DailyJob {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);
    static final int WARN_PERCENT = 80;
    /** Ab diesem Tag im Monat wird eine Hochrechnung bis Monatsende gezeigt. */
    static final int PROJECTION_FROM_DAY = 5;
    static final long UNUSUAL_MIN_DIFF_CENTS = 5_000;
    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final JdbcTemplate jdbc;
    private final InsightsService insights;
    private final AccountService accounts;
    private final NotificationService notifications;
    private final Clock clock;

    public BudgetService(JdbcTemplate jdbc, InsightsService insights, AccountService accounts,
                         NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.insights = insights;
        this.accounts = accounts;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Budgets

    @Transactional(readOnly = true)
    public BudgetOverview overview(Long userId) {
        LocalDate today = LocalDate.now(clock);
        YearMonth ym = YearMonth.from(today);
        Map<Category, Long> limits = limits(userId);
        Map<Category, Long> spent = spentThisMonth(userId);
        List<BudgetItem> items = new ArrayList<>();
        long totalLimit = 0;
        long totalSpent = 0;
        for (Map.Entry<Category, Long> e : limits.entrySet()) {
            items.add(item(e.getKey(), e.getValue(), spent.getOrDefault(e.getKey(), 0L), today));
            totalLimit += e.getValue();
            totalSpent += spent.getOrDefault(e.getKey(), 0L);
        }
        items.sort(Comparator.comparingDouble(BudgetItem::percent).reversed());
        List<Unbudgeted> unbudgeted = spent.entrySet().stream()
                .filter(e -> !limits.containsKey(e.getKey()) && e.getValue() > 0)
                .sorted(Map.Entry.<Category, Long>comparingByValue().reversed()).limit(5)
                .map(e -> new Unbudgeted(e.getKey().name(), e.getKey().label(), e.getValue())).toList();
        List<String> notes = List.of(
                "Ausgaben wie im Finanzüberblick: Girokonto, verbundene Banken und CSV-Importe.",
                "Warnungen kommen bei 80 % und 100 % – höchstens einmal je Monat und Kategorie.");
        return new BudgetOverview(ym.toString(), ym.lengthOfMonth() - today.getDayOfMonth(), items, totalLimit,
                totalSpent, unbudgeted, unusual(userId, today), notes);
    }

    static BudgetItem item(Category c, long limit, long spent, LocalDate today) {
        double percent = Math.round(spent * 1000.0 / limit) / 10.0;
        String status = spent > limit ? "OVER" : percent >= WARN_PERCENT ? "WARN" : "OK";
        Long projected = null;
        String text = null;
        int day = today.getDayOfMonth();
        int length = today.lengthOfMonth();
        if (day >= PROJECTION_FROM_DAY && day < length && spent > 0) {
            projected = Math.round(spent * (double) length / day);
            text = projected > limit
                    ? "Bei gleichem Tempo voraussichtlich " + Money.format(projected) + " – "
                            + Money.format(projected - limit) + " über dem Budget."
                    : "Bei gleichem Tempo voraussichtlich " + Money.format(projected) + ".";
        }
        return new BudgetItem(c.name(), c.label(), limit, spent, limit - spent, percent, status, projected, text);
    }

    @Transactional
    public BudgetOverview set(Long userId, String category, long monthlyCents) {
        Category c = parse(category);
        jdbc.update("""
                INSERT INTO budget (user_id, category, monthly_cents) VALUES (?, ?, ?)
                ON CONFLICT (user_id, category) DO UPDATE SET monthly_cents = EXCLUDED.monthly_cents, updated_at = now()
                """, userId, c.name(), monthlyCents);
        return overview(userId);
    }

    @Transactional
    public void delete(Long userId, String category) {
        jdbc.update("DELETE FROM budget WHERE user_id = ? AND category = ?", userId, parse(category).name());
    }

    static Category parse(String category) {
        try {
            Category c = Category.valueOf(category == null ? "" : category.trim().toUpperCase(Locale.ROOT));
            if (c.isExpense() && c.isUserSelectable()) {
                return c;
            }
        } catch (IllegalArgumentException e) {
            // unten
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "Für diese Kategorie gibt es kein Budget.");
    }

    private Map<Category, Long> limits(Long userId) {
        Map<Category, Long> m = new LinkedHashMap<>();
        jdbc.query("SELECT category, monthly_cents FROM budget WHERE user_id = ? ORDER BY category", rs -> {
            try {
                m.put(Category.valueOf(rs.getString("category")), rs.getLong("monthly_cents"));
            } catch (IllegalArgumentException e) {
                // Kategorie gibt es nicht mehr
            }
        }, userId);
        return m;
    }

    /** Ausgaben (positiv) je Kategorie im laufenden Monat – dieselben Zahlen wie im Finanzüberblick. */
    private Map<Category, Long> spentThisMonth(Long userId) {
        Map<Category, Long> m = new EnumMap<>(Category.class);
        List<MonthSummaryDto> months = insights.months(userId, 1);
        if (!months.isEmpty()) {
            for (MonthSummaryDto.CategoryAmount c : months.get(0).categories()) {
                if (c.category().isExpense() && c.cents() > 0) {
                    m.put(c.category(), c.cents());
                }
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ Warnungen (Tageslauf)

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void runFor(LocalDate date) {
        String ym = YearMonth.from(date).toString();
        for (Long userId : jdbc.queryForList("SELECT DISTINCT user_id FROM budget ORDER BY user_id", Long.class)) {
            try {
                checkUser(userId, ym);
            } catch (RuntimeException e) {
                log.error("Budgetprüfung für Nutzer {} fehlgeschlagen", userId, e);
            }
        }
    }

    void checkUser(Long userId, String ym) {
        Map<Category, Long> spent = spentThisMonth(userId);
        for (Map.Entry<Category, Long> e : limits(userId).entrySet()) {
            long s = spent.getOrDefault(e.getKey(), 0L);
            long limit = e.getValue();
            int level = s > limit ? 100 : s * 100 >= limit * WARN_PERCENT ? WARN_PERCENT : 0;
            if (level == 0) {
                continue;
            }
            int inserted = jdbc.update("INSERT INTO budget_alert (user_id, category, month, level) VALUES (?, ?, ?, ?)"
                    + " ON CONFLICT DO NOTHING", userId, e.getKey().name(), ym, level);
            if (level == 100) {
                // die 80-%-Warnung ist damit überholt
                jdbc.update("INSERT INTO budget_alert (user_id, category, month, level) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT DO NOTHING", userId, e.getKey().name(), ym, WARN_PERCENT);
            }
            if (inserted == 1) {
                long percent = Math.round(s * 100.0 / limit);
                notifications.send(userId, NotificationType.INFO,
                        level == 100 ? "Budget überschritten" : "Budget fast aufgebraucht",
                        "„" + e.getKey().label() + "“: " + Money.format(s) + " von " + Money.format(limit) + " ("
                                + percent + " %) im Monat " + YearMonth.parse(ym).format(MONTH) + ".", null);
            }
        }
    }

    // ------------------------------------------------------------------ Ungewöhnliche Ausgaben

    private record Line(LocalDate date, Category category, long cents, String text) {}

    /**
     * Auffällige Ausgaben im laufenden Monat, verglichen mit den 6 Monaten davor: einzelne Ausgaben über dem
     * Dreifachen des Üblichen (Median, mindestens 3 Vergleichswerte) und Kategorien, die schon jetzt 50 % über dem
     * Monatsschnitt liegen (mindestens 2 Vergleichsmonate). Unterschied jeweils mindestens 50 €.
     */
    List<Unusual> unusual(Long userId, LocalDate today) {
        YearMonth cur = YearMonth.from(today);
        LocalDate from = cur.minusMonths(6).atDay(1);
        List<Line> lines = expenseLines(userId, from, today);
        Map<Category, List<Line>> history = new EnumMap<>(Category.class);
        Map<Category, List<Line>> current = new EnumMap<>(Category.class);
        for (Line l : lines) {
            (YearMonth.from(l.date()).equals(cur) ? current : history)
                    .computeIfAbsent(l.category(), k -> new ArrayList<>()).add(l);
        }
        List<Unusual> result = new ArrayList<>();
        for (Map.Entry<Category, List<Line>> e : current.entrySet()) {
            Category c = e.getKey();
            List<Line> past = history.getOrDefault(c, List.of());
            if (past.size() >= 3) {
                long median = median(past.stream().mapToLong(Line::cents).sorted().toArray());
                for (Line l : e.getValue()) {
                    if (median > 0 && l.cents() > 3 * median && l.cents() - median >= UNUSUAL_MIN_DIFF_CENTS) {
                        result.add(new Unusual("SINGLE", c.name(), c.label(), l.date(), l.text(), l.cents(), median,
                                "Etwa " + Math.round(l.cents() / (double) median) + "-mal so viel wie sonst bei „"
                                        + c.label() + "“ (üblich: " + Money.format(median) + ")."));
                    }
                }
            }
            Map<YearMonth, Long> perMonth = new LinkedHashMap<>();
            for (Line l : past) {
                YearMonth m = YearMonth.from(l.date());
                if (!m.isBefore(cur.minusMonths(3))) {
                    perMonth.merge(m, l.cents(), Long::sum);
                }
            }
            if (perMonth.size() >= 2) {
                long avg = perMonth.values().stream().mapToLong(Long::longValue).sum() / perMonth.size();
                long now = e.getValue().stream().mapToLong(Line::cents).sum();
                if (now * 2 > avg * 3 && now - avg >= UNUSUAL_MIN_DIFF_CENTS) {
                    result.add(new Unusual("CATEGORY", c.name(), c.label(), null, null, now, avg,
                            "Diesen Monat schon " + Money.format(now) + " für „" + c.label() + "“ – sonst im Schnitt "
                                    + Money.format(avg) + " pro Monat."));
                }
            }
        }
        result.sort(Comparator.comparingLong((Unusual u) -> u.amountCents() - u.usualCents()).reversed());
        return result.size() > 5 ? result.subList(0, 5) : result;
    }

    static long median(long[] sorted) {
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    /** Ausgaben (positiv) aus denselben Quellen wie der Finanzüberblick. */
    private List<Line> expenseLines(Long userId, LocalDate from, LocalDate to) {
        List<Line> lines = new ArrayList<>();
        Long main = accounts.mainAccount(userId).getId();
        jdbc.query("""
                SELECT (b.booked_at AT TIME ZONE ?)::date AS d, l.category, l.amount_cents, b.description AS text
                  FROM booking_line l JOIN booking b ON b.id = l.booking_id
                 WHERE l.account_id = ? AND l.amount_cents < 0 AND b.booked_at >= ? AND b.booked_at < ?
                """, rs -> {
            add(lines, rs.getDate("d").toLocalDate(), rs.getString("category"), rs.getLong("amount_cents"),
                    rs.getString("text"));
        }, clock.getZone().getId(), main, Timestamp.from(from.atStartOfDay(clock.getZone()).toInstant()),
                Timestamp.from(to.plusDays(1).atStartOfDay(clock.getZone()).toInstant()));
        jdbc.query("""
                SELECT i.booked_on AS d, i.category, i.amount_cents, COALESCE(i.counterparty, i.description) AS text
                  FROM imported_transaction i
                 WHERE i.user_id = ? AND i.amount_cents < 0 AND i.booked_on >= ? AND i.booked_on <= ?
                   AND NOT EXISTS (SELECT 1 FROM external_transaction x
                                    WHERE x.user_id = i.user_id AND x.booking_date = i.booked_on
                                      AND x.amount_cents = i.amount_cents AND x.status IN ('BOOKED', 'REVERSED'))
                """, rs -> {
            add(lines, rs.getDate("d").toLocalDate(), rs.getString("category"), rs.getLong("amount_cents"),
                    rs.getString("text"));
        }, userId, Date.valueOf(from), Date.valueOf(to));
        jdbc.query("""
                SELECT t.booking_date AS d, COALESCE(e.category, t.auto_category) AS category, t.amount_cents,
                       COALESCE(t.counterparty, t.remittance) AS text
                  FROM external_transaction t LEFT JOIN external_transaction_edit e ON e.transaction_id = t.id
                 WHERE t.user_id = ? AND t.status = 'BOOKED' AND NOT t.is_reversal AND t.currency = 'EUR'
                   AND COALESCE(e.excluded, FALSE) = FALSE AND t.amount_cents < 0
                   AND t.booking_date >= ? AND t.booking_date <= ?
                """, rs -> {
            add(lines, rs.getDate("d").toLocalDate(), rs.getString("category"), rs.getLong("amount_cents"),
                    rs.getString("text"));
        }, userId, Date.valueOf(from), Date.valueOf(to));
        return lines;
    }

    private static void add(List<Line> lines, LocalDate d, String category, long cents, String text) {
        Category c;
        try {
            c = category == null ? Category.SONSTIGES : Category.valueOf(category);
        } catch (IllegalArgumentException e) {
            c = Category.SONSTIGES;
        }
        if (c.isExpense()) {
            lines.add(new Line(d, c, -cents, text));
        }
    }
}
