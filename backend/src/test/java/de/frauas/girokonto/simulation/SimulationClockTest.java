package de.frauas.girokonto.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SimulationClockTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void vorspulenUeberDieZeitumstellungTrifftDenRichtigenTag() {
        // 13.10.2026, 00:30 Uhr in Berlin (Sommerzeit) = 12.10. 22:30 UTC
        Clock base = Clock.fixed(Instant.parse("2026-10-12T22:30:00Z"), ZoneOffset.UTC);
        SimulationClock clock = new SimulationClock(BERLIN, base);
        assertThat(LocalDate.now(clock)).isEqualTo("2026-10-13");

        // 30 Tage weiter, über das Ende der Sommerzeit am 25.10. hinweg
        clock.setOffsetDays(30);
        assertThat(LocalDate.now(clock)).isEqualTo("2026-11-12");
        assertThat(LocalDateTime.now(clock).getHour()).isZero();          // weiterhin 00:30 Uhr

        // Tag für Tag: jedes Datum genau einmal
        LocalDate previous = LocalDate.of(2026, 10, 13);
        for (int i = 1; i <= 200; i++) {
            clock.setOffsetDays(i);
            LocalDate d = LocalDate.now(clock);
            assertThat(d).isEqualTo(previous.plusDays(1));
            previous = d;
        }
    }

    @Test
    void ohneVerschiebungEchteZeit() {
        Clock base = Clock.fixed(Instant.parse("2026-03-29T00:30:00Z"), ZoneOffset.UTC);
        SimulationClock clock = new SimulationClock(BERLIN, base);
        assertThat(clock.instant()).isEqualTo(base.instant());
    }
}
