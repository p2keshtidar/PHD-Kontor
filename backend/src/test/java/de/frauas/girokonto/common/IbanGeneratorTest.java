package de.frauas.girokonto.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IbanGeneratorTest {

    private final IbanGenerator generator = new IbanGenerator();

    @Test
    void erkenntBekannteGueltigeIban() {
        // Offizielles Beispiel einer gültigen deutschen IBAN
        assertThat(generator.isValid("DE89370400440532013000")).isTrue();
        assertThat(generator.isValid("DE89 3704 0044 0532 0130 00")).isTrue();
    }

    @Test
    void erkenntUngueltigePruefziffer() {
        assertThat(generator.isValid("DE88370400440532013000")).isFalse();
        assertThat(generator.isValid("kein iban")).isFalse();
        assertThat(generator.isValid(null)).isFalse();
    }

    @Test
    void erzeugteIbanIstGueltigUndHat22Zeichen() {
        for (long nr = 1_000_000_001L; nr < 1_000_000_101L; nr++) {
            String iban = generator.generate("00010001", nr);
            assertThat(iban).hasSize(22).startsWith("DE");
            assertThat(generator.isValid(iban)).as(iban).isTrue();
        }
    }

    @Test
    void erzeugtBekannteIbanAusBlzUndKontonummer() {
        assertThat(generator.generate("37040044", 532013000L)).isEqualTo("DE89370400440532013000");
    }

    @Test
    void lehntFalscheBlzAb() {
        assertThatThrownBy(() -> generator.generate("123", 1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
