package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** T108–T112: Finanz-Marktplatz mit simulierten Anbietern, Favoriten, Vergleich, simulierte Anträge. */
class MarketFlowTest extends FlowTestSupport {

    @Test
    void T108_vergleichMitBeispielrechnungUndFiltern() throws Exception {
        TestUser u = registerUser("Mia");
        JsonNode call = getJson(u, "/api/market/products?category=CALL&amountCents=1000000");
        assertThat(call.get("disclaimer").asText()).contains("fiktive Anbieter");
        JsonNode first = call.get("products").get(0);
        assertThat(first.get("id").asText()).isEqualTo("call-mosaik");                 // 3,10 %
        assertThat(first.get("exampleCents").asLong()).isEqualTo(31_000);             // 10.000 € × 3,10 %
        assertThat(first.get("provider").asText()).endsWith("(fiktiv)");
        assertThat(first.get("simulation").asBoolean()).isTrue();
        // unter der Mindestanlage rutscht das Angebot nach hinten
        JsonNode small = getJson(u, "/api/market/products?category=CALL&amountCents=50000").get("products");
        assertThat(small.get(small.size() - 1).get("id").asText()).isEqualTo("call-mosaik");
        assertThat(small.get(small.size() - 1).get("eligible").asBoolean()).isFalse();
        JsonNode fixed = getJson(u, "/api/market/products?category=FIXED&termMonths=24").get("products");
        assertThat(fixed).hasSize(1);
        assertThat(fixed.get(0).get("exampleCents").asLong()).isEqualTo(56_000);     // 2,80 % × 2 Jahre
        JsonNode giro = getJson(u, "/api/market/products?category=GIRO&maxYearlyFeeCents=3000").get("products");
        assertThat(giro).hasSize(2);
        assertThat(giro.get(0).get("exampleCents").asLong()).isZero();
        JsonNode depot = getJson(u, "/api/market/products?category=DEPOT").get("products");
        assertThat(depot.get(0).get("exampleCents").asLong()).isEqualTo(1_200);       // 12 Käufe × 1 €
        assertThat(mvc.perform(get("/api/market/products?category=GOLD").header("Authorization", u.bearer()))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void T109_favoritenUndVergleichsliste() throws Exception {
        TestUser u = registerUser("Fritz");
        putJson(u, "/api/market/favorites/call-kranich", "", 204);
        putJson(u, "/api/market/favorites/call-kranich", "", 204);
        putJson(u, "/api/market/favorites/gibt-es-nicht", "", 404);
        assertThat(getJson(u, "/api/market/favorites")).hasSize(1);
        assertThat(getJson(u, "/api/market/products?category=CALL").toString()).contains("\"favorite\":true");
        JsonNode cmp = getJson(u, "/api/market/compare?ids=call-kranich,call-nordlicht,fixed-kranich-24");
        assertThat(cmp.get("products")).hasSize(3);
        assertThat(cmp.get("rows").toString()).contains("Zinssatz").contains("2,75 %").contains("24 Monate");
        assertThat(mvc.perform(get("/api/market/compare?ids=call-kranich").header("Authorization", u.bearer()))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        deletePath(u, "/api/market/favorites/call-kranich", 204);
        assertThat(getJson(u, "/api/market/favorites")).isEmpty();
        assertThat(getJson(registerUser("Andere"), "/api/market/favorites")).isEmpty();
    }

    @Test
    void T110_antragMitUnterlagenUndSimulierterZusage() throws Exception {
        TestUser u = registerUser("Anna");
        postJson(u, "/api/market/applications", "{\"productId\":\"call-kranich\",\"consent\":false}", 400);
        JsonNode a = postJson(u, "/api/market/applications", "{\"productId\":\"call-kranich\",\"consent\":true}", 201);
        long id = a.get("id").asLong();
        assertThat(a.get("status").asText()).isEqualTo("DOCUMENTS_MISSING");
        assertThat(a.get("documents").get(0).get("label").asText()).isEqualTo("Ausweis (Kopie)");
        postJson(u, "/api/market/applications", "{\"productId\":\"call-kranich\",\"consent\":true}", 409);
        JsonNode s = postJson(u, "/api/market/applications/" + id + "/documents/doc1", "", 200);
        assertThat(s.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(putJson(u, "/api/market/applications/" + id + "/note", "{\"note\":\"Bester Zins ohne Mindestanlage\"}", 200)
                .get("note").asText()).isEqualTo("Bester Zins ohne Mindestanlage");
        advance("days", 1);
        u = relogin(u);
        JsonNode done = getJson(u, "/api/market/applications/" + id);
        assertThat(done.get("status").asText()).isEqualTo("APPROVED");
        assertThat(done.get("statusReason").asText()).contains("kein echter Vertrag").contains("kein echtes Konto");
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Simulierte Zusage | „Tagesgeld“"));
        postJson(u, "/api/market/applications/" + id + "/withdraw", "", 409);
    }

    @Test
    void T111_kreditkarteAbgelehntOhneEinnahmenKeineKarte() throws Exception {
        TestUser u = registerUser("Karl");
        long id = postJson(u, "/api/market/applications", "{\"productId\":\"cc-polarstern\",\"consent\":true}", 201)
                .get("id").asLong();
        postJson(u, "/api/market/applications/" + id + "/documents/doc1", "", 200);
        postJson(u, "/api/market/applications/" + id + "/documents/doc2", "", 200);
        advance("days", 1);
        u = relogin(u);
        JsonNode done = getJson(u, "/api/market/applications/" + id);
        assertThat(done.get("status").asText()).isEqualTo("DECLINED");
        assertThat(done.get("statusReason").asText()).contains("Einnahmen ab 1.200,00 €");
        // eigener Antrag kann zurückgezogen werden, fremde nicht
        long other = postJson(u, "/api/market/applications", "{\"productId\":\"depot-mosaik\",\"consent\":true}", 201)
                .get("id").asLong();
        TestUser eve = registerUser("Eve");
        postJson(eve, "/api/market/applications/" + other + "/withdraw", "", 404);
        assertThat(postJson(u, "/api/market/applications/" + other + "/withdraw", "", 200).get("status").asText())
                .isEqualTo("WITHDRAWN");
        assertThat(getJson(eve, "/api/market/applications")).isEmpty();
    }

    @Test
    void T112_englisch() throws Exception {
        TestUser u = registerUser("Emma");
        String body = mvc.perform(get("/api/market/products?category=CALL").header("Authorization", u.bearer())
                        .header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("fictitious providers").contains("(fictitious)").contains("Available daily");
    }
}
