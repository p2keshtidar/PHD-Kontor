package de.frauas.girokonto.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die EINZIGE Stelle im System, die Geld bewegt (Pflichtenheft Kap. 6.3 und 7.2).
 * Jede Buchung besteht aus mindestens zwei Zeilen, deren Summe genau 0 ist.
 */
@Service
public class BookingService {

    /**
     * Eine gewünschte Buchungszeile: Konto und Betrag in Cent (positiv = Eingang).
     * category ist optional; ohne Angabe wird sie automatisch bestimmt (/F560/).
     */
    public record Line(Long accountId, long amountCents, Category category) {
        public Line(Long accountId, long amountCents) {
            this(accountId, amountCents, null);
        }
    }

    private final BookingRepository bookings;
    private final BookingLineRepository lines;
    private final CategoryService categories;
    private final Clock clock;

    public BookingService(BookingRepository bookings, BookingLineRepository lines, CategoryService categories,
                          Clock clock) {
        this.bookings = bookings;
        this.lines = lines;
        this.categories = categories;
        this.clock = clock;
    }

    /**
     * Bucht alle Zeilen in einer Datenbanktransaktion: entweder alles oder nichts.
     *
     * @param idempotencyKey optional; wird dieselbe Anfrage zweimal gesendet, wird nur einmal gebucht (/F250/)
     */
    @Transactional
    public Booking post(BookingType type, String description, String idempotencyKey, Long createdBy, List<Line> requested) {
        return post(type, description, idempotencyKey, createdBy, requested, Instant.now(clock));
    }

    /** Wie oben, aber mit festem Buchungszeitpunkt (für Beispielmonate in der Vergangenheit, /F590/). */
    @Transactional
    public Booking post(BookingType type, String description, String idempotencyKey, Long createdBy,
                        List<Line> requested, Instant bookedAt) {
        if (idempotencyKey != null) {
            Optional<Booking> existing = bookings.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        validate(requested);

        Booking booking = bookings.save(new Booking(type, description, bookedAt, idempotencyKey, createdBy));
        for (Line l : requested) {
            Category category = l.category() != null ? l.category()
                    : categories.categorize(type, description, l.amountCents());
            lines.save(new BookingLine(booking.getId(), l.accountId(), l.amountCents(), category.name()));
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public long balanceOf(Long accountId) {
        return lines.balanceOf(accountId);
    }

    static void validate(List<Line> requested) {
        if (requested == null || requested.size() < 2) {
            throw new IllegalArgumentException("Eine Buchung braucht mindestens zwei Zeilen");
        }
        long sum = 0;
        for (Line l : requested) {
            if (l.accountId() == null) {
                throw new IllegalArgumentException("Buchungszeile ohne Konto");
            }
            if (l.amountCents() == 0) {
                throw new IllegalArgumentException("Buchungszeile mit Betrag 0");
            }
            sum = Math.addExact(sum, l.amountCents());
        }
        if (sum != 0) {
            throw new IllegalArgumentException("Summe der Buchungszeilen ist " + sum + " statt 0");
        }
    }
}
