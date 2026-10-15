package de.frauas.girokonto.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.booking.BookingLineRepository;
import de.frauas.girokonto.common.IbanGenerator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import de.frauas.girokonto.IntegrationTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integrationstest mit echter PostgreSQL-Datenbank in Docker (Testcontainers).
 * Deckt Testfälle T01, T02, T14 und T15 aus dem Pflichtenheft ab.
 */
class AuthFlowTest extends IntegrationTestBase {

    @Autowired BookingLineRepository bookingLines;
    @Autowired IbanGenerator ibanGenerator;

    private String registerBody(String email, LocalDate birthDate) {
        return registerBody(email, uniquePhone(), birthDate);
    }

    private JsonNode register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, LocalDate.now().minusYears(25))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void T01_registrierungLegtKontoMitIbanUndStartguthabenAn() throws Exception {
        JsonNode auth = register(uniqueEmail());
        String token = auth.get("accessToken").asText();
        assertThat(auth.get("refreshToken").asText()).isNotBlank();

        String accounts = mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("MAIN"))
                .andExpect(jsonPath("$[0].balanceCents").value(100000))
                .andReturn().getResponse().getContentAsString();
        String iban = json.readTree(accounts).get(0).get("iban").asText();
        assertThat(ibanGenerator.isValid(iban)).isTrue();
    }

    @Test
    void T02_registrierungUnter18WirdAbgelehnt() throws Exception {
        // Alter wird am (simulierten) Tagesdatum des Servers gemessen – andere Tests spulen die Zeit vor
        String token = register(uniqueEmail()).get("accessToken").asText();
        LocalDate serverToday = LocalDate.parse(json.readTree(mvc.perform(get("/api/clock")
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString())
                .get("date").asText());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), serverToday.minusYears(18).plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNDERAGE"));
    }

    @Test
    void doppelteEmailWirdAbgelehnt() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, LocalDate.now().minusYears(30))))
                .andExpect(status().isConflict());
    }

    @Test
    void loginRefreshUndLogout() throws Exception {
        String email = uniqueEmail();
        register(email);

        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"geheim12345\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tokens = json.readTree(login);
        String access = tokens.get("accessToken").asText();
        String refresh = tokens.get("refreshToken").asText();

        // Refresh-Token ist nur einmal verwendbar
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // Nach dem Logout ist das alte Access-Token ungültig
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nachFuenfFehlversuchenGesperrt() throws Exception {
        String email = uniqueEmail();
        register(email);
        String wrong = "{\"email\":\"" + email + "\",\"password\":\"falschfalsch\"}";
        for (int i = 1; i <= 4; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isLocked());
        // auch mit richtigem Passwort vorerst gesperrt
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"geheim12345\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void T14_ohneTokenKeinZugriff() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer kaputt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void T15_summeAllerBuchungszeilenIstNull() throws Exception {
        register(uniqueEmail());
        register(uniqueEmail());
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }
}
