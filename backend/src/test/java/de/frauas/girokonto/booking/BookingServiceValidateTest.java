package de.frauas.girokonto.booking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Prüft die Grundregel der doppelten Buchführung (Pflichtenheft Kap. 6.3). */
class BookingServiceValidateTest {

    @Test
    void summeNullIstErlaubt() {
        assertThatCode(() -> BookingService.validate(List.of(
                new BookingService.Line(1L, -2500),
                new BookingService.Line(2L, 2500)))).doesNotThrowAnyException();
    }

    @Test
    void dreiZeilenMitSummeNullSindErlaubt() {
        assertThatCode(() -> BookingService.validate(List.of(
                new BookingService.Line(1L, -10000),
                new BookingService.Line(2L, 5000),
                new BookingService.Line(3L, 5000)))).doesNotThrowAnyException();
    }

    @Test
    void summeUngleichNullWirdAbgelehnt() {
        assertThatThrownBy(() -> BookingService.validate(List.of(
                new BookingService.Line(1L, -2500),
                new BookingService.Line(2L, 2400)))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nurEineZeileWirdAbgelehnt() {
        assertThatThrownBy(() -> BookingService.validate(List.of(new BookingService.Line(1L, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void betragNullWirdAbgelehnt() {
        assertThatThrownBy(() -> BookingService.validate(List.of(
                new BookingService.Line(1L, 0),
                new BookingService.Line(2L, 0)))).isInstanceOf(IllegalArgumentException.class);
    }
}
