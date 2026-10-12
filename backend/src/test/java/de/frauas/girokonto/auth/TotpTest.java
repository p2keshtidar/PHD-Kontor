package de.frauas.girokonto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** Testwerte aus RFC 6238, Anhang B (SHA1, Schlüssel "12345678901234567890"), letzte 6 Ziffern. */
    @Test
    void rfc6238Testwerte() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.code(key, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(key, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(key, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.code(key, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void base32HinUndZurueck() {
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        String b32 = Totp.base32Encode(key);
        assertThat(b32).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.base32Decode(b32)).isEqualTo(key);
        assertThat(Totp.base32Decode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")).isEqualTo(key);
    }

    @Test
    void pruefungMitToleranzUndOhneWiederverwendung() {
        String secret = Totp.newSecret(new SecureRandom());
        assertThat(secret).hasSize(32);
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        long step = Totp.step(now);
        byte[] key = Totp.base32Decode(secret);
        assertThat(Totp.verify(secret, Totp.code(key, step), now, null)).isEqualTo(step);
        assertThat(Totp.verify(secret, Totp.code(key, step - 1), now, null)).isEqualTo(step - 1);   // 30 s alt
        assertThat(Totp.verify(secret, Totp.code(key, step + 2), now, null)).isEqualTo(-1);         // zu weit
        assertThat(Totp.verify(secret, Totp.code(key, step), now, step)).isEqualTo(-1);             // schon benutzt
        assertThat(Totp.verify(secret, "12345", now, null)).isEqualTo(-1);
        assertThat(Totp.verify(secret, null, now, null)).isEqualTo(-1);
    }

    @Test
    void linkFuerDieApp() {
        assertThat(Totp.otpauthUri("Projektbank", "erika@example.org", "ABC"))
                .isEqualTo("otpauth://totp/Projektbank%3Aerika%40example.org?secret=ABC&issuer=Projektbank"
                        + "&algorithm=SHA1&digits=6&period=30");
    }
}
