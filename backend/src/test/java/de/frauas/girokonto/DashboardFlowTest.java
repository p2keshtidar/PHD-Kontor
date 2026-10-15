package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T85–T88: Zentrales Finanz-Dashboard (nur aus vorhandenen Daten, nichts geschätzt). */
class DashboardFlowTest extends FlowTestSupport {

    private static List<String> kinds(JsonNode d) {
        List<String> k = new ArrayList<>();
        d.get("accounts").forEach(a -> k.add(a.get("kind").asText()));
        return k;
    }

    @Test
    void T85_neuesKontoNurStartguthaben() throws Exception {
        TestUser u = registerUser("Neu");
        JsonNode d = getJson(u, "/api/dashboard");
        assertThat(d.get("netWorth").get("totalCents").asLong()).isEqualTo(100_000);
        assertThat(d.get("liquidity").get("availableCents").asLong()).isEqualTo(100_000);
        assertThat(d.get("liquidity").get("reservedCents").asLong()).isZero();
        assertThat(d.get("debts").get("totalCents").asLong()).isZero();
        assertThat(d.get("debts").get("debtRatioPercent").isNull()).isTrue();
        assertThat(d.get("goals")).isEmpty();
        assertThat(kinds(d)).containsExactly("PROJEKTBANK");
        assertThat(d.get("cards").get(0).get("simulation").asBoolean()).isTrue();
        JsonNode h = d.get("history");
        assertThat(h).hasSize(12);
        assertThat(h.get(11).get("totalCents").asLong()).isEqualTo(100_000);
        assertThat(h.get(10).get("totalCents").asLong()).isZero();   // vor der Registrierung kein Vermögen
        assertThat(h.get(11).get("externalCents").isNull()).isTrue();
        assertThat(d.get("notes").get(0).asText()).contains("Simulation mit Spielgeld");
    }

    @Test
    void T86_vollstaendigesBildStimmtMitGesamtvermoegenUeberein() throws Exception {
        TestUser u = userWithDemo("Dana");
        long sub = postJson(u, "/api/subaccounts", "{\"name\":\"Urlaub\",\"purpose\":\"URLAUB\",\"targetCents\":300000}", 201)
                .get("id").asLong();
        postJson(u, "/api/subaccounts/move", "{\"fromAccountId\":" + u.accountId() + ",\"toAccountId\":" + sub
                + ",\"amountCents\":50000}", 204);
        postJson(u, "/api/loans", """
                {"bank":"NORDLICHT","purpose":"AUTO","principalCents":500000,"equityCents":0,"termMonths":24}
                """, 201);
        JsonNode c = postJson(u, "/api/banking/connections", "{\"provider\":\"SANDBOX\",\"institutionId\":\"SANDBOX_ONLINEBANK\"}", 201);
        postJson(u, "/api/banking/connections/" + c.get("connection").get("id").asLong() + "/complete", "{\"approve\":true}", 200);

        JsonNode d = getJson(u, "/api/dashboard");
        JsonNode nw = getJson(u, "/api/insights/net-worth");
        JsonNode n = d.get("netWorth");
        assertThat(n.get("totalCents").asLong()).isEqualTo(nw.get("totalCents").asLong());
        assertThat(n.get("totalCents").asLong()).isEqualTo(n.get("accountsCents").asLong() + n.get("digitalCents").asLong()
                + n.get("plansCents").asLong() + n.get("depotCents").asLong() + n.get("externalCents").asLong()
                + n.get("loansCents").asLong());
        assertThat(n.get("digitalCents").asLong()).isEqualTo(50_000);
        assertThat(n.get("loansCents").asLong()).isEqualTo(-500_000);
        assertThat(n.get("externalCents").asLong()).isEqualTo(getJson(u, "/api/banking/accounts").get("totalBalanceCents").asLong());
        assertThat(d.get("liquidity").get("reservedCents").asLong()).isEqualTo(50_000);
        assertThat(kinds(d)).contains("PROJEKTBANK", "DIGITAL", "EXTERNAL", "LOAN");

        JsonNode debts = d.get("debts");
        assertThat(debts.get("totalCents").asLong()).isEqualTo(500_000);
        assertThat(debts.get("monthlyRatesCents").asLong()).isPositive();
        assertThat(debts.get("debtRatioPercent").asDouble()).isPositive();
        assertThat(debts.get("loans")).hasSize(1);

        JsonNode goal = d.get("goals").get(0);
        assertThat(goal.get("name").asText()).isEqualTo("Urlaub");
        assertThat(goal.get("savedCents").asLong()).isEqualTo(50_000);
        assertThat(goal.get("progressPercent").asDouble()).isEqualTo(16.7);

        assertThat(d.get("cashflow").get("months")).hasSize(6);
        double percent = 0;
        for (JsonNode t : d.get("topExpenses")) {
            percent += t.get("percent").asDouble();
            assertThat(t.get("cents").asLong()).isPositive();
        }
        assertThat(d.get("topExpenses")).isNotEmpty();
        assertThat(percent).isLessThanOrEqualTo(100.1);
        assertThat(d.get("history").get(11).get("totalCents").asLong()).isEqualTo(n.get("totalCents").asLong());
        assertThat(d.get("notes").toString()).contains("Verbundene Banken");
    }

    @Test
    void T87_verlaufMitDepotUndVerbundenerBank() throws Exception {
        TestUser u = userWithDemo("Vera");
        postJson(u, "/api/plans", """
                {"type":"INVEST","name":"Welt","monthlyRateCents":10000,"executionDay":1,"riskLevel":"AUSGEWOGEN"}
                """, 201);
        JsonNode c = postJson(u, "/api/banking/connections", "{\"provider\":\"SANDBOX\",\"institutionId\":\"SANDBOX_MAINBANK\"}", 201);
        postJson(u, "/api/banking/connections/" + c.get("connection").get("id").asLong() + "/complete", "{\"approve\":true}", 200);
        advance("months", 2);
        u = relogin(u);
        JsonNode d = getJson(u, "/api/dashboard");
        JsonNode h = d.get("history");
        JsonNode last = h.get(11);
        assertThat(last.get("totalCents").asLong()).isEqualTo(d.get("netWorth").get("totalCents").asLong());
        assertThat(last.get("depotCents").asLong()).isEqualTo(d.get("netWorth").get("depotCents").asLong()).isPositive();
        // Monate sind aufsteigend, Depot wächst mit den Sparraten
        for (int i = 1; i < h.size(); i++) {
            assertThat(h.get(i).get("month").asText()).isGreaterThan(h.get(i - 1).get("month").asText());
        }
        assertThat(h.get(10).get("depotCents").asLong()).isPositive().isLessThan(last.get("depotCents").asLong() + 1);
        // verbundene Bank: vor dem ersten importierten Umsatz unbekannt, danach enthalten
        assertThat(h.get(0).get("externalIncluded").asBoolean()).isFalse();
        assertThat(h.get(0).get("externalCents").isNull()).isTrue();
        assertThat(last.get("externalIncluded").asBoolean()).isTrue();
        // Kontostand der Bank seit 2 Monaten nicht abgerufen → als veraltet gekennzeichnet
        boolean stale = false;
        for (JsonNode a : d.get("accounts")) {
            if (a.get("kind").asText().equals("EXTERNAL")) {
                stale |= a.get("stale").asBoolean();
            }
        }
        assertThat(stale).isTrue();
        assertThat(d.get("notes").toString()).contains("älter als 2 Tage");
    }

    @Test
    void T88_englischUndNurEigeneDaten() throws Exception {
        TestUser u = registerUser("Eli");
        TestUser other = registerUser("Ole");
        postJson(other, "/api/subaccounts", "{\"name\":\"Geheim\",\"targetCents\":5000}", 201);
        JsonNode d = getJson(u, "/api/dashboard");
        assertThat(d.get("goals")).isEmpty();
        assertThat(d.get("netWorth").get("totalCents").asLong()).isEqualTo(100_000);
        String en = mvc.perform(get("/api/dashboard").header("Authorization", u.bearer()).header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(en).contains("simulation with play money").contains("Debit card (simulation)")
                .contains("\"institution\":\"Projektbank (simulation)\"");
    }
}
