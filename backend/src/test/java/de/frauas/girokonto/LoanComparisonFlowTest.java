package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T78–T84: Kreditvergleich mit mehreren Anbietern und getrennten Schritten
 * (Vergleich → Anfrage → Vertrag → Auszahlung). Alle Banken sind fiktiv (Simulation).
 */
class LoanComparisonFlowTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

    private static final String AUTO_10K = "\"purpose\":\"AUTO\",\"principalCents\":1000000,\"termMonths\":48";

    private JsonNode offers(TestUser u, String extra) throws Exception {
        return postJson(u, "/api/loans/offers", "{" + AUTO_10K + (extra == null ? "" : "," + extra) + "}", 200);
    }

    private Map<String, JsonNode> byBank(JsonNode result) {
        Map<String, JsonNode> m = new HashMap<>();
        result.get("offers").forEach(o -> m.put(o.get("bank").asText(), o));
        return m;
    }

    private JsonNode apply(TestUser u, String bank, String cls, int expected) throws Exception {
        return postJson(u, "/api/loans/applications", "{\"bank\":\"" + bank + "\"," + AUTO_10K
                + ",\"creditClass\":\"" + cls + "\",\"consent\":true}", expected);
    }

    private long loanBookings(long userId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM booking WHERE created_by = ? AND type = ?", Long.class,
                userId, type);
    }

    @Test
    void T78_vergleichMitKennzeichnungUndAnbieterstatus() throws Exception {
        TestUser u = userWithDemo("Lena");
        JsonNode r = offers(u, null);
        assertThat(r.get("offers")).hasSize(4);
        assertThat(r.get("creditClass").asText()).isEqualTo("GUT");
        assertThat(r.get("hiddenCount").asInt()).isZero();
        assertThat(r.get("disclaimerText").asText()).contains("fiktiven Banken").contains("unverbindliche Rechenbeispiele");
        assertThat(r.get("referenceRates")).isEmpty();   // Bundesbank-Abruf ist in Tests abgeschaltet
        int recommended = 0;
        for (JsonNode o : r.get("offers")) {
            assertThat(o.get("simulation").asBoolean()).isTrue();
            assertThat(o.get("providerId").asText()).isEqualTo("SIMULATION");
            assertThat(o.get("exampleText").asText()).startsWith("Unverbindliches Rechenbeispiel (Simulation, fiktive Bank)");
            assertThat(o.get("effectiveRate").decimalValue()).isGreaterThan(o.get("interestRate").decimalValue());
            assertThat(o.get("feesCents").asLong()).isZero();
            assertThat(o.get("requirements")).isNotEmpty();
            assertThat(o.get("conditions")).isNotEmpty();
            assertThat(o.get("marketDeltaPercent").isNull()).isTrue();
            recommended += o.get("recommended").asBoolean() ? 1 : 0;
        }
        assertThat(recommended).isEqualTo(1);

        Map<String, JsonNode> providers = new HashMap<>();
        getJson(u, "/api/loans/providers").forEach(p -> providers.put(p.get("id").asText(), p));
        assertThat(providers.get("SIMULATION").get("state").asText()).isEqualTo("OK");
        assertThat(providers.get("BUNDESBANK").get("state").asText()).isEqualTo("DISABLED");
        assertThat(providers.get("BUNDESBANK").get("kind").asText()).isEqualTo("REFERENCE");
        assertThat(providers.get("PARTNER").get("state").asText()).isEqualTo("NOT_CONNECTED");
        assertThat(providers.get("PARTNER").get("message").asText()).contains("Vertrag");
    }

    @Test
    void T79_bonitaetUndFilter() throws Exception {
        TestUser u = userWithDemo("Mira");
        Map<String, JsonNode> gut = byBank(offers(u, null));
        JsonNode mittelResult = offers(u, "\"creditClass\":\"MITTEL\"");
        Map<String, JsonNode> mittel = byBank(mittelResult);
        assertThat(mittel.get("NORDLICHT").get("interestRate").decimalValue()
                .subtract(gut.get("NORDLICHT").get("interestRate").decimalValue())).isEqualByComparingTo("1.50");
        assertThat(mittel.get("MAINUFER").get("eligible").asBoolean()).isFalse();
        assertThat(mittel.get("MAINUFER").get("declined").asBoolean()).isTrue();
        assertThat(mittel.get("MAINUFER").get("declineReason").asText()).contains("erst ab Bonität „gut“");
        assertThat(mittel.get("MAINUFER").get("recommended").asBoolean()).isFalse();

        JsonNode eligible = offers(u, "\"creditClass\":\"MITTEL\",\"onlyEligible\":true");
        assertThat(eligible.get("offers")).hasSize(3);
        assertThat(eligible.get("hiddenCount").asInt()).isEqualTo(1);

        long cheapestRate = gut.values().stream().mapToLong(o -> o.get("monthlyRateCents").asLong()).min().orElseThrow();
        JsonNode byRate = offers(u, "\"maxMonthlyRateCents\":" + cheapestRate);
        assertThat(byRate.get("offers")).hasSize(1);
        assertThat(byRate.get("offers").get(0).get("monthlyRateCents").asLong()).isEqualTo(cheapestRate);
        long cheapestTotal = gut.values().stream().mapToLong(o -> o.get("totalCostCents").asLong()).min().orElseThrow();
        JsonNode byBudget = offers(u, "\"maxTotalCents\":" + (cheapestTotal - 1));
        assertThat(byBudget.get("offers")).isEmpty();
        assertThat(byBudget.get("hiddenCount").asInt()).isEqualTo(4);

        postJson(u, "/api/loans/offers", "{" + AUTO_10K + ",\"creditClass\":\"EXZELLENT\"}", 400);
        postJson(u, "/api/loans/offers", "{" + AUTO_10K + ",\"maxMonthlyRateCents\":0}", 400);
    }

    @Test
    void T80_getrennteSchritteAnfrageVertragAuszahlung() throws Exception {
        TestUser u = userWithDemo("Nina");
        long start = balance(u);
        // Anfrage nur mit Einwilligung
        postJson(u, "/api/loans/applications", "{\"bank\":\"NORDLICHT\"," + AUTO_10K + "}", 400);
        JsonNode app = apply(u, "NORDLICHT", "GUT", 201);
        assertThat(app.get("status").asText()).isEqualTo("APPROVED");
        assertThat(app.get("simulation").asBoolean()).isTrue();
        assertThat(app.get("validUntil").asText()).isEqualTo(today(u).plusDays(14).toString());
        assertThat(app.get("decisionReason").asText()).contains("Noch kein Vertrag");
        assertThat(app.get("loanId").isNull()).isTrue();
        assertThat(getJson(u, "/api/loans")).isEmpty();          // Anfrage ist kein Kredit
        assertThat(balance(u)).isEqualTo(start);

        // Vertrag nur mit ausdrücklicher Bestätigung; danach noch kein Geld
        long appId = app.get("id").asLong();
        postJson(u, "/api/loans/applications/" + appId + "/accept", "{}", 400);
        JsonNode contract = postJson(u, "/api/loans/applications/" + appId + "/accept", "{\"confirm\":true}", 201);
        JsonNode loan = contract.get("loan");
        long loanId = loan.get("id").asLong();
        assertThat(loan.get("status").asText()).isEqualTo("CONTRACTED");
        assertThat(loan.get("nextDueDate").isNull()).isTrue();
        assertThat(loan.get("remainingCents").asLong()).isZero();
        assertThat(balance(u)).isEqualTo(start);
        assertThat(loanBookings(u.id(), "LOAN_PAYOUT")).isZero();
        postJson(u, "/api/loans/applications/" + appId + "/accept", "{\"confirm\":true}", 409);
        postJson(u, "/api/loans/" + loanId + "/extra-payment", "{\"amountCents\":1000}", 409);
        assertThat(getJson(u, "/api/loans/applications/" + appId).get("status").asText()).isEqualTo("ACCEPTED");

        // ohne Auszahlung keine Raten
        advance("days", 35);
        u = relogin(u);
        assertThat(getJson(u, "/api/loans/" + loanId).get("loan").get("paidInstallments").asInt()).isZero();
        assertThat(loanBookings(u.id(), "LOAN_RATE")).isZero();

        // Auszahlung: genau einmal (Kontostand ändert sich nur um den Kreditbetrag)
        LocalDate today = today(u);
        long beforePayout = balance(u);
        JsonNode paid = postJson(u, "/api/loans/" + loanId + "/payout", "", 200).get("loan");
        assertThat(paid.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(paid.get("startDate").asText()).isEqualTo(today.toString());
        assertThat(paid.get("nextDueDate").asText()).isEqualTo(today.plusMonths(1).toString());
        assertThat(paid.get("remainingCents").asLong()).isEqualTo(1_000_000);
        assertThat(balance(u)).isEqualTo(beforePayout + 1_000_000);
        postJson(u, "/api/loans/" + loanId + "/payout", "", 409);
        postJson(u, "/api/loans/" + loanId + "/cancel", "", 409);
        assertThat(loanBookings(u.id(), "LOAN_PAYOUT")).isEqualTo(1);
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Kreditanfrage beantwortet |"))
                .anyMatch(t -> t.startsWith("Kreditvertrag geschlossen (Simulation) |"))
                .anyMatch(t -> t.startsWith("Kredit ausgezahlt |"));
    }

    @Test
    void T81_ablehnungRueckzugUndAblauf() throws Exception {
        TestUser u = userWithDemo("Olaf");
        JsonNode declined = apply(u, "MAINUFER", "MITTEL", 201);
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(declined.get("validUntil").isNull()).isTrue();
        assertThat(declined.get("interestRate").isNull()).isTrue();
        postJson(u, "/api/loans/applications/" + declined.get("id").asLong() + "/accept", "{\"confirm\":true}", 409);

        JsonNode withdrawn = apply(u, "NORDLICHT", "GUT", 201);
        assertThat(postJson(u, "/api/loans/applications/" + withdrawn.get("id").asLong() + "/withdraw", "", 200)
                .get("status").asText()).isEqualTo("WITHDRAWN");
        postJson(u, "/api/loans/applications/" + withdrawn.get("id").asLong() + "/accept", "{\"confirm\":true}", 409);
        postJson(u, "/api/loans/applications/" + withdrawn.get("id").asLong() + "/withdraw", "", 409);

        JsonNode old = apply(u, "TAUNUS", "SCHWACH", 201);
        assertThat(old.get("status").asText()).isEqualTo("APPROVED");
        advance("days", 15);
        u = relogin(u);
        assertThat(getJson(u, "/api/loans/applications/" + old.get("id").asLong()).get("status").asText())
                .isEqualTo("EXPIRED");
        JsonNode err = postJson(u, "/api/loans/applications/" + old.get("id").asLong() + "/accept", "{\"confirm\":true}", 409);
        assertThat(err.get("code").asText()).isEqualTo("OFFER_EXPIRED");
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Kreditzusage abgelaufen |"));
        assertThat(getJson(u, "/api/loans/applications")).hasSize(3);
        postJson(u, "/api/loans/applications", "{\"bank\":\"NORDLICHT\"," + AUTO_10K
                + ",\"creditClass\":\"PLATIN\",\"consent\":true}", 400);
    }

    @Test
    void T82_widerrufVorDerAuszahlung() throws Exception {
        TestUser u = userWithDemo("Pia");
        long start = balance(u);
        JsonNode app = apply(u, "PROJEKTBANK", "GUT", 201);
        long loanId = postJson(u, "/api/loans/applications/" + app.get("id").asLong() + "/accept", "{\"confirm\":true}", 201)
                .get("loan").get("id").asLong();
        JsonNode cancelled = postJson(u, "/api/loans/" + loanId + "/cancel", "", 200).get("loan");
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("statusLabel").asText()).isEqualTo("Widerrufen");
        postJson(u, "/api/loans/" + loanId + "/payout", "", 409);
        postJson(u, "/api/loans/" + loanId + "/cancel", "", 409);
        assertThat(balance(u)).isEqualTo(start);
        Long account = cancelled.get("accountId").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM account WHERE id = ?", String.class, account)).isEqualTo("CLOSED");
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Kreditvertrag widerrufen |")
                && t.contains("kein Geld ausgezahlt"));
    }

    @Test
    void T83_keinZugriffAufFremdeAnfragenUndVertraege() throws Exception {
        TestUser owner = userWithDemo("Rosa");
        TestUser eve = registerUser("Eve");
        JsonNode app = apply(owner, "NORDLICHT", "GUT", 201);
        long appId = app.get("id").asLong();
        getJsonStatus(eve, "/api/loans/applications/" + appId, 404);
        postJson(eve, "/api/loans/applications/" + appId + "/accept", "{\"confirm\":true}", 404);
        postJson(eve, "/api/loans/applications/" + appId + "/withdraw", "", 404);
        long loanId = postJson(owner, "/api/loans/applications/" + appId + "/accept", "{\"confirm\":true}", 201)
                .get("loan").get("id").asLong();
        postJson(eve, "/api/loans/" + loanId + "/payout", "", 404);
        postJson(eve, "/api/loans/" + loanId + "/cancel", "", 404);
        assertThat(getJson(eve, "/api/loans/applications")).isEmpty();
        assertThat(getJson(owner, "/api/loans/" + loanId).get("loan").get("status").asText()).isEqualTo("CONTRACTED");
        mvc.perform(post("/api/loans/applications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void T84_englischeTexte() throws Exception {
        TestUser u = userWithDemo("Sam");
        String en = mvc.perform(post("/api/loans/offers").header("Authorization", u.bearer()).header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON).content("{" + AUTO_10K + "}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(en).contains("Non-binding example calculation (simulation, fictitious bank)")
                .contains("No processing fee").contains("Credit rating at least “medium” (self-assessment)")
                .contains("All offers come from fictitious banks").contains("\"stateLabel\":\"Not connected\"")
                .doesNotContain("Rechenbeispiel");
        String app = mvc.perform(post("/api/loans/applications").header("Authorization", u.bearer())
                        .header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bank\":\"NORDLICHT\"," + AUTO_10K + ",\"consent\":true}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(app).contains("\"statusLabel\":\"Approved\"").contains("No contract yet")
                .contains("Next step: conclude the contract.");
    }

    private void getJsonStatus(TestUser u, String path, int expected) throws Exception {
        mvc.perform(get(path).header("Authorization", u.bearer()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(expected));
    }
}
