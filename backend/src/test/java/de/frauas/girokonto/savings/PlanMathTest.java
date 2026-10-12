package de.frauas.girokonto.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PlanMathTest {

    @Test
    void T09_rateOhneRendite() {
        // 5.000 € in 36 Monaten, vorsichtig (0 %) -> 138,89 € -> aufgerundet 139 €
        assertThat(PlanMath.requiredRate(0, 500_000, 0, 36)).isEqualTo(13_900);
    }

    @Test
    void beispielAusDemPflichtenheft() {
        // Ziel 5.000 €, bisher 1.200 €, Rate 100 €, r = 2 % -> 37 Monate; ohne Rendite 38 Monate
        double i = PlanMath.monthlyRate(0.02);
        assertThat(PlanMath.monthsToTarget(120_000, 10_000, i, 500_000)).isEqualTo(37);
        assertThat(PlanMath.monthsToTarget(120_000, 10_000, 0, 500_000)).isEqualTo(38);
    }

    @Test
    void nichtErreichbarOhneRate() {
        assertThat(PlanMath.monthsToTarget(0, 0, 0, 100_000)).isNull();
        assertThat(PlanMath.monthsToTarget(200_000, 0, 0, 100_000)).isZero();
    }

    @Test
    void erwarteteRenditen() {
        assertThat(RiskLevel.VORSICHTIG.expectedReturn(PlanType.GOAL)).isCloseTo(0.0, within(1e-9));
        assertThat(RiskLevel.AUSGEWOGEN.expectedReturn(PlanType.GOAL)).isCloseTo(0.02, within(1e-9));
        assertThat(RiskLevel.MUTIG.expectedReturn(PlanType.GOAL)).isCloseTo(0.0416, within(1e-9));
        assertThat(RiskLevel.VORSICHTIG.expectedReturn(PlanType.INVEST)).isCloseTo(0.028, within(1e-9));
        assertThat(RiskLevel.AUSGEWOGEN.expectedReturn(PlanType.INVEST)).isCloseTo(0.04, within(1e-9));
        assertThat(RiskLevel.MUTIG.expectedReturn(PlanType.INVEST)).isCloseTo(0.052, within(1e-9));
    }

    @Test
    void fondspreiseSindReproduzierbar() {
        assertThat(Fund.WELT_AKTIEN.price(YearMonth.of(2025, 12))).isEqualTo(100.0);
        assertThat(Fund.WELT_AKTIEN.price(YearMonth.of(2026, 1))).isEqualTo(100.0);
        double p = Fund.WELT_AKTIEN.price(YearMonth.of(2027, 6));
        assertThat(Fund.WELT_AKTIEN.price(YearMonth.of(2027, 6))).isEqualTo(p);
        assertThat(p).isBetween(50.0, 200.0);
        // Anleihen schwanken weniger
        double bond = Fund.EURO_ANLEIHEN.price(YearMonth.of(2027, 6));
        assertThat(bond).isBetween(90.0, 115.0);
    }
}
