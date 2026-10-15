package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** T61–T67: Digitale Konten (Zweck, Ziel, Fortschritt, Rollen, Kennzeichnung, Gesamtvermögen). */
class DigitalAccountFlowTest extends FlowTestSupport {

    private JsonNode account(TestUser u, long id) throws Exception {
        for (JsonNode a : getJson(u, "/api/accounts")) {
            if (a.get("id").asLong() == id) {
                return a;
            }
        }
        return null;
    }

    private void move(TestUser u, long from, long to, long cents, int expected) throws Exception {
        postJson(u, "/api/subaccounts/move",
                "{\"fromAccountId\":" + from + ",\"toAccountId\":" + to + ",\"amountCents\":" + cents + "}", expected);
    }

    @Test
    void T61_digitalesKontoMitZweckZielUndKennzeichnung() throws Exception {
        TestUser u = registerUser("Dora");
        LocalDate target = today(u).plusMonths(10).withDayOfMonth(1);
        JsonNode created = postJson(u, "/api/subaccounts", """
                {"name":"Neues Auto","purpose":"AUTO","targetCents":500000,"targetDate":"%s"}
                """.formatted(target), 201);
        assertThat(created.get("icon").asText()).isEqualTo("car");   // Symbol aus dem Zweck
        JsonNode d = account(u, created.get("id").asLong()).get("digital");
        assertThat(d.get("purpose").asText()).isEqualTo("AUTO");
        assertThat(d.get("targetCents").asLong()).isEqualTo(500_000);
        assertThat(d.get("progressPercent").asDouble()).isZero();
        assertThat(d.get("remainingCents").asLong()).isEqualTo(500_000);
        assertThat(d.get("monthlyNeededCents").asLong()).isEqualTo(50_000);   // 10 Monate
        assertThat(d.get("role").asText()).isEqualTo("OWNER");
        assertThat(d.get("canWithdraw").asBoolean()).isTrue();
        assertThat(d.get("custody").asText()).isEqualTo("INTERNAL");
        assertThat(d.get("custodyText").asText()).contains("kein eigenes Bankkonto").contains("Einlagensicherung");
        // Girokonto hat keine Angaben für digitale Konten
        assertThat(account(u, u.accountId()).get("digital").isNull()).isTrue();

        String en = mvc.perform(get("/api/accounts").header("Authorization", u.bearer()).header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(en).contains("Moved within Projektbank (simulation)").contains("not a separate bank account")
                .contains("\"roleLabel\":\"Owner\"").contains("Neues Auto");
    }

    @Test
    void T62_eingabenPruefen() throws Exception {
        TestUser u = registerUser("Pia");
        postJson(u, "/api/subaccounts", "{\"name\":\"X\",\"purpose\":\"YACHT\"}", 400);
        postJson(u, "/api/subaccounts", "{\"name\":\"X\",\"targetCents\":0}", 400);
        postJson(u, "/api/subaccounts", "{\"name\":\"X\",\"targetCents\":100000001}", 400);
        postJson(u, "/api/subaccounts", "{\"name\":\"X\",\"targetCents\":1000,\"targetDate\":\"" + today(u) + "\"}", 400);
        postJson(u, "/api/subaccounts", "{\"name\":\"X\",\"targetCents\":1000,\"targetDate\":\"2200-01-01\"}", 400);
        // ohne Zweck: Sparziel; ohne Ziel: kein Fortschritt
        JsonNode plain = postJson(u, "/api/subaccounts", "{\"name\":\"Topf\"}", 201);
        JsonNode d = plain.get("digital");
        assertThat(d.get("purpose").asText()).isEqualTo("SPARZIEL");
        assertThat(d.get("progressPercent").isNull()).isTrue();
        assertThat(d.get("remainingCents").isNull()).isTrue();
    }

    @Test
    void T63_fortschrittUndZielErreichtNurEinmal() throws Exception {
        TestUser u = registerUser("Zora");
        long id = postJson(u, "/api/subaccounts", "{\"name\":\"Urlaub\",\"purpose\":\"URLAUB\",\"targetCents\":10000}", 201)
                .get("id").asLong();
        move(u, u.accountId(), id, 6_000, 204);
        JsonNode d = account(u, id).get("digital");
        assertThat(d.get("progressPercent").asDouble()).isEqualTo(60.0);
        assertThat(d.get("remainingCents").asLong()).isEqualTo(4_000);
        assertThat(d.get("goalReached").asBoolean()).isFalse();

        move(u, u.accountId(), id, 5_000, 204);
        d = account(u, id).get("digital");
        assertThat(d.get("progressPercent").asDouble()).isEqualTo(100.0);
        assertThat(d.get("remainingCents").asLong()).isZero();
        assertThat(d.get("goalReached").asBoolean()).isTrue();
        move(u, u.accountId(), id, 1_000, 204);
        assertThat(notificationTexts(u).stream().filter(t -> t.startsWith("Ziel erreicht |")).count()).isEqualTo(1);

        // wieder unter das Ziel und erneut erreicht → neue Meldung
        move(u, id, u.accountId(), 5_000, 204);
        move(u, u.accountId(), id, 5_000, 204);
        assertThat(notificationTexts(u).stream().filter(t -> t.startsWith("Ziel erreicht |")).count()).isEqualTo(2);
        assertThat(balance(u)).isEqualTo(100_000 - 12_000);   // Summe bleibt erhalten
    }

    @Test
    void T64_rollenInGeteiltenKonten() throws Exception {
        TestUser owner = registerUser("Olli");
        TestUser member = registerUser("Mona");
        long id = postJson(owner, "/api/subaccounts", "{\"name\":\"WG\",\"purpose\":\"WG\",\"targetCents\":50000}", 201)
                .get("id").asLong();
        move(owner, owner.accountId(), id, 10_000, 204);
        JsonNode list = postJson(owner, "/api/subaccounts/" + id + "/members",
                "{\"recipient\":\"" + member.email() + "\",\"role\":\"DEPOSIT\"}", 200);
        assertThat(list.get(1).get("role").asText()).isEqualTo("DEPOSIT");
        assertThat(notificationTexts(member)).anyMatch(t -> t.contains("Du kannst einzahlen und die Umsätze sehen."));

        // nur einzahlen
        move(member, member.accountId(), id, 2_000, 204);
        move(member, id, member.accountId(), 1_000, 403);
        JsonNode d = account(member, id).get("digital");
        assertThat(d.get("role").asText()).isEqualTo("DEPOSIT");
        assertThat(d.get("canDeposit").asBoolean()).isTrue();
        assertThat(d.get("canWithdraw").asBoolean()).isFalse();
        assertThat(d.get("canManage").asBoolean()).isFalse();

        // nur ansehen
        putJson(owner, "/api/subaccounts/" + id + "/members/" + member.id(), "{\"role\":\"VIEW\"}", 200);
        move(member, member.accountId(), id, 1_000, 403);
        assertThat(getJson(member, "/api/accounts/" + id + "/bookings").get("items")).isNotEmpty();
        assertThat(account(member, id).get("digital").get("canDeposit").asBoolean()).isFalse();

        // volle Rechte
        putJson(owner, "/api/subaccounts/" + id + "/members/" + member.id(), "{\"role\":\"FULL\"}", 200);
        move(member, id, member.accountId(), 1_000, 204);

        // nur der Besitzer ändert Rollen, Ziel und Name
        putJson(member, "/api/subaccounts/" + id + "/members/" + member.id(), "{\"role\":\"VIEW\"}", 403);
        putJson(owner, "/api/subaccounts/" + id + "/members/" + member.id(), "{\"role\":\"ADMIN\"}", 400);
        putJson(member, "/api/subaccounts/" + id, "{\"name\":\"Meins\"}", 403);
        putJson(owner, "/api/subaccounts/" + id + "/members/999999", "{\"role\":\"VIEW\"}", 404);
    }

    @Test
    void T65_zielAendernUndEntfernen() throws Exception {
        TestUser u = registerUser("Theo");
        long id = postJson(u, "/api/subaccounts", "{\"name\":\"Wohnung\",\"purpose\":\"IMMOBILIE\",\"targetCents\":5000}", 201)
                .get("id").asLong();
        move(u, u.accountId(), id, 6_000, 204);
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Ziel erreicht |"));
        // höheres Ziel: noch nicht erreicht; Zweck ändern ändert das Standardsymbol
        JsonNode upd = putJson(u, "/api/subaccounts/" + id,
                "{\"name\":\"Eigenkapital\",\"purpose\":\"IMMOBILIE\",\"targetCents\":20000}", 200);
        assertThat(upd.get("digital").get("goalReached").asBoolean()).isFalse();
        assertThat(upd.get("digital").get("progressPercent").asDouble()).isEqualTo(30.0);
        assertThat(upd.get("icon").asText()).isEqualTo("home");
        // Ziel entfernen
        JsonNode none = putJson(u, "/api/subaccounts/" + id, "{\"name\":\"Eigenkapital\",\"purpose\":\"IMMOBILIE\"}", 200);
        assertThat(none.get("digital").get("targetCents").isNull()).isTrue();
        assertThat(none.get("digital").get("progressPercent").isNull()).isTrue();
    }

    @Test
    void T66_gesamtvermoegenMitVerbundenenBanken() throws Exception {
        TestUser u = registerUser("Vera");
        JsonNode before = getJson(u, "/api/insights/net-worth");
        assertThat(before.get("externalCents").asLong()).isZero();
        JsonNode c = postJson(u, "/api/banking/connections",
                "{\"provider\":\"SANDBOX\",\"institutionId\":\"SANDBOX_ONLINEBANK\"}", 201);
        postJson(u, "/api/banking/connections/" + c.get("connection").get("id").asLong() + "/complete",
                "{\"approve\":true}", 200);
        long bank = getJson(u, "/api/banking/accounts").get("totalBalanceCents").asLong();
        JsonNode after = getJson(u, "/api/insights/net-worth");
        assertThat(after.get("externalCents").asLong()).isEqualTo(bank).isNotZero();
        assertThat(after.get("totalCents").asLong()).isEqualTo(after.get("accountsCents").asLong()
                + after.get("depotCents").asLong() + after.get("loansCents").asLong() + bank);
    }

    @Test
    void T67_vieleDigitaleKontenMoeglich() throws Exception {
        TestUser u = registerUser("Gabi");
        for (int i = 1; i <= 12; i++) {
            postJson(u, "/api/subaccounts", "{\"name\":\"Ziel " + i + "\",\"purpose\":\"SPARZIEL\"}", 201);
        }
        long subs = 0;
        for (JsonNode a : getJson(u, "/api/accounts")) {
            if (a.get("type").asText().equals("SUB")) {
                subs++;
            }
        }
        assertThat(subs).isEqualTo(12);
    }
}
