package de.frauas.girokonto.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import de.frauas.girokonto.insights.Category;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** F01–F02: Kategorien und Monatsnamen in Fragen erkennen (ohne Datenbank). */
class AssistantParsingTest {

    private static Category cat(String q) {
        return AssistantService.categoryIn(AssistantService.normalize(q));
    }

    @Test
    void F01_kategorienDeutschUndEnglisch() {
        assertThat(cat("Wie viel habe ich für Lebensmittel ausgegeben?")).isEqualTo(Category.LEBENSMITTEL);
        assertThat(cat("Was kostet mich die Miete?")).isEqualTo(Category.WOHNEN);
        assertThat(cat("Ausgaben für Mobilität im Mai")).isEqualTo(Category.MOBILITAET);
        assertThat(cat("How much did I spend on groceries?")).isEqualTo(Category.LEBENSMITTEL);
        assertThat(cat("Wie hoch ist mein Gehalt?")).isEqualTo(Category.GEHALT);
        assertThat(cat("Wie wird das Wetter?")).isNull();
    }

    @Test
    void F02_monatsnameMitUmlaut() {
        assertThat(AssistantService.normalize("März")).isEqualTo("maerz");
        assertThat(AssistantService.monthLabel(YearMonth.of(2026, 3))).isEqualTo("März 2026");
    }
}
