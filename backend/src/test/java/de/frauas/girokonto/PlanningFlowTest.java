package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.booking.BookingLineRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Phase 4: Finanzüberblick, Spar-/Investpläne, Kredite, Demo-Modus (T09 bis T25). */
class PlanningFlowTest extends IntegrationTestBase {

    @Autowired BookingLineRepository bookingLines;

    // ---------------------------------------------------------------- Hilfen

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    private String admin() throws Exception {
        return login("admin@projektbank.de", "admin12345");
    }

    private void advanceMonths(int months) throws Exception {
        mvc.perform(post("/api/admin/clock/advance").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"months\":" + months + "}"))
                .andExpect(status().isOk());
    }

    private LocalDate today(TestUser u) throws Exception {
        JsonNode c = json.readTree(mvc.perform(get("/api/clock").header("Authorization", u.bearer()))
                .andReturn().getResponse().getContentAsString());
        return LocalDate.parse(c.get("date").asText());
    }

    private TestUser userWithDemo(String name, String profile) throws Exception {
        TestUser u = registerUser(name);
        mvc.perform(post("/api/demo/sample-months").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"profile\":\"" + profile + "\"}"))
                .andExpect(status().isCreated());
        return u;
    }

    private JsonNode getJson(TestUser u, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(TestUser u, String path, String body, int expected) throws Exception {
        String r = mvc.perform(post(path).header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return r.isEmpty() ? null : json.readTree(r);
    }

    // ---------------------------------------------------------------- Finanzüberblick

    @Test
    void T16_beispielmonateUndUebersicht() throws Exception {
        TestUser u = userWithDemo("Emil", "EINSTEIGER");
        JsonNode months = getJson(u, "/api/insights/months?count=6");
        assertThat(months).hasSize(6);
        for (int i = 0; i < 5; i++) {
            assertThat(months.get(i).get("complete").asBoolean()).isTrue();
            assertThat(months.get(i).get("incomeCents").asLong()).isEqualTo(265_000);
            assertThat(months.get(i).get("fixedCents").asLong()).isEqualTo(100_000);
        }
        JsonNode ov = getJson(u, "/api/insights/overview");
        assertThat(ov.get("hasData").asBoolean()).isTrue();
        assertThat(ov.get("avgIncomeCents").asLong()).isEqualTo(265_000);
        assertThat(ov.get("avgFreeCents").asLong()).isPositive();
        assertThat(ov.get("demoProfile").asText()).isEqualTo("EINSTEIGER");

        // nur einmal möglich
        postJson(u, "/api/demo/sample-months", "{\"profile\":\"STUDENT\"}", 409);
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void T17_kategorieAendern() throws Exception {
        TestUser u = userWithDemo("Karla", "STUDENT");
        JsonNode items = getJson(u, "/api/accounts/" + u.accountId() + "/bookings?type=CARD&size=1").get("items");
        long bookingId = items.get(0).get("bookingId").asLong();
        mvc.perform(put("/api/accounts/" + u.accountId() + "/bookings/" + bookingId + "/category")
                        .header("Authorization", u.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"FREIZEIT\"}"))
                .andExpect(status().isNoContent());
        JsonNode after = getJson(u, "/api/accounts/" + u.accountId() + "/bookings?type=CARD&size=1").get("items");
        assertThat(after.get(0).get("category").asText()).isEqualTo("FREIZEIT");

        // Startguthaben hat eine feste Kategorie
        JsonNode initial = getJson(u, "/api/accounts/" + u.accountId() + "/bookings?type=INITIAL").get("items");
        mvc.perform(put("/api/accounts/" + u.accountId() + "/bookings/" + initial.get(0).get("bookingId").asLong() + "/category")
                        .header("Authorization", u.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"GEHALT\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Sparplan

    @Test
    void T09_T18_machbarkeit() throws Exception {
        TestUser u = userWithDemo("Mia", "EINSTEIGER");
        LocalDate in36 = today(u).plusMonths(36);
        JsonNode ok = postJson(u, "/api/plans/check", """
                {"type":"GOAL","targetCents":500000,"targetDate":"%s","riskLevel":"VORSICHTIG"}
                """.formatted(in36), 200);
        assertThat(ok.get("requiredRateCents").asLong()).isEqualTo(13_900);
        assertThat(ok.get("feasibility").asText()).isEqualTo("GREEN");

        JsonNode red = postJson(u, "/api/plans/check", """
                {"type":"GOAL","targetCents":5000000,"targetDate":"%s","riskLevel":"AUSGEWOGEN"}
                """.formatted(today(u).plusMonths(12)), 200);
        assertThat(red.get("feasibility").asText()).isEqualTo("RED");
        assertThat(red.get("alternativeDate").isNull()).isFalse();
    }

    @Test
    void ohneDatenIstMachbarkeitUnbekannt() throws Exception {
        TestUser u = registerUser("Noah");
        JsonNode r = postJson(u, "/api/plans/check", """
                {"type":"INVEST","monthlyRateCents":20000,"riskLevel":"MUTIG"}
                """, 200);
        assertThat(r.get("feasibility").asText()).isEqualTo("UNKNOWN");
    }

    @Test
    void T10_T13_sparplanAusgewogenUndAuszahlen() throws Exception {
        TestUser u = userWithDemo("Lea", "EINSTEIGER");
        JsonNode plan = postJson(u, "/api/plans", """
                {"type":"GOAL","name":"Urlaub","icon":"flight","targetCents":300000,"targetDate":"%s",
                 "monthlyRateCents":10000,"executionDay":15,"riskLevel":"AUSGEWOGEN"}
                """.formatted(today(u).plusMonths(24)), 201);
        long planId = plan.get("summary").get("id").asLong();

        advanceMonths(1);
        u = new TestUser(u.id(), u.email(), u.phone(), login(u.email(), "geheim12345").substring(7), u.accountId(), u.iban());
        JsonNode d = getJson(u, "/api/plans/" + planId);
        assertThat(d.get("summary").get("depositedCents").asLong()).isEqualTo(10_000);
        assertThat(d.get("cashCents").asLong()).isEqualTo(5_000);
        long invested = 0;
        for (JsonNode p : d.get("positions")) {
            invested += p.get("investedCents").asLong();
        }
        assertThat(invested).isEqualTo(5_000);
        assertThat(d.get("depotValueCents").asLong()).isPositive();

        long before = balance(u);
        long value = d.get("summary").get("currentValueCents").asLong();
        JsonNode paid = postJson(u, "/api/plans/" + planId + "/payout", "{}", 200);
        assertThat(paid.get("summary").get("status").asText()).isEqualTo("COMPLETED");
        assertThat(balance(u)).isEqualTo(before + value);
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void T11_T19_rateAusgesetztUndInvestplan() throws Exception {
        TestUser poor = registerUser("Paul");      // nur 1.000 € Startguthaben, keine Einnahmen
        JsonNode tooBig = postJson(poor, "/api/plans", """
                {"type":"INVEST","name":"Zu viel","monthlyRateCents":200000,"executionDay":10,"riskLevel":"MUTIG"}
                """, 201);
        TestUser rich = userWithDemo("Rita", "FAMILIE");
        JsonNode invest = postJson(rich, "/api/plans", """
                {"type":"INVEST","name":"ETF-Sparplan","monthlyRateCents":20000,"executionDay":20,"riskLevel":"AUSGEWOGEN"}
                """, 201);

        advanceMonths(3);
        poor = new TestUser(poor.id(), poor.email(), poor.phone(), login(poor.email(), "geheim12345").substring(7),
                poor.accountId(), poor.iban());
        rich = new TestUser(rich.id(), rich.email(), rich.phone(), login(rich.email(), "geheim12345").substring(7),
                rich.accountId(), rich.iban());

        JsonNode p1 = getJson(poor, "/api/plans/" + tooBig.get("summary").get("id").asLong());
        assertThat(p1.get("summary").get("depositedCents").asLong()).isZero();
        JsonNode notes = getJson(poor, "/api/notifications");
        assertThat(notes.get("items").get(0).get("title").asText()).isEqualTo("Rate ausgesetzt");

        JsonNode p2 = getJson(rich, "/api/plans/" + invest.get("summary").get("id").asLong());
        assertThat(p2.get("summary").get("depositedCents").asLong()).isEqualTo(60_000);
        assertThat(p2.get("depotValueCents").asLong()).isPositive();
        assertThat(p2.get("history").size()).isGreaterThanOrEqualTo(3);

        // auflösen
        mvc.perform(delete("/api/plans/" + invest.get("summary").get("id").asLong()).header("Authorization", rich.bearer()))
                .andExpect(status().isNoContent());
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    // ---------------------------------------------------------------- Kredit

    @Test
    void T20_bis_T23_kreditVergleichenAbschliessenAbbezahlen() throws Exception {
        TestUser u = userWithDemo("Kai", "EINSTEIGER");
        JsonNode offers = postJson(u, "/api/loans/offers", """
                {"purpose":"AUTO","principalCents":2000000,"equityCents":0,"termMonths":60}
                """, 200);
        assertThat(offers.get("offers")).hasSize(4);
        long previous = 0;
        JsonNode nordlicht = null;
        for (JsonNode o : offers.get("offers")) {
            assertThat(o.get("totalCostCents").asLong()).isGreaterThanOrEqualTo(previous);
            previous = o.get("totalCostCents").asLong();
            if (o.get("bank").asText().equals("NORDLICHT")) {
                nordlicht = o;
            }
        }
        assertThat(nordlicht).isNotNull();
        assertThat(nordlicht.get("monthlyRateCents").asLong()).isEqualTo(38_286);

        JsonNode schedule = mvc.perform(post("/api/loans/schedule").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalCents\":2000000,\"interestRate\":5.59,\"termMonths\":60}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(s -> {
                    try { return json.readTree(s); } catch (Exception e) { throw new RuntimeException(e); }
                });
        assertThat(schedule).hasSize(60);
        assertThat(schedule.get(59).get("remainingCents").asLong()).isZero();

        long before = balance(u);
        JsonNode loan = postJson(u, "/api/loans", """
                {"bank":"NORDLICHT","purpose":"AUTO","principalCents":2000000,"equityCents":0,"termMonths":60}
                """, 201);
        long loanId = loan.get("loan").get("id").asLong();
        assertThat(balance(u)).isEqualTo(before + 2_000_000);
        assertThat(loan.get("loan").get("remainingCents").asLong()).isEqualTo(2_000_000);

        advanceMonths(1);
        u = new TestUser(u.id(), u.email(), u.phone(), login(u.email(), "geheim12345").substring(7), u.accountId(), u.iban());
        JsonNode after = getJson(u, "/api/loans/" + loanId).get("loan");
        assertThat(after.get("paidInstallments").asInt()).isEqualTo(1);
        assertThat(after.get("remainingCents").asLong()).isEqualTo(2_000_000 - (38_286 - 9_317));
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void T24_zuHoheRateWirdAbgelehnt() throws Exception {
        TestUser u = userWithDemo("Ben", "STUDENT");
        postJson(u, "/api/loans", """
                {"bank":"MAINUFER","purpose":"IMMOBILIE","principalCents":90000000,"equityCents":0,"termMonths":120}
                """, 400);
    }

    @Test
    void T25_nurAdminDarfVorspulen() throws Exception {
        TestUser u = registerUser("Otto");
        mvc.perform(post("/api/admin/clock/advance").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}"))
                .andExpect(status().isForbidden());
    }
}
