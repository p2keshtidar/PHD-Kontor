package de.frauas.girokonto.card;

import static org.assertj.core.api.Assertions.assertThat;

import de.frauas.girokonto.config.AppProperties;
import org.junit.jupiter.api.Test;

class CardUnitTest {

    @Test
    void kartennummerHatGueltigeLuhnPruefziffer() {
        for (int i = 0; i < 200; i++) {
            String pan = CardNumberGenerator.generate("999999");
            assertThat(pan).hasSize(16).startsWith("999999");
            assertThat(CardNumberGenerator.isValid(pan)).as(pan).isTrue();
        }
    }

    @Test
    void bekannteTestnummerIstGueltig() {
        // bekannte Luhn-gültige Testnummer
        assertThat(CardNumberGenerator.isValid("4111111111111111")).isTrue();
        assertThat(CardNumberGenerator.isValid("4111111111111112")).isFalse();
    }

    @Test
    void verschluesselnUndEntschluesseln() {
        AppProperties props = new AppProperties(null, null, 0, null, null,
                new AppProperties.Card("q3Jm0QbN8y4k9tXc2Vh7LpZr5Wd1Fs6Ge0Ua3Ti8Ko4=", "999999", 100000, 200000),
                null, null, null, null);
        CardCrypto crypto = new CardCrypto(props);
        String enc1 = crypto.encrypt("9999991234567890");
        String enc2 = crypto.encrypt("9999991234567890");
        assertThat(enc1).isNotEqualTo(enc2);               // zufälliger IV
        assertThat(enc1).doesNotContain("9999991234567890");
        assertThat(crypto.decrypt(enc1)).isEqualTo("9999991234567890");
    }
}
