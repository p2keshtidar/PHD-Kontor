package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T124–T128: Finanzassistent – Antworten nur aus echten App-Daten, Monatsbericht, Szenarien, Englisch. */
class AssistantFlowTest extends FlowTestSupport {

    private JsonNode ask(TestUser u, String q) throws Exception {
        return postJson(u, "/api/assistant/ask", json.writeValueAsString(java.util.Map.of("question", q)), 200);
    }

    /** Vorletzter Eintrag der Monatsübersicht = letzter voller Monat. */
    private JsonNode lastFullMonth(TestUser u) throws Exception {
        JsonNode months = getJson(u, "/api/insights/months?count=2");
        return months.get(0);
    }

    private static long category(JsonNode month, String cat) {
        for (JsonNode c : month.get("categories")) {
            if (c.get("category").asText().equals(cat)) {
                return c.get("cents").asLong();
            }
        }
        return 0;
    }

    @Test
    void T124_kategorieFrageNutztDieEchtenZahlen() throws Exception {
        TestUser u = userWithDemo("Asta");
        JsonNode month = lastFullMonth(u);
        JsonNode a = ask(u, "Wie viel habe ich letzten Monat für Lebensmittel ausgegeben?");
        assertThat(a.get("intent").asText()).isEqualTo("CATEGORY");
        long expected = category(month, "LEBENSMITTEL");
        assertThat(expected).isPositive();
        assertThat(a.get("facts").get(0).get("amountCents").asLong()).isEqualTo(expected);
        assertThat(a.get("text").asText()).contains("Lebensmittel").contains(String.format(java.util.Locale.GERMANY, "%,.2f €", expected / 100.0));
        assertThat(a.get("disclaimer").asText()).contains("keine Finanzberatung");
        JsonNode top = ask(u, "Wofür gebe ich am meisten aus?");
        assertThat(top.get("intent").asText()).isEqualTo("TOP_CATEGORIES");
        assertThat(top.get("facts")).hasSizeBetween(1, 3);
    }

    @Test
    void T125_monatsberichtStimmtMitDerMonatsuebersicht() throws Exception {
        TestUser u = userWithDemo("Bodo");
        JsonNode month = lastFullMonth(u);
        JsonNode r = getJson(u, "/api/assistant/report");
        assertThat(r.get("month").asText()).isEqualTo(month.get("month").asText());
        assertThat(r.get("incomeCents").asLong()).isEqualTo(month.get("incomeCents").asLong());
        assertThat(r.get("expenseCents").asLong()).isEqualTo(month.get("expenseCents").asLong());
        assertThat(r.get("freeCents").asLong()).isEqualTo(month.get("freeCents").asLong());
        assertThat(r.get("notes")).isNotEmpty();
        assertThat(r.get("notes").get(0).asText()).startsWith("Einnahmen ");
        mvcExpect(u, "/api/assistant/report?month=2001-01", 400);
        mvcExpect(u, "/api/assistant/report?month=kaputt", 400);
        assertThat(ask(u, "Zeig mir den Monatsbericht für letzten Monat.").get("intent").asText()).isEqualTo("REPORT");
    }

    @Test
    void T126_szenarien() throws Exception {
        TestUser u = userWithDemo("Cleo");
        JsonNode s = postJson(u, "/api/assistant/scenario", "{\"type\":\"CUT_CATEGORY\",\"category\":\"FREIZEIT\",\"amountCents\":2000}", 200);
        assertThat(s.get("yearlyEffectCents").asLong()).isEqualTo(24_000);
        assertThat(s.get("newFreeCents").asLong()).isEqualTo(s.get("currentFreeCents").asLong() + 2_000);
        assertThat(s.get("disclaimer").asText()).contains("Rechenbeispiel");
        postJson(u, "/api/assistant/scenario", "{\"type\":\"CUT_CATEGORY\",\"category\":\"FREIZEIT\",\"amountCents\":9000000}", 400);
        JsonNode t = postJson(u, "/api/assistant/scenario", "{\"type\":\"EXTRA_SAVING\",\"amountCents\":20000,\"targetCents\":300100}", 200);
        assertThat(t.get("monthsToTarget").asInt()).isEqualTo(16);
        JsonNode i = postJson(u, "/api/assistant/scenario", "{\"type\":\"INCOME_CHANGE\",\"percent\":-10}", 200);
        assertThat(i.get("monthlyEffectCents").asLong()).isNegative();
        postJson(u, "/api/assistant/scenario", "{\"type\":\"INCOME_CHANGE\",\"percent\":90}", 400);
        postJson(u, "/api/assistant/scenario", "{\"type\":\"LOTTO\"}", 400);
    }

    @Test
    void T127_ohneDatenKeineErfundenenZahlen() throws Exception {
        TestUser u = registerUser("Leer");
        assertThat(ask(u, "Wo kann ich sparen?").get("text").asText()).contains("mindestens einen vollen Monat");
        assertThat(ask(u, "Wie wird das Wetter morgen?").get("intent").asText()).isEqualTo("UNKNOWN");
        postJson(u, "/api/assistant/ask", "{\"question\":\"\"}", 400);
        postJson(u, "/api/assistant/scenario", "{\"type\":\"INCOME_CHANGE\",\"percent\":10}", 409);
        assertThat(getJson(u, "/api/assistant/questions")).hasSize(6);
    }

    @Test
    void T128_englisch() throws Exception {
        TestUser u = userWithDemo("Ezra");
        String body = mvc.perform(post("/api/assistant/ask").header("Authorization", u.bearer())
                        .header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What do I spend the most on?\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode a = json.readTree(body);
        assertThat(a.get("intent").asText()).isEqualTo("TOP_CATEGORIES");
        assertThat(a.get("text").asText()).startsWith("Most spending");
        assertThat(a.get("disclaimer").asText()).contains("no financial advice");
    }

    private void mvcExpect(TestUser u, String path, int expected) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .header("Authorization", u.bearer())).andExpect(status().is(expected));
    }
}
