package de.frauas.girokonto.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Z01–Z05: Termine und Prognose der Sparregeln (ohne Datenbank). */
class AllocationScheduleTest {

    private static AllocationService.Rule rule(long cents, AllocationInterval i, LocalDate start, LocalDate end) {
        return new AllocationService.Rule(1L, 1L, 2L, "PROJEKTBANK", 3L, null, cents, i, start, end, start, "ACTIVE",
                null, Instant.EPOCH);
    }

    @Test
    void Z01_monatlichAmMonatsendeOhneVerrutschen() {
        LocalDate start = LocalDate.of(2027, 1, 31);
        AllocationInterval m = AllocationInterval.MONTHLY;
        assertThat(m.at(start, 1)).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(m.at(start, 2)).isEqualTo(LocalDate.of(2027, 3, 31));   // nicht der 28.
        assertThat(m.after(start, LocalDate.of(2027, 2, 28))).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(m.onOrAfter(start, LocalDate.of(2027, 3, 1))).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(m.onOrAfter(start, LocalDate.of(2026, 12, 1))).isEqualTo(start);
    }

    @Test
    void Z02_woechentlichZweiwoechentlichVierteljaehrlich() {
        LocalDate start = LocalDate.of(2026, 10, 16);   // Freitag
        assertThat(AllocationInterval.WEEKLY.after(start, start)).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(AllocationInterval.WEEKLY.onOrAfter(start, LocalDate.of(2026, 10, 24)))
                .isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(AllocationInterval.BIWEEKLY.after(start, LocalDate.of(2026, 10, 23)))
                .isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(AllocationInterval.QUARTERLY.after(start, start)).isEqualTo(LocalDate.of(2027, 1, 16));
        // weit in der Zukunft: kein Verrutschen durch Schätzung
        assertThat(AllocationInterval.WEEKLY.onOrAfter(start, LocalDate.of(2036, 10, 16)).getDayOfWeek())
                .isEqualTo(start.getDayOfWeek());
    }

    @Test
    void Z03_rhythmusEinlesen() {
        assertThat(AllocationInterval.parse(null)).isEqualTo(AllocationInterval.MONTHLY);
        assertThat(AllocationInterval.parse(" WEEKLY ")).isEqualTo(AllocationInterval.WEEKLY);
        assertThat(AllocationInterval.parse("DAILY")).isNull();
        assertThat(AllocationInterval.BIWEEKLY.label()).isEqualTo("alle 2 Wochen");
    }

    @Test
    void Z04_prognoseUeberMehrereRegeln() {
        LocalDate today = LocalDate.of(2026, 10, 16);
        var weekly = rule(5_000, AllocationInterval.WEEKLY, LocalDate.of(2026, 10, 23), null);
        var monthly = rule(20_000, AllocationInterval.MONTHLY, LocalDate.of(2026, 11, 1), null);
        // 23.10. 50 € · 30.10. 100 € · 01.11. 300 € → 300 € fehlen: erreicht am 01.11.
        assertThat(AllocationService.projectGoal(List.of(weekly, monthly), 30_000, today))
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(AllocationService.projectGoal(List.of(weekly), 10_000, today)).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(AllocationService.projectGoal(List.of(), 10_000, today)).isNull();
    }

    @Test
    void Z05_prognoseMitEnddatumUndGrenze() {
        LocalDate today = LocalDate.of(2026, 10, 16);
        var shortRule = rule(1_000, AllocationInterval.MONTHLY, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 1));
        assertThat(AllocationService.projectGoal(List.of(shortRule), 3_000, today)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(AllocationService.projectGoal(List.of(shortRule), 3_001, today)).isNull();   // reicht nie
        var tiny = rule(1, AllocationInterval.QUARTERLY, LocalDate.of(2026, 11, 1), null);
        assertThat(AllocationService.projectGoal(List.of(tiny), 1_000_000, today)).isNull();   // > 30 Jahre
    }
}
