package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.auth.Totp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** /K050/ Zwei-Faktor-Anmeldung (T34, T35). */
class TwoFactorFlowTest extends FlowTestSupport {

    private static String codeAt(String secret, long step) {
        return Totp.code(Totp.base32Decode(secret), step);
    }

    private org.springframework.test.web.servlet.ResultActions loginWith(String email, String code) throws Exception {
        String body = code == null
                ? "{\"email\":\"" + email + "\",\"password\":\"geheim12345\"}"
                : "{\"email\":\"" + email + "\",\"password\":\"geheim12345\",\"code\":\"" + code + "\"}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String enable(TestUser u) throws Exception {
        JsonNode setup = postJson(u, "/api/me/2fa/setup", "{}", 200);
        String secret = setup.get("secret").asText();
        assertThat(setup.get("otpauthUri").asText()).startsWith("otpauth://totp/Projektbank").contains(secret);
        postJson(u, "/api/me/2fa/enable", "{\"code\":\"000000x\"}", 400);              // ungültig
        long step = Totp.step(Instant.now());
        postJson(u, "/api/me/2fa/enable", "{\"code\":\"" + codeAt(secret, step) + "\"}", 204);
        assertThat(getJson(u, "/api/me").get("twoFactorEnabled").asBoolean()).isTrue();
        return secret;
    }

    @Test
    void T34_anmeldungMitCode() throws Exception {
        TestUser u = registerUser("Zora");
        assertThat(getJson(u, "/api/me").get("twoFactorEnabled").asBoolean()).isFalse();
        String secret = enable(u);
        long step = Totp.step(Instant.now());

        loginWith(u.email(), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_REQUIRED"));
        String wrong = codeAt(secret, step).equals("123456") ? "654321" : "123456";
        loginWith(u.email(), wrong).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));

        // Code des nächsten Abschnitts ist erlaubt (±30 s Toleranz); derselbe Code nur einmal
        String next = codeAt(secret, step + 1);
        loginWith(u.email(), next).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").exists());
        loginWith(u.email(), next).andExpect(status().isUnauthorized());
    }

    @Test
    void T35_ausschaltenNurMitPasswortUndCode() throws Exception {
        TestUser u = registerUser("Yuri");
        String secret = enable(u);
        long step = Totp.step(Instant.now());
        postJson(u, "/api/me/2fa/disable", "{\"password\":\"falsch\",\"code\":\"" + codeAt(secret, step + 1) + "\"}", 400);
        postJson(u, "/api/me/2fa/disable", "{\"password\":\"geheim12345\",\"code\":\"" + codeAt(secret, step + 1) + "\"}", 204);
        assertThat(getJson(u, "/api/me").get("twoFactorEnabled").asBoolean()).isFalse();
        loginWith(u.email(), null).andExpect(status().isOk());
    }
}
