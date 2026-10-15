package de.frauas.girokonto.multibank;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** J01–J05: Zinsrechnung für Tagesgeld (tagesgenau, act/act, auf Cent gerundet). */
class InterestMathTest {

    private static final BigDecimal TWO = new BigDecimal("2.00");

    private static TreeMap<LocalDate, Long> deltas(Object... dateAndCents) {
        TreeMap<LocalDate, Long> m = new TreeMap<>();
        for (int i = 0; i < dateAndCents.length; i += 2) {
            m.merge(LocalDate.parse((String) dateAndCents[i]), ((Number) dateAndCents[i + 1]).longValue(), Long::sum);
        }
        return m;
    }

    @Test
    void J01_konstantesGuthabenGanzerMonat() {
        // 730 € × 2 % / 365 = 4 Cent pro Tag, September hat 30 Tage
        var d = deltas("2026-08-20", 73_000);
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), TWO))
                .isEqualTo(120);
    }

    @Test
    void J02_einUndAuszahlungTagesgenau() {
        // 1.–10.: 730 €, 11.–30.: 365 € (Auszahlung am 11.) → 10 × 4 + 20 × 2 = 80 Cent
        var d = deltas("2026-08-31", 73_000, "2026-09-11", -36_500);
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), TWO))
                .isEqualTo(80);
        // Einzahlung erst am 21.: nur 10 Tage
        var e = deltas("2026-09-21", 73_000);
        assertThat(InterestMath.interestCents(e, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"), TWO))
                .isEqualTo(40);
    }

    @Test
    void J03_schaltjahrRechnetMit366Tagen() {
        // 732 € × 2 % / 366 = 4 Cent pro Tag im Jahr 2028, Februar 2028 hat 29 Tage
        var d = deltas("2028-01-15", 73_200);
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2028-02-01"), LocalDate.parse("2028-02-29"), TWO))
                .isEqualTo(116);
    }

    @Test
    void J04_rundungAufCentHalbGerade() {
        // 1 Tag: 91,25 € × 2 % / 365 = 0,5 Cent → 0 (gerade); 2 Tage = 1 Cent
        var d = deltas("2026-01-01", 9_125);
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-01"), TWO))
                .isZero();
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-02"), TWO))
                .isEqualTo(1);
        // 3 Tage = 1,5 Cent → 2 (gerade)
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-03"), TWO))
                .isEqualTo(2);
    }

    @Test
    void J05_keineZinsenOhneGuthabenSatzOderZeitraum() {
        var d = deltas("2026-09-01", 50_000, "2026-09-05", -50_000);
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-30"), TWO)).isZero();
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"),
                BigDecimal.ZERO)).isZero();
        assertThat(InterestMath.interestCents(d, LocalDate.parse("2026-09-30"), LocalDate.parse("2026-09-01"), TWO)).isZero();
        assertThat(InterestMath.interestCents(new TreeMap<>(), LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"), TWO)).isZero();
    }
}
