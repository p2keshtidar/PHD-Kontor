package de.frauas.girokonto.savings;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.booking.Booking;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.InsightsService;
import de.frauas.girokonto.insights.OverviewDto;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.savings.PlanDtos.CheckRequest;
import de.frauas.girokonto.savings.PlanDtos.CheckResult;
import de.frauas.girokonto.savings.PlanDtos.CreateRequest;
import de.frauas.girokonto.savings.PlanDtos.Detail;
import de.frauas.girokonto.savings.PlanDtos.Feasibility;
import de.frauas.girokonto.savings.PlanDtos.HistoryPoint;
import de.frauas.girokonto.savings.PlanDtos.Position;
import de.frauas.girokonto.savings.PlanDtos.Summary;
import de.frauas.girokonto.savings.PlanDtos.Tip;
import de.frauas.girokonto.savings.PlanDtos.UpdateRequest;
import de.frauas.girokonto.simulation.DailyJob;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spar- und Investpläne mit Musterdepot (/F610/ bis /F650/, /F810/ bis /F840/, /F910/ bis /F940/).
 *
 * Sparrate: Hauptkonto −Rate, Unterkonto +Sparkonto-Anteil, System „DEPOT“ +Depot-Anteil; mit dem Depot-Anteil
 * werden Fondsanteile zum Preis des Monats gekauft. Der Wert eines Plans = Unterkonto + Depotwert.
 */
@Service
public class SavingsPlanService implements DailyJob {

    private static final Set<PlanStatus> VISIBLE = EnumSet.of(PlanStatus.ACTIVE, PlanStatus.PAUSED, PlanStatus.COMPLETED);
    private static final Set<PlanStatus> OPEN = EnumSet.of(PlanStatus.ACTIVE, PlanStatus.PAUSED);
    private static final Set<Category> TIP_CATEGORIES =
            EnumSet.of(Category.LEBENSMITTEL, Category.FREIZEIT, Category.SHOPPING, Category.MOBILITAET);

    private final SavingsPlanRepository plans;
    private final DepotPositionRepository positions;
    private final PlanSnapshotRepository snapshots;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final BookingService bookingService;
    private final NotificationService notifications;
    private final InsightsService insights;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SavingsPlanService(SavingsPlanRepository plans, DepotPositionRepository positions,
                              PlanSnapshotRepository snapshots, AccountRepository accounts,
                              AccountService accountService, BookingService bookingService,
                              NotificationService notifications, InsightsService insights,
                              TransactionTemplate tx, Clock clock) {
        this.plans = plans;
        this.positions = positions;
        this.snapshots = snapshots;
        this.accounts = accounts;
        this.accountService = accountService;
        this.bookingService = bookingService;
        this.notifications = notifications;
        this.insights = insights;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public int order() {
        return 20; // nach den Einnahmen des Tages
    }

    // ------------------------------------------------------------------ Überblick

    /** Summe der Raten aller aktiven Pläne. */
    @Transactional(readOnly = true)
    public long activeRates(Long userId) {
        return plans.findByUserIdAndStatusInOrderByIdAsc(userId, EnumSet.of(PlanStatus.ACTIVE)).stream()
                .mapToLong(SavingsPlan::getMonthlyRateCents).sum();
    }

    /** Finanzüberblick inkl. "verfügbar für Pläne" (/F580/). */
    @Transactional(readOnly = true)
    public OverviewDto overview(Long userId) {
        return insights.overview(userId, activeRates(userId));
    }

    @Transactional(readOnly = true)
    public long totalDepotValue(Long userId) {
        YearMonth ym = YearMonth.now(clock);
        return plans.findByUserIdAndStatusInOrderByIdAsc(userId, OPEN).stream()
                .mapToLong(p -> depotValue(p.getId(), ym)).sum();
    }

    // ------------------------------------------------------------------ Machbarkeit (/F620/)

    @Transactional(readOnly = true)
    public CheckResult check(Long userId, CheckRequest r) {
        validateNumbers(r.type(), r.targetCents(), r.targetDate(), r.monthlyRateCents(), false);
        OverviewDto ov = overview(userId);
        double i = PlanMath.monthlyRate(r.riskLevel().expectedReturn(r.type()));
        LocalDate today = LocalDate.now(clock);

        Long required = null;
        long rate;
        if (r.type() == PlanType.GOAL) {
            required = PlanMath.requiredRate(0, r.targetCents(), i, monthsUntil(today, r.targetDate()));
            rate = r.monthlyRateCents() != null ? r.monthlyRateCents() : required;
        } else {
            rate = r.monthlyRateCents();
        }

        Feasibility level;
        if (!ov.hasData()) {
            level = Feasibility.UNKNOWN;
        } else if (ov.availableCents() <= 0) {
            level = Feasibility.RED;
        } else if (rate <= ov.availableCents() * 0.5) {
            level = Feasibility.GREEN;
        } else if (rate <= ov.availableCents()) {
            level = Feasibility.YELLOW;
        } else {
            level = Feasibility.RED;
        }

        LocalDate projected = projectDate(0, rate, i, r.targetCents(), today);
        Long altRate = null;
        LocalDate altDate = null;
        if (level == Feasibility.RED && r.targetCents() != null && ov.availableCents() >= 200) {
            altRate = Math.max(100, (ov.availableCents() / 2) / 100 * 100);
            altDate = projectDate(0, altRate, i, r.targetCents(), today);
        }
        return new CheckResult(required, rate, ov.availableCents(), ov.hasData(), level,
                message(level, rate, ov, altRate, altDate), projected, altRate, altDate,
                r.riskLevel().expectedReturn(r.type()));
    }

    private static String message(Feasibility level, long rate, OverviewDto ov, Long altRate, LocalDate altDate) {
        return switch (level) {
            case UNKNOWN -> "Noch keine Einnahmen und Ausgaben bekannt. Erzeuge Beispielmonate oder nutze das Konto "
                    + "einige Wochen, dann kann die App die Machbarkeit prüfen.";
            case GREEN -> "Gut machbar: " + Money.format(rate) + " sind höchstens die Hälfte deines verfügbaren Betrags ("
                    + Money.format(ov.availableCents()) + " pro Monat).";
            case YELLOW -> "Knapp: " + Money.format(rate) + " brauchen mehr als die Hälfte deines verfügbaren Betrags ("
                    + Money.format(ov.availableCents()) + "). Plane etwas Puffer ein.";
            case RED -> altRate == null
                    ? "Nicht machbar: Nach deinen Ausgaben bleibt derzeit kein Geld für diesen Plan übrig."
                    : "Nicht machbar: " + Money.format(rate) + " ist mehr, als dir pro Monat übrig bleibt ("
                            + Money.format(ov.availableCents()) + "). Vorschlag: " + Money.format(altRate)
                            + " pro Monat" + (altDate != null ? ", Ziel erreicht im " + monthText(altDate) : "") + ".";
        };
    }

    // ------------------------------------------------------------------ Anlegen, ändern, auflösen

    @Transactional
    public Detail create(Long userId, CreateRequest r) {
        validateNumbers(r.type(), r.targetCents(), r.targetDate(), r.monthlyRateCents(), true);
        Instant now = Instant.now(clock);
        Long accountId = null;
        if (r.type() == PlanType.GOAL) {
            Account a = accounts.save(new Account(userId, AccountType.SAVINGS, "Sparplan: " + r.name().trim(),
                    r.icon(), null, now));
            accountId = a.getId();
        }
        SavingsPlan plan = plans.save(new SavingsPlan(userId, accountId, r.type(), r.name().trim(), r.icon(),
                r.targetCents(), r.targetDate(), r.monthlyRateCents(), r.executionDay(), r.riskLevel(), now));
        if (r.reminderDays() != null) {
            plan.setReminderDays(r.reminderDays());
        }
        return detail(userId, plan.getId());
    }

    @Transactional
    public Detail update(Long userId, Long planId, UpdateRequest r) {
        SavingsPlan p = requireOpen(userId, planId);
        if (r.name() != null && !r.name().isBlank()) {
            p.setName(r.name().trim());
            if (p.getAccountId() != null) {
                accounts.findById(p.getAccountId()).ifPresent(a -> a.setName("Sparplan: " + r.name().trim()));
            }
        }
        if (r.icon() != null) {
            p.setIcon(r.icon());
        }
        if (r.targetCents() != null) {
            p.setTargetCents(r.targetCents());
        }
        if (r.targetDate() != null) {
            p.setTargetDate(r.targetDate());
        }
        if (r.monthlyRateCents() != null) {
            p.setMonthlyRateCents(r.monthlyRateCents());
        }
        if (r.executionDay() != null) {
            p.setExecutionDay(r.executionDay());
        }
        if (r.riskLevel() != null) {
            p.setRiskLevel(r.riskLevel()); // gilt ab der nächsten Rate (/F720/)
        }
        if (r.paused() != null) {
            p.setStatus(r.paused() ? PlanStatus.PAUSED : PlanStatus.ACTIVE);
        }
        if (r.reminderDays() != null) {
            p.setReminderDays(r.reminderDays());
        }
        validateNumbers(p.getType(), p.getTargetCents(), p.getTargetDate(), p.getMonthlyRateCents(), false);
        return detail(userId, planId);
    }

    /** Ziel erreicht: alles aufs Girokonto (/F940/). */
    @Transactional
    public Detail payout(Long userId, Long planId) {
        SavingsPlan p = requireOpen(userId, planId);
        payoutAll(p, PlanStatus.COMPLETED);
        return detail(userId, planId);
    }

    /** Plan vorzeitig auflösen (/F640/). */
    @Transactional
    public void close(Long userId, Long planId) {
        payoutAll(requireOpen(userId, planId), PlanStatus.CLOSED);
    }

    private void payoutAll(SavingsPlan p, PlanStatus newStatus) {
        Account main = accountService.mainAccount(p.getUserId());
        YearMonth ym = YearMonth.now(clock);
        long depotValue = depotValue(p.getId(), ym);
        if (depotValue > 0) {
            bookingService.post(BookingType.DEPOT, "Verkauf Musterdepot „" + p.getName() + "“", null, p.getUserId(), List.of(
                    new BookingService.Line(accountService.systemAccount("DEPOT").getId(), -depotValue),
                    new BookingService.Line(main.getId(), depotValue)));
        }
        long cash = cash(p);
        if (cash > 0) {
            bookingService.post(BookingType.INTERNAL, "Auszahlung „" + p.getName() + "“", null, p.getUserId(), List.of(
                    new BookingService.Line(p.getAccountId(), -cash),
                    new BookingService.Line(main.getId(), cash)));
        }
        positions.findByPlanIdOrderByFundAsc(p.getId()).forEach(DepotPosition::clear);
        if (p.getAccountId() != null) {
            accounts.findById(p.getAccountId()).ifPresent(a -> a.setStatus(AccountStatus.CLOSED));
        }
        p.setStatus(newStatus);
        notifications.send(p.getUserId(), NotificationType.GOAL,
                newStatus == PlanStatus.COMPLETED ? "Plan ausgezahlt" : "Plan aufgelöst",
                Money.format(depotValue + cash) + " aus „" + p.getName() + "“ aufs Girokonto gebucht", null);
    }

    // ------------------------------------------------------------------ Tageslauf (/F630/)

    @Override
    public void runFor(LocalDate date) {
        int day = date.getDayOfMonth();
        for (SavingsPlan p : plans.findByStatusAndExecutionDay(PlanStatus.ACTIVE, day)) {
            Long id = p.getId();
            tx.executeWithoutResult(s -> execute(id, date));
        }
        for (SavingsPlan p : plans.findByStatusAndReminderDaysGreaterThan(PlanStatus.ACTIVE, 0)) {
            Long id = p.getId();
            tx.executeWithoutResult(s -> remind(id, date));
        }
    }

    /**
     * /K010/: Erinnerung {@code reminderDays} Tage vor der Abbuchung, höchstens einmal pro Monat.
     * Reicht das Guthaben heute nicht für die Rate, enthält die Meldung eine Warnung.
     */
    void remind(Long planId, LocalDate today) {
        SavingsPlan p = plans.findById(planId).orElseThrow();
        if (p.getStatus() != PlanStatus.ACTIVE || p.getReminderDays() <= 0) {
            return;
        }
        LocalDate due = today.plusDays(p.getReminderDays());
        String ym = YearMonth.from(due).toString();
        if (due.getDayOfMonth() != p.getExecutionDay() || ym.equals(p.getLastExecutedMonth())
                || ym.equals(p.getLastRemindedMonth())) {
            return;
        }
        p.setLastRemindedMonth(ym);
        long rate = p.getMonthlyRateCents();
        long balance = bookingService.balanceOf(accountService.mainAccount(p.getUserId()).getId());
        String what = p.getType() == PlanType.INVEST ? "Investrate" : "Sparrate";
        String days = p.getReminderDays() == 1 ? "morgen" : "in " + p.getReminderDays() + " Tagen";
        String body = "Am " + due.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.")) + " werden "
                + Money.format(rate) + " für „" + p.getName() + "“ abgebucht.";
        if (balance < rate) {
            body += " Achtung: Auf dem Girokonto sind nur " + Money.format(balance)
                    + " – bitte rechtzeitig Geld einzahlen, sonst wird die Rate ausgesetzt.";
        }
        notifications.send(p.getUserId(), NotificationType.SAVING_RATE, what + " " + days, body, null);
    }

    void execute(Long planId, LocalDate date) {
        SavingsPlan p = plans.findById(planId).orElseThrow();
        String ym = YearMonth.from(date).toString();
        if (p.getStatus() != PlanStatus.ACTIVE || ym.equals(p.getLastExecutedMonth())) {
            return;
        }
        Account main = accounts.findByIdForUpdate(accountService.mainAccount(p.getUserId()).getId()).orElseThrow();
        long rate = p.getMonthlyRateCents();
        p.setLastExecutedMonth(ym);
        if (bookingService.balanceOf(main.getId()) < rate) {
            notifications.send(p.getUserId(), NotificationType.SAVING_RATE, "Rate ausgesetzt",
                    "„" + p.getName() + "“: nicht genug Guthaben für " + Money.format(rate) + ". Nächster Versuch im nächsten Monat.", null);
            return;
        }
        long before = currentValue(p);
        long depot = Math.round(rate * p.getRiskLevel().depotShare(p.getType()));
        long cashPart = rate - depot;

        List<BookingService.Line> lines = new ArrayList<>();
        lines.add(new BookingService.Line(main.getId(), -rate));
        if (cashPart > 0) {
            lines.add(new BookingService.Line(p.getAccountId(), cashPart));
        }
        if (depot > 0) {
            lines.add(new BookingService.Line(accountService.systemAccount("DEPOT").getId(), depot));
        }
        String what = p.getType() == PlanType.INVEST ? "Investrate" : "Sparrate";
        Booking booking = bookingService.post(BookingType.SAVING_RATE, what + " „" + p.getName() + "“",
                "plan:" + p.getId() + ":" + ym, p.getUserId(), lines);
        buy(p, depot, YearMonth.from(date));
        p.addDeposited(rate);

        long after = currentValue(p);
        snapshots.save(new PlanSnapshot(p.getId(), date, after, p.getDepositedCents()));
        notifications.send(p.getUserId(), NotificationType.SAVING_RATE, what + " gebucht",
                Money.format(rate) + " in „" + p.getName() + "“" + (depot > 0 ? " (davon " + Money.format(depot) + " in Fonds)" : ""),
                booking.getId());
        if (p.getTargetCents() != null && before < p.getTargetCents() && after >= p.getTargetCents()) {
            notifications.send(p.getUserId(), NotificationType.GOAL, "Ziel erreicht!",
                    "„" + p.getName() + "“ hat " + Money.format(p.getTargetCents()) + " erreicht. Du kannst jetzt auszahlen.", null);
        }
    }

    private void buy(SavingsPlan p, long depotCents, YearMonth ym) {
        if (depotCents <= 0) {
            return;
        }
        long equity = Math.round(depotCents * p.getRiskLevel().equityWeight());
        buyFund(p.getId(), Fund.WELT_AKTIEN, equity, ym);
        buyFund(p.getId(), Fund.EURO_ANLEIHEN, depotCents - equity, ym);
    }

    private void buyFund(Long planId, Fund fund, long cents, YearMonth ym) {
        if (cents <= 0) {
            return;
        }
        BigDecimal priceCents = BigDecimal.valueOf(fund.price(ym) * 100);
        BigDecimal units = BigDecimal.valueOf(cents).divide(priceCents, 6, RoundingMode.HALF_UP);
        DepotPosition pos = positions.findByPlanIdAndFund(planId, fund)
                .orElseGet(() -> positions.save(new DepotPosition(planId, fund)));
        pos.buy(units, cents);
    }

    // ------------------------------------------------------------------ Lesen

    @Transactional(readOnly = true)
    public List<Summary> list(Long userId) {
        return plans.findByUserIdAndStatusInOrderByIdAsc(userId, VISIBLE).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Detail detail(Long userId, Long planId) {
        SavingsPlan p = plans.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan nicht gefunden."));
        YearMonth ym = YearMonth.now(clock);
        LocalDate today = LocalDate.now(clock);
        Summary s = summary(p);
        long cash = cash(p);

        List<Position> pos = new ArrayList<>();
        long depotValue = 0;
        for (DepotPosition dp : positions.findByPlanIdOrderByFundAsc(p.getId())) {
            depotValue += valueOf(dp, ym);
        }
        for (DepotPosition dp : positions.findByPlanIdOrderByFundAsc(p.getId())) {
            if (dp.getUnits().signum() == 0) {
                continue;
            }
            long v = valueOf(dp, ym);
            pos.add(new Position(dp.getFund(), dp.getFund().label(), dp.getUnits().doubleValue(),
                    Math.round(dp.getFund().price(ym) * 100) / 100.0, v, dp.getInvestedCents(),
                    depotValue == 0 ? 0 : Math.round(v * 1000.0 / depotValue) / 10.0));
        }

        List<HistoryPoint> history = new ArrayList<>(snapshots.findByPlanIdOrderBySnapshotDateAscIdAsc(p.getId()).stream()
                .map(x -> new HistoryPoint(x.getSnapshotDate(), x.getValueCents(), x.getDepositedCents())).toList());
        if (p.isOpen()) {
            history.add(new HistoryPoint(today, s.currentValueCents(), p.getDepositedCents()));
        }

        long gain = s.currentValueCents() - p.getDepositedCents();
        double gainPercent = p.getDepositedCents() == 0 ? 0 : Math.round(gain * 1000.0 / p.getDepositedCents()) / 10.0;
        double i = PlanMath.monthlyRate(p.getRiskLevel().expectedReturn(p.getType()));
        Long required = null;
        if (p.getTargetCents() != null && p.getTargetDate() != null && p.isOpen()) {
            required = PlanMath.requiredRate(s.currentValueCents(), p.getTargetCents(), i, monthsUntil(today, p.getTargetDate()));
        }
        return new Detail(s, cash, depotValue, gain, gainPercent, p.getRiskLevel().expectedReturn(p.getType()),
                p.getRiskLevel().explanation(), required, pos, history, tips(p, s, i));
    }

    /** Spartipps (/F650/): variable Kategorien über dem Durchschnitt und ihr Effekt auf das Ziel. */
    private List<Tip> tips(SavingsPlan p, Summary s, double i) {
        if (p.getTargetCents() == null || p.getStatus() != PlanStatus.ACTIVE
                || s.currentValueCents() >= p.getTargetCents()) {
            return List.of();
        }
        Map<Category, long[]> trend = insights.categoryTrend(p.getUserId());
        List<Tip> tips = new ArrayList<>();
        Category biggest = null;
        long biggestValue = 0;
        for (Category c : TIP_CATEGORIES) {
            long[] v = trend.get(c);
            if (v == null) {
                continue;
            }
            long avg = v[0];
            long last = v[1];
            if (last > biggestValue) {
                biggest = c;
                biggestValue = last;
            }
            if (avg > 0 && last > avg * 1.1) {
                tips.add(tip(p, s, i, c, last, avg, true));
            }
        }
        if (tips.isEmpty() && biggest != null && biggestValue >= 1000) {
            tips.add(tip(p, s, i, biggest, biggestValue, trend.get(biggest)[0], false));
        }
        return tips;
    }

    private Tip tip(SavingsPlan p, Summary s, double i, Category c, long last, long avg, boolean aboveAverage) {
        long save = Math.max(100, Math.round(last * 0.10) / 100 * 100);
        Integer now = PlanMath.monthsToTarget(s.currentValueCents(), p.getMonthlyRateCents(), i, p.getTargetCents());
        Integer better = PlanMath.monthsToTarget(s.currentValueCents(), p.getMonthlyRateCents() + save, i, p.getTargetCents());
        Integer earlier = now != null && better != null ? now - better : null;
        String start = aboveAverage
                ? "Im letzten Monat hast du für " + c.label() + " " + Money.format(last) + " ausgegeben, "
                        + Math.round((last - avg) * 100.0 / avg) + " % mehr als im Durchschnitt. "
                : "Deine größte variable Ausgabe ist " + c.label() + " mit " + Money.format(last) + " im letzten Monat. ";
        String effect = earlier != null && earlier > 0
                ? "Mit " + Money.format(save) + " weniger pro Monat erreichst du dein Ziel " + earlier
                        + (earlier == 1 ? " Monat" : " Monate") + " früher."
                : "Mit " + Money.format(save) + " mehr Sparrate pro Monat kommst du schneller ans Ziel.";
        return new Tip(c.name(), c.label(), last, avg, save, earlier, start + effect);
    }

    private Summary summary(SavingsPlan p) {
        LocalDate today = LocalDate.now(clock);
        long value = currentValue(p);
        double i = PlanMath.monthlyRate(p.getRiskLevel().expectedReturn(p.getType()));
        Double progress = null;
        LocalDate projected = null;
        Boolean onTrack = null;
        if (p.getTargetCents() != null && p.getTargetCents() > 0) {
            progress = Math.min(100.0, Math.round(value * 1000.0 / p.getTargetCents()) / 10.0);
            long rate = p.getStatus() == PlanStatus.ACTIVE ? p.getMonthlyRateCents() : 0;
            projected = projectDate(value, rate, i, p.getTargetCents(), today);
            if (p.getTargetDate() != null) {
                onTrack = projected != null && !YearMonth.from(projected).isAfter(YearMonth.from(p.getTargetDate()));
            }
        }
        LocalDate next = null;
        if (p.getStatus() == PlanStatus.ACTIVE) {
            LocalDate candidate = today.withDayOfMonth(p.getExecutionDay());
            next = candidate.isAfter(today) ? candidate : candidate.plusMonths(1);
        }
        return new Summary(p.getId(), p.getType(), p.getName(), p.getIcon(), p.getStatus(), p.getRiskLevel(),
                p.getTargetCents(), p.getTargetDate(), p.getMonthlyRateCents(), p.getExecutionDay(), value,
                p.getDepositedCents(), progress, projected, onTrack, next, p.getAccountId(), p.getReminderDays());
    }

    // ------------------------------------------------------------------ Hilfsfunktionen

    private long cash(SavingsPlan p) {
        return p.getAccountId() == null ? 0 : bookingService.balanceOf(p.getAccountId());
    }

    private long currentValue(SavingsPlan p) {
        if (!p.isOpen()) {
            return 0;
        }
        return cash(p) + depotValue(p.getId(), YearMonth.now(clock));
    }

    long depotValue(Long planId, YearMonth ym) {
        return positions.findByPlanIdOrderByFundAsc(planId).stream().mapToLong(dp -> valueOf(dp, ym)).sum();
    }

    private static long valueOf(DepotPosition dp, YearMonth ym) {
        return dp.getUnits().multiply(BigDecimal.valueOf(dp.getFund().price(ym) * 100))
                .setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static LocalDate projectDate(long w0, long rate, double i, Long target, LocalDate today) {
        if (target == null) {
            return null;
        }
        Integer n = PlanMath.monthsToTarget(w0, rate, i, target);
        return n == null ? null : today.plusMonths(n);
    }

    static int monthsUntil(LocalDate today, LocalDate target) {
        return (int) Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(target)));
    }

    private static String monthText(LocalDate d) {
        String[] names = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September",
                "Oktober", "November", "Dezember"};
        return names[d.getMonthValue() - 1] + " " + d.getYear();
    }

    private SavingsPlan requireOpen(Long userId, Long planId) {
        SavingsPlan p = plans.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan nicht gefunden."));
        if (!p.isOpen()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_CLOSED", "Dieser Plan ist bereits abgeschlossen.");
        }
        return p;
    }

    private void validateNumbers(PlanType type, Long target, LocalDate targetDate, Long rate, boolean requireRate) {
        LocalDate today = LocalDate.now(clock);
        if (type == PlanType.GOAL) {
            if (target == null || target < 5_000 || target > 10_000_000) {
                throw bad("INVALID_TARGET", "Zielbetrag zwischen 50 € und 100.000 € angeben.");
            }
            if (targetDate == null || targetDate.isBefore(today.plusMonths(1))) {
                throw bad("INVALID_TARGET_DATE", "Das Wunschdatum muss mindestens einen Monat in der Zukunft liegen.");
            }
            if (rate != null && (rate < 100 || rate > 1_000_000)) {
                throw bad("INVALID_RATE", "Monatsrate zwischen 1 € und 10.000 € angeben.");
            }
        } else {
            if (rate == null || rate < 1_000 || rate > 500_000) {
                throw bad("INVALID_RATE", "Monatsbetrag zwischen 10 € und 5.000 € angeben.");
            }
            if (target != null && (target < 5_000 || target > 100_000_000)) {
                throw bad("INVALID_TARGET", "Zielbetrag ungültig.");
            }
        }
        if (requireRate && rate == null) {
            throw bad("INVALID_RATE", "Monatsrate fehlt.");
        }
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
