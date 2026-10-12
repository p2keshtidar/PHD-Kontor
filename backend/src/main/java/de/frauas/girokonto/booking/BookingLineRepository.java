package de.frauas.girokonto.booking;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingLineRepository extends JpaRepository<BookingLine, Long> {

    /** Kontostand = Summe aller Buchungszeilen des Kontos (/F110/). */
    @Query("select coalesce(sum(l.amountCents), 0L) from BookingLine l where l.accountId = :accountId")
    long balanceOf(@Param("accountId") Long accountId);

    /** Muss im ganzen System immer 0 sein (/NF020/). */
    @Query("select coalesce(sum(l.amountCents), 0L) from BookingLine l")
    long totalOfAllLines();

    List<BookingLine> findByBookingId(Long bookingId);

    java.util.Optional<BookingLine> findFirstByBookingIdAndAccountId(Long bookingId, Long accountId);

    /** Summe der Ausgänge eines Kontos seit [from] für einen Buchungstyp (negativ), für das Tageslimit /F230/. */
    @Query("""
            select coalesce(sum(l.amountCents), 0L) from BookingLine l, Booking b
            where b.id = l.bookingId and l.accountId = :accountId and b.type = :type
              and l.amountCents < 0 and b.bookedAt >= :from
            """)
    long sumOutgoingSince(@Param("accountId") Long accountId, @Param("type") BookingType type,
                          @Param("from") Instant from);
}
