package de.frauas.girokonto.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** K01–K04: Laufzeiten, Kündigungsfristen, Kosten und Dateiprüfung ohne Datenbank. */
class ContractMathTest {

    @Test
    void K01_mindestlaufzeitDannMonatlich() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        var t = ContractService.term(start, 24, 1, 1, "MONTHS", LocalDate.of(2026, 10, 15));
        assertThat(t.end()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(t.cancelBy()).isEqualTo(LocalDate.of(2026, 11, 30));
        // Frist verpasst: nächstes Ende einen Monat später
        var later = ContractService.term(start, 24, 1, 1, "MONTHS", LocalDate.of(2026, 12, 1));
        assertThat(later.end()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void K02_jaehrlicheVerlaengerungMitDreiMonatenFrist() {
        var t = ContractService.term(LocalDate.of(2024, 4, 1), 12, 12, 3, "MONTHS", LocalDate.of(2026, 1, 10));
        assertThat(t.end()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(t.cancelBy()).isEqualTo(LocalDate.of(2026, 12, 31));
        var weeks = ContractService.term(LocalDate.of(2026, 1, 1), 0, 1, 2, "WEEKS", LocalDate.of(2026, 1, 1));
        assertThat(weeks.end()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(weeks.cancelBy()).isEqualTo(LocalDate.of(2026, 1, 17));
    }

    @Test
    void K03_kostenJeMonatUndJahr() {
        assertThat(ContractService.monthly(12_000, "YEARLY")).isEqualTo(1_000);
        assertThat(ContractService.yearly(3_000, "QUARTERLY")).isEqualTo(12_000);
        assertThat(ContractService.yearly(2_999, "MONTHLY")).isEqualTo(35_988);
        assertThat(ContractService.noticeLabel(0, "MONTHS")).isEqualTo("jederzeit");
        assertThat(ContractService.noticeLabel(3, "MONTHS")).isEqualTo("3 Monate");
    }

    @Test
    void K04_dateitypAmInhaltUndSichererName() {
        assertThat(DocumentService.detect("%PDF-1.7".getBytes())).isEqualTo("application/pdf");
        assertThat(DocumentService.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
        assertThat(DocumentService.detect("<html>".getBytes())).isNull();
        assertThat(DocumentService.safeName("../../etc/passwd", "application/pdf")).isEqualTo("passwd.pdf");
        assertThat(DocumentService.safeName("C:\\tmp\\Rechnung<1>.exe", "image/png")).isEqualTo("Rechnung_1_.png");
        assertThat(DocumentService.safeName("", "image/jpeg")).isEqualTo("dokument.jpg");
    }
}
