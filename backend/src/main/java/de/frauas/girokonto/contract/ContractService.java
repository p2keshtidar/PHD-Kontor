package de.frauas.girokonto.contract;

import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.simulation.DailyJob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vertragsverwaltung: Kosten, Mindestlaufzeit, Verlängerung, Kündigungsfrist und Erinnerung vor Fristende.
 * Die App merkt sich Verträge nur – gekündigt wird beim Anbieter, nicht über die App.
 */
@Service
public class ContractService implements DailyJob {

    public static final int MAX_CONTRACTS = 100;
    public static final String NOTE = "Die App merkt sich Verträge und Fristen nur. Kündigen musst du selbst beim Anbieter.";
    private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    static final Map<String, String> KINDS = Map.of(
            "STROM_GAS", "Strom & Gas", "HANDY_INTERNET", "Handy & Internet", "STREAMING", "Streaming & Abos",
            "VERSICHERUNG", "Versicherung", "MIETE", "Miete", "FITNESS", "Fitness & Verein",
            "MOBILITAET", "Mobilität", "SONSTIGES", "Sonstiges");

    public record ContractInput(String name, String provider, String kind, Long costCents, String billing,
                                LocalDate startDate, Integer minTermMonths, Integer renewalMonths, Integer noticeValue,
                                String noticeUnit, Integer reminderDays, String note) {}

    public record ContractDto(Long id, String name, String provider, String kind, String kindLabel, long costCents,
                              String billing, String billingLabel, long monthlyCents, long yearlyCents,
                              LocalDate startDate, int minTermMonths, int renewalMonths, int noticeValue,
                              String noticeUnit, String noticeLabel, int reminderDays, String status,
                              String statusLabel, LocalDate nextEndDate, LocalDate cancelByDate,
                              Long daysUntilCancelBy, LocalDate endsOn, String note, int documentCount,
                              String hintText) {}

    public record Overview(List<ContractDto> contracts, long monthlyTotalCents, long yearlyTotalCents, int dueSoon,
                           String note) {}

    /** Nächstes mögliches Vertragsende und der letzte Tag, an dem dafür gekündigt werden kann. */
    public record Term(LocalDate end, LocalDate cancelBy) {}

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final Clock clock;

    private final AuditLog audit;

    public ContractService(JdbcTemplate jdbc, NotificationService notifications, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- Laufzeiten

    static LocalDate cancelBy(LocalDate end, int value, String unit) {
        return switch (unit) {
            case "DAYS" -> end.minusDays(value);
            case "WEEKS" -> end.minusWeeks(value);
            default -> end.minusMonths(value);
        };
    }

    /** Erstes Ende nach Mindestlaufzeit, danach in Schritten der Verlängerung – bis die Frist noch erreichbar ist. */
    static Term term(LocalDate start, int minTerm, int renewal, int noticeValue, String noticeUnit, LocalDate today) {
        long months = Math.max(minTerm, renewal);
        LocalDate end = start.plusMonths(months).minusDays(1);
        LocalDate by = cancelBy(end, noticeValue, noticeUnit);
        int guard = 0;
        while (by.isBefore(today) && guard++ < 2000) {
            months += renewal;
            end = start.plusMonths(months).minusDays(1);
            by = cancelBy(end, noticeValue, noticeUnit);
        }
        return new Term(end, by);
    }

    static long monthly(long cost, String billing) {
        return switch (billing) {
            case "QUARTERLY" -> Math.round(cost / 3.0);
            case "YEARLY" -> Math.round(cost / 12.0);
            default -> cost;
        };
    }

    static long yearly(long cost, String billing) {
        return switch (billing) {
            case "QUARTERLY" -> cost * 4;
            case "YEARLY" -> cost;
            default -> cost * 12;
        };
    }

    // ---------------------------------------------------------------- Lesen

    @Transactional(readOnly = true)
    public Overview overview(Long userId) {
        List<ContractDto> list = jdbc.query("SELECT c.*, (SELECT count(*) FROM document d WHERE d.contract_id = c.id) docs "
                + "FROM contract c WHERE c.user_id = ? ORDER BY c.name, c.id", (rs, n) -> map(rs), userId);
        long monthly = 0;
        long yearly = 0;
        int dueSoon = 0;
        for (ContractDto c : list) {
            if (!"ENDED".equals(c.status())) {
                monthly += c.monthlyCents();
                yearly += c.yearlyCents();
            }
            if ("ACTIVE".equals(c.status()) && c.daysUntilCancelBy() != null && c.daysUntilCancelBy() <= 30) {
                dueSoon++;
            }
        }
        List<ContractDto> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> {
            LocalDate x = a.cancelByDate() == null ? LocalDate.MAX : a.cancelByDate();
            LocalDate y = b.cancelByDate() == null ? LocalDate.MAX : b.cancelByDate();
            return x.compareTo(y);
        });
        return new Overview(sorted, monthly, yearly, dueSoon, NOTE);
    }

    @Transactional(readOnly = true)
    public ContractDto get(Long userId, Long id) {
        List<ContractDto> l = jdbc.query("SELECT c.*, (SELECT count(*) FROM document d WHERE d.contract_id = c.id) docs "
                + "FROM contract c WHERE c.id = ? AND c.user_id = ?", (rs, n) -> map(rs), id, userId);
        if (l.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "Vertrag nicht gefunden.");
        }
        return l.get(0);
    }

    private ContractDto map(ResultSet rs) throws SQLException {
        LocalDate today = LocalDate.now(clock);
        String billing = rs.getString("billing");
        long cost = rs.getLong("cost_cents");
        LocalDate start = rs.getObject("start_date", LocalDate.class);
        int minTerm = rs.getInt("min_term_months");
        int renewal = rs.getInt("renewal_months");
        int nv = rs.getInt("notice_value");
        String nu = rs.getString("notice_unit");
        LocalDate endsOn = rs.getObject("ends_on", LocalDate.class);
        String status = rs.getString("status");
        LocalDate nextEnd = null;
        LocalDate by = null;
        Long days = null;
        String hint;
        if ("CANCELLED".equals(status)) {
            if (endsOn != null && endsOn.isBefore(today)) {
                status = "ENDED";
                hint = "Beendet am " + DE.format(endsOn) + ".";
            } else {
                hint = "Gekündigt zum " + DE.format(endsOn) + ".";
            }
            nextEnd = endsOn;
        } else {
            Term t = term(start, minTerm, renewal, nv, nu, today);
            nextEnd = t.end();
            by = t.cancelBy();
            days = ChronoUnit.DAYS.between(today, by);
            hint = days == 0 ? "Heute ist der letzte Tag zum Kündigen (Vertragsende " + DE.format(nextEnd) + ")."
                    : "Kündigen bis " + DE.format(by) + " (Vertragsende " + DE.format(nextEnd) + "), sonst Verlängerung um "
                    + months(renewal) + ".";
        }
        String kind = rs.getString("kind");
        return new ContractDto(rs.getLong("id"), rs.getString("name"), rs.getString("provider"), kind,
                KINDS.getOrDefault(kind, kind), cost, billing, billingLabel(billing), monthly(cost, billing),
                yearly(cost, billing), start, minTerm, renewal, nv, nu, noticeLabel(nv, nu), rs.getInt("reminder_days"),
                status, statusLabel(status), nextEnd, by, days, endsOn, rs.getString("note"), rs.getInt("docs"), hint);
    }

    private static String billingLabel(String b) {
        return switch (b) {
            case "QUARTERLY" -> "vierteljährlich";
            case "YEARLY" -> "jährlich";
            default -> "monatlich";
        };
    }

    private static String statusLabel(String s) {
        return switch (s) {
            case "CANCELLED" -> "Gekündigt";
            case "ENDED" -> "Beendet";
            default -> "Läuft";
        };
    }

    static String noticeLabel(int v, String unit) {
        if (v == 0) {
            return "jederzeit";
        }
        return switch (unit) {
            case "DAYS" -> v == 1 ? "1 Tag" : v + " Tage";
            case "WEEKS" -> v == 1 ? "1 Woche" : v + " Wochen";
            default -> months(v);
        };
    }

    private static String months(int v) {
        return v == 1 ? "1 Monat" : v + " Monate";
    }

    // ---------------------------------------------------------------- Schreiben

    @Transactional
    public ContractDto create(Long userId, ContractInput in) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM contract WHERE user_id = ?", Integer.class, userId);
        if (count != null && count >= MAX_CONTRACTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_CONTRACTS", "Höchstens " + MAX_CONTRACTS + " Verträge.");
        }
        Valid v = validate(in);
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(c -> {
            var ps = c.prepareStatement("INSERT INTO contract (user_id, name, provider, kind, cost_cents, billing, "
                    + "start_date, min_term_months, renewal_months, notice_value, notice_unit, reminder_days, note, "
                    + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setLong(1, userId);
            fill(ps, v, 2);
            ps.setTimestamp(14, Timestamp.from(Instant.now(clock)));
            return ps;
        }, kh);
        return get(userId, kh.getKey().longValue());
    }

    @Transactional
    public ContractDto update(Long userId, Long id, ContractInput in) {
        get(userId, id);
        Valid v = validate(in);
        jdbc.update(c -> {
            var ps = c.prepareStatement("UPDATE contract SET name = ?, provider = ?, kind = ?, cost_cents = ?, billing = ?, "
                    + "start_date = ?, min_term_months = ?, renewal_months = ?, notice_value = ?, notice_unit = ?, "
                    + "reminder_days = ?, note = ?, reminded_for = NULL WHERE id = ? AND user_id = ?");
            fill(ps, v, 1);
            ps.setLong(13, id);
            ps.setLong(14, userId);
            return ps;
        });
        return get(userId, id);
    }

    /** Kündigung vermerken: endet zum nächsten möglichen Termin (oder zum angegebenen späteren Datum). */
    @Transactional
    public ContractDto markCancelled(Long userId, Long id, LocalDate effective) {
        ContractDto c = get(userId, id);
        if (!"ACTIVE".equals(c.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTRACT_NOT_ACTIVE", "Der Vertrag ist schon gekündigt.");
        }
        LocalDate end = effective == null ? c.nextEndDate() : effective;
        if (end.isBefore(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Das Vertragsende darf nicht in der Vergangenheit liegen.");
        }
        jdbc.update("UPDATE contract SET status = 'CANCELLED', ends_on = ? WHERE id = ? AND user_id = ?", end, id, userId);
        audit.record(userId, "CONTRACT_CANCEL_NOTED", "contract:" + id, null);
        return get(userId, id);
    }

    @Transactional
    public ContractDto reactivate(Long userId, Long id) {
        get(userId, id);
        jdbc.update("UPDATE contract SET status = 'ACTIVE', ends_on = NULL, reminded_for = NULL WHERE id = ? AND user_id = ?",
                id, userId);
        return get(userId, id);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        get(userId, id);
        jdbc.update("DELETE FROM contract WHERE id = ? AND user_id = ?", id, userId);
    }

    private record Valid(String name, String provider, String kind, long cost, String billing, LocalDate start,
                         int minTerm, int renewal, int noticeValue, String noticeUnit, int reminder, String note) {}

    private Valid validate(ContractInput in) {
        String name = in.name() == null ? "" : in.name().trim();
        if (name.isEmpty() || name.length() > 80) {
            throw bad("INVALID_NAME", "Bitte einen Namen (bis 80 Zeichen) angeben.");
        }
        String provider = in.provider() == null || in.provider().isBlank() ? null : in.provider().trim();
        if (provider != null && provider.length() > 80) {
            throw bad("INVALID_PROVIDER", "Der Anbieter darf höchstens 80 Zeichen haben.");
        }
        String kind = in.kind() == null ? "SONSTIGES" : in.kind();
        if (!KINDS.containsKey(kind)) {
            throw bad("INVALID_KIND", "Unbekannte Vertragsart.");
        }
        long cost = in.costCents() == null ? -1 : in.costCents();
        if (cost < 0 || cost > 10_000_000) {
            throw bad("INVALID_AMOUNT", "Die Kosten müssen zwischen 0 € und 100.000 € liegen.");
        }
        String billing = in.billing() == null ? "MONTHLY" : in.billing();
        if (!List.of("MONTHLY", "QUARTERLY", "YEARLY").contains(billing)) {
            throw bad("INVALID_BILLING", "Unbekannter Zahlungsrhythmus.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = in.startDate() == null ? today : in.startDate();
        if (start.isBefore(today.minusYears(30)) || start.isAfter(today.plusYears(1))) {
            throw bad("INVALID_DATE", "Der Vertragsbeginn muss in den letzten 30 Jahren oder im nächsten Jahr liegen.");
        }
        int minTerm = in.minTermMonths() == null ? 0 : in.minTermMonths();
        int renewal = in.renewalMonths() == null ? 1 : in.renewalMonths();
        int nv = in.noticeValue() == null ? 1 : in.noticeValue();
        String nu = in.noticeUnit() == null ? "MONTHS" : in.noticeUnit();
        int reminder = in.reminderDays() == null ? 14 : in.reminderDays();
        if (minTerm < 0 || minTerm > 120 || renewal < 1 || renewal > 60) {
            throw bad("INVALID_TERM", "Laufzeit: Mindestlaufzeit 0 bis 120 Monate, Verlängerung 1 bis 60 Monate.");
        }
        if (nv < 0 || nv > 24 || !List.of("DAYS", "WEEKS", "MONTHS").contains(nu)) {
            throw bad("INVALID_NOTICE", "Ungültige Kündigungsfrist.");
        }
        if (reminder < 0 || reminder > 90) {
            throw bad("INVALID_REMINDER", "Die Erinnerung muss 0 bis 90 Tage vorher sein.");
        }
        String note = in.note() == null || in.note().isBlank() ? null : in.note().trim();
        if (note != null && note.length() > 300) {
            throw bad("INVALID_NOTE", "Die Notiz darf höchstens 300 Zeichen haben.");
        }
        return new Valid(name, provider, kind, cost, billing, start, minTerm, renewal, nv, nu, reminder, note);
    }

    private static void fill(java.sql.PreparedStatement ps, Valid v, int i) throws SQLException {
        ps.setString(i, v.name());
        ps.setString(i + 1, v.provider());
        ps.setString(i + 2, v.kind());
        ps.setLong(i + 3, v.cost());
        ps.setString(i + 4, v.billing());
        ps.setObject(i + 5, v.start());
        ps.setInt(i + 6, v.minTerm());
        ps.setInt(i + 7, v.renewal());
        ps.setInt(i + 8, v.noticeValue());
        ps.setString(i + 9, v.noticeUnit());
        ps.setInt(i + 10, v.reminder());
        ps.setString(i + 11, v.note());
    }

    private static ApiException bad(String code, String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, msg);
    }

    // ---------------------------------------------------------------- Erinnerung (Tageslauf)

    @Override
    public int order() {
        return 65;
    }

    /** Einmal je Vertragsperiode erinnern, sobald die Kündigungsfrist in reminder_days Tagen endet. */
    @Override
    @Transactional
    public void runFor(LocalDate date) {
        jdbc.query("SELECT id, user_id, name, start_date, min_term_months, renewal_months, notice_value, notice_unit, "
                + "reminder_days, reminded_for FROM contract WHERE status = 'ACTIVE' AND reminder_days > 0", rs -> {
            Term t = term(rs.getObject("start_date", LocalDate.class), rs.getInt("min_term_months"),
                    rs.getInt("renewal_months"), rs.getInt("notice_value"), rs.getString("notice_unit"), date);
            LocalDate remindFrom = t.cancelBy().minusDays(rs.getInt("reminder_days"));
            LocalDate done = rs.getObject("reminded_for", LocalDate.class);
            if (!date.isBefore(remindFrom) && !date.isAfter(t.cancelBy()) && !t.end().equals(done)) {
                long id = rs.getLong("id");
                jdbc.update("UPDATE contract SET reminded_for = ? WHERE id = ?", t.end(), id);
                notifications.send(rs.getLong("user_id"), NotificationType.INFO, "Kündigungsfrist",
                        "„" + rs.getString("name") + "“ kann noch bis " + DE.format(t.cancelBy())
                                + " gekündigt werden (Vertragsende " + DE.format(t.end()) + ").", null);
            }
        });
    }
}
