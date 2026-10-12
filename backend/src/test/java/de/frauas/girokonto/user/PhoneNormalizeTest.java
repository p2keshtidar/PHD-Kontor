package de.frauas.girokonto.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNormalizeTest {

    @Test
    void verschiedeneSchreibweisenErgebenDieselbeNummer() {
        assertThat(UserService.normalizePhone("0151 1234567")).isEqualTo("01511234567");
        assertThat(UserService.normalizePhone("+49 151 1234567")).isEqualTo("01511234567");
        assertThat(UserService.normalizePhone("+49 (151) 123-4567")).isEqualTo("01511234567");
        assertThat(UserService.normalizePhone("0049 151 1234567")).isEqualTo("01511234567");
    }

    @Test
    void auslaendischeNummerBehaeltPlus() {
        assertThat(UserService.normalizePhone("+43 660 1234567")).isEqualTo("+436601234567");
    }
}
