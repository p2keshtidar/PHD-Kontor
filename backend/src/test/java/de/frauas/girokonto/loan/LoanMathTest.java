package de.frauas.girokonto.loan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanMathTest {

    @Test
    void beispielAuto20000Euro60Monate() {
        BigDecimal rate = LoanPurpose.AUTO.rateFor(60, 2_000_000, 0).add(Bank.NORDLICHT.adjustment());
        assertThat(rate).isEqualByComparingTo("5.59");
        assertThat(LoanMath.annuity(2_000_000, rate, 60)).isEqualTo(38_286);
        assertThat(LoanMath.interest(2_000_000, rate)).isEqualTo(9_317);
    }

    @Test
    void T21_tilgungsplanGehtGenauAuf() {
        BigDecimal rate = new BigDecimal("5.59");
        List<LoanMath.Row> rows = LoanMath.schedule(2_000_000, rate, 60);
        assertThat(rows).hasSize(60);
        assertThat(rows.stream().mapToLong(LoanMath.Row::principalCents).sum()).isEqualTo(2_000_000);
        assertThat(rows.get(59).remainingCents()).isZero();
        assertThat(rows.get(0).principalCents()).isEqualTo(38_286 - 9_317);
    }

    @Test
    void eigenkapitalUndLaufzeitVeraendernDenZins() {
        // Haus: 240 Monate -> 2 Schritte à 0,15 über 120 Monate; 25 % Eigenkapital -> −0,30
        BigDecimal r = LoanPurpose.IMMOBILIE.rateFor(240, 30_000_000, 10_000_000);
        assertThat(r).isEqualByComparingTo("3.79");
        assertThat(LoanPurpose.IMMOBILIE.rateFor(240, 30_000_000, 0)).isEqualByComparingTo("4.09");
    }

    @Test
    void maximalerKreditbetragPasstZurRate() {
        BigDecimal rate = new BigDecimal("5.59");
        long max = LoanMath.maxPrincipal(38_286, rate, 60);
        assertThat(LoanMath.annuity(max, rate, 60)).isLessThanOrEqualTo(38_286);
        assertThat(max).isBetween(1_999_000L, 2_000_200L);
    }

    @Test
    void ohneSondertilgungGleicherPlanWieVorher() {
        BigDecimal rate = new BigDecimal("5.59");
        List<LoanMath.Row> a = LoanMath.schedule(2_000_000, rate, 60);
        List<LoanMath.Row> b = LoanMath.schedule(2_000_000, rate, 38_286, 60, java.util.Map.of());
        assertThat(b).isEqualTo(a);
    }

    @Test
    void K020_sondertilgungVerkuerztLaufzeitUndSpartZinsen() {
        BigDecimal rate = new BigDecimal("5.59");
        List<LoanMath.Row> ohne = LoanMath.schedule(2_000_000, rate, 60);
        List<LoanMath.Row> mit = LoanMath.schedule(2_000_000, rate, 38_286, 60, java.util.Map.of(12, 500_000L));
        assertThat(mit.size()).isLessThan(ohne.size());
        assertThat(mit.get(mit.size() - 1).remainingCents()).isZero();
        // Tilgung aus Raten + Sondertilgung = Kreditbetrag
        assertThat(mit.stream().mapToLong(LoanMath.Row::principalCents).sum() + 500_000).isEqualTo(2_000_000);
        long zinsenOhne = ohne.stream().mapToLong(LoanMath.Row::interestCents).sum();
        long zinsenMit = mit.stream().mapToLong(LoanMath.Row::interestCents).sum();
        assertThat(zinsenMit).isLessThan(zinsenOhne);
        // bis einschließlich Monat 12 identisch, Restschuld danach um 5.000 € niedriger
        assertThat(mit.subList(0, 11)).isEqualTo(ohne.subList(0, 11));
        assertThat(mit.get(11).remainingCents()).isEqualTo(ohne.get(11).remainingCents() - 500_000);
    }

    @Test
    void K020_sondertilgungVorDerErstenRate() {
        BigDecimal rate = new BigDecimal("5.59");
        List<LoanMath.Row> mit = LoanMath.schedule(2_000_000, rate, 38_286, 60, java.util.Map.of(0, 1_000_000L));
        assertThat(mit.get(0).interestCents()).isEqualTo(LoanMath.interest(1_000_000, rate));
        assertThat(mit.size()).isLessThan(30);
    }
}
