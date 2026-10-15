package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T134–T136: API-Versionierung (/api/v1) und Prüfprotokoll für sicherheitsrelevante Aktionen. */
class ApiVersionAndAuditTest extends FlowTestSupport {

    private List<String> actions(TestUser u) throws Exception {
        List<String> a = new ArrayList<>();
        getJson(u, "/api/me/audit-log?size=100").get("items").forEach(x -> a.add(x.get("action").asText()));
        return a;
    }

    @Test
    void T134_v1UndKurzformLiefernDasselbe() throws Exception {
        TestUser u = registerUser("Vic");
        String a = mvc.perform(get("/api/v1/accounts").header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andExpect(header().string("API-Version", "1"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String b = mvc.perform(get("/api/accounts").header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andExpect(header().string("API-Version", "1"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(a).isEqualTo(b);
        // öffentliche Endpunkte auch unter v1, geschützte auch unter v1 nur mit Anmeldung
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.email() + "\",\"password\":\"geheim12345\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        // unbekannte Version
        int s = mvc.perform(get("/api/v9/accounts").header("Authorization", u.bearer())).andReturn().getResponse().getStatus();
        assertThat(s).isEqualTo(404);
    }

    @Test
    void T135_pruefprotokollOhneGeheimnisse() throws Exception {
        TestUser u = registerUser("Audra");
        TestUser b = registerUser("Bodo");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.email() + "\",\"password\":\"falsch-falsch\"}")).andExpect(status().isUnauthorized());
        login(u.email(), "geheim12345");
        mvc.perform(post("/api/transfers").header("Authorization", u.bearer()).header("Idempotency-Key", "audit-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"" + b.iban() + "\",\"amountCents\":1234,\"description\":\"Test\"}"))
                .andExpect(status().is2xxSuccessful());
        putJson(u, "/api/me/password", "{\"oldPassword\":\"geheim12345\",\"newPassword\":\"neuesPasswort123\"}", 204);
        assertThat(actions(u)).contains("REGISTERED", "LOGIN_FAILED", "LOGIN", "TRANSFER_SENT", "PASSWORD_CHANGED");
        String raw = mvc.perform(get("/api/me/audit-log?size=100").header("Authorization", u.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("falsch-falsch").doesNotContain("neuesPasswort123").doesNotContain(b.iban())
                .contains("12,34 €").contains("Überweisung gesendet");
    }

    @Test
    void T136_pruefprotokollFuerNeueFunktionen() throws Exception {
        TestUser u = registerUser("Prue");
        TestUser b = registerUser("Paul");
        long g = postJson(u, "/api/groups", "{\"name\":\"WG\"}", 201).get("id").asLong();
        postJson(u, "/api/groups/" + g + "/members", "{\"recipient\":\"" + b.email() + "\"}", 200);
        postJson(u, "/api/groups/" + g + "/expenses", "{\"description\":\"Brot\",\"amountCents\":400}", 201);
        postJson(u, "/api/groups/" + g + "/settlements/cash", "{\"fromUserId\":" + b.id() + ",\"amountCents\":200}", 200);
        long c = postJson(u, "/api/contracts", "{\"name\":\"Strom\",\"kind\":\"STROM_GAS\",\"costCents\":5000}", 201)
                .get("id").asLong();
        postJson(u, "/api/contracts/" + c + "/cancel", "{}", 200);
        assertThat(actions(u)).contains("GROUP_CASH_CONFIRMED", "CONTRACT_CANCEL_NOTED");
        JsonNode en = json.readTree(mvc.perform(get("/api/me/audit-log").header("Authorization", u.bearer())
                .header("Accept-Language", "en")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> labels = new ArrayList<>();
        en.get("items").forEach(x -> labels.add(x.get("actionLabel").asText()));
        assertThat(labels).contains("Cancellation recorded", "Cash payment confirmed", "Account opened");
    }
}
