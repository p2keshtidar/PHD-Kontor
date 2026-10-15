package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/** S11–S12: kein Zugriff auf fremde Daten der neuen Funktionen; Antworten ohne Geheimnisse. */
class SecurityNewFeaturesTest extends FlowTestSupport {

    private void denied(TestUser intruder, HttpMethod method, String path, String body, Set<String> failures)
            throws Exception {
        int s = mvc.perform(request(method, path).header("Authorization", intruder.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        if (s != 403 && s != 404) {
            failures.add(method + " " + path + " → " + s);
        }
    }

    @Test
    void S11_keinZugriffAufFremdeGruppenVertraegeAntraege() throws Exception {
        TestUser anna = registerUser("Anna");
        TestUser bert = registerUser("Bert");
        TestUser eve = registerUser("Eve");
        long g = postJson(anna, "/api/groups", "{\"name\":\"WG\"}", 201).get("id").asLong();
        postJson(anna, "/api/groups/" + g + "/members", "{\"recipient\":\"" + bert.email() + "\"}", 200);
        long e = postJson(anna, "/api/groups/" + g + "/expenses", "{\"description\":\"Brot\",\"amountCents\":400}", 201)
                .get("expenses").get(0).get("id").asLong();
        long c = postJson(anna, "/api/contracts", "{\"name\":\"Strom\",\"kind\":\"STROM_GAS\",\"costCents\":5000}", 201)
                .get("id").asLong();
        long app = postJson(anna, "/api/market/applications", "{\"productId\":\"call-kranich\",\"consent\":true}", 201)
                .get("id").asLong();

        Set<String> f = new TreeSet<>();
        denied(eve, HttpMethod.GET, "/api/groups/" + g, "", f);
        denied(eve, HttpMethod.PUT, "/api/groups/" + g, "{\"name\":\"Meins\"}", f);
        denied(eve, HttpMethod.DELETE, "/api/groups/" + g, "", f);
        denied(eve, HttpMethod.POST, "/api/groups/" + g + "/members", "{\"recipient\":\"" + eve.email() + "\"}", f);
        denied(eve, HttpMethod.DELETE, "/api/groups/" + g + "/members/" + bert.id(), "", f);
        denied(eve, HttpMethod.POST, "/api/groups/" + g + "/expenses", "{\"description\":\"X\",\"amountCents\":100}", f);
        denied(eve, HttpMethod.DELETE, "/api/groups/" + g + "/expenses/" + e, "", f);
        denied(eve, HttpMethod.POST, "/api/groups/" + g + "/settlements/cash", "{\"fromUserId\":" + bert.id() + ",\"amountCents\":100}", f);
        denied(eve, HttpMethod.GET, "/api/contracts/" + c, "", f);
        denied(eve, HttpMethod.PUT, "/api/contracts/" + c, "{\"name\":\"X\",\"costCents\":1}", f);
        denied(eve, HttpMethod.POST, "/api/contracts/" + c + "/cancel", "{}", f);
        denied(eve, HttpMethod.POST, "/api/contracts/" + c + "/reactivate", "", f);
        denied(eve, HttpMethod.DELETE, "/api/contracts/" + c, "", f);
        denied(eve, HttpMethod.GET, "/api/documents/999999/content", "", f);
        denied(eve, HttpMethod.POST, "/api/market/applications/" + app + "/withdraw", "", f);
        denied(eve, HttpMethod.PUT, "/api/market/applications/" + app + "/note", "{\"note\":\"meins\"}", f);
        assertThat(f).as("Zugriffe auf fremde Daten, die nicht abgelehnt wurden").isEmpty();

        assertThat(getJson(eve, "/api/groups")).isEmpty();
        assertThat(getJson(eve, "/api/contracts").get("contracts")).isEmpty();
        assertThat(getJson(eve, "/api/market/applications")).isEmpty();
        assertThat(getJson(anna, "/api/groups/" + g).get("expenses")).hasSize(1);
        assertThat(getJson(anna, "/api/contracts/" + c).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void S12_antwortenOhneGeheimnisse() throws Exception {
        TestUser u = registerUser("Geheim");
        postJson(u, "/api/credit-card", "{\"limitCents\":100000}", 201);
        for (String path : new String[] {"/api/credit-card", "/api/me/audit-log", "/api/dashboard", "/api/groups",
                "/api/contracts", "/api/documents", "/api/health-score"}) {
            String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .header("Authorization", u.bearer())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).as(path).doesNotContain("passwordHash").doesNotContain("$2a$").doesNotContain("geheim12345")
                    .doesNotContainPattern("\\b\\d{16}\\b").doesNotContain("totpSecret");
        }
    }
}
