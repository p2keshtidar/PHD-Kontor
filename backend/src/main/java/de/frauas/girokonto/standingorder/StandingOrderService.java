package de.frauas.girokonto.standingorder;

import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.simulation.DailyJob;
import de.frauas.girokonto.standingorder.StandingOrderDtos.CreateRequest;
import de.frauas.girokonto.standingorder.StandingOrderDtos.StandingOrderDto;
import de.frauas.girokonto.standingorder.StandingOrderDtos.UpdateRequest;
import de.frauas.girokonto.transfer.Recipient;
import de.frauas.girokonto.transfer.RecipientResolver;
import de.frauas.girokonto.transfer.TransferRequest;
import de.frauas.girokonto.transfer.TransferService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * /K030/ Daueraufträge. Ausgeführt wird über den {@link TransferService}, damit dieselben Regeln gelten
 * wie bei einer normalen Überweisung (Deckung, Tageslimit, keine Selbstüberweisung) und der
 * Idempotenz-Schlüssel "so-ID-Monat" doppelte Ausführungen verhindert.
 */
@Service
public class StandingOrderService implements DailyJob {

    private static final Logger log = LoggerFactory.getLogger(StandingOrderService.class);
    private static final Set<Integer> INTERVALS = Set.of(1, 3, 6, 12);
    public static final String DEFAULT_REFERENCE = "Dauerauftrag";

    private final StandingOrderRepository orders;
    private final RecipientResolver recipients;
    private final TransferService transfers;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final Clock clock;

    public StandingOrderService(StandingOrderRepository orders, RecipientResolver recipients, TransferService transfers,
                                NotificationService notifications, TransactionTemplate tx, AppProperties props,
                                Clock clock) {
        this.orders = orders;
        this.recipients = recipients;
        this.transfers = transfers;
        this.notifications = notifications;
        this.tx = tx;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Verwalten

    @Transactional(readOnly = true)
    public List<StandingOrderDto> list(Long userId) {
        return orders.findByUserIdAndStatusNotOrderByIdAsc(userId, StandingOrder.Status.ENDED).stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public StandingOrderDto create(Long userId, CreateRequest r) {
        Recipient recipient = recipients.resolve(r.recipient());
        if (recipient.user().getId().equals(userId)) {
            throw bad("SELF_TRANSFER", "Daueraufträge an dich selbst sind nicht möglich. Nutze dafür einen Sparplan.");
        }
        validateAmount(r.amountCents());
        int interval = r.intervalMonths() == null ? 1 : r.intervalMonths();
        if (!INTERVALS.contains(interval)) {
            throw bad("INVALID_INTERVAL", "Intervall: monatlich, vierteljährlich, halbjährlich oder jährlich.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate first = firstExecution(today, r.executionDay());
        if (r.endDate() != null && r.endDate().isBefore(first)) {
            throw bad("INVALID_END_DATE", "Das Enddatum liegt vor der ersten Ausführung am " + first + ".");
        }
        StandingOrder o = orders.save(new StandingOrder(userId, recipient.account().getIban(), recipient.fullName(),
                r.amountCents(), reference(r.reference()), r.executionDay(), interval, first, r.endDate(),
                Instant.now(clock)));
        notifications.send(userId, NotificationType.INFO, "Dauerauftrag eingerichtet",
                Money.format(o.getAmountCents()) + " an " + o.getRecipientName() + ", " + intervalLabel(interval)
                        + " am " + o.getExecutionDay() + ". – erstmals am " + first + ".", null);
        return toDto(o);
    }

    @Transactional
    public StandingOrderDto update(Long userId, Long id, UpdateRequest r) {
        StandingOrder o = requireOpen(userId, id);
        if (r.amountCents() != null) {
            validateAmount(r.amountCents());
            o.setAmountCents(r.amountCents());
        }
        if (r.reference() != null) {
            o.setReference(reference(r.reference()));
        }
        if (r.executionDay() != null && r.executionDay() != o.getExecutionDay()) {
            o.setExecutionDay(r.executionDay());
            LocalDate today = LocalDate.now(clock);
            if (o.getStartDate().isAfter(today) || o.getLastExecutedMonth() == null) {
                o.setStartDate(firstExecution(today, r.executionDay()));
            } else {
                // Intervall bleibt am Startmonat ausgerichtet, nur der Tag ändert sich
                o.setStartDate(YearMonth.from(o.getStartDate()).atDay(r.executionDay()));
            }
        }
        if (Boolean.TRUE.equals(r.removeEndDate())) {
            o.setEndDate(null);
        } else if (r.endDate() != null) {
            if (r.endDate().isBefore(LocalDate.now(clock))) {
                throw bad("INVALID_END_DATE", "Das Enddatum darf nicht in der Vergangenheit liegen.");
            }
            o.setEndDate(r.endDate());
        }
        if (r.paused() != null) {
            o.setStatus(r.paused() ? StandingOrder.Status.PAUSED : StandingOrder.Status.ACTIVE);
        }
        return toDto(o);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        requireOpen(userId, id).setStatus(StandingOrder.Status.ENDED);
    }

    // ------------------------------------------------------------------ Tageslauf

    @Override
    public int order() {
        return 25; // nach Sparraten (20), vor Kreditraten (30)
    }

    @Override
    public void runFor(LocalDate date) {
        for (StandingOrder o : orders.findByStatusAndExecutionDay(StandingOrder.Status.ACTIVE, date.getDayOfMonth())) {
            run(o.getId(), date);
        }
    }

    void run(Long id, LocalDate date) {
        StandingOrder o = orders.findById(id).orElseThrow();
        String ym = YearMonth.from(date).toString();
        if (o.getStatus() != StandingOrder.Status.ACTIVE || ym.equals(o.getLastExecutedMonth()) || !o.isDueOn(date)) {
            return;
        }
        String failure = null;
        try {
            tx.executeWithoutResult(s -> {
                transfers.execute(o.getUserId(), "so-" + o.getId() + "-" + ym,
                        new TransferRequest(o.getRecipientIban(), o.getAmountCents(), o.getReference()));
                markExecuted(o.getId(), ym);
            });
        } catch (ApiException e) {
            failure = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Dauerauftrag {} fehlgeschlagen", id, e);
            failure = "Technischer Fehler.";
        }
        if (failure != null) {
            String reason = failure;
            tx.executeWithoutResult(s -> {
                markExecuted(o.getId(), ym); // kein zweiter Versuch im selben Monat
                notifications.send(o.getUserId(), NotificationType.INFO, "Dauerauftrag nicht ausgeführt",
                        Money.format(o.getAmountCents()) + " an " + o.getRecipientName() + ": " + reason, null);
            });
        }
    }

    private void markExecuted(Long id, String ym) {
        StandingOrder o = orders.findById(id).orElseThrow();
        o.setLastExecutedMonth(ym);
        if (o.nextExecutionAfter(LocalDate.now(clock)) == null) {
            o.setStatus(StandingOrder.Status.ENDED);
        }
    }

    // ------------------------------------------------------------------ Hilfen

    /** Erste Ausführung: der nächste passende Tag NACH heute (der heutige Tageslauf ist schon vorbei). */
    static LocalDate firstExecution(LocalDate today, int day) {
        LocalDate d = today.withDayOfMonth(day);
        return d.isAfter(today) ? d : d.plusMonths(1);
    }

    static String intervalLabel(int months) {
        return switch (months) {
            case 1 -> "monatlich";
            case 3 -> "vierteljährlich";
            case 6 -> "halbjährlich";
            case 12 -> "jährlich";
            default -> "alle " + months + " Monate";
        };
    }

    private void validateAmount(long cents) {
        if (cents <= 0) {
            throw bad("INVALID_AMOUNT", "Der Betrag muss größer als 0 sein.");
        }
        if (cents > props.transfer().maxAmountCents()) {
            throw bad("AMOUNT_TOO_HIGH", "Höchstens " + Money.format(props.transfer().maxAmountCents()) + ".");
        }
    }

    private static String reference(String r) {
        return r == null || r.isBlank() ? DEFAULT_REFERENCE : r.trim();
    }

    private StandingOrder requireOpen(Long userId, Long id) {
        StandingOrder o = orders.findByIdAndUserId(id, userId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "STANDING_ORDER_NOT_FOUND", "Dauerauftrag nicht gefunden."));
        if (o.getStatus() == StandingOrder.Status.ENDED) {
            throw new ApiException(HttpStatus.CONFLICT, "STANDING_ORDER_ENDED", "Der Dauerauftrag ist beendet.");
        }
        return o;
    }

    private StandingOrderDto toDto(StandingOrder o) {
        return new StandingOrderDto(o.getId(), o.getRecipientName(), o.getRecipientIban(), o.getAmountCents(),
                o.getReference(), o.getExecutionDay(), o.getIntervalMonths(), intervalLabel(o.getIntervalMonths()),
                o.getStartDate(), o.getEndDate(), o.getStatus(),
                o.getStatus() == StandingOrder.Status.ACTIVE ? o.nextExecutionAfter(LocalDate.now(clock)) : null,
                o.getLastExecutedMonth());
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
