package de.frauas.girokonto.insights;

import java.util.List;

/**
 * Ein Monat im Finanzüberblick (/F570/).
 *
 * @param complete false für den laufenden Monat
 */
public record MonthSummaryDto(
        String month,
        boolean complete,
        boolean hasData,
        long incomeCents,
        long expenseCents,
        long fixedCents,
        long freeCents,
        List<CategoryAmount> categories) {

    /** cents: bei Einnahmen positiv eingenommen, bei Ausgaben positiv ausgegeben. */
    public record CategoryAmount(Category category, String label, Category.Kind kind, long cents) {}
}
