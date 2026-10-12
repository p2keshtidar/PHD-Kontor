package de.frauas.girokonto.subaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Testfall T08 und /F410/ bis /F430/. */
class SubaccountFlowTest extends IntegrationTestBase {

    private long createSub(TestUser u, String name) throws Exception {
        String body = mvc.perform(post("/api/subaccounts").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"icon\":\"flight\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void move(TestUser u, long from, long to, long cents, int expectedStatus) throws Exception {
        mvc.perform(post("/api/subaccounts/move").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":" + from + ",\"toAccountId\":" + to + ",\"amountCents\":" + cents + "}"))
                .andExpect(status().is(expectedStatus));
    }

    private long total(TestUser u) throws Exception {
        JsonNode accounts = json.readTree(mvc.perform(get("/api/accounts").header("Authorization", u.bearer()))
                .andReturn().getResponse().getContentAsString());
        long sum = 0;
        for (JsonNode a : accounts) {
            sum += a.get("balanceCents").asLong();
        }
        return sum;
    }

    @Test
    void T08_anlegenUmbuchenAufloesen() throws Exception {
        TestUser u = registerUser("Anna");
        long sub = createSub(u, "Urlaub");

        move(u, u.accountId(), sub, 4000, 204);
        assertThat(balance(u)).isEqualTo(96000);
        assertThat(total(u)).isEqualTo(100000);   // Gesamtsumme bleibt gleich

        move(u, sub, u.accountId(), 5000, 400);   // mehr als vorhanden

        mvc.perform(delete("/api/subaccounts/" + sub).header("Authorization", u.bearer()))
                .andExpect(status().isNoContent());
        assertThat(balance(u)).isEqualTo(100000);
        mvc.perform(get("/api/accounts").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void hoechstensZehnUnterkonten() throws Exception {
        TestUser u = registerUser("Anna");
        for (int i = 1; i <= 10; i++) {
            createSub(u, "Topf " + i);
        }
        mvc.perform(post("/api/subaccounts").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Zu viel\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_SUBACCOUNTS"));
    }

    @Test
    void fremdesKontoGeht_nicht() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        long subB = createSub(b, "Bens Topf");
        move(a, a.accountId(), subB, 100, 403);
        mvc.perform(delete("/api/subaccounts/" + subB).header("Authorization", a.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void girokontoKannNichtAufgeloestWerden() throws Exception {
        TestUser u = registerUser("Anna");
        mvc.perform(delete("/api/subaccounts/" + u.accountId()).header("Authorization", u.bearer()))
                .andExpect(status().isBadRequest());
    }
}
