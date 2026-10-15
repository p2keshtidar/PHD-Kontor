package de.frauas.girokonto.account;

import de.frauas.girokonto.booking.BookingService;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * "Ziel erreicht" für digitale Konten: wird genau einmal gemeldet (an alle Beteiligten). Fällt der Stand
 * wieder unter das Ziel, darf es erneut gemeldet werden. Zum Stand zählen Guthaben und Vormerkungen.
 */
@Component
public class GoalTracker {

    private final BookingService bookings;
    private final EarmarkRepository earmarks;
    private final AccountMemberRepository members;
    private final NotificationService notifications;
    private final Clock clock;

    public GoalTracker(BookingService bookings, EarmarkRepository earmarks, AccountMemberRepository members,
                       NotificationService notifications, Clock clock) {
        this.bookings = bookings;
        this.earmarks = earmarks;
        this.members = members;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Stand eines digitalen Kontos für das Ziel: Guthaben plus Vormerkungen. */
    public long saved(Account a) {
        return bookings.balanceOf(a.getId()) + earmarks.forAccount(a.getId());
    }

    /** Nach jeder Änderung am Stand aufrufen (Konto muss verwaltet sein, damit goalReachedAt gespeichert wird). */
    public void check(Account a) {
        if (a.getType() != AccountType.SUB || a.getTargetCents() == null) {
            return;
        }
        long saved = saved(a);
        if (a.getGoalReachedAt() == null && saved >= a.getTargetCents()) {
            a.setGoalReachedAt(Instant.now(clock));
            for (Long p : participants(a)) {
                notifications.send(p, NotificationType.GOAL, "Ziel erreicht",
                        "„" + a.getName() + "“ hat das Ziel von " + Money.format(a.getTargetCents()) + " erreicht.", null);
            }
        } else if (a.getGoalReachedAt() != null && saved < a.getTargetCents()) {
            a.setGoalReachedAt(null);
        }
    }

    private List<Long> participants(Account a) {
        List<Long> all = new ArrayList<>(members.members(a.getId()));
        all.add(0, a.getOwnerId());
        return all;
    }
}
