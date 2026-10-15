package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** T141–T142: Demo-Karten für digitale Konten – Zahlung vom digitalen Konto, Limit, Sperre, Rechte. */
class SubaccountCardFlowTest extends FlowTestSupport {

    private long sub(TestUser u, String name, long fill) throws Exception {
        long id = postJson(u, "/api/subaccounts", "{\"name\":\"" + name + "\"}", 201).get("id").asLong();
        postJson(u, "/api/subaccounts/move", "{\"fromAccountId\":" + u.accountId() + ",\"toAccountId\":" + id
                + ",\"amountCents\":" + fill + "}", 204);
        return id;
    }

    private long balanceOf(TestUser u, long account) throws Exception {
        for (JsonNode a : getJson(u, "/api/accounts")) {
            if (a.get("id").asLong() == account) {
                return a.get("balanceCents").asLong();
            }
        }
        return -1;
    }

    @Test
    void T141_zahlungVomDigitalenKontoMitLimitUndSperre() throws Exception {
        TestUser u = registerUser("Karla");
        long s = sub(u, "Lebensmittel", 10_000);
        assertThat(getJson(u, "/api/subaccounts/" + s + "/card").get("exists").asBoolean()).isFalse();
        JsonNode c = postJson(u, "/api/subaccounts/" + s + "/card", "{\"dailyLimitCents\":5000}", 201);
        assertThat(c.get("last4").asText()).matches("\\d{4}");
        assertThat(c.get("note").asText()).contains("keine echte Karte");
        postJson(u, "/api/subaccounts/" + s + "/card", "{\"dailyLimitCents\":5000}", 409);
        JsonNode p = postJson(u, "/api/subaccounts/" + s + "/card/payment", "{\"merchant\":\"Markt\",\"amountCents\":3000}", 200);
        assertThat(p.get("approved").asBoolean()).isTrue();
        assertThat(balanceOf(u, s)).isEqualTo(7_000);
        assertThat(balanceOf(u, u.accountId())).isEqualTo(90_000);                  // Girokonto unberührt
        assertThat(postJson(u, "/api/subaccounts/" + s + "/card/payment", "{\"merchant\":\"Markt\",\"amountCents\":3000}", 200)
                .get("approved").asBoolean()).isFalse();                             // Tageslimit
        postJson(u, "/api/subaccounts/" + s + "/card/lock", "", 200);
        assertThat(postJson(u, "/api/subaccounts/" + s + "/card/payment", "{\"merchant\":\"Markt\",\"amountCents\":100}", 200)
                .get("approved").asBoolean()).isFalse();
        assertThat(balanceOf(u, s)).isEqualTo(7_000);
        postJson(u, "/api/subaccounts/" + u.accountId() + "/card", "{\"dailyLimitCents\":5000}", 400);   // nur digitale Konten
    }

    @Test
    void T142_nurMitVollenRechten() throws Exception {
        TestUser owner = registerUser("Olga");
        TestUser full = registerUser("Fritz");
        TestUser deposit = registerUser("Dora");
        TestUser eve = registerUser("Eve");
        long s = sub(owner, "WG-Kasse", 10_000);
        postJson(owner, "/api/subaccounts/" + s + "/members", "{\"recipient\":\"" + full.email() + "\",\"role\":\"FULL\"}", 200);
        postJson(owner, "/api/subaccounts/" + s + "/members", "{\"recipient\":\"" + deposit.email() + "\",\"role\":\"DEPOSIT\"}", 200);
        postJson(owner, "/api/subaccounts/" + s + "/card", "{\"dailyLimitCents\":20000}", 201);
        assertThat(postJson(full, "/api/subaccounts/" + s + "/card/payment", "{\"merchant\":\"Baumarkt\",\"amountCents\":2500}", 200)
                .get("approved").asBoolean()).isTrue();
        postJson(deposit, "/api/subaccounts/" + s + "/card/payment", "{\"merchant\":\"X\",\"amountCents\":100}", 403);
        assertThat(getJson(deposit, "/api/subaccounts/" + s + "/card").get("canPay").asBoolean()).isFalse();
        postJson(eve, "/api/subaccounts/" + s + "/card/payment", "{\"merchant\":\"X\",\"amountCents\":100}", 403);
        assertThat(balanceOf(owner, s)).isEqualTo(7_500);
    }
}
