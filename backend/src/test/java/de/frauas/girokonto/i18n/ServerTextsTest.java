package de.frauas.girokonto.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** I01–I05: englische Server-Texte. */
class ServerTextsTest {

    private final ServerTexts t = new ServerTexts();

    /** Alle Meldungen, die im Code als ein einziger Text stehen, müssen übersetzt sein. */
    @Test
    void I01_jedeFesteMeldungIstUebersetzt() throws IOException {
        Pattern[] patterns = {
                Pattern.compile("\\bbad\\(\\s*\"[A-Z_]+\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)"),
                Pattern.compile("new ApiException\\([^,]+,\\s*\"[A-Z_]+\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)"),
                Pattern.compile("message\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\""),
                Pattern.compile("notifications\\.send\\([^,]+,[^,]+,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,"),
                Pattern.compile("ApiError\\.of\\(\"[A-Z_]+\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\)"),
        };
        List<String> missing = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                for (Pattern p : patterns) {
                    Matcher m = p.matcher(src);
                    while (m.find()) {
                        String text = m.group(1).replace("\\\"", "\"");
                        checked++;
                        if (!t.knows(text)) {
                            missing.add(f.getFileName() + ": " + text);
                        }
                    }
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
        assertThat(missing).as("Fehlende Einträge in src/main/resources/i18n/en.tsv").isEmpty();
    }

    @Test
    void I02_platzhalterUndVerschachtelteBegriffe() {
        assertThat(t.toEnglish("Nicht genug Guthaben. Verfügbar: 1.234,56 €."))
                .isEqualTo("Insufficient funds. Available: €1,234.56.");
        assertThat(t.toEnglish("Die Kategorie „Gehalt“ passt nicht zu einer Ausgabe."))
                .isEqualTo("The category “Salary” does not fit an expense.");
        assertThat(t.toEnglish("Kreditbetrag für Wohnung/Haus zwischen 50.000,00 € und 1.000.000,00 €."))
                .isEqualTo("Loan amount for Flat/house between €50,000.00 and €1,000,000.00.");
        assertThat(t.toEnglish("Erika Muster hat 240,00 € eingezahlt in „WG-Kasse“"))
                .isEqualTo("Erika Muster deposited €240.00 into “WG-Kasse”");
    }

    @Test
    void I03_datumMonatProzent() {
        assertThat(t.toEnglish("Das Enddatum liegt vor der ersten Ausführung am 01.11.2026."))
                .isEqualTo("The end date is before the first execution on 1 Nov 2026.");
        assertThat(t.toEnglish("Rate zu hoch: höchstens 70 % von 1.234,00 € möglich."))
                .isEqualTo("Rate too high: at most 70% of €1,234.00 possible.");
        assertThat(ServerTexts.formats("Ziel erreicht im März 2028, Zins 5,59 %"))
                .isEqualTo("Ziel erreicht im March 2028, Zins 5.59%");
        assertThat(t.toEnglish("Am 05.11. werden 50,00 € für „Japan“ abgebucht."))
                .isEqualTo("On 5 Nov, €50.00 will be debited for “Japan”.");
    }

    @Test
    void I04_unbekannteTexteBleiben() {
        assertThat(t.toEnglish("Pizza mit Ben")).isEqualTo("Pizza mit Ben");
        assertThat(t.toEnglish("")).isEmpty();
        assertThat(t.toEnglish(null)).isNull();
        assertThat(ServerTexts.wantsEnglish("en-US,en;q=0.9")).isTrue();
        assertThat(ServerTexts.wantsEnglish("de-DE")).isFalse();
        assertThat(ServerTexts.wantsEnglish(null)).isFalse();
    }

    @Test
    void I05_tabelleIstGueltig() throws IOException {
        Pattern ph = Pattern.compile("\\{\\d+}");
        for (String line : Files.readAllLines(Path.of("src/main/resources/i18n/en.tsv"), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\t", -1);
            assertThat(p).as(line).hasSize(2);
            assertThat(p[1]).as(line).isNotBlank();
            assertThat(ph.matcher(p[1]).results().map(Object::toString).count())
                    .as("Platzhalter: " + line).isEqualTo(ph.matcher(p[0]).results().count());
        }
    }
}
