package de.frauas.girokonto.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** SEC01–SEC04: Anfragebegrenzung und Schutz vor Entwicklungs-Schlüsseln im Betrieb. */
class RateLimitAndSecretsTest {

    @Test
    void SEC01_zaehlfensterProMinute() {
        Instant t0 = Instant.parse("2026-10-15T10:00:10Z");
        RateLimiter l = new RateLimiter(Clock.fixed(t0, ZoneOffset.UTC));
        for (int i = 0; i < 5; i++) {
            assertThat(l.tryAcquire("a", 5)).isZero();
        }
        assertThat(l.tryAcquire("a", 5)).isEqualTo(50);   // 50 s bis zur nächsten Minute
        assertThat(l.tryAcquire("b", 5)).isZero();        // anderer Schlüssel unabhängig
        RateLimiter next = new RateLimiter(Clock.fixed(t0.plusSeconds(60), ZoneOffset.UTC));
        assertThat(next.tryAcquire("a", 5)).isZero();
    }

    @Test
    void SEC02_filterAntwortet429MitRetryAfter() throws Exception {
        RateLimitFilter f = new RateLimitFilter(new RateLimitProperties(true, 3, 1000));
        int last = 0;
        MockHttpServletResponse res = null;
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.7");
            res = new MockHttpServletResponse();
            f.doFilter(req, res, new MockFilterChain());
            last = res.getStatus();
        }
        assertThat(last).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotBlank();
        assertThat(res.getContentAsString()).contains("RATE_LIMITED");
        // andere Adresse und Web-App-Dateien sind nicht betroffen
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/auth/login");
        other.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        f.doFilter(other, ok, new MockFilterChain());
        assertThat(ok.getStatus()).isEqualTo(200);
        MockHttpServletRequest page = new MockHttpServletRequest("GET", "/index.html");
        page.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse pageRes = new MockHttpServletResponse();
        f.doFilter(page, pageRes, new MockFilterChain());
        assertThat(pageRes.getStatus()).isEqualTo(200);
    }

    @Test
    void SEC03_produktionMitEntwicklungsSchluesselnStartetNicht() {
        AppProperties dev = props(SecretsGuard.DEV_JWT, SecretsGuard.DEV_CARD_KEY, SecretsGuard.DEV_ADMIN_PASSWORD);
        assertThat(SecretsGuard.insecure(dev)).containsExactly("JWT_SECRET", "CARD_KEY", "ADMIN_PASSWORD");
        assertThatThrownBy(() -> SecretsGuard.check("production", dev)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        SecretsGuard.check("development", dev);   // nur Warnung
    }

    @Test
    void SEC04_eigeneSchluesselImBetriebErlaubt() {
        AppProperties prod = props("eigenesGeheimnis-mindestens-32-Byte-lang-xxxxxxx", "andererKey", "langesPasswort!");
        assertThat(SecretsGuard.insecure(prod)).isEmpty();
        SecretsGuard.check("production", prod);
    }

    private static AppProperties props(String jwt, String cardKey, String adminPw) {
        return new AppProperties(new AppProperties.Jwt(jwt, 15, 7), null, 0, null, null,
                new AppProperties.Card(cardKey, "999999", 1, 2), null, new AppProperties.Admin("a@b.de", adminPw),
                null, new AppProperties.Cors(List.of()));
    }
}
