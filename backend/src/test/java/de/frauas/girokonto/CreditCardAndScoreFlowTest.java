package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** T113–T118: virtuelle Kreditkarte (Simulation) mit Abrechnung und Gebühren, interner Finanz-Check. */
class CreditCardAndScoreFlowTest extends FlowTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode pay(TestUser u, String merchant, long cents, boolean foreign) throws Exception {
        return postJson(u, "/api/credit-card/payment", "{\"merchant\":\"" + merchant + "\",\"amountCents\":" + cents
                + ",\"foreignCurrency\":" + foreign + "}", 200);
    }

    @Test
    void T113_kreditkarteEinrichtenLimitUndSperre() throws Exception {
        TestUser u = registerUser("Carla");
        assertThat(getJson(u, "/api/credit-card").get("exists").asBoolean()).isFalse();
        postJson(u, "/api/credit-card", "{\"limitCents\":5000}", 400);
        postJson(u, "/api/credit-card", "{\"limitCents\":600000}", 400);
        JsonNode v = postJson(u, "/api/credit-card", "{\"limitCents\":100000}", 201);
        assertThat(v.get("maskedNumber").asText()).matches("•••• \\d{4}");
        assertThat(v.get("note").asText()).contains("keine echte Karte");
        postJson(u, "/api/credit-card", "{\"limitCents\":100000}", 409);
        assertThat(putJson(u, "/api/credit-card/limit", "{\"limitCents\":200000}", 200).get("limitCents").asLong())
                .isEqualTo(200_000);
        assertThat(postJson(u, "/api/credit-card/lock", "", 200).get("status").asText()).isEqualTo("BLOCKED");
        assertThat(pay(u, "Kiosk", 500, false).get("approved").asBoolean()).isFalse();
        postJson(u, "/api/credit-card/unlock", "", 200);
        assertThat(pay(u, "Kiosk", 500, false).get("approved").asBoolean()).isTrue();
    }

    @Test
    void T114_auslandseinsatzUndVerfuegungsrahmen() throws Exception {
        TestUser u = registerUser("Rolf");
        postJson(u, "/api/credit-card", "{\"limitCents\":100000}", 201);
        JsonNode p = pay(u, "Hotel Roma", 40_000, true);
        assertThat(p.get("feeCents").asLong()).isEqualTo(700);               // 1,75 %
        JsonNode v = getJson(u, "/api/credit-card");
        assertThat(v.get("openCents").asLong()).isEqualTo(40_700);
        assertThat(v.get("availableCents").asLong()).isEqualTo(59_300);
        assertThat(pay(u, "Laptop", 70_000, false).get("approved").asBoolean()).isFalse();
        assertThat(balance(u)).isEqualTo(100_000);                          // erst die Abrechnung bucht
        assertThat(notificationTexts(u)).anyMatch(t -> t.contains("Auslandseinsatz"));
    }

    @Test
    void T115_monatsabrechnungVomGirokontoGenauEinmal() throws Exception {
        TestUser u = registerUser("Moni");
        postJson(u, "/api/credit-card", "{\"limitCents\":100000}", 201);
        pay(u, "Buchladen", 2_500, false);
        pay(u, "Tankstelle", 6_000, false);
        LocalDate first = advanceToNextDay(u, 1);
        u = relogin(u);
        JsonNode v = getJson(u, "/api/credit-card");
        assertThat(v.get("openCents").asLong()).isZero();
        JsonNode st = v.get("statements").get(0);
        assertThat(st.get("totalCents").asLong()).isEqualTo(8_500);
        assertThat(st.get("status").asText()).isEqualTo("PAID");
        assertThat(st.get("period").asText()).isEqualTo(first.minusMonths(1).toString().substring(0, 7));
        assertThat(balance(u)).isEqualTo(100_000 - 8_500);
        advance("days", 2);
        u = relogin(u);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking WHERE idempotency_key = ?", Integer.class,
                "cc-statement:" + st.get("id").asLong())).isEqualTo(1);
        assertThat(balance(u)).isEqualTo(100_000 - 8_500);
    }

    @Test
    void T116_abrechnungWartetBeiZuWenigGuthabenKeinDispo() throws Exception {
        TestUser u = registerUser("Otto");
        TestUser other = registerUser("Paul");
        postJson(u, "/api/credit-card", "{\"limitCents\":100000}", 201);
        pay(u, "Möbel", 90_000, false);
        // Girokonto fast leer überweisen
        long sub = postJson(u, "/api/subaccounts", "{\"name\":\"Topf\"}", 201).get("id").asLong();
        postJson(u, "/api/accounts/move", "{\"fromAccountId\":" + u.accountId() + ",\"toAccountId\":" + sub
                + ",\"amountCents\":95000}", 204);
        advanceToNextDay(u, 1);
        u = relogin(u);
        JsonNode v = getJson(u, "/api/credit-card");
        assertThat(v.get("statements").get(0).get("status").asText()).isEqualTo("OPEN");
        assertThat(v.get("openCents").asLong()).isEqualTo(90_000);
        assertThat(balance(u)).isEqualTo(5_000);                             // nie ins Minus
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Kreditkartenabrechnung offen"));
        postJson(u, "/api/accounts/move", "{\"fromAccountId\":" + sub + ",\"toAccountId\":" + u.accountId()
                + ",\"amountCents\":95000}", 204);
        advance("days", 1);
        u = relogin(u);
        assertThat(getJson(u, "/api/credit-card").get("statements").get(0).get("status").asText()).isEqualTo("PAID");
        assertThat(balance(u)).isEqualTo(10_000);
        assertThat(getJson(other, "/api/credit-card").get("exists").asBoolean()).isFalse();
    }

    @Test
    void T117_finanzCheckOhneDatenUndMitBeispielmonaten() throws Exception {
        TestUser fresh = registerUser("Neu");
        JsonNode s = getJson(fresh, "/api/health-score");
        assertThat(s.get("level").asText()).isEqualTo("OHNE_DATEN");
        assertThat(s.get("disclaimer").asText()).contains("keine SCHUFA-Auskunft");
        assertThat(s.get("components")).hasSize(4);

        TestUser demo = userWithDemo("Demo");
        JsonNode d = getJson(demo, "/api/health-score");
        int sum = 0;
        for (JsonNode c : d.get("components")) {
            sum += c.get("points").asInt();
            assertThat(c.get("points").asInt()).isBetween(0, 25);
        }
        assertThat(d.get("score").asInt()).isEqualTo(sum).isBetween(0, 100);
        assertThat(d.get("level").asText()).isIn("GUT", "MITTEL", "KRITISCH");
        // ein Kredit verschlechtert die Verschuldung
        postJson(demo, "/api/loans", """
                {"bank":"NORDLICHT","purpose":"AUTO","principalCents":1000000,"equityCents":0,"termMonths":24}
                """, 201);
        JsonNode after = getJson(demo, "/api/health-score");
        int debtBefore = 0;
        int debtAfter = 0;
        for (JsonNode c : d.get("components")) {
            if (c.get("key").asText().equals("DEBT_RATIO")) {
                debtBefore = c.get("points").asInt();
            }
        }
        for (JsonNode c : after.get("components")) {
            if (c.get("key").asText().equals("DEBT_RATIO")) {
                debtAfter = c.get("points").asInt();
            }
        }
        assertThat(debtBefore).isEqualTo(25);
        assertThat(debtAfter).isLessThan(25);
    }

    @Test
    void T118_englisch() throws Exception {
        TestUser u = registerUser("Ezra");
        String s = mvc.perform(get("/api/health-score").header("Authorization", u.bearer()).header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(s).contains("no credit bureau report").contains("Emergency fund");
        String c = mvc.perform(get("/api/credit-card").header("Authorization", u.bearer()).header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(c).contains("no real card");
    }
}
