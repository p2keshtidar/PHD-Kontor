package de.frauas.girokonto.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.frauas.girokonto.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Testfälle T06, T07 und weitere zu /F310/ bis /F340/. */
class CardFlowTest extends IntegrationTestBase {

    private ResultActions pay(TestUser u, long cents, boolean online) throws Exception {
        return mvc.perform(post("/api/simulator/card-payment").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"merchant\":\"Bäckerei Schmidt\",\"amountCents\":" + cents + ",\"online\":" + online + "}"));
    }

    @Test
    void karteWirdBeiRegistrierungAngelegtUndIstMaskiert() throws Exception {
        TestUser u = registerUser("Anna");
        mvc.perform(get("/api/cards/me").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.maskedNumber").value(org.hamcrest.Matchers.startsWith("••••")))
                .andExpect(jsonPath("$.holderName").value("ANNA MUSTER"))
                .andExpect(jsonPath("$.dailyLimitCents").value(100000));
    }

    @Test
    void detailsNurMitRichtigemPasswort() throws Exception {
        TestUser u = registerUser("Anna");
        mvc.perform(post("/api/cards/me/details").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"falschfalsch\"}"))
                .andExpect(status().isBadRequest());
        String body = mvc.perform(post("/api/cards/me/details").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"geheim12345\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String number = json.readTree(body).get("number").asText();
        assertThat(CardNumberGenerator.isValid(number)).isTrue();
        assertThat(json.readTree(body).get("cvc").asText()).hasSize(3);
    }

    @Test
    void zahlungWirdGebucht() throws Exception {
        TestUser u = registerUser("Anna");
        pay(u, 1290, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));
        assertThat(balance(u)).isEqualTo(100000 - 1290);
        mvc.perform(get("/api/accounts/" + u.accountId() + "/bookings").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$.items[0].type").value("CARD"))
                .andExpect(jsonPath("$.items[0].counterpartyName").value("Bäckerei Schmidt"));
        mvc.perform(get("/api/cards/me").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$.spentTodayCents").value(1290));
    }

    @Test
    void T06_gesperrteKarteLehntAb() throws Exception {
        TestUser u = registerUser("Anna");
        mvc.perform(post("/api/cards/me/lock").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$.status").value("LOCKED"));
        pay(u, 500, false).andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.reasonCode").value("CARD_LOCKED"));
        assertThat(balance(u)).isEqualTo(100000);

        mvc.perform(post("/api/cards/me/unlock").header("Authorization", u.bearer()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        pay(u, 500, false).andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void T07_tageslimitUndOnlineSchalter() throws Exception {
        TestUser u = registerUser("Anna");
        mvc.perform(put("/api/cards/me/limits").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimitCents\":5000,\"onlineEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimitCents").value(5000));

        pay(u, 1000, true).andExpect(jsonPath("$.reasonCode").value("ONLINE_DISABLED"));
        pay(u, 4000, false).andExpect(jsonPath("$.approved").value(true));
        pay(u, 1001, false).andExpect(jsonPath("$.reasonCode").value("DAILY_LIMIT"));
        pay(u, 1000, false).andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void limitUeber2000EuroAbgelehnt() throws Exception {
        TestUser u = registerUser("Anna");
        mvc.perform(put("/api/cards/me/limits").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimitCents\":200001,\"onlineEnabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nichtGenugGuthaben() throws Exception {
        TestUser u = registerUser("Anna");
        mvc.perform(put("/api/cards/me/limits").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimitCents\":200000,\"onlineEnabled\":true}"))
                .andExpect(status().isOk());
        pay(u, 100001, false).andExpect(jsonPath("$.reasonCode").value("INSUFFICIENT_FUNDS"));
    }
}
