package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T46–T48: Sprache pro Nutzer und englische Server-Antworten. */
class I18nFlowTest extends FlowTestSupport {

    private JsonNode getEn(TestUser u, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", u.bearer()).header("Accept-Language", "en"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode postEn(TestUser u, String path, String body, int expected) throws Exception {
        var req = post(path).header("Accept-Language", "en-US,en;q=0.9").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString()).content(body);
        if (u != null) {
            req.header("Authorization", u.bearer());
        }
        return json.readTree(mvc.perform(req).andExpect(status().is(expected)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void T46_spracheWirdBeimNutzerGespeichert() throws Exception {
        TestUser u = registerUser("Lena");
        assertThat(getJson(u, "/api/me").get("language").isNull()).isTrue();
        putJson(u, "/api/me/language", "{\"language\":\"en\"}", 204);
        assertThat(getJson(u, "/api/me").get("language").asText()).isEqualTo("en");
        assertThat(putJson(u, "/api/me/language", "{\"language\":\"fr\"}", 400).get("code").asText())
                .isEqualTo("VALIDATION_FAILED");
        putJson(u, "/api/me/language", "{}", 400);
        putJson(u, "/api/me/language", "{\"language\":\"de\"}", 204);
        assertThat(getJson(u, "/api/me").get("language").asText()).isEqualTo("de");
    }

    @Test
    void T47_fehlermeldungenAufEnglisch() throws Exception {
        TestUser a = registerUser("Mona");
        TestUser b = registerUser("Nils");
        JsonNode e = postEn(a, "/api/transfers", "{\"recipient\":\"" + b.email() + "\",\"amountCents\":999999}", 400);
        assertThat(e.get("message").asText()).isIn("Insufficient funds. Available: €1,000.00.",
                "The amount is higher than the daily limit.");
        JsonNode v = postEn(null, "/api/auth/register", "{\"email\":\"kaputt\",\"password\":\"x\"}", 400);
        assertThat(v.get("message").asText()).isEqualTo("Please check your input.");
        assertThat(v.get("fieldErrors").get("email").asText()).isEqualTo("Email is invalid.");
        assertThat(v.get("fieldErrors").get("firstName").asText()).isEqualTo("First name is missing.");
        // ohne Accept-Language: Deutsch wie bisher
        assertThat(postJson(a, "/api/transfers/check", "{\"recipient\":\"" + a.email() + "\",\"amountCents\":100}", 400)
                .get("message").asText()).startsWith("Überweisungen an dich selbst");
        assertThat(postEn(a, "/api/transfers/check", "{\"recipient\":\"" + a.email() + "\",\"amountCents\":100}", 400)
                .get("message").asText()).isEqualTo("You cannot transfer to yourself. Use sub-accounts for that.");
    }

    @Test
    void T48_benachrichtigungenUndBezeichnungenAufEnglisch() throws Exception {
        TestUser a = userWithDemo("Olga");
        TestUser b = registerUser("Paul");
        mvc.perform(post("/api/transfers").header("Authorization", a.bearer()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"" + b.email() + "\",\"amountCents\":1234,\"description\":\"Pizza\"}"))
                .andExpect(status().isCreated());
        postJson(a, "/api/entries", "{\"kind\":\"EXPENSE\",\"title\":\"Rent\",\"category\":\"WOHNEN\",\"amountCents\":1000,"
                + "\"intervalMonths\":1,\"date\":\"" + today(a).plusMonths(1).withDayOfMonth(1) + "\"}", 201);
        postJson(a, "/api/loans", """
                {"bank":"NORDLICHT","purpose":"AUTO","principalCents":500000,"equityCents":0,"termMonths":24}
                """, 201);

        JsonNode notesB = getEn(b, "/api/notifications?size=50").get("items");
        assertThat(notesB.findValuesAsText("title")).contains("Welcome to Projektbank", "Money received");
        assertThat(notesB.findValuesAsText("body")).anyMatch(s -> s.startsWith("+€12.34 from Olga"));
        JsonNode notesA = getEn(a, "/api/notifications?size=50").get("items");
        for (JsonNode n : notesA) {
            String text = n.get("title").asText() + " " + n.get("body").asText();
            assertThat(text).as(text).doesNotContainPattern("[äöüÄÖÜß]| der | und | von | ist |Konto ");
        }
        assertThat(notesA.findValuesAsText("title")).contains("Loan paid out", "Transfer sent");

        JsonNode entries = getEn(a, "/api/entries").get("entries");
        assertThat(entries.get(0).get("intervalLabel").asText()).isEqualTo("monthly");
        assertThat(entries.get(0).get("categoryLabel").asText()).isEqualTo("Housing");
        assertThat(getEn(a, "/api/entries/categories").findValuesAsText("label")).contains("Salary", "Groceries");
        assertThat(getEn(a, "/api/accounts").get(0).get("name").asText()).isEqualTo("Current account");
        assertThat(getJson(a, "/api/accounts").get(0).get("name").asText()).isEqualTo("Girokonto");
        // Codes bleiben unverändert
        assertThat(getEn(a, "/api/entries").get("entries").get(0).get("category").asText()).isEqualTo("WOHNEN");
    }
}
