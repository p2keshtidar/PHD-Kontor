package de.frauas.girokonto;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Gemeinsame Hilfen für Ablauf-Tests ab Phase 5 (Antworten immer als UTF-8 gelesen). */
abstract class FlowTestSupport extends IntegrationTestBase {

    protected String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("accessToken").asText();
    }

    /** Nach dem Vorspulen neu anmelden. */
    protected TestUser relogin(TestUser u) throws Exception {
        return new TestUser(u.id(), u.email(), u.phone(), login(u.email(), "geheim12345"), u.accountId(), u.iban());
    }

    /** Simulationszeit vorspulen: field = "days" oder "months". */
    protected void advance(String field, int value) throws Exception {
        mvc.perform(post("/api/admin/clock/advance")
                        .header("Authorization", "Bearer " + login("admin@projektbank.de", "admin12345"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"" + field + "\":" + value + "}"))
                .andExpect(status().isOk());
    }

    /** Vorspulen bis zum nächsten Tag mit diesem Tag im Monat (mindestens 1 Tag). */
    protected LocalDate advanceToNextDay(TestUser u, int dayOfMonth) throws Exception {
        LocalDate t = today(u);
        LocalDate target = t.getDayOfMonth() < dayOfMonth ? t.withDayOfMonth(dayOfMonth)
                : t.plusMonths(1).withDayOfMonth(dayOfMonth);
        advance("days", (int) (target.toEpochDay() - t.toEpochDay()));
        return target;
    }

    protected LocalDate today(TestUser u) throws Exception {
        return LocalDate.parse(getJson(u, "/api/clock").get("date").asText());
    }

    protected TestUser userWithDemo(String name) throws Exception {
        TestUser u = registerUser(name);
        mvc.perform(post("/api/demo/sample-months").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"profile\":\"EINSTEIGER\"}"))
                .andExpect(status().isCreated());
        return u;
    }

    protected JsonNode getJson(TestUser u, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode postJson(TestUser u, String path, String body, int expected) throws Exception {
        return send(post(path), u, body, expected);
    }

    protected JsonNode putJson(TestUser u, String path, String body, int expected) throws Exception {
        return send(put(path), u, body, expected);
    }

    protected void deletePath(TestUser u, String path, int expected) throws Exception {
        mvc.perform(delete(path).header("Authorization", u.bearer())).andExpect(status().is(expected));
    }

    private JsonNode send(MockHttpServletRequestBuilder req, TestUser u, String body, int expected) throws Exception {
        String r = mvc.perform(req.header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return r.isEmpty() ? null : json.readTree(r);
    }

    protected List<String> notificationTexts(TestUser u) throws Exception {
        List<String> texts = new ArrayList<>();
        for (JsonNode n : getJson(u, "/api/notifications?size=50").get("items")) {
            texts.add(n.get("title").asText() + " | " + n.get("body").asText());
        }
        return texts;
    }
}
