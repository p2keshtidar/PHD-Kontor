package de.frauas.girokonto.standingorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StandingOrderTest {

    private static StandingOrder order(int day, int interval, LocalDate start, LocalDate end) {
        return new StandingOrder(1L, "DE00", "Bea", 5000, "Test", day, interval, start, end, Instant.now());
    }

    @Test
    void ersteAusfuehrungIstImmerNachHeute() {
        assertThat(StandingOrderService.firstExecution(LocalDate.of(2026, 10, 12), 15)).isEqualTo("2026-10-15");
        assertThat(StandingOrderService.firstExecution(LocalDate.of(2026, 10, 12), 12)).isEqualTo("2026-11-12");
        assertThat(StandingOrderService.firstExecution(LocalDate.of(2026, 10, 12), 1)).isEqualTo("2026-11-01");
        assertThat(StandingOrderService.firstExecution(LocalDate.of(2026, 12, 20), 5)).isEqualTo("2027-01-05");
    }

    @Test
    void monatlichFaelligAmAusfuehrungstag() {
        StandingOrder o = order(5, 1, LocalDate.of(2026, 11, 5), null);
        assertThat(o.isDueOn(LocalDate.of(2026, 11, 5))).isTrue();
        assertThat(o.isDueOn(LocalDate.of(2026, 12, 5))).isTrue();
        assertThat(o.isDueOn(LocalDate.of(2026, 12, 6))).isFalse();
        assertThat(o.isDueOn(LocalDate.of(2026, 10, 5))).isFalse();   // vor dem Start
    }

    @Test
    void vierteljaehrlichNurJedenDrittenMonat() {
        StandingOrder o = order(15, 3, LocalDate.of(2026, 11, 15), null);
        assertThat(o.isDueOn(LocalDate.of(2026, 11, 15))).isTrue();
        assertThat(o.isDueOn(LocalDate.of(2026, 12, 15))).isFalse();
        assertThat(o.isDueOn(LocalDate.of(2027, 1, 15))).isFalse();
        assertThat(o.isDueOn(LocalDate.of(2027, 2, 15))).isTrue();
        assertThat(o.nextExecutionAfter(LocalDate.of(2026, 11, 15))).isEqualTo("2027-02-15");
    }

    @Test
    void enddatumBeendetDieAusfuehrungen() {
        StandingOrder o = order(1, 1, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31));
        assertThat(o.isDueOn(LocalDate.of(2027, 1, 1))).isTrue();
        assertThat(o.isDueOn(LocalDate.of(2027, 2, 1))).isFalse();
        assertThat(o.nextExecutionAfter(LocalDate.of(2026, 12, 10))).isEqualTo("2027-01-01");
        assertThat(o.nextExecutionAfter(LocalDate.of(2027, 1, 1))).isNull();
    }

    @Test
    void intervallTexte() {
        assertThat(StandingOrderService.intervalLabel(1)).isEqualTo("monatlich");
        assertThat(StandingOrderService.intervalLabel(12)).isEqualTo("jährlich");
    }
}
