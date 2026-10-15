package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T119–T123: gemeinsame Ausgaben (WG) – Aufteilung, Salden, Ausgleich per Überweisung und bar, Zugriffsschutz. */
class GroupFlowTest extends FlowTestSupport {

    private JsonNode settle(TestUser u, long group, long to, long cents, String key, int expected) throws Exception {
        String r = mvc.perform(post("/api/groups/" + group + "/settlements").header("Authorization", u.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + to + ",\"amountCents\":" + cents + "}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(r);
    }

    private static Map<Long, Long> balances(JsonNode d) {
        Map<Long, Long> m = new HashMap<>();
        d.get("members").forEach(x -> m.put(x.get("userId").asLong(), x.get("balanceCents").asLong()));
        return m;
    }

    private long group(TestUser owner, TestUser... others) throws Exception {
        long id = postJson(owner, "/api/groups", "{\"name\":\"WG Berger\"}", 201).get("id").asLong();
        for (TestUser o : others) {
            postJson(owner, "/api/groups/" + id + "/members", "{\"recipient\":\"" + o.email() + "\"}", 200);
        }
        return id;
    }

    @Test
    void T119_gleichmaessigAufteilenMitRestCent() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        TestUser c = registerUser("Cem");
        long g = group(a, b, c);
        JsonNode d = postJson(a, "/api/groups/" + g + "/expenses", "{\"description\":\"Einkauf\",\"amountCents\":10000}", 201);
        Map<Long, Long> bal = balances(d);
        assertThat(bal.get(a.id())).isEqualTo(6_666);
        assertThat(bal.get(b.id())).isEqualTo(-3_333);
        assertThat(bal.get(c.id())).isEqualTo(-3_333);
        long sumShares = 0;
        for (JsonNode s : d.get("expenses").get(0).get("shares")) {
            sumShares += s.get("shareCents").asLong();
        }
        assertThat(sumShares).isEqualTo(10_000);
        assertThat(notificationTexts(b)).anyMatch(t -> t.contains("„Einkauf“") && t.contains("33,33 €"));
    }

    @Test
    void T120_teilnehmerUndFesteAnteile() throws Exception {
        TestUser a = registerUser("Alma");
        TestUser b = registerUser("Bodo");
        TestUser c = registerUser("Cora");
        long g = group(a, b, c);
        postJson(a, "/api/groups/" + g + "/expenses", "{\"description\":\"Pizza\",\"amountCents\":3000,\"payerId\":"
                + b.id() + ",\"participants\":[" + b.id() + "," + c.id() + "]}", 201);
        JsonNode err = postJson(a, "/api/groups/" + g + "/expenses", "{\"description\":\"Strom\",\"amountCents\":5000,"
                + "\"shares\":[{\"userId\":" + a.id() + ",\"shareCents\":2000},{\"userId\":" + b.id() + ",\"shareCents\":2000}]}", 400);
        assertThat(err.get("code").asText()).isEqualTo("SHARES_DO_NOT_ADD_UP");
        JsonNode d = postJson(a, "/api/groups/" + g + "/expenses", "{\"description\":\"Strom\",\"amountCents\":5000,"
                + "\"shares\":[{\"userId\":" + a.id() + ",\"shareCents\":1000},{\"userId\":" + b.id() + ",\"shareCents\":2000},"
                + "{\"userId\":" + c.id() + ",\"shareCents\":2000}]}", 201);
        Map<Long, Long> bal = balances(d);
        assertThat(bal.get(a.id())).isEqualTo(4_000);
        assertThat(bal.get(b.id())).isEqualTo(-500);
        assertThat(bal.get(c.id())).isEqualTo(-3_500);
        assertThat(bal.values().stream().mapToLong(Long::longValue).sum()).isZero();
        assertThat(d.get("suggestions")).hasSize(2);
        assertThat(d.get("suggestions").get(0).get("fromUserId").asLong()).isEqualTo(c.id());
        assertThat(d.get("suggestions").get(0).get("amountCents").asLong()).isEqualTo(3_500);
    }

    @Test
    void T121_ausgleichPerUeberweisungGenauEinmal() throws Exception {
        TestUser a = registerUser("Aida");
        TestUser b = registerUser("Bert");
        long g = group(a, b);
        postJson(a, "/api/groups/" + g + "/expenses", "{\"description\":\"Miete Möbel\",\"amountCents\":8000}", 201);
        assertThat(settle(b, g, a.id(), 4_001, "k1", 400).get("code").asText()).isEqualTo("SETTLEMENT_TOO_HIGH");
        settle(b, g, a.id(), 4_000, "k1", 200);
        JsonNode d = settle(b, g, a.id(), 4_000, "k1", 200);                  // Wiederholung: nichts doppelt
        assertThat(d.get("settlements")).hasSize(1);
        assertThat(balances(d).values()).containsOnly(0L);
        assertThat(balance(b)).isEqualTo(96_000);
        assertThat(balance(a)).isEqualTo(104_000);
        assertThat(notificationTexts(a)).anyMatch(t -> t.contains("Ausgleich „WG Berger“"));
    }

    @Test
    void T122_barzahlungNurVomEmpfaengerBestaetigt() throws Exception {
        TestUser a = registerUser("Arne");
        TestUser b = registerUser("Bea");
        long g = group(a, b);
        postJson(a, "/api/groups/" + g + "/expenses", "{\"description\":\"Kino\",\"amountCents\":2000}", 201);
        postJson(b, "/api/groups/" + g + "/settlements/cash", "{\"fromUserId\":" + b.id() + ",\"amountCents\":1000}", 400);
        JsonNode d = postJson(a, "/api/groups/" + g + "/settlements/cash", "{\"fromUserId\":" + b.id() + ",\"amountCents\":1000}", 200);
        assertThat(d.get("settlements").get(0).get("method").asText()).isEqualTo("CASH");
        assertThat(balances(d).values()).containsOnly(0L);
        assertThat(balance(b)).isEqualTo(100_000);                            // bar: kein Geld bewegt
    }

    @Test
    void T123_zugriffAustrittUndLoeschen() throws Exception {
        TestUser a = registerUser("Alex");
        TestUser b = registerUser("Bine");
        TestUser x = registerUser("Fremd");
        long g = group(a, b);
        getJsonExpect(x, "/api/groups/" + g, 404);
        postJson(x, "/api/groups/" + g + "/expenses", "{\"description\":\"X\",\"amountCents\":100}", 404);
        long e = postJson(a, "/api/groups/" + g + "/expenses", "{\"description\":\"Brot\",\"amountCents\":400}", 201)
                .get("expenses").get(0).get("id").asLong();
        deletePath(b, "/api/groups/" + g + "/members/" + b.id(), 409);        // Saldo offen
        deletePath(a, "/api/groups/" + g, 409);
        deletePath(b, "/api/groups/" + g + "/expenses/" + e, 403);
        deletePath(a, "/api/groups/" + g + "/expenses/" + e, 200);
        deletePath(b, "/api/groups/" + g + "/members/" + a.id(), 403);
        deletePath(b, "/api/groups/" + g + "/members/" + b.id(), 204);
        assertThat(getJson(b, "/api/groups")).isEmpty();
        deletePath(a, "/api/groups/" + g, 204);
    }

    private void getJsonExpect(TestUser u, String path, int expected) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .header("Authorization", u.bearer())).andExpect(status().is(expected));
    }
}
