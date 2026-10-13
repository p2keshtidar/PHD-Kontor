package de.frauas.girokonto.entry;

import de.frauas.girokonto.account.Account;
import de.frauas.girokonto.account.AccountRepository;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Dates;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.entry.EntryDtos.CategoryOption;
import de.frauas.girokonto.entry.EntryDtos.CreateRequest;
import de.frauas.girokonto.entry.EntryDtos.CreateResult;
import de.frauas.girokonto.entry.EntryDtos.EntryDto;
import de.frauas.girokonto.entry.EntryDtos.Summary;
import de.frauas.girokonto.entry.EntryDtos.UpdateRequest;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.CategoryService;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.simulation.DailyJob;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Eigene Einnahmen und Ausgaben (Gehalt, Miete, Urlaub …).
 *
 * <p>Jeder fällige Termin wird als echte (simulierte) Buchung auf dem Girokonto ausgeführt – gegen das
 * System-Konto EXTERN, also Geld von bzw. nach außerhalb der Bank. Dadurch zählen die Einträge überall mit:
 * Kontostand, Umsätze, Finanzüberblick, Sparplan-Prüfung.
 *
 * <ul>
 *   <li>Termine in der Vergangenheit (bis 12 Monate) werden beim Anlegen sofort nachgebucht.</li>
 *   <li>Künftige Termine bucht der Tageslauf (läuft vor den Daueraufträgen, damit z. B. das Gehalt
 *       schon da ist, wenn die Miete abgeht).</li>
 *   <li>Eine Ausgabe wird nie ins Minus gebucht: Beim Anlegen gibt es dann eine Fehlermeldung,
 *       im Tageslauf eine Benachrichtigung.</li>
 *   <li>Der Idempotenz-Schlüssel "entry-ID-Datum" verhindert doppelte Buchungen.</li>
 * </ul>
 */
@Service
public class EntryService implements DailyJob {

    private static final Logger log = LoggerFactory.getLogger(EntryService.class);
    static final Set<Integer> INTERVALS = Set.of(0, 1, 3, 6, 12);
    static final long MAX_AMOUNT_CENTS = 5_000_000;   // 50.000 € je Eintrag
    static final int MAX_MONTHS_BACK = 12;
    static final int MAX_MONTHS_AHEAD = 24;
    static final String EXTERN = "EXTERN";
    private static final Set<Entry.Status> VISIBLE = EnumSet.of(Entry.Status.ACTIVE, Entry.Status.PAUSED, Entry.Status.DONE);

    private final EntryRepository entries;
    private final AccountService accountService;
    private final AccountRepository accounts;
    private final BookingService bookingService;
    private final CategoryService categoryService;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final Clock clock;

    public EntryService(EntryRepository entries, AccountService accountService, AccountRepository accounts,
                        BookingService bookingService, CategoryService categoryService,
                        NotificationService notifications, TransactionTemplate tx, Clock clock) {
        this.entries = entries;
        this.accountService = accountService;
        this.accounts = accounts;
        this.bookingService = bookingService;
        this.categoryService = categoryService;
        this.notifications = notifications;
        this.tx = tx;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Verwalten

    @Transactional(readOnly = true)
    public Summary list(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<EntryDto> list = entries.findByUserIdAndStatusInOrderByIdAsc(userId, VISIBLE).stream()
                .map(e -> toDto(e, today)).toList();
        long income = 0;
        long expense = 0;
        for (EntryDto e : list) {
            if (e.status() == Entry.Status.ACTIVE) {
                if (e.monthlyCents() > 0) {
                    income += e.monthlyCents();
                } else {
                    expense -= e.monthlyCents();
                }
            }
        }
        return new Summary(income, expense, income - expense, list);
    }

    @Transactional
    public CreateResult create(Long userId, CreateRequest r) {
        LocalDate today = LocalDate.now(clock);
        int interval = r.intervalMonths() == null ? 0 : r.intervalMonths();
        if (!INTERVALS.contains(interval)) {
            throw bad("INVALID_INTERVAL", "Häufigkeit: einmalig, monatlich, vierteljährlich, halbjährlich oder jährlich.");
        }
        validateAmount(r.amountCents());
        String title = r.title().trim();
        Category category = resolveCategory(r.kind(), r.category(), title, r.amountCents());
        LocalDate date = r.date();
        if (date.isBefore(today.minusMonths(MAX_MONTHS_BACK))) {
            throw bad("INVALID_DATE", "Das Datum darf höchstens " + MAX_MONTHS_BACK + " Monate zurückliegen.");
        }
        if (date.isAfter(today.plusMonths(MAX_MONTHS_AHEAD))) {
            throw bad("INVALID_DATE", "Das Datum darf höchstens " + MAX_MONTHS_AHEAD + " Monate in der Zukunft liegen.");
        }
        if (interval > 0 && date.getDayOfMonth() > 28) {
            throw bad("INVALID_DAY", "Regelmäßige Einträge: bitte einen Tag zwischen 1. und 28. wählen "
                    + "(nicht jeder Monat hat 29, 30 oder 31 Tage).");
        }
        LocalDate end = interval > 0 ? r.endDate() : null;
        if (end != null && end.isBefore(date)) {
            throw bad("INVALID_END_DATE", "Das Enddatum liegt vor dem ersten Termin am " + Dates.de(date) + ".");
        }
        Entry e = entries.save(new Entry(userId, r.kind(), title, category.name(), r.amountCents(), interval, date, end,
                Instant.now(clock)));

        // vergangene und heutige Termine sofort buchen (der heutige Tageslauf ist schon vorbei)
        int booked = 0;
        long bookedCents = 0;
        for (LocalDate d = date; !d.isAfter(today); d = next(e, d)) {
            if (e.isDueOn(d)) {
                book(e, d, today);
                booked++;
                bookedCents += e.getAmountCents();
            }
            if (e.isOnce()) {
                break;
            }
        }
        finishIfOver(e, today);
        return new CreateResult(toDto(e, today), booked, e.getKind() == Entry.Kind.INCOME ? bookedCents : -bookedCents);
    }

    @Transactional
    public EntryDto update(Long userId, Long id, UpdateRequest r) {
        Entry e = requireOpen(userId, id);
        if (r.amountCents() != null) {
            validateAmount(r.amountCents());
            e.setAmountCents(r.amountCents());
        }
        if (r.title() != null) {
            if (r.title().isBlank()) {
                throw bad("INVALID_TITLE", "Bitte eine Bezeichnung eingeben.");
            }
            e.setTitle(r.title().trim());
        }
        if (r.category() != null) {
            e.setCategory(resolveCategory(e.getKind(), r.category(), e.getTitle(), e.getAmountCents()).name());
        }
        LocalDate today = LocalDate.now(clock);
        if (Boolean.TRUE.equals(r.removeEndDate())) {
            e.setEndDate(null);
        } else if (r.endDate() != null) {
            if (e.isOnce()) {
                throw bad("INVALID_END_DATE", "Einmalige Einträge haben kein Enddatum.");
            }
            if (r.endDate().isBefore(today)) {
                throw bad("INVALID_END_DATE", "Das Enddatum darf nicht in der Vergangenheit liegen.");
            }
            e.setEndDate(r.endDate());
        }
        if (r.paused() != null && e.getStatus() != Entry.Status.DONE) {
            e.setStatus(r.paused() ? Entry.Status.PAUSED : Entry.Status.ACTIVE);
        }
        return toDto(e, today);
    }

    /** Entfernt den Eintrag. Bereits gebuchte Beträge bleiben in den Umsätzen (wie bei einer echten Bank). */
    @Transactional
    public void delete(Long userId, Long id) {
        Entry e = entries.findByIdAndUserId(id, userId).orElseThrow(EntryService::notFound);
        if (e.getStatus() == Entry.Status.DELETED) {
            throw notFound();
        }
        e.setStatus(Entry.Status.DELETED);
    }

    public List<CategoryOption> categories() {
        List<CategoryOption> list = new ArrayList<>();
        for (Category c : Category.values()) {
            if (c.kind() == Category.Kind.INCOME) {
                list.add(new CategoryOption(c.name(), c.label(), "INCOME"));
            } else if (c.kind() == Category.Kind.FIXED || c.kind() == Category.Kind.VARIABLE) {
                list.add(new CategoryOption(c.name(), c.label(), "EXPENSE"));
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ Tageslauf

    @Override
    public int order() {
        return 22; // nach Sparraten (20), vor Daueraufträgen (25): erst Gehalt, dann Miete
    }

    @Override
    public void runFor(LocalDate date) {
        for (Entry e : entries.findByStatus(Entry.Status.ACTIVE)) {
            if (e.isDueOn(date) && (e.getLastBookedDate() == null || e.getLastBookedDate().isBefore(date))) {
                run(e.getId(), date);
            }
        }
    }

    void run(Long id, LocalDate date) {
        String failure = null;
        try {
            tx.executeWithoutResult(s -> {
                Entry e = entries.findById(id).orElseThrow();
                book(e, date, date);
                finishIfOver(e, date);
                notifications.send(e.getUserId(), NotificationType.INFO,
                        e.getKind() == Entry.Kind.INCOME ? "Einnahme gebucht" : "Ausgabe gebucht",
                        signed(e) + " – " + e.getTitle(), null);
            });
        } catch (ApiException ex) {
            failure = ex.getMessage();
        } catch (RuntimeException ex) {
            log.error("Eintrag {} am {} nicht gebucht", id, date, ex);
            failure = "Technischer Fehler.";
        }
        if (failure != null) {
            String reason = failure;
            tx.executeWithoutResult(s -> {
                Entry e = entries.findById(id).orElseThrow();
                e.setLastBookedDate(date); // kein zweiter Versuch für diesen Termin
                finishIfOver(e, date);
                notifications.send(e.getUserId(), NotificationType.INFO, "Ausgabe nicht gebucht",
                        Money.format(e.getAmountCents()) + " für „" + e.getTitle() + "“: " + reason, null);
            });
        }
    }

    // ------------------------------------------------------------------ Buchen

    /** Bucht einen Termin auf das Girokonto. Ausgaben nur, wenn das Guthaben reicht. */
    private void book(Entry e, LocalDate date, LocalDate today) {
        Account main = accountService.mainAccount(e.getUserId());
        accounts.findByIdForUpdate(main.getId()); // parallele Buchungen nacheinander
        long cents = e.getAmountCents();
        boolean income = e.getKind() == Entry.Kind.INCOME;
        if (!income) {
            long balance = bookingService.balanceOf(main.getId());
            if (balance < cents) {
                throw bad("INSUFFICIENT_FUNDS", "Nicht genug Guthaben auf dem Girokonto für „" + e.getTitle() + "“ am "
                        + Dates.de(date) + " (verfügbar: " + Money.format(balance) + ").");
            }
        }
        Long extern = accountService.systemAccount(EXTERN).getId();
        Category category = Category.valueOf(e.getCategory());
        Instant at = date.equals(today) ? Instant.now(clock)
                : date.atTime(LocalTime.of(9, 0)).atZone(clock.getZone()).toInstant();
        bookingService.post(income ? BookingType.ENTRY_INCOME : BookingType.ENTRY_EXPENSE, e.getTitle(),
                "entry-" + e.getId() + "-" + date, e.getUserId(), List.of(
                        new BookingService.Line(main.getId(), income ? cents : -cents, category),
                        new BookingService.Line(extern, income ? -cents : cents)), at);
        e.setLastBookedDate(date);
        e.setBookedCount(e.getBookedCount() + 1);
    }

    private static void finishIfOver(Entry e, LocalDate today) {
        if (e.getStatus() != Entry.Status.DELETED && e.nextAfter(today) == null) {
            e.setStatus(Entry.Status.DONE);
        }
    }

    private static LocalDate next(Entry e, LocalDate d) {
        return e.isOnce() ? d.plusDays(1) : d.plusMonths(e.getIntervalMonths());
    }

    // ------------------------------------------------------------------ Hilfen

    /** Kategorie prüfen; ohne Angabe passend zum Titel vorschlagen ("Miete" → Wohnen, "Gehalt" → Gehalt). */
    Category resolveCategory(Entry.Kind kind, String key, String title, long cents) {
        Category c;
        if (key == null || key.isBlank()) {
            String t = title.toLowerCase(Locale.ROOT);
            if (kind == Entry.Kind.INCOME) {
                c = t.contains("gehalt") || t.contains("lohn") ? Category.GEHALT : Category.SONSTIGE_EINNAHMEN;
            } else {
                c = categoryService.categorize(BookingType.ENTRY_EXPENSE, title, -Math.abs(cents));
            }
        } else {
            c = Arrays.stream(Category.values()).filter(x -> x.name().equals(key)).findFirst()
                    .orElseThrow(() -> bad("INVALID_CATEGORY", "Unbekannte Kategorie."));
        }
        boolean ok = kind == Entry.Kind.INCOME ? c.kind() == Category.Kind.INCOME
                : c.kind() == Category.Kind.FIXED || c.kind() == Category.Kind.VARIABLE;
        if (!ok) {
            throw bad("INVALID_CATEGORY", "Die Kategorie „" + c.label() + "“ passt nicht zu "
                    + (kind == Entry.Kind.INCOME ? "einer Einnahme." : "einer Ausgabe."));
        }
        return c;
    }

    static String intervalLabel(int months) {
        return switch (months) {
            case 0 -> "einmalig";
            case 1 -> "monatlich";
            case 3 -> "vierteljährlich";
            case 6 -> "halbjährlich";
            case 12 -> "jährlich";
            default -> "alle " + months + " Monate";
        };
    }

    private static void validateAmount(long cents) {
        if (cents <= 0) {
            throw bad("INVALID_AMOUNT", "Der Betrag muss größer als 0 sein.");
        }
        if (cents > MAX_AMOUNT_CENTS) {
            throw bad("AMOUNT_TOO_HIGH", "Höchstens " + Money.format(MAX_AMOUNT_CENTS) + " je Eintrag.");
        }
    }

    private static String signed(Entry e) {
        return (e.getKind() == Entry.Kind.INCOME ? "+" : "−") + Money.format(e.getAmountCents());
    }

    private Entry requireOpen(Long userId, Long id) {
        Entry e = entries.findByIdAndUserId(id, userId).orElseThrow(EntryService::notFound);
        if (e.getStatus() == Entry.Status.DELETED) {
            throw notFound();
        }
        return e;
    }

    private EntryDto toDto(Entry e, LocalDate today) {
        Category c = Category.valueOf(e.getCategory());
        long monthly = e.isOnce() ? 0 : Math.round((double) e.getAmountCents() / e.getIntervalMonths());
        if (e.getKind() == Entry.Kind.EXPENSE) {
            monthly = -monthly;
        }
        LocalDate nextDate = e.getStatus() == Entry.Status.ACTIVE ? e.nextAfter(today) : null;
        return new EntryDto(e.getId(), e.getKind(), e.getTitle(), c.name(), c.label(), e.getAmountCents(),
                e.getIntervalMonths(), intervalLabel(e.getIntervalMonths()), e.getStartDate(), e.getEndDate(),
                e.getStatus(), nextDate, e.getLastBookedDate(), e.getBookedCount(), monthly);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND", "Eintrag nicht gefunden.");
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
