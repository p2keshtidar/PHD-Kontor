package de.frauas.girokonto.banking;

import static org.assertj.core.api.Assertions.assertThat;

import de.frauas.girokonto.card.CardCrypto;
import de.frauas.girokonto.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** B20–B23: Hilfsfunktionen der Bankverbindungen. */
class BankingHelpersTest {

    @Test
    void B20_ibanNurGekuerztAnzeigen() {
        assertThat(BankingService.maskIban("DE89 3704 0044 0532 0130 00")).isEqualTo("DE89 •••• 3000");
        assertThat(BankingService.maskIban("de89370400440532013000")).isEqualTo("DE89 •••• 3000");
        assertThat(BankingService.maskIban("DE1")).isEqualTo("••••");
        assertThat(BankingService.maskIban(null)).isNull();
    }

    @Test
    void B21_pruefwertErkenntIbanOhneSieZuSpeichern() {
        CardCrypto crypto = new CardCrypto(new AppProperties(null, null, 0, null, null,
                new AppProperties.Card("q3Jm0QbN8y4k9tXc2Vh7LpZr5Wd1Fs6Ge0Ua3Ti8Ko4=", "999999", 1, 1),
                null, null, null, null));
        String a = crypto.fingerprint(BankingService.normalizeIban("DE89 3704 0044 0532 0130 00"));
        String b = crypto.fingerprint(BankingService.normalizeIban("de89370400440532013000"));
        assertThat(a).isEqualTo(b).hasSize(64).doesNotContain("3704");
        assertThat(crypto.fingerprint("DE02100100100006820101")).isNotEqualTo(a);
    }

    @Test
    void B22_abrufAbstandDerBank() {
        Instant now = Instant.parse("2026-06-01T12:00:00Z");
        assertThat(BankingService.tooSoon(null, now, Duration.ofHours(6))).isFalse();
        assertThat(BankingService.tooSoon(now.minusSeconds(3600), now, Duration.ofHours(6))).isTrue();
        assertThat(BankingService.tooSoon(now.minusSeconds(6 * 3600), now, Duration.ofHours(6))).isFalse();
        assertThat(BankingService.tooSoon(now, now, Duration.ZERO)).isFalse();
    }

    @Test
    void B23_schlagwoerterUndBezeichnungen() {
        assertThat(BankingService.normalizeTag("  Urlaub,  Italien ")).isEqualTo("urlaub italien");
        assertThat(BankingService.normalizeTag("x".repeat(40))).hasSize(30);
        assertThat(BankingService.statusLabel("PENDING")).isEqualTo("Vorgemerkt");
        assertThat(BankingService.statusLabel("REVERSED")).isEqualTo("Zurückgebucht");
        assertThat(BankingService.connectionStatusLabel("EXPIRED")).isEqualTo("Freigabe abgelaufen");
        assertThat(BankingService.PERMISSIONS).noneMatch(p -> p.contains("Zahlung"));   // nur Lesezugriff
    }
}
