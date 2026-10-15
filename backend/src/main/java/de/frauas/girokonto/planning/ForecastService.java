package de.frauas.girokonto.planning;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.allocation.AllocationInterval;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.common.Dates;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.entry.Entry;
import de.frauas.girokonto.entry.EntryRepository;
import de.frauas.girokonto.loan.Loan;
import de.frauas.girokonto.loan.LoanRepository;
import de.frauas.girokonto.planning.PlanningDtos.Forecast;
import de.frauas.girokonto.planning.PlanningDtos.ForecastEvent;
import de.frauas.girokonto.planning.PlanningDtos.ForecastPoint;
import de.frauas.girokonto.planning.PlanningDtos.MonthEnd;
import de.frauas.girokonto.savings.PlanStatus;
import de.frauas.girokonto.savings.PlanType;
import de.frauas.girokonto.savings.SavingsPlan;
import de.frauas.girokonto.savings.SavingsPlanRepository;
import de.frauas.girokonto.standingorder.StandingOrder;
import de.frauas.girokonto.standingorder.StandingOrderRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prognose des Girokontos für die nächsten Tage. Bekannte künftige Buchungen (Einnahmen & Ausgaben, Daueraufträge,
 * Spar- und Investraten, Kreditraten, Sparregeln) werden genau eingeplant. Übrige Umsätze (Gehalt aus Beispielmonaten,
 * Karte, Lastschriften, Überweisungen) werden aus den letzten bis zu 3 vollen Monaten am selben Tag im Monat
 * übernommen (Durchschnitt). Günstiger/ungünstiger Fall: übrige Ausgaben 20 % niedriger bzw. höher.
 * Eine Schätzung aus den eigenen Daten – keine Zusage und keine erfundenen Werte.
 */
@Service
public class ForecastService {

    static final double BEST = 0.8;
    static final double WORST = 1.2;
    static final int HISTORY_MONTHS = 3;
    static final long LOW_BALANCE_CENTS = 10_000;

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final BookingService bookings;
    private final EntryRepository entries;
    private final StandingOrderRepository standingOrders;
    private final SavingsPlanRepository plans;
    private final LoanRepository loans;
    private final Clock clock;

    public ForecastService(JdbcTemplate jdbc, AccountService accounts, BookingService bookings, EntryRepository entries,
                           StandingOrderRepository standingOrders, SavingsPlanRepository plans, LoanRepository loans,
                           Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.bookings = bookings;
        this.entries = entries;
        this.standingOrders = standingOrders;
        this.plans = plans;
        this.loans = loans;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Forecast forecast(Long userId, int days) {
        int n = Math.min(Math.max(days, 7), 180);
        LocalDate today = LocalDate.now(clock);
        LocalDate end = today.plusDays(n);
        Account main = accounts.mainAccount(userId);
        long start = bookings.balanceOf(main.getId());

        List<ForecastEvent> events = knownEvents(userId, main.getId(), today, end);
        Map<LocalDate, Long> known = new HashMap<>();
        for (ForecastEvent e : events) {
            known.merge(e.date(), e.amountCents(), Long::sum);
        }

        // übrige Umsätze der letzten vollen Monate je Tag im Monat
        YearMonth cur = YearMonth.from(today);
        YearMonth firstHistory = cur.minusMonths(HISTORY_MONTHS);
        Map<Integer, long[]> perDay = new HashMap<>();   // Tag → [Einnahmen, Ausgaben (negativ)]
        Set<YearMonth> monthsWithData = new HashSet<>();
        String zone = clock.getZone().getId();
        Timestamp from = Timestamp.from(firstHistory.atDay(1).atStartOfDay(clock.getZone()).toInstant());
        Timestamp to = Timestamp.from(cur.atDay(1).atStartOfDay(clock.getZone()).toInstant());
        jdbc.query("""
                SELECT (b.booked_at AT TIME ZONE ?)::date AS d, l.amount_cents, b.type, b.idempotency_key
                  FROM booking_line l JOIN booking b ON b.id = l.booking_id
                 WHERE l.account_id = ? AND b.booked_at >= ? AND b.booked_at < ?
                """, rs -> {
            LocalDate d = rs.getDate("d").toLocalDate();
            monthsWithData.add(YearMonth.from(d));
            String type = rs.getString("type");
            String key = rs.getString("idempotency_key");
            boolean other = switch (type) {
                case "CARD", "INCOME", "DIRECT_DEBIT" -> true;
                case "TRANSFER" -> key == null || !key.matches("transfer:\\d+:so-.*");   // Daueraufträge sind bekannt
                default -> false;   // Einträge, Raten, Umbuchungen, Startguthaben: bekannt oder einmalig
            };
            if (other) {
                long cents = rs.getLong("amount_cents");
                long[] v = perDay.computeIfAbsent(d.getDayOfMonth(), k -> new long[2]);
                v[cents > 0 ? 0 : 1] += cents;
            }
        }, zone, main.getId(), from, to);
        int m = perDay.isEmpty() ? 0 : monthsWithData.size();

        List<ForecastPoint> points = new ArrayList<>();
        double expected = start;
        double best = start;
        double worst = start;
        for (LocalDate d = today.plusDays(1); !d.isAfter(end); d = d.plusDays(1)) {
            long k = known.getOrDefault(d, 0L);
            double income = 0;
            double expense = 0;
            if (m > 0) {
                for (int day : daysFor(d)) {
                    long[] v = perDay.get(day);
                    if (v != null) {
                        income += v[0] / (double) m;
                        expense += v[1] / (double) m;
                    }
                }
            }
            expected += k + income + expense;
            best += k + income + BEST * expense;
            worst += k + income + WORST * expense;
            points.add(new ForecastPoint(d, Math.round(expected), Math.round(best), Math.round(worst)));
        }

        List<MonthEnd> monthEnds = new ArrayList<>();
        for (ForecastPoint p : points) {
            if (p.date().getDayOfMonth() == p.date().lengthOfMonth()) {
                monthEnds.add(new MonthEnd(YearMonth.from(p.date()).toString(), p.expectedCents(), p.bestCents(),
                        p.worstCents()));
            }
        }
        ForecastPoint lowest = points.stream().min(Comparator.comparingLong(ForecastPoint::expectedCents)
                .thenComparing(ForecastPoint::date)).orElse(null);

        List<String> warnings = new ArrayList<>();
        ForecastPoint negative = points.stream().filter(p -> p.expectedCents() < 0).findFirst().orElse(null);
        ForecastPoint worstNegative = points.stream().filter(p -> p.worstCents() < 0).findFirst().orElse(null);
        if (negative != null) {
            warnings.add("Voraussichtlich reicht das Guthaben am " + Dates.de(negative.date()) + " nicht: erwartet "
                    + Money.format(negative.expectedCents()) + ".");
        } else if (worstNegative != null) {
            warnings.add("Im ungünstigen Fall wird es ab " + Dates.de(worstNegative.date()) + " knapp: "
                    + Money.format(worstNegative.worstCents()) + ".");
        } else if (lowest != null && lowest.expectedCents() < LOW_BALANCE_CENTS) {
            warnings.add("Am " + Dates.de(lowest.date()) + " voraussichtlich nur noch "
                    + Money.format(lowest.expectedCents()) + " auf dem Girokonto.");
        }

        List<String> assumptions = new ArrayList<>();
        assumptions.add("Bekannte Buchungen: Einnahmen & Ausgaben, Daueraufträge, Spar- und Investraten, Kreditraten "
                + "und Sparregeln vom Girokonto.");
        assumptions.add(m > 0
                ? "Übrige Umsätze (z. B. Gehalt, Kartenzahlungen, Lastschriften) wie im Schnitt der letzten " + m
                        + " Monate am selben Tag im Monat."
                : "Noch keine Umsätze aus Vormonaten – nur bekannte Buchungen.");
        assumptions.add("Günstig: 20 % weniger, ungünstig: 20 % mehr übrige Ausgaben. Eine Schätzung, keine Zusage.");
        assumptions.add("Nur das Girokonto der Projektbank (Simulation).");
        return new Forecast(today, start, n, points, events, monthEnds, lowest, warnings, assumptions, m);
    }

    /** Tage im Monat, deren Umsätze auf {@code d} fallen: am Monatsletzten auch die Tage, die es im Monat nicht gibt. */
    static List<Integer> daysFor(LocalDate d) {
        List<Integer> r = new ArrayList<>();
        r.add(d.getDayOfMonth());
        if (d.getDayOfMonth() == d.lengthOfMonth()) {
            for (int x = d.lengthOfMonth() + 1; x <= 31; x++) {
                r.add(x);
            }
        }
        return r;
    }

    /** Bekannte Buchungen auf dem Girokonto zwischen morgen und {@code end}. */
    List<ForecastEvent> knownEvents(Long userId, Long mainId, LocalDate today, LocalDate end) {
        List<ForecastEvent> events = new ArrayList<>();
        List<Entry> active = entries.findByUserIdAndStatusInOrderByIdAsc(userId, List.of(Entry.Status.ACTIVE));
        List<StandingOrder> orders = standingOrders.findByUserIdAndStatusNotOrderByIdAsc(userId, StandingOrder.Status.ENDED)
                .stream().filter(o -> o.getStatus() == StandingOrder.Status.ACTIVE).toList();
        List<SavingsPlan> activePlans = plans.findByUserIdAndStatusInOrderByIdAsc(userId, List.of(PlanStatus.ACTIVE));
        for (LocalDate d = today.plusDays(1); !d.isAfter(end); d = d.plusDays(1)) {
            String ym = YearMonth.from(d).toString();
            for (Entry e : active) {
                if (e.isDueOn(d) && (e.getLastBookedDate() == null || e.getLastBookedDate().isBefore(d))) {
                    events.add(new ForecastEvent(d, e.getTitle(),
                            e.getKind() == Entry.Kind.INCOME ? e.getAmountCents() : -e.getAmountCents(), "ENTRY"));
                }
            }
            for (StandingOrder o : orders) {
                if (o.isDueOn(d) && !ym.equals(o.getLastExecutedMonth())) {
                    events.add(new ForecastEvent(d, "Dauerauftrag an " + o.getRecipientName(), -o.getAmountCents(),
                            "STANDING_ORDER"));
                }
            }
            for (SavingsPlan p : activePlans) {
                if (d.getDayOfMonth() == p.getExecutionDay() && !ym.equals(p.getLastExecutedMonth())) {
                    events.add(new ForecastEvent(d, (p.getType() == PlanType.INVEST ? "Investrate " : "Sparrate ")
                            + p.getName(), -p.getMonthlyRateCents(), "PLAN"));
                }
            }
        }
        for (Loan l : loans.findByUserIdOrderByIdAsc(userId)) {
            if (l.getStatus() != Loan.Status.ACTIVE || l.getNextDueDate() == null) {
                continue;
            }
            int open = l.getTermMonths() - l.getPaidInstallments();
            for (int k = 0; k < open; k++) {
                LocalDate d = l.getNextDueDate().plusMonths(k);
                if (d.isAfter(end)) {
                    break;
                }
                if (d.isAfter(today)) {
                    events.add(new ForecastEvent(d, "Kreditrate " + l.getPurpose().label() + " (" + l.getBankName() + ")",
                            -l.getMonthlyRateCents(), "LOAN"));
                }
            }
        }
        jdbc.query("""
                SELECT r.amount_cents, r.interval_unit, r.start_date, r.end_date, r.next_date, a.name
                  FROM allocation_rule r JOIN account a ON a.id = r.target_account_id
                 WHERE r.user_id = ? AND r.source_type = 'PROJEKTBANK' AND r.source_account_id = ?
                   AND r.status = 'ACTIVE' AND r.next_date IS NOT NULL
                """, rs -> {
            AllocationInterval interval = AllocationInterval.parse(rs.getString("interval_unit"));
            LocalDate startDate = rs.getDate("start_date").toLocalDate();
            Date ruleEnd = rs.getDate("end_date");
            LocalDate d = rs.getDate("next_date").toLocalDate();
            while (!d.isAfter(end) && (ruleEnd == null || !d.isAfter(ruleEnd.toLocalDate()))) {
                if (d.isAfter(today)) {
                    events.add(new ForecastEvent(d, "Sparregel → " + rs.getString("name"), -rs.getLong("amount_cents"),
                            "ALLOCATION"));
                }
                d = interval.after(startDate, d);
            }
        }, userId, mainId);
        events.sort(Comparator.comparing(ForecastEvent::date).thenComparing(ForecastEvent::label));
        return events;
    }
}
