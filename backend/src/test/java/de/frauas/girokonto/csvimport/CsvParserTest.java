package de.frauas.girokonto.csvimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CsvParserTest {

    @Test
    void sparkasseMitAnfuehrungszeichenUndUmlauten() {
        String csv = """
                "Auftragskonto";"Buchungstag";"Valutadatum";"Buchungstext";"Verwendungszweck";"Beguenstigter/Zahlungspflichtiger";"Kontonummer/IBAN";"Betrag";"Waehrung"
                "DE001";"01.09.26";"01.09.26";"GUTSCHRIFT";"Gehalt September";"Muster GmbH";"DE002";"2.400,00";"EUR"
                "DE001";"03.09.26";"03.09.26";"DAUERAUFTRAG";"Miete; inkl. NK";"Hausverwaltung Müller";"DE003";"-850,00";"EUR"
                """;
        CsvParser.Result r = CsvParser.parse(csv);
        assertThat(r.format()).contains("Semikolon");
        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(r.rows().get(0).amountCents()).isEqualTo(240_000);
        assertThat(r.rows().get(1).amountCents()).isEqualTo(-85_000);
        assertThat(r.rows().get(1).description()).isEqualTo("Miete; inkl. NK");        // Semikolon in Anführungszeichen
        assertThat(r.rows().get(1).counterparty()).isEqualTo("Hausverwaltung Müller");
    }

    @Test
    void ingMitVorspannZeilen() {
        String csv = """
                Umsatzanzeige;Datei erstellt am: 13.10.2026
                IBAN;DE12 5001 0517 0000 0000 01
                Zeitraum;01.09.2026 - 30.09.2026

                Buchung;Valuta;Auftraggeber/Empfänger;Buchungstext;Verwendungszweck;Saldo;Währung;Betrag;Währung
                05.09.2026;05.09.2026;REWE Markt;Lastschrift;Einkauf;1.234,56;EUR;-54,37;EUR
                """;
        CsvParser.Result r = CsvParser.parse(csv);
        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).amountCents()).isEqualTo(-5_437);         // Betrag, nicht Saldo
        assertThat(r.rows().get(0).counterparty()).isEqualTo("REWE Markt");
        assertThat(r.rows().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void n26MitKommaUndPunktAlsDezimaltrenner() {
        String csv = """
                "Booking Date","Value Date","Partner Name","Partner Iban",Type,"Payment Reference","Account Name","Amount (EUR)"
                2026-09-07,2026-09-07,"Spotify",,Presentment,"Abo",Main,-10.99
                2026-09-28,2026-09-28,"Arbeitgeber AG",,Credit Transfer,"Lohn",Main,1850.5
                """;
        CsvParser.Result r = CsvParser.parse(csv);
        assertThat(r.format()).contains("Komma");
        assertThat(r.rows()).extracting(CsvParser.Row::amountCents).containsExactly(-1_099L, 185_050L);
        assertThat(r.rows().get(0).description()).isEqualTo("Abo");
    }

    @Test
    void betraegeUndDaten() {
        assertThat(CsvParser.parseAmount("-1.234,56")).isEqualTo(-123_456);
        assertThat(CsvParser.parseAmount("12,5 €")).isEqualTo(1_250);
        assertThat(CsvParser.parseAmount("+3.000,00")).isEqualTo(300_000);
        assertThat(CsvParser.parseAmount("1.000.000")).isEqualTo(100_000_000);
        assertThat(CsvParser.parseAmount("25,00-")).isEqualTo(-2_500);
        assertThat(CsvParser.parseDate("1.2.2026")).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(CsvParser.parseDate("2026-02-01T00:00:00")).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThatThrownBy(() -> CsvParser.parseAmount("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fehlerhafteZeilenWerdenGemeldetAberNichtAbgebrochen() {
        String csv = """
                Datum;Betrag;Verwendungszweck
                01.09.2026;-10,00;Gut
                32.13.2026;-5,00;Kaputtes Datum
                02.09.2026;abc;Kaputter Betrag
                """;
        CsvParser.Result r = CsvParser.parse(csv);
        assertThat(r.rows()).hasSize(1);
        assertThat(r.errors()).hasSize(2);
    }

    @Test
    void spaltennamenNurBisZumWortende() {
        assertThat(CsvParser.findColumns(java.util.List.of("Umsatzanzeige", "Datei erstellt am: 13.10.2026")))
                .containsExactly(-1, -1, -1, -1);
        assertThat(CsvParser.findColumns(java.util.List.of("Buchungstag", "Wertstellung (Valuta)", "Vorgang",
                "Buchungstext", "Umsatz in EUR"))).containsExactly(0, 4, -1, 3);
        assertThat(CsvParser.findColumns(java.util.List.of("Buchungsdatum", "Zahlungsempfänger*in",
                "Verwendungszweck", "Betrag (€)"))).containsExactly(0, 3, 1, 2);
    }

    @Test
    void ohneKopfzeileFehler() {
        assertThatThrownBy(() -> CsvParser.parse("a;b;c\n1;2;3\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Kopfzeile");
    }
}
