package de.frauas.girokonto.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.frauas.girokonto.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** /F510/ und /F520/: Meldungen bei Buchungen, Zähler, gelesen markieren. */
class NotificationFlowTest extends IntegrationTestBase {

    @Test
    void ueberweisungErzeugtMeldungenFuerBeide() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        // nach der Registrierung: je 1 Willkommensmeldung
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.count").value(1));

        mvc.perform(post("/api/transfers").header("Authorization", a.bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"" + b.iban() + "\",\"amountCents\":2500,\"description\":\"Kino\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/notifications").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.items[0].title").value("Geld erhalten"))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mvc.perform(get("/api/notifications").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER_OUT"));

        mvc.perform(get("/api/notifications/unread-count").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(put("/api/notifications/read-all").header("Authorization", b.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", b.bearer()))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void abgelehnteKartenzahlungErzeugtMeldung() throws Exception {
        TestUser a = registerUser("Anna");
        mvc.perform(post("/api/cards/me/lock").header("Authorization", a.bearer())).andExpect(status().isOk());
        mvc.perform(post("/api/simulator/card-payment").header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchant\":\"Kiosk\",\"amountCents\":300,\"online\":false}"))
                .andExpect(jsonPath("$.approved").value(false));
        mvc.perform(get("/api/notifications").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.items[0].type").value("CARD_DECLINED"));
    }

    @Test
    void fremdeMeldungNichtAenderbar() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        String body = mvc.perform(get("/api/notifications").header("Authorization", a.bearer()))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("items").get(0).get("id").asLong();
        mvc.perform(put("/api/notifications/" + id + "/read").header("Authorization", b.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/notifications/" + id + "/read").header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());
    }
}
