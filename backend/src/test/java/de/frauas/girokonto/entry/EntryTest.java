package de.frauas.girokonto.entry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Termine eigener Einträge: einmalig, monatlich, vierteljährlich, mit Ende. */
class EntryTest {

    private static Entry entry(int interval, LocalDate start, LocalDate end) {
        return new Entry(1L, Entry.Kind.INCOME, "Test", "GEHALT", 100, interval, start, end, Instant.EPOCH);
    }

    @Test
    void einmalig() {
        Entry e = entry(0, LocalDate.of(2026, 7, 31), null);
        assertThat(e.isOnce()).isTrue();
        assertThat(e.isDueOn(LocalDate.of(2026, 7, 31))).isTrue();
        assertThat(e.isDueOn(LocalDate.of(2026, 8, 31))).isFalse();
        assertThat(e.nextAfter(LocalDate.of(2026, 7, 1))).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(e.nextAfter(LocalDate.of(2026, 7, 31))).isNull();
    }

    @Test
    void monatlich() {
        Entry e = entry(1, LocalDate.of(2026, 1, 28), null);
        assertThat(e.isDueOn(LocalDate.of(2026, 2, 28))).isTrue();
        assertThat(e.isDueOn(LocalDate.of(2026, 2, 27))).isFalse();
        assertThat(e.isDueOn(LocalDate.of(2025, 12, 28))).isFalse();      // vor dem Start
        assertThat(e.nextAfter(LocalDate.of(2026, 3, 28))).isEqualTo(LocalDate.of(2026, 4, 28));
        assertThat(e.nextAfter(LocalDate.of(2025, 6, 1))).isEqualTo(LocalDate.of(2026, 1, 28));
    }

    @Test
    void vierteljaehrlichMitEnde() {
        Entry e = entry(3, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 7, 15));
        assertThat(e.isDueOn(LocalDate.of(2026, 4, 15))).isTrue();
        assertThat(e.isDueOn(LocalDate.of(2026, 5, 15))).isFalse();
        assertThat(e.isDueOn(LocalDate.of(2026, 7, 15))).isTrue();
        assertThat(e.isDueOn(LocalDate.of(2026, 10, 15))).isFalse();     // nach dem Ende
        assertThat(e.nextAfter(LocalDate.of(2026, 4, 15))).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(e.nextAfter(LocalDate.of(2026, 7, 15))).isNull();
    }

    @Test
    void erledigtHatKeinenTermin() {
        Entry e = entry(1, LocalDate.of(2026, 1, 5), null);
        e.setStatus(Entry.Status.DONE);
        assertThat(e.nextAfter(LocalDate.of(2026, 1, 1))).isNull();
        e.setStatus(Entry.Status.DELETED);
        assertThat(e.nextAfter(LocalDate.of(2026, 1, 1))).isNull();
    }

    @Test
    void intervallTexte() {
        assertThat(EntryService.intervalLabel(0)).isEqualTo("einmalig");
        assertThat(EntryService.intervalLabel(1)).isEqualTo("monatlich");
        assertThat(EntryService.intervalLabel(3)).isEqualTo("vierteljährlich");
        assertThat(EntryService.intervalLabel(6)).isEqualTo("halbjährlich");
        assertThat(EntryService.intervalLabel(12)).isEqualTo("jährlich");
    }
}
