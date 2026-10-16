package de.frauas.girokonto.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.InsightsService;
import de.frauas.girokonto.insights.MonthSummaryDto;
import de.frauas.girokonto.insights.MonthSummaryDto.CategoryAmount;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** H01–H03: gemeinsame Helfer (#75) – Datum/Monat, Durchschnitt der letzten vollen Monate, Idempotency-Key. */
class SharedHelpersTest {

    private static MonthSummaryDto month(String ym, boolean complete, long food) {
        List<CategoryAmount> cats = food == 0 ? List.of()
                : List.of(new CategoryAmount(Category.LEBENSMITTEL, "Lebensmittel", Category.Kind.VARIABLE, food));
        return new MonthSummaryDto(ym, complete, food != 0, 0, food, 0, -food, cats);
    }

    @Test
    void H01_datumUndMonat() {
        assertThat(Dates.de(LocalDate.of(2026, 11, 1))).isEqualTo("01.11.2026");
        assertThat(Dates.monthYear(YearMonth.of(2026, 3))).isEqualTo("März 2026");
        assertThat(Dates.MONTHS_DE).hasSize(12).startsWith("Januar").endsWith("Dezember");
    }

    @Test
    void H02_durchschnittNurVolleMonateMitDaten() {
        List<MonthSummaryDto> months = List.of(
                month("2026-05", true, 0),          // vor dem Start: keine Daten → zählt nicht
                month("2026-06", true, 30_000),
                month("2026-07", true, 40_000),
                month("2026-08", true, 0),          // Monat ohne Umsätze → zählt nicht
                month("2026-09", true, 50_000),
                month("2026-10", false, 99_000));   // laufender Monat → zählt nicht
        assertThat(InsightsService.lastFullMonths(months, 3)).extracting(MonthSummaryDto::month)
                .containsExactly("2026-06", "2026-07", "2026-09");
        assertThat(InsightsService.averageCents(months, Category.LEBENSMITTEL)).isEqualTo(40_000);
        assertThat(InsightsService.averageCents(months, Category.FREIZEIT)).isZero();
        assertThat(InsightsService.averageCents(List.of(month("2026-10", false, 5_000)), Category.LEBENSMITTEL)).isZero();
    }

    @Test
    void H03_idempotencyKey() {
        assertThat(IdempotencyKeys.require("abc-1", 40)).isEqualTo("abc-1");
        assertThatThrownBy(() -> IdempotencyKeys.require(" ", 40)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> IdempotencyKeys.require(null, 40)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> IdempotencyKeys.require("x".repeat(41), 40)).isInstanceOf(ApiException.class);
    }
}
