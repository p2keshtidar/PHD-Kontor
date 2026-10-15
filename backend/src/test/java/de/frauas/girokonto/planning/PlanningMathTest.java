package de.frauas.girokonto.planning;

import static org.assertj.core.api.Assertions.assertThat;

import de.frauas.girokonto.insights.Category;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Q01–Q04: Budget-Status, Hochrechnung, Median, Monatsende in der Prognose. */
class PlanningMathTest {

    @Test
    void Q01_statusUndProzent() {
        LocalDate d = LocalDate.parse("2026-10-03");   // vor dem 5.: noch keine Hochrechnung
        assertThat(BudgetService.item(Category.FREIZEIT, 10_000, 7_999, d).status()).isEqualTo("OK");
        assertThat(BudgetService.item(Category.FREIZEIT, 10_000, 8_000, d).status()).isEqualTo("WARN");
        assertThat(BudgetService.item(Category.FREIZEIT, 10_000, 10_000, d).status()).isEqualTo("WARN");
        var over = BudgetService.item(Category.FREIZEIT, 10_000, 10_001, d);
        assertThat(over.status()).isEqualTo("OVER");
        assertThat(over.remainingCents()).isEqualTo(-1);
        assertThat(over.percent()).isEqualTo(100.0);
        assertThat(over.projectedCents()).isNull();
    }

    @Test
    void Q02_hochrechnungBisMonatsende() {
        // 10. Oktober (31 Tage), 100 € ausgegeben → 310 € bei gleichem Tempo
        var i = BudgetService.item(Category.LEBENSMITTEL, 25_000, 10_000, LocalDate.parse("2026-10-10"));
        assertThat(i.projectedCents()).isEqualTo(31_000);
        assertThat(i.projectionText()).isEqualTo("Bei gleichem Tempo voraussichtlich 310,00 € – 60,00 € über dem Budget.");
        var ok = BudgetService.item(Category.LEBENSMITTEL, 50_000, 10_000, LocalDate.parse("2026-10-10"));
        assertThat(ok.projectionText()).isEqualTo("Bei gleichem Tempo voraussichtlich 310,00 €.");
        // am Monatsletzten keine Hochrechnung mehr
        assertThat(BudgetService.item(Category.LEBENSMITTEL, 50_000, 10_000, LocalDate.parse("2026-10-31"))
                .projectedCents()).isNull();
    }

    @Test
    void Q03_median() {
        assertThat(BudgetService.median(new long[] {5})).isEqualTo(5);
        assertThat(BudgetService.median(new long[] {1, 2, 9})).isEqualTo(2);
        assertThat(BudgetService.median(new long[] {1, 3, 5, 100})).isEqualTo(4);
    }

    @Test
    void Q04_monatsletzterNimmtFehlendeTageAuf() {
        assertThat(ForecastService.daysFor(LocalDate.parse("2026-10-15"))).containsExactly(15);
        assertThat(ForecastService.daysFor(LocalDate.parse("2026-10-31"))).containsExactly(31);
        assertThat(ForecastService.daysFor(LocalDate.parse("2026-11-30"))).containsExactly(30, 31);
        assertThat(ForecastService.daysFor(LocalDate.parse("2027-02-28"))).containsExactly(28, 29, 30, 31);
        assertThat(ForecastService.daysFor(LocalDate.parse("2028-02-29"))).containsExactly(29, 30, 31);
    }
}
