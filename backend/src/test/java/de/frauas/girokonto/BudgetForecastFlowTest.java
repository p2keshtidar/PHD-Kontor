package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** T102–T107: Budgets je Kategorie, Warnungen, ungewöhnliche Ausgaben, Prognose des Girokontos. */
class BudgetForecastFlowTest extends FlowTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private void expense(TestUser u, String title, String category, long cents, LocalDate date) throws Exception {
        postJson(u, "/api/entries", "{\"kind\":\"EXPENSE\",\"title\":\"" + title + "\",\"category\":\"" + category
                + "\",\"amountCents\":" + cents + ",\"intervalMonths\":0,\"date\":\"" + date + "\"}", 201);
    }

    /** Ausgaben des laufenden Monats in einer Kategorie laut Finanzüberblick. */
    private long spent(TestUser u, String category) throws Exception {
        for (JsonNode c : getJson(u, "/api/insights/months?count=1").get(0).get("categories")) {
            if (c.get("category").asText().equals(category)) {
                return c.get("cents").asLong();
            }
        }
        return 0;
    }

    private static JsonNode item(JsonNode overview, String category) {
        for (JsonNode i : overview.get("items")) {
            if (i.get("category").asText().equals(category)) {
                return i;
            }
        }
        return null;
    }

    /** Ein Tag im Monat, der nicht heute ist (für Termine, die nicht sofort fällig werden sollen). */
    private static int otherDay(LocalDate today, int offset) {
        return (today.getDayOfMonth() + offset - 1) % 28 + 1;
    }

    @Test
    void T102_budgetsSetzenAendernLoeschen() throws Exception {
        TestUser u = userWithDemo("Bea");
        JsonNode o = getJson(u, "/api/budgets");
        assertThat(o.get("items")).isEmpty();
        assertThat(o.get("unbudgeted")).isNotEmpty();
        assertThat(o.get("notes").toString()).contains("wie im Finanzüberblick");

        long groceries = spent(u, "LEBENSMITTEL");
        JsonNode after = putJson(u, "/api/budgets/lebensmittel", "{\"monthlyCents\":100000}", 200);
        JsonNode i = item(after, "LEBENSMITTEL");
        assertThat(i.get("limitCents").asLong()).isEqualTo(100_000);
        assertThat(i.get("spentCents").asLong()).isEqualTo(groceries);
        assertThat(i.get("remainingCents").asLong()).isEqualTo(100_000 - groceries);
        assertThat(i.get("status").asText()).isEqualTo(groceries > 100_000 ? "OVER" : groceries >= 80_000 ? "WARN" : "OK");
        assertThat(after.get("unbudgeted").toString()).doesNotContain("LEBENSMITTEL");

        putJson(u, "/api/budgets/LEBENSMITTEL", "{\"monthlyCents\":100}", 200);
        assertThat(item(getJson(u, "/api/budgets"), "LEBENSMITTEL").get("status").asText())
                .isEqualTo(groceries > 100 ? "OVER" : "OK");
        putJson(u, "/api/budgets/GEHALT", "{\"monthlyCents\":5000}", 400);        // keine Ausgabenkategorie
        putJson(u, "/api/budgets/UMBUCHUNG", "{\"monthlyCents\":5000}", 400);
        putJson(u, "/api/budgets/GIBTESNICHT", "{\"monthlyCents\":5000}", 400);
        putJson(u, "/api/budgets/FREIZEIT", "{\"monthlyCents\":99}", 400);
        putJson(u, "/api/budgets/FREIZEIT", "{\"monthlyCents\":10000001}", 400);
        deletePath(u, "/api/budgets/LEBENSMITTEL", 204);
        assertThat(getJson(u, "/api/budgets").get("items")).isEmpty();
        assertThat(getJson(registerUser("Andere"), "/api/budgets").get("items")).isEmpty();
    }

    @Test
    void T103_warnungBei80Und100ProzentNurEinmal() throws Exception {
        TestUser u = registerUser("Willi");
        advanceToNextDay(u, 2);
        u = relogin(u);
        LocalDate today = today(u);
        putJson(u, "/api/budgets/FREIZEIT", "{\"monthlyCents\":10000}", 200);
        expense(u, "Kino und Essen", "FREIZEIT", 8_500, today);
        assertThat(item(getJson(u, "/api/budgets"), "FREIZEIT").get("status").asText()).isEqualTo("WARN");
        advance("days", 1);
        u = relogin(u);
        advance("days", 1);
        u = relogin(u);
        List<String> texts = notificationTexts(u);
        assertThat(texts.stream().filter(t -> t.startsWith("Budget fast aufgebraucht | „Freizeit“: 85,00 € von 100,00 €")))
                .hasSize(1);
        expense(u, "Konzert", "FREIZEIT", 2_000, today(u));
        advance("days", 1);
        u = relogin(u);
        advance("days", 1);
        u = relogin(u);
        texts = notificationTexts(u);
        assertThat(texts.stream().filter(t -> t.startsWith("Budget überschritten | „Freizeit“: 105,00 €"))).hasSize(1);
        assertThat(item(getJson(u, "/api/budgets"), "FREIZEIT").get("status").asText()).isEqualTo("OVER");
        assertThat(jdbc.queryForList("SELECT level FROM budget_alert WHERE user_id = ? ORDER BY level", Integer.class,
                u.id())).containsExactly(80, 100);
    }

    @Test
    void T104_ungewoehnlicheAusgabeErkannt() throws Exception {
        TestUser u = userWithDemo("Ulla");
        expense(u, "Neuer Fernseher", "SHOPPING", 189_900, today(u));
        JsonNode unusual = getJson(u, "/api/budgets").get("unusual");
        JsonNode single = null;
        for (JsonNode x : unusual) {
            if (x.get("kind").asText().equals("SINGLE") && "Neuer Fernseher".equals(x.get("text").asText())) {
                single = x;
            }
        }
        assertThat(single).as("einzelne große Ausgabe erkannt: %s", unusual).isNotNull();
        assertThat(single.get("amountCents").asLong()).isEqualTo(189_900);
        assertThat(single.get("usualCents").asLong()).isPositive().isLessThan(189_900 / 3);
        assertThat(single.get("reason").asText()).contains("so viel wie sonst bei „Shopping“");
        // ein neuer Nutzer ohne Verlauf hat nichts Auffälliges
        TestUser fresh = registerUser("Frisch");
        expense(fresh, "Fernseher", "SHOPPING", 189_900, today(fresh));
        assertThat(getJson(fresh, "/api/budgets").get("unusual")).isEmpty();
    }

    @Test
    void T105_prognoseMitBekanntenBuchungen() throws Exception {
        TestUser u = registerUser("Pia");
        TestUser rita = registerUser("Rita");
        LocalDate today = today(u);
        postJson(u, "/api/standing-orders", "{\"recipient\":\"" + rita.email() + "\",\"amountCents\":20000,"
                + "\"executionDay\":" + otherDay(today, 5) + "}", 201);
        postJson(u, "/api/plans", "{\"type\":\"INVEST\",\"name\":\"Welt\",\"monthlyRateCents\":10000,\"executionDay\":"
                + otherDay(today, 10) + ",\"riskLevel\":\"AUSGEWOGEN\"}", 201);
        postJson(u, "/api/entries", "{\"kind\":\"INCOME\",\"title\":\"Gehalt\",\"category\":\"GEHALT\","
                + "\"amountCents\":150000,\"intervalMonths\":1,\"date\":\"" + today.plusDays(7) + "\"}", 201);
        long start = balance(u);
        JsonNode f = getJson(u, "/api/forecast?days=60");
        assertThat(f.get("startBalanceCents").asLong()).isEqualTo(start);
        assertThat(f.get("historyMonths").asInt()).isZero();
        assertThat(f.get("assumptions").toString()).contains("Noch keine Umsätze aus Vormonaten");
        JsonNode points = f.get("points");
        assertThat(points).hasSize(60);
        assertThat(points.get(0).get("date").asText()).isEqualTo(today.plusDays(1).toString());
        List<String> sources = new ArrayList<>();
        long sum = 0;
        for (JsonNode e : f.get("events")) {
            sources.add(e.get("source").asText());
            sum += e.get("amountCents").asLong();
            assertThat(e.get("date").asText()).isGreaterThan(today.toString());
        }
        assertThat(sources).contains("STANDING_ORDER", "PLAN", "ENTRY");
        assertThat(f.get("events").toString()).contains("Dauerauftrag an Rita").contains("Investrate Welt")
                .contains("\"amountCents\":150000");
        JsonNode last = points.get(59);
        assertThat(last.get("expectedCents").asLong()).isEqualTo(start + sum);   // ohne Verlauf: nur bekannte Buchungen
        assertThat(last.get("bestCents").asLong()).isEqualTo(last.get("expectedCents").asLong());
        assertThat(last.get("worstCents").asLong()).isEqualTo(last.get("expectedCents").asLong());
        assertThat(f.get("monthEnds")).isNotEmpty();
        assertThat(getJson(u, "/api/forecast?days=1000").get("points")).hasSize(180);
        assertThat(getJson(u, "/api/forecast?days=1").get("points")).hasSize(7);
    }

    @Test
    void T106_liquiditaetswarnungUndSzenarien() throws Exception {
        TestUser u = registerUser("Lena");
        LocalDate due = today(u).plusDays(10);
        expense(u, "Reparatur Auto", "MOBILITAET", 120_000, due);
        JsonNode f = getJson(u, "/api/forecast?days=30");
        assertThat(f.get("warnings").toString()).contains("reicht das Guthaben am " + String.format("%02d.%02d.%d",
                due.getDayOfMonth(), due.getMonthValue(), due.getYear()) + " nicht");
        assertThat(f.get("lowest").get("expectedCents").asLong()).isEqualTo(100_000 - 120_000);

        TestUser demo = userWithDemo("Dirk");
        JsonNode g = getJson(demo, "/api/forecast?days=90");
        assertThat(g.get("historyMonths").asInt()).isBetween(1, 3);
        JsonNode end = g.get("points").get(89);
        assertThat(end.get("bestCents").asLong()).isGreaterThanOrEqualTo(end.get("expectedCents").asLong());
        assertThat(end.get("expectedCents").asLong()).isGreaterThanOrEqualTo(end.get("worstCents").asLong());
        assertThat(end.get("bestCents").asLong()).isGreaterThan(end.get("worstCents").asLong());
    }

    @Test
    void T107_englisch() throws Exception {
        TestUser u = registerUser("Eric");
        String budgets = mvc.perform(get("/api/budgets").header("Authorization", u.bearer()).header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(budgets).contains("Expenses as in the finance overview");
        String forecast = mvc.perform(get("/api/forecast").header("Authorization", u.bearer())
                        .header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(forecast).contains("No transactions from previous months yet").contains("An estimate, not a promise.");
    }
}
