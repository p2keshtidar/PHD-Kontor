package de.frauas.girokonto.allocation;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountMemberRepository;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountStatus;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.account.EarmarkRepository;
import de.frauas.girokonto.account.GoalTracker;
import de.frauas.girokonto.allocation.AllocationDtos.AccountAllocations;
import de.frauas.girokonto.allocation.AllocationDtos.CreateRuleRequest;
import de.frauas.girokonto.allocation.AllocationDtos.EarmarkDto;
import de.frauas.girokonto.allocation.AllocationDtos.EarmarkRequest;
import de.frauas.girokonto.allocation.AllocationDtos.ReleaseRequest;
import de.frauas.girokonto.allocation.AllocationDtos.RuleDto;
import de.frauas.girokonto.allocation.AllocationDtos.RunDto;
import de.frauas.girokonto.allocation.AllocationDtos.SourceDto;
import de.frauas.girokonto.allocation.AllocationDtos.UpcomingDto;
import de.frauas.girokonto.allocation.AllocationDtos.UpdateRuleRequest;
import de.frauas.girokonto.banking.BankingService;
import de.frauas.girokonto.booking.BookingRepository;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Dates;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.simulation.DailyJob;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sparregeln und Geldzuweisungen für digitale Konten.
 *
 * <ul>
 *   <li><b>PROJEKTBANK</b>: Umbuchung vom eigenen Girokonto der Projektbank (Simulation) ins digitale Konto –
 *       im Hauptbuch als INTERNAL gebucht, keine Überweisung, keine echte Bank.</li>
 *   <li><b>EXTERNAL</b>: verbundenes Bankkonto (Open Banking, nur Lesezugriff). Es wird kein Geld bewegt und keine
 *       Zahlung ausgelöst – der Betrag wird nur vorgemerkt, nachdem Verbindung, Kontostand und freie Deckung
 *       geprüft wurden.</li>
 * </ul>
 *
 * <p>Jede Fälligkeit einer Regel ist genau ein Vorgang (allocation_run, eindeutig je Regel und Datum). Ausgeführt
 * wird unter Sperre des Vorgangs; die Buchung trägt zusätzlich den Idempotenz-Schlüssel "alloc-Regel-Datum".
 * Fehlgeschlagene Vorgänge werden an den Folgetagen wiederholt (höchstens {@code maxAttempts} Versuche), ohne dass
 * es je zu einer doppelten Abbuchung kommt.
 */
@Service
public class AllocationService implements DailyJob {

    private static final Logger log = LoggerFactory.getLogger(AllocationService.class);

    static final String PROJEKTBANK = "PROJEKTBANK";
    static final String EXTERNAL = "EXTERNAL";
    static final String PROJEKTBANK_NAME = "Projektbank (Simulation)";
    static final String PROJEKTBANK_EXPLANATION =
            "Umbuchung innerhalb der Projektbank (Simulation) – keine echte Überweisung.";
    static final String EARMARK_EXPLANATION = "Vormerkung: Das Geld bleibt auf deinem Konto bei der anderen Bank. "
            + "Es wird keine Zahlung ausgelöst; der Betrag wird nur deinem Ziel zugerechnet.";
    private static final int MAX_CATCH_UP = 60;

    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final AccountMemberRepository members;
    private final BookingService bookings;
    private final BookingRepository bookingRepository;
    private final EarmarkRepository earmarks;
    private final GoalTracker goals;
    private final NotificationService notifications;
    private final BankingService banking;
    private final AppProperties appProps;
    private final AllocationProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AllocationService(JdbcTemplate jdbc, AccountRepository accounts, AccountService accountService,
                             AccountMemberRepository members, BookingService bookings,
                             BookingRepository bookingRepository, EarmarkRepository earmarks,
                             GoalTracker goals, NotificationService notifications, BankingService banking,
                             AppProperties appProps, AllocationProperties props, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.accountService = accountService;
        this.members = members;
        this.bookings = bookings;
        this.bookingRepository = bookingRepository;
        this.earmarks = earmarks;
        this.goals = goals;
        this.notifications = notifications;
        this.banking = banking;
        this.appProps = appProps;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    // ================================================================== interne Daten

    record Rule(Long id, Long userId, Long targetAccountId, String sourceType, Long sourceAccountId,
                Long sourceExternalId, long amountCents, AllocationInterval interval, LocalDate startDate,
                LocalDate endDate, LocalDate nextDate, String status, String statusReason, Instant createdAt) {}

    record Run(Long id, Long ruleId, Long userId, Long targetAccountId, String sourceType, Long sourceExternalId,
               String kind, LocalDate dueDate, long amountCents, String status, int attempts, LocalDate nextAttempt,
               String reason, Long bookingId, boolean manual, Instant updatedAt) {}

    record External(Long id, Long userId, Long connectionId, String name, String bankName, String ibanMasked,
                    String currency, Long balanceCents, String connectionStatus, Instant lastSyncAt,
                    String lastSyncStatus, String provider) {}

    /** Grund, warum ein Vorgang nicht ausgeführt werden kann; retryable = an den Folgetagen erneut versuchen. */
    record Failure(String code, String reason, boolean retryable) {}

    // ================================================================== Quellen

    /** Mögliche Finanzierungsquellen: das eigene Girokonto und verbundene Bankkonten. */
    public List<SourceDto> sources(Long userId) {
        List<SourceDto> list = new ArrayList<>();
        Account main = accountService.mainAccount(userId);
        long balance = bookings.balanceOf(main.getId());
        list.add(new SourceDto(PROJEKTBANK, main.getId(), main.getName(), PROJEKTBANK_NAME, null, balance, 0, balance,
                null, main.getStatus() == AccountStatus.ACTIVE, null, true, PROJEKTBANK_EXPLANATION, true, false, null));
        Set<String> sandbox = sandboxProviders();
        for (External e : externals("a.user_id = ? AND c.status IN ('ACTIVE', 'EXPIRED', 'ERROR')", userId)) {
            long marked = earmarks.forSource(e.id());
            Failure f = sourceProblem(e);
            Long available = e.balanceCents() == null ? null : e.balanceCents() - marked;
            boolean warning = e.balanceCents() != null && marked > e.balanceCents();
            list.add(new SourceDto(EXTERNAL, e.id(), e.name(), e.bankName(), e.ibanMasked(), e.balanceCents(), marked,
                    available == null ? null : Math.max(0, available), e.lastSyncAt(), f == null,
                    f == null ? null : f.reason(), false, earmarkText(e.bankName()), sandbox.contains(e.provider()),
                    warning, warning ? "Deine Vormerkungen (" + Money.format(marked) + ") sind höher als der Kontostand ("
                            + Money.format(e.balanceCents()) + ")." : null));
        }
        return list;
    }

    static String earmarkText(String bank) {
        return "Nur Vormerkung: Das Geld bleibt bei " + bank + ". Es wird keine Zahlung ausgelöst.";
    }

    /** Grundsätzliche Hindernisse einer Quelle (ohne Deckung). */
    private Failure sourceProblem(External e) {
        if (e == null) {
            return new Failure("SOURCE_GONE", "Das Bankkonto ist nicht mehr verbunden.", false);
        }
        if ("EXPIRED".equals(e.connectionStatus())) {
            return new Failure("CONSENT_EXPIRED",
                    "Die Freigabe für " + e.bankName() + " ist abgelaufen. Bitte die Bank neu verbinden.", true);
        }
        if (!"ACTIVE".equals(e.connectionStatus())) {
            return new Failure("SOURCE_INACTIVE", "Die Verbindung zu " + e.bankName() + " ist nicht aktiv.",
                    !"REVOKED".equals(e.connectionStatus()) && !"REJECTED".equals(e.connectionStatus()));
        }
        if (!"EUR".equals(e.currency())) {
            return new Failure("NOT_EUR", "Nur Konten in Euro können vorgemerkt werden.", false);
        }
        if (e.balanceCents() == null) {
            return new Failure("BALANCE_UNKNOWN", "Der Kontostand bei " + e.bankName() + " ist unbekannt.", true);
        }
        return null;
    }

    /** Alles für eine Vormerkung: Verbindung, aktueller Kontostand, freie Deckung (Aufrufer hält die Sperre). */
    private Failure coverage(External e, long amountCents) {
        Failure f = sourceProblem(e);
        if (f != null) {
            return f;
        }
        Instant now = Instant.now(clock);
        boolean fresh = e.lastSyncAt() != null && "OK".equals(e.lastSyncStatus())
                && now.isBefore(e.lastSyncAt().plus(maxAge()));
        if (!fresh) {
            return new Failure("BALANCE_STALE", "Der Kontostand bei " + e.bankName()
                    + " ist nicht aktuell. Bitte das Bankkonto aktualisieren.", true);
        }
        long free = e.balanceCents() - earmarks.forSource(e.id());
        if (free < amountCents) {
            return new Failure("NO_COVERAGE", "Nicht genug frei bei " + e.bankName() + ": verfügbar "
                    + Money.format(Math.max(0, free)) + ".", true);
        }
        return null;
    }

    private Duration maxAge() {
        return Duration.ofHours(props.balanceMaxAgeHours());
    }

    // ================================================================== Regeln verwalten

    public List<RuleDto> rules(Long userId) {
        return jdbc.query("SELECT * FROM allocation_rule WHERE user_id = ? AND status <> 'ENDED' ORDER BY id",
                this::mapRule, userId).stream().map(this::ruleDto).toList();
    }

    public RuleDto create(Long userId, CreateRuleRequest r) {
        Account target = requireDepositTarget(userId, r.targetAccountId());
        String type = r.sourceType() == null || r.sourceType().isBlank() ? PROJEKTBANK : r.sourceType().trim();
        Long sourceAccountId = null;
        Long sourceExternalId = null;
        String sourceName;
        if (type.equals(PROJEKTBANK)) {
            Account main = accountService.mainAccount(userId);
            if (r.sourceId() != null && !r.sourceId().equals(main.getId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", "Finanzierungsquelle nicht gefunden.");
            }
            sourceAccountId = main.getId();
            sourceName = main.getName();
        } else if (type.equals(EXTERNAL)) {
            External e = r.sourceId() == null ? null : external(r.sourceId());
            if (e == null || !e.userId().equals(userId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", "Finanzierungsquelle nicht gefunden.");
            }
            Failure f = sourceProblem(e);
            if (f != null && !f.code().equals("BALANCE_UNKNOWN")) {
                throw new ApiException(HttpStatus.CONFLICT, "SOURCE_NOT_USABLE", f.reason());
            }
            sourceExternalId = e.id();
            sourceName = e.bankName();
        } else {
            throw bad("INVALID_SOURCE", "Unbekannte Finanzierungsquelle.");
        }
        validateAmount(r.amountCents());
        AllocationInterval interval = AllocationInterval.parse(r.interval());
        if (interval == null) {
            throw bad("INVALID_INTERVAL", "Rhythmus: wöchentlich, alle 2 Wochen, monatlich oder vierteljährlich.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = r.startDate() == null ? today : r.startDate();
        if (start.isBefore(today)) {
            throw bad("START_IN_PAST", "Das Startdatum darf nicht in der Vergangenheit liegen.");
        }
        if (start.isAfter(today.plusYears(1))) {
            throw bad("START_TOO_FAR", "Das Startdatum darf höchstens ein Jahr in der Zukunft liegen.");
        }
        if (r.endDate() != null && r.endDate().isBefore(start)) {
            throw bad("INVALID_END_DATE", "Das Enddatum liegt vor dem Startdatum.");
        }
        Integer open = jdbc.queryForObject("SELECT count(*) FROM allocation_rule WHERE user_id = ? AND status <> 'ENDED'",
                Integer.class, userId);
        if (open != null && open >= props.maxRulesPerUser()) {
            throw bad("TOO_MANY_RULES", "Höchstens " + props.maxRulesPerUser() + " Sparregeln möglich.");
        }
        Timestamp now = Timestamp.from(Instant.now(clock));
        Long id = jdbc.queryForObject("""
                INSERT INTO allocation_rule (user_id, target_account_id, source_type, source_account_id,
                       source_external_id, amount_cents, interval_unit, start_date, end_date, next_date, status,
                       created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?) RETURNING id
                """, Long.class, userId, target.getId(), type, sourceAccountId, sourceExternalId, r.amountCents(),
                interval.name(), Date.valueOf(start), r.endDate() == null ? null : Date.valueOf(r.endDate()),
                Date.valueOf(start), now, now);
        notifications.send(userId, NotificationType.SAVING_RATE, "Sparregel eingerichtet",
                "Für „" + target.getName() + "“ werden " + Money.format(r.amountCents()) + " (" + interval.label()
                        + ") von " + sourceName + " zugewiesen – erstmals am " + Dates.de(start) + ".", null);
        if (!start.isAfter(today)) {
            runNow(id, today);   // der Tageslauf für heute ist schon vorbei: gleich ausführen
        }
        return ruleDto(requireRule(userId, id));
    }

    public RuleDto update(Long userId, Long id, UpdateRuleRequest r) {
        Rule rule = requireOpenRule(userId, id);
        long amount = rule.amountCents();
        if (r.amountCents() != null) {
            validateAmount(r.amountCents());
            amount = r.amountCents();
        }
        AllocationInterval interval = rule.interval();
        LocalDate start = rule.startDate();
        if (r.interval() != null && !r.interval().isBlank()) {
            AllocationInterval i = AllocationInterval.parse(r.interval());
            if (i == null) {
                throw bad("INVALID_INTERVAL", "Rhythmus: wöchentlich, alle 2 Wochen, monatlich oder vierteljährlich.");
            }
            if (i != interval) {
                interval = i;
                // neuer Rhythmus ab dem nächsten Termin
                start = rule.nextDate() != null ? rule.nextDate() : LocalDate.now(clock).plusDays(1);
            }
        }
        LocalDate next = rule.status().equals("ACTIVE") ? interval.onOrAfter(start,
                rule.nextDate() != null ? rule.nextDate() : LocalDate.now(clock).plusDays(1)) : rule.nextDate();
        LocalDate end = rule.endDate();
        if (Boolean.TRUE.equals(r.removeEndDate())) {
            end = null;
        } else if (r.endDate() != null) {
            LocalDate earliest = next != null ? next : LocalDate.now(clock);
            if (r.endDate().isBefore(earliest)) {
                throw bad("INVALID_END_DATE", "Das Enddatum liegt vor der nächsten Ausführung am " + Dates.de(earliest) + ".");
            }
            end = r.endDate();
        }
        jdbc.update("""
                UPDATE allocation_rule SET amount_cents = ?, interval_unit = ?, start_date = ?, end_date = ?,
                       next_date = ?, updated_at = ? WHERE id = ?
                """, amount, interval.name(), Date.valueOf(start), end == null ? null : Date.valueOf(end),
                next == null ? null : Date.valueOf(next), Timestamp.from(Instant.now(clock)), id);
        return ruleDto(requireRule(userId, id));
    }

    public RuleDto pause(Long userId, Long id) {
        Rule rule = requireOpenRule(userId, id);
        if (rule.status().equals("ACTIVE")) {
            jdbc.update("UPDATE allocation_rule SET status = 'PAUSED', status_reason = NULL, updated_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now(clock)), id);
            cancelOpenRuns(id, "Sparregel pausiert");
        }
        return ruleDto(requireRule(userId, id));
    }

    public RuleDto resume(Long userId, Long id) {
        Rule rule = requireOpenRule(userId, id);
        if (rule.status().equals("PAUSED")) {
            requireDepositTarget(userId, rule.targetAccountId());
            if (rule.sourceType().equals(EXTERNAL)) {
                Failure f = sourceProblem(rule.sourceExternalId() == null ? null : external(rule.sourceExternalId()));
                if (f != null && !f.retryable()) {
                    throw new ApiException(HttpStatus.CONFLICT, "SOURCE_NOT_USABLE", f.reason());
                }
            }
            LocalDate today = LocalDate.now(clock);
            LocalDate next = rule.interval().onOrAfter(rule.startDate(), today);   // Pausiertes wird nicht nachgeholt
            boolean ended = rule.endDate() != null && next.isAfter(rule.endDate());
            jdbc.update("""
                    UPDATE allocation_rule SET status = ?, next_date = ?, status_reason = ?, updated_at = ? WHERE id = ?
                    """, ended ? "ENDED" : "ACTIVE", ended ? null : Date.valueOf(next),
                    ended ? "Enddatum erreicht" : null, Timestamp.from(Instant.now(clock)), id);
            if (!ended && next.equals(today)) {
                runNow(id, today);
            }
        }
        return ruleDto(requireRule(userId, id));
    }

    /** Beenden: keine weiteren Ausführungen; Vormerkungen bleiben, bis sie aufgehoben werden. */
    public void end(Long userId, Long id) {
        requireOpenRule(userId, id);
        jdbc.update("""
                UPDATE allocation_rule SET status = 'ENDED', next_date = NULL, status_reason = 'Beendet', updated_at = ?
                 WHERE id = ?
                """, Timestamp.from(Instant.now(clock)), id);
        cancelOpenRuns(id, "Sparregel beendet");
    }

    private void cancelOpenRuns(Long ruleId, String reason) {
        jdbc.update("""
                UPDATE allocation_run SET status = 'CANCELLED', next_attempt = NULL, reason = ?, updated_at = ?
                 WHERE rule_id = ? AND status IN ('PENDING', 'RETRYING')
                """, reason, Timestamp.from(Instant.now(clock)), ruleId);
    }

    // ================================================================== Tageslauf

    @Override
    public int order() {
        return 35; // nach Daueraufträgen (25) und Kreditraten (30): erst die Pflichten, dann das Sparen
    }

    @Override
    public void runFor(LocalDate date) {
        for (Long id : jdbc.queryForList("""
                SELECT id FROM allocation_rule WHERE status = 'ACTIVE' AND next_date <= ? ORDER BY next_date, id
                """, Long.class, Date.valueOf(date))) {
            try {
                schedule(id, date);
            } catch (RuntimeException e) {
                log.error("Sparregel {}: Termine anlegen fehlgeschlagen", id, e);
            }
        }
        for (Long runId : jdbc.queryForList("""
                SELECT id FROM allocation_run WHERE status IN ('PENDING', 'RETRYING') AND next_attempt <= ?
                 ORDER BY due_date, id
                """, Long.class, Date.valueOf(date))) {
            attempt(runId, date, false);
        }
    }

    /** Vorgänge für alle fälligen Termine einer Regel anlegen und den nächsten Termin setzen (in einer Transaktion). */
    void schedule(Long ruleId, LocalDate date) {
        tx.executeWithoutResult(s -> {
            Rule r = jdbc.queryForObject("SELECT * FROM allocation_rule WHERE id = ? FOR UPDATE", this::mapRule, ruleId);
            LocalDate next = r.nextDate();
            String status = r.status();
            String reason = r.statusReason();
            int guard = 0;
            while (status.equals("ACTIVE") && next != null && !next.isAfter(date) && guard++ < MAX_CATCH_UP) {
                jdbc.update("""
                        INSERT INTO allocation_run (rule_id, user_id, target_account_id, source_type, source_external_id,
                               kind, due_date, amount_cents, status, attempts, next_attempt, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                        ON CONFLICT (rule_id, due_date) DO NOTHING
                        """, r.id(), r.userId(), r.targetAccountId(), r.sourceType(), r.sourceExternalId(),
                        r.sourceType().equals(PROJEKTBANK) ? "TRANSFER" : "EARMARK", Date.valueOf(next), r.amountCents(),
                        Date.valueOf(date), Timestamp.from(Instant.now(clock)), Timestamp.from(Instant.now(clock)));
                next = r.interval().after(r.startDate(), next);
                if (r.endDate() != null && next.isAfter(r.endDate())) {
                    next = null;
                    status = "ENDED";
                    reason = "Enddatum erreicht";
                }
            }
            jdbc.update("UPDATE allocation_rule SET next_date = ?, status = ?, status_reason = ?, updated_at = ? WHERE id = ?",
                    next == null ? null : Date.valueOf(next), status, reason, Timestamp.from(Instant.now(clock)), r.id());
        });
    }

    /** Regel heute gleich ausführen (nach dem Anlegen oder Fortsetzen). */
    private void runNow(Long ruleId, LocalDate today) {
        schedule(ruleId, today);
        for (Long runId : jdbc.queryForList("""
                SELECT id FROM allocation_run WHERE rule_id = ? AND status = 'PENDING' AND next_attempt <= ? ORDER BY id
                """, Long.class, ruleId, Date.valueOf(today))) {
            attempt(runId, today, false);
        }
    }

    /**
     * Einen Vorgang versuchen. Unter Sperre des Vorgangs: ist er schon erledigt, passiert nichts (keine doppelte
     * Abbuchung). Fehler → Wiederholung am Folgetag, nach dem letzten Versuch "fehlgeschlagen".
     */
    Run attempt(Long runId, LocalDate today, boolean manualRetry) {
        Run before = run(runId);
        if (before == null) {
            return null;
        }
        if (before.sourceType().equals(EXTERNAL) && before.sourceExternalId() != null) {
            External e = external(before.sourceExternalId());
            if (e != null) {
                banking.refreshIfOlder(e.connectionId(), maxAge());   // außerhalb der Transaktion (Netzwerk)
            }
        }
        try {
            tx.executeWithoutResult(s -> execute(runId, today, manualRetry));
        } catch (RuntimeException ex) {
            log.error("Sparregel-Vorgang {} fehlgeschlagen", runId, ex);
            tx.executeWithoutResult(s -> {
                Run run = lockRun(runId);
                if (run != null && !run.status().equals("COMPLETED")) {
                    fail(run, today, new Failure("TECHNICAL", "Technischer Fehler – neuer Versuch folgt.", true),
                            manualRetry);
                }
            });
        }
        return run(runId);
    }

    private void execute(Long runId, LocalDate today, boolean manualRetry) {
        Run run = lockRun(runId);
        if (run == null || run.status().equals("COMPLETED") || run.status().equals("CANCELLED")) {
            return;
        }
        Account target = accounts.findById(run.targetAccountId()).orElse(null);
        Failure f = targetProblem(target, run.userId());
        Long bookingId = null;
        External ext = null;
        String key = run.ruleId() == null ? "alloc-run-" + run.id() : "alloc-" + run.ruleId() + "-" + run.dueDate();
        Long existing = run.sourceType().equals(PROJEKTBANK)
                ? bookingRepository.findByIdempotencyKey(key).map(b -> b.getId()).orElse(null) : null;
        if (f == null && existing != null) {
            bookingId = existing;   // schon gebucht (z. B. Abbruch direkt nach der Buchung): nie ein zweites Mal
        } else if (f == null && run.sourceType().equals(PROJEKTBANK)) {
            Account main = accountService.mainAccount(run.userId());
            accounts.findByIdForUpdate(main.getId()).orElseThrow();
            long balance = bookings.balanceOf(main.getId());
            if (main.getStatus() != AccountStatus.ACTIVE) {
                f = new Failure("SOURCE_INACTIVE", "Das Girokonto ist nicht aktiv.", false);
            } else if (balance < run.amountCents()) {
                f = new Failure("INSUFFICIENT_FUNDS", "Nicht genug Guthaben auf „" + main.getName() + "“. Verfügbar: "
                        + Money.format(balance) + ".", true);
            } else {
                bookingId = bookings.post(BookingType.INTERNAL, "Sparregel: " + main.getName() + " → " + target.getName(),
                        key, run.userId(), List.of(
                                new BookingService.Line(main.getId(), -run.amountCents()),
                                new BookingService.Line(target.getId(), run.amountCents()))).getId();
            }
        } else if (f == null) {
            if (run.sourceExternalId() != null) {
                // Vormerkungen einer Quelle nacheinander prüfen (Deckung nie doppelt vergeben)
                jdbc.queryForList("SELECT id FROM external_account WHERE id = ? FOR UPDATE", Long.class,
                        run.sourceExternalId());
                ext = external(run.sourceExternalId());
            }
            f = coverage(ext, run.amountCents());
        }
        if (f != null) {
            fail(run, today, f, manualRetry);
            return;
        }
        jdbc.update("""
                UPDATE allocation_run SET status = 'COMPLETED', attempts = attempts + 1, next_attempt = NULL,
                       reason = NULL, booking_id = ?, updated_at = ? WHERE id = ?
                """, bookingId, Timestamp.from(Instant.now(clock)), run.id());
        goals.check(target);
        if (existing != null) {
            return;   // Benachrichtigung gab es schon bei der ersten Buchung
        }
        String body = run.kind().equals("TRANSFER")
                ? Money.format(run.amountCents()) + " vom Girokonto in „" + target.getName() + "“ umgebucht."
                : Money.format(run.amountCents()) + " bei " + ext.bankName() + " für „" + target.getName()
                        + "“ vorgemerkt – das Geld bleibt dort.";
        notifications.send(run.userId(), NotificationType.SAVING_RATE,
                run.kind().equals("TRANSFER") ? "Automatisch gespart" : "Automatisch vorgemerkt", body, bookingId);
        informOthers(target, run.userId(), run.amountCents(), run.kind(), bookingId);
    }

    private void fail(Run run, LocalDate today, Failure f, boolean manualRetry) {
        int attempts = run.attempts() + 1;
        String status;
        LocalDate nextAttempt;
        if (manualRetry && !run.status().equals("PENDING")) {
            status = run.status();   // manueller Versuch ändert den Ablauf nicht
            nextAttempt = run.nextAttempt();
        } else if (f.retryable() && attempts < props.maxAttempts()) {
            status = "RETRYING";
            nextAttempt = today.plusDays(1);
        } else {
            status = "FAILED";
            nextAttempt = null;
        }
        jdbc.update("""
                UPDATE allocation_run SET status = ?, attempts = ?, next_attempt = ?, reason = ?, updated_at = ?
                 WHERE id = ?
                """, status, attempts, nextAttempt == null ? null : Date.valueOf(nextAttempt), cut(f.reason()),
                Timestamp.from(Instant.now(clock)), run.id());
        if (manualRetry) {
            return;
        }
        String name = accounts.findById(run.targetAccountId()).map(Account::getName).orElse("?");
        if (status.equals("RETRYING")) {
            notifications.send(run.userId(), NotificationType.SAVING_RATE, "Sparregel: neuer Versuch",
                    Money.format(run.amountCents()) + " für „" + name + "“: " + f.reason() + " Neuer Versuch am "
                            + Dates.de(nextAttempt) + ".", null);
        } else {
            notifications.send(run.userId(), NotificationType.SAVING_RATE, "Sparregel fehlgeschlagen",
                    Money.format(run.amountCents()) + " für „" + name + "“ wurde nicht ausgeführt: " + f.reason(), null);
        }
        if (!f.retryable() && run.ruleId() != null) {
            int paused = jdbc.update("""
                    UPDATE allocation_rule SET status = 'PAUSED', status_reason = ?, updated_at = ?
                     WHERE id = ? AND status = 'ACTIVE'
                    """, cut(f.reason()), Timestamp.from(Instant.now(clock)), run.ruleId());
            if (paused > 0) {
                notifications.send(run.userId(), NotificationType.SAVING_RATE, "Sparregel pausiert",
                        "Die Sparregel für „" + name + "“ wurde pausiert: " + f.reason(), null);
            }
        }
    }

    /** Darf der Nutzer (noch) in das digitale Konto einzahlen? */
    private Failure targetProblem(Account target, Long userId) {
        if (target == null || target.getType() != AccountType.SUB || target.getStatus() != AccountStatus.ACTIVE) {
            return new Failure("TARGET_CLOSED", "Das digitale Konto ist aufgelöst.", false);
        }
        String role = target.getOwnerId().equals(userId) ? "OWNER" : members.role(target.getId(), userId);
        if (role == null || role.equals("VIEW")) {
            return new Failure("NO_PERMISSION", "Keine Berechtigung mehr für „" + target.getName() + "“.", false);
        }
        return null;
    }

    /** Bei geteilten digitalen Konten die anderen Beteiligten informieren (wie bei einer Einzahlung von Hand). */
    private void informOthers(Account target, Long actorId, long amountCents, String kind, Long bookingId) {
        if (members.count(target.getId()) == 0) {
            return;
        }
        String actor = accountService.ownerName(actorId);
        List<Long> all = new ArrayList<>(members.members(target.getId()));
        all.add(0, target.getOwnerId());
        for (Long other : all) {
            if (!other.equals(actorId)) {
                notifications.send(other, NotificationType.INTERNAL, "Geteiltes digitales Konto", kind.equals("TRANSFER")
                        ? actor + " hat " + Money.format(amountCents) + " eingezahlt in „" + target.getName() + "“"
                        : actor + " hat " + Money.format(amountCents) + " für „" + target.getName() + "“ vorgemerkt",
                        bookingId);
            }
        }
    }

    // ================================================================== Vorgänge, Vormerkungen

    public List<RunDto> runs(Long userId, Long accountId, int limit) {
        int n = Math.max(1, Math.min(limit, 100));
        List<Run> list = accountId == null
                ? jdbc.query("SELECT * FROM allocation_run WHERE user_id = ? ORDER BY due_date DESC, id DESC LIMIT ?",
                        this::mapRun, userId, n)
                : jdbc.query("""
                        SELECT * FROM allocation_run WHERE user_id = ? AND target_account_id = ?
                         ORDER BY due_date DESC, id DESC LIMIT ?
                        """, this::mapRun, userId, accountId, n);
        return list.stream().map(this::runDto).toList();
    }

    /** Fehlgeschlagenen oder wartenden Vorgang jetzt erneut versuchen (höchstens 30 Tage nach Fälligkeit). */
    public RunDto retry(Long userId, Long runId) {
        Run run = run(runId);
        if (run == null || !run.userId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Vorgang nicht gefunden.");
        }
        LocalDate today = LocalDate.now(clock);
        if (!(run.status().equals("RETRYING") || run.status().equals("FAILED"))
                || run.dueDate().isBefore(today.minusDays(30))) {
            throw new ApiException(HttpStatus.CONFLICT, "RUN_NOT_RETRYABLE", "Dieser Vorgang kann nicht wiederholt werden.");
        }
        return runDto(attempt(runId, today, true));
    }

    /** Zusätzliche Vormerkung aus einem verbundenen Bankkonto (Deckung wird vorher geprüft). */
    public RunDto earmark(Long userId, EarmarkRequest r) {
        Account target = requireDepositTarget(userId, r.targetAccountId());
        External e = external(r.externalAccountId());
        if (e == null || !e.userId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", "Finanzierungsquelle nicht gefunden.");
        }
        validateAmount(r.amountCents());
        banking.refreshIfOlder(e.connectionId(), maxAge());
        LocalDate today = LocalDate.now(clock);
        Long id = tx.execute(s -> {
            jdbc.queryForList("SELECT id FROM external_account WHERE id = ? FOR UPDATE", Long.class, e.id());
            External fresh = external(e.id());
            Failure f = coverage(fresh, r.amountCents());
            if (f != null) {
                throw new ApiException(HttpStatus.CONFLICT, f.code(), f.reason());
            }
            Long runId = insertManual(userId, target.getId(), e.id(), "EARMARK", r.amountCents(), today);
            Account managed = accounts.findById(target.getId()).orElseThrow();
            goals.check(managed);
            notifications.send(userId, NotificationType.SAVING_RATE, "Vormerkung erfasst",
                    Money.format(r.amountCents()) + " bei " + fresh.bankName() + " für „" + target.getName()
                            + "“ vorgemerkt – das Geld bleibt dort.", null);
            informOthers(managed, userId, r.amountCents(), "EARMARK", null);
            return runId;
        });
        return runDto(run(id));
    }

    /** Vormerkung aufheben (ganz oder teilweise) – z. B. wenn das Geld bei der Bank anders gebraucht wird. */
    public RunDto release(Long userId, ReleaseRequest r) {
        Account target = accountService.requireAccess(r.targetAccountId(), userId);
        if (target.getType() != AccountType.SUB) {
            throw notDigital();
        }
        External e = external(r.externalAccountId());
        if (e == null || !e.userId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", "Finanzierungsquelle nicht gefunden.");
        }
        LocalDate today = LocalDate.now(clock);
        Long id = tx.execute(s -> {
            jdbc.queryForList("SELECT id FROM external_account WHERE id = ? FOR UPDATE", Long.class, e.id());
            long current = earmarks.forSourceAndAccount(e.id(), target.getId());
            if (current <= 0) {
                throw new ApiException(HttpStatus.CONFLICT, "NOTHING_EARMARKED",
                        "Von diesem Bankkonto ist für „" + target.getName() + "“ nichts vorgemerkt.");
            }
            long amount = r.amountCents() == null ? current : r.amountCents();
            if (amount > current) {
                throw bad("RELEASE_TOO_HIGH", "Höchstens " + Money.format(current) + " sind vorgemerkt.");
            }
            Long runId = insertManual(userId, target.getId(), e.id(), "RELEASE", amount, today);
            goals.check(accounts.findById(target.getId()).orElseThrow());
            return runId;
        });
        return runDto(run(id));
    }

    private Long insertManual(Long userId, Long targetId, Long externalId, String kind, long amount, LocalDate today) {
        Timestamp now = Timestamp.from(Instant.now(clock));
        return jdbc.queryForObject("""
                INSERT INTO allocation_run (rule_id, user_id, target_account_id, source_type, source_external_id, kind,
                       due_date, amount_cents, status, attempts, manual, created_at, updated_at)
                VALUES (NULL, ?, ?, 'EXTERNAL', ?, ?, ?, ?, 'COMPLETED', 1, TRUE, ?, ?) RETURNING id
                """, Long.class, userId, targetId, externalId, kind, Date.valueOf(today), amount, now, now);
    }

    // ================================================================== Übersicht je digitalem Konto

    public AccountAllocations forAccount(Long userId, Long accountId) {
        Account a = accountService.requireAccess(accountId, userId);
        if (a.getType() != AccountType.SUB) {
            throw notDigital();
        }
        String role = a.getOwnerId().equals(userId) ? "OWNER" : members.role(accountId, userId);
        boolean canAllocate = a.getStatus() == AccountStatus.ACTIVE && role != null && !role.equals("VIEW");
        long balance = bookings.balanceOf(accountId);
        long earmarked = earmarks.forAccount(accountId);

        List<EarmarkDto> marks = new ArrayList<>();
        jdbc.query("""
                SELECT r.source_external_id, ea.user_id, ea.name, c.institution_name, c.status,
                       SUM(CASE r.kind WHEN 'EARMARK' THEN r.amount_cents ELSE -r.amount_cents END) AS s
                  FROM allocation_run r
                  JOIN external_account ea ON ea.id = r.source_external_id
                  JOIN bank_connection c ON c.id = ea.connection_id
                 WHERE r.target_account_id = ? AND r.status = 'COMPLETED' AND r.kind IN ('EARMARK', 'RELEASE')
                 GROUP BY r.source_external_id, ea.user_id, ea.name, c.institution_name, c.status
                HAVING SUM(CASE r.kind WHEN 'EARMARK' THEN r.amount_cents ELSE -r.amount_cents END) > 0
                 ORDER BY r.source_external_id
                """, rs -> {
            boolean own = rs.getLong("user_id") == userId;
            // fremde Vormerkungen nur mit Namen der Person, ohne Bank
            marks.add(new EarmarkDto(own ? rs.getLong("source_external_id") : null,
                    own ? rs.getString("name") : accountService.ownerName(rs.getLong("user_id")),
                    own ? rs.getString("institution_name") : null, rs.getLong("s"), own,
                    "ACTIVE".equals(rs.getString("status"))));
        }, accountId);

        List<Rule> all = jdbc.query("SELECT * FROM allocation_rule WHERE target_account_id = ? AND status <> 'ENDED'"
                + " ORDER BY id", this::mapRule, accountId);
        List<RuleDto> own = all.stream().filter(r -> r.userId().equals(userId)).map(this::ruleDto).toList();
        int others = (int) all.stream().filter(r -> !r.userId().equals(userId)).count();

        LocalDate today = LocalDate.now(clock);
        List<UpcomingDto> upcoming = new ArrayList<>();
        for (Rule r : all) {
            if (!r.userId().equals(userId) || !r.status().equals("ACTIVE") || r.nextDate() == null) {
                continue;
            }
            LocalDate d = r.nextDate();
            for (int i = 0; i < 5 && d != null; i++) {
                upcoming.add(new UpcomingDto(d, r.amountCents(), r.id(), sourceName(r), r.sourceType().equals(PROJEKTBANK)));
                d = r.interval().after(r.startDate(), d);
                if (r.endDate() != null && d.isAfter(r.endDate())) {
                    d = null;
                }
            }
        }
        upcoming.sort(Comparator.comparing(UpcomingDto::date).thenComparing(UpcomingDto::ruleId));
        List<UpcomingDto> next5 = upcoming.subList(0, Math.min(5, upcoming.size()));

        LocalDate projected = null;
        String projection = null;
        Long target = a.getTargetCents();
        if (target != null) {
            long saved = balance + earmarked;
            if (saved >= target) {
                projection = "Ziel erreicht.";
            } else {
                projected = projectGoal(all, target - saved, today);
                if (projected == null) {
                    projection = "Mit einer Sparregel siehst du, wann du dein Ziel voraussichtlich erreichst.";
                } else if (a.getTargetDate() != null && projected.isAfter(a.getTargetDate())) {
                    projection = "Voraussichtlich erreicht am " + Dates.de(projected) + " – nach deinem Zieldatum "
                            + Dates.de(a.getTargetDate()) + ".";
                } else {
                    projection = "Voraussichtlich erreicht am " + Dates.de(projected)
                            + ", wenn alle Sparregeln wie geplant laufen.";
                }
            }
        }
        return new AccountAllocations(accountId, a.getName(), canAllocate,
                marks.stream().anyMatch(EarmarkDto::own), balance, earmarked, marks, own, others,
                runs(userId, accountId, 20), List.copyOf(next5), projected, projection, EARMARK_EXPLANATION);
    }

    /** Datum, an dem die Summe der künftigen Termine aller aktiven Regeln den Rest deckt (höchstens 30 Jahre). */
    static LocalDate projectGoal(List<Rule> rules, long remaining, LocalDate today) {
        record Cursor(Rule rule, LocalDate date) {}
        List<Cursor> cursors = new ArrayList<>();
        for (Rule r : rules) {
            if (r.status().equals("ACTIVE") && r.nextDate() != null) {
                cursors.add(new Cursor(r, r.nextDate()));
            }
        }
        LocalDate limit = today.plusYears(30);
        long sum = 0;
        for (int guard = 0; guard < 5000 && !cursors.isEmpty(); guard++) {
            Cursor c = cursors.stream().min(Comparator.comparing(Cursor::date)).orElseThrow();
            if (c.date().isAfter(limit)) {
                return null;
            }
            sum += c.rule().amountCents();
            if (sum >= remaining) {
                return c.date();
            }
            cursors.remove(c);
            LocalDate next = c.rule().interval().after(c.rule().startDate(), c.date());
            if (c.rule().endDate() == null || !next.isAfter(c.rule().endDate())) {
                cursors.add(new Cursor(c.rule(), next));
            }
        }
        return null;
    }

    // ================================================================== DTOs und Laden

    private RuleDto ruleDto(Rule r) {
        String targetName = accounts.findById(r.targetAccountId()).map(Account::getName).orElse("?");
        String bank;
        String explanation;
        if (r.sourceType().equals(PROJEKTBANK)) {
            bank = PROJEKTBANK_NAME;
            explanation = PROJEKTBANK_EXPLANATION;
        } else {
            External e = r.sourceExternalId() == null ? null : external(r.sourceExternalId());
            bank = e == null ? null : e.bankName();
            explanation = e == null ? "Das Bankkonto ist nicht mehr verbunden." : earmarkText(e.bankName());
        }
        List<Run> last = jdbc.query("SELECT * FROM allocation_run WHERE rule_id = ? ORDER BY due_date DESC, id DESC LIMIT 1",
                this::mapRun, r.id());
        return new RuleDto(r.id(), r.targetAccountId(), targetName, r.sourceType(),
                r.sourceType().equals(PROJEKTBANK) ? r.sourceAccountId() : r.sourceExternalId(), sourceName(r), bank,
                r.sourceType().equals(PROJEKTBANK), explanation, r.amountCents(), r.interval().name(),
                r.interval().label(), r.startDate(), r.endDate(), r.status().equals("ACTIVE") ? r.nextDate() : null,
                r.status(), ruleStatusLabel(r.status()), r.statusReason(), last.isEmpty() ? null : runDto(last.get(0)),
                r.createdAt());
    }

    private String sourceName(Rule r) {
        if (r.sourceType().equals(PROJEKTBANK)) {
            return accounts.findById(r.sourceAccountId()).map(Account::getName).orElse("Girokonto");
        }
        External e = r.sourceExternalId() == null ? null : external(r.sourceExternalId());
        return e == null ? null : e.name();
    }

    private RunDto runDto(Run run) {
        String targetName = accounts.findById(run.targetAccountId()).map(Account::getName).orElse("?");
        String source;
        if (run.sourceType().equals(PROJEKTBANK)) {
            source = "Girokonto";
        } else {
            External e = run.sourceExternalId() == null ? null : external(run.sourceExternalId());
            source = e == null ? null : e.bankName();
        }
        return new RunDto(run.id(), run.ruleId(), run.targetAccountId(), targetName, run.sourceType(), source, run.kind(),
                kindLabel(run.kind()), run.dueDate(), run.amountCents(), run.status(), runStatusLabel(run),
                run.attempts(), run.nextAttempt(), run.reason(), run.bookingId(), run.manual(), run.updatedAt());
    }

    static String ruleStatusLabel(String status) {
        return switch (status) {
            case "ACTIVE" -> "Aktiv";
            case "PAUSED" -> "Pausiert";
            default -> "Beendet";
        };
    }

    static String kindLabel(String kind) {
        return switch (kind) {
            case "TRANSFER" -> "Umbuchung";
            case "EARMARK" -> "Vormerkung";
            default -> "Vormerkung aufgehoben";
        };
    }

    static String runStatusLabel(Run run) {
        return switch (run.status()) {
            case "COMPLETED" -> switch (run.kind()) {
                case "TRANSFER" -> "Umgebucht";
                case "EARMARK" -> "Vormerkung erfasst";
                default -> "Aufgehoben";
            };
            case "PENDING" -> "Ausstehend";
            case "RETRYING" -> run.nextAttempt() == null ? "Neuer Versuch geplant"
                    : "Neuer Versuch am " + Dates.de(run.nextAttempt());
            case "CANCELLED" -> "Abgebrochen";
            default -> "Fehlgeschlagen";
        };
    }

    private Account requireDepositTarget(Long userId, Long accountId) {
        Account a = accountService.requireAccess(accountId, userId);
        if (a.getType() != AccountType.SUB || a.getStatus() != AccountStatus.ACTIVE) {
            throw notDigital();
        }
        String role = a.getOwnerId().equals(userId) ? "OWNER" : members.role(accountId, userId);
        if (role == null || role.equals("VIEW")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ROLE_VIEW_ONLY", "Du darfst „" + a.getName() + "“ nur ansehen.");
        }
        return a;
    }

    private Rule requireRule(Long userId, Long id) {
        List<Rule> r = jdbc.query("SELECT * FROM allocation_rule WHERE id = ? AND user_id = ?", this::mapRule, id, userId);
        if (r.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Sparregel nicht gefunden.");
        }
        return r.get(0);
    }

    private Rule requireOpenRule(Long userId, Long id) {
        Rule r = requireRule(userId, id);
        if (r.status().equals("ENDED")) {
            throw new ApiException(HttpStatus.CONFLICT, "RULE_ENDED", "Die Sparregel ist beendet.");
        }
        return r;
    }

    private void validateAmount(Long cents) {
        if (cents == null || cents <= 0) {
            throw bad("INVALID_AMOUNT", "Der Betrag muss größer als 0 sein.");
        }
        if (cents > appProps.transfer().maxAmountCents()) {
            throw bad("AMOUNT_TOO_HIGH", "Höchstens " + Money.format(appProps.transfer().maxAmountCents()) + ".");
        }
    }

    private Set<String> sandboxProviders() {
        return banking.providers().stream().filter(p -> p.sandbox()).map(p -> p.id()).collect(Collectors.toSet());
    }

    private Run lockRun(Long id) {
        List<Run> r = jdbc.query("SELECT * FROM allocation_run WHERE id = ? FOR UPDATE", this::mapRun, id);
        return r.isEmpty() ? null : r.get(0);
    }

    private Run run(Long id) {
        List<Run> r = jdbc.query("SELECT * FROM allocation_run WHERE id = ?", this::mapRun, id);
        return r.isEmpty() ? null : r.get(0);
    }

    private External external(Long id) {
        List<External> r = externals("a.id = ?", id);
        return r.isEmpty() ? null : r.get(0);
    }

    private List<External> externals(String where, Object arg) {
        return jdbc.query("""
                SELECT a.id, a.user_id, a.connection_id, a.name, a.iban_masked, a.currency, a.balance_cents,
                       c.institution_name, c.status, c.last_sync_at, c.last_sync_status, c.provider
                  FROM external_account a JOIN bank_connection c ON c.id = a.connection_id
                 WHERE\s""" + where + " ORDER BY c.id, a.id", (rs, i) -> {
            long b = rs.getLong("balance_cents");
            Long balance = rs.wasNull() ? null : b;
            Timestamp sync = rs.getTimestamp("last_sync_at");
            return new External(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("connection_id"), rs.getString("name"),
                    rs.getString("institution_name"), rs.getString("iban_masked"), rs.getString("currency"), balance,
                    rs.getString("status"), sync == null ? null : sync.toInstant(), rs.getString("last_sync_status"),
                    rs.getString("provider"));
        }, arg);
    }

    private Rule mapRule(ResultSet rs, int i) throws SQLException {
        Date end = rs.getDate("end_date");
        Date next = rs.getDate("next_date");
        long src = rs.getLong("source_account_id");
        Long sourceAccount = rs.wasNull() ? null : src;
        long ext = rs.getLong("source_external_id");
        Long sourceExternal = rs.wasNull() ? null : ext;
        return new Rule(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("target_account_id"),
                rs.getString("source_type"), sourceAccount, sourceExternal, rs.getLong("amount_cents"),
                AllocationInterval.valueOf(rs.getString("interval_unit")), rs.getDate("start_date").toLocalDate(),
                end == null ? null : end.toLocalDate(), next == null ? null : next.toLocalDate(), rs.getString("status"),
                rs.getString("status_reason"), rs.getTimestamp("created_at").toInstant());
    }

    private Run mapRun(ResultSet rs, int i) throws SQLException {
        long rule = rs.getLong("rule_id");
        Long ruleId = rs.wasNull() ? null : rule;
        long ext = rs.getLong("source_external_id");
        Long sourceExternal = rs.wasNull() ? null : ext;
        long booking = rs.getLong("booking_id");
        Long bookingId = rs.wasNull() ? null : booking;
        Date next = rs.getDate("next_attempt");
        return new Run(rs.getLong("id"), ruleId, rs.getLong("user_id"), rs.getLong("target_account_id"),
                rs.getString("source_type"), sourceExternal, rs.getString("kind"), rs.getDate("due_date").toLocalDate(),
                rs.getLong("amount_cents"), rs.getString("status"), rs.getInt("attempts"),
                next == null ? null : next.toLocalDate(), rs.getString("reason"), bookingId, rs.getBoolean("manual"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static String cut(String s) {
        return s == null || s.length() <= 200 ? s : s.substring(0, 200);
    }

    private static ApiException notDigital() {
        return new ApiException(HttpStatus.BAD_REQUEST, "NOT_DIGITAL", "Sparregeln gibt es nur für digitale Konten.");
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
