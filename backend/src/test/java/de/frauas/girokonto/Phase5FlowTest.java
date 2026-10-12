package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.booking.BookingLineRepository;
import de.frauas.girokonto.loan.LoanMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Phase 5: Kannkriterien /K010/ Erinnerung an die Sparrate und /K020/ Sondertilgung (T26 bis T29). */
class Phase5FlowTest extends IntegrationTestBase {

    @Autowired BookingLineRepository bookingLines;

    // ---------------------------------------------------------------- Hilfen

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"geheim12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private TestUser relogin(TestUser u) throws Exception {
        return new TestUser(u.id(), u.email(), u.phone(), login(u.email()), u.accountId(), u.iban());
    }

    private void advance(String field, int value) throws Exception {
        String admin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@projektbank.de\",\"password\":\"admin12345\"}"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/admin/clock/advance")
                        .header("Authorization", "Bearer " + json.readTree(admin).get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"" + field + "\":" + value + "}"))
                .andExpect(status().isOk());
    }

    private LocalDate today(TestUser u) throws Exception {
        return LocalDate.parse(getJson(u, "/api/clock").get("date").asText());
    }

    private TestUser userWithDemo(String name) throws Exception {
        TestUser u = registerUser(name);
        mvc.perform(post("/api/demo/sample-months").header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"profile\":\"EINSTEIGER\"}"))
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

    private List<String> notificationTexts(TestUser u) throws Exception {
        List<String> texts = new ArrayList<>();
        for (JsonNode n : getJson(u, "/api/notifications?size=50").get("items")) {
            texts.add(n.get("title").asText() + " | " + n.get("body").asText());
        }
        return texts;
    }

    // ---------------------------------------------------------------- /K010/ Erinnerung

    @Test
    void T26_erinnerungDreiTageVorDerSparrate() throws Exception {
        TestUser rich = userWithDemo("Rosa");
        TestUser poor = registerUser("Piet");   // nur 1.000 € Startguthaben
        JsonNode richPlan = postJson(rich, "/api/plans", """
                {"type":"INVEST","name":"ETF","monthlyRateCents":10000,"executionDay":15,"riskLevel":"AUSGEWOGEN"}
                """, 201);
        assertThat(richPlan.get("summary").get("reminderDays").asInt()).isEqualTo(3);
        postJson(poor, "/api/plans", """
                {"type":"INVEST","name":"Zu viel","monthlyRateCents":200000,"executionDay":15,"riskLevel":"MUTIG"}
                """, 201);

        // bis zum nächsten 12. vorspulen: 3 Tage vor dem Ausführungstag 15
        LocalDate t = today(rich);
        LocalDate twelfth = t.getDayOfMonth() < 12 ? t.withDayOfMonth(12) : t.plusMonths(1).withDayOfMonth(12);
        advance("days", (int) (twelfth.toEpochDay() - t.toEpochDay()));
        rich = relogin(rich);
        poor = relogin(poor);

        List<String> richTexts = notificationTexts(rich);
        assertThat(richTexts).as("Rosa %s→%s: %s", t, twelfth, String.join(" || ", richTexts)).anyMatch(s -> s.startsWith("Investrate in 3 Tagen")
                && s.contains("100,00") && s.contains("„ETF“") && !s.contains("Achtung"));
        List<String> poorTexts = notificationTexts(poor);
        assertThat(poorTexts).as("Piet: %s", String.join(" || ", poorTexts)).anyMatch(s -> s.startsWith("Investrate in 3 Tagen") && s.contains("Achtung"));

        // Erinnerung ausschalten -> wird gespeichert
        long planId = richPlan.get("summary").get("id").asLong();
        String r = mvc.perform(put("/api/plans/" + planId).header("Authorization", rich.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reminderDays\":0}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(r).get("summary").get("reminderDays").asInt()).isZero();
        mvc.perform(put("/api/plans/" + planId).header("Authorization", rich.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reminderDays\":11}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- /K020/ Sondertilgung

    private record LoanCtx(TestUser user, long loanId) {}

    private LoanCtx userWithLoan(String name) throws Exception {
        TestUser u = userWithDemo(name);
        JsonNode loan = postJson(u, "/api/loans", """
                {"bank":"NORDLICHT","purpose":"AUTO","principalCents":2000000,"equityCents":0,"termMonths":60}
                """, 201);
        return new LoanCtx(u, loan.get("loan").get("id").asLong());
    }

    @Test
    void T27_sondertilgungSenktRestschuldUndLaufzeit() throws Exception {
        LoanCtx c = userWithLoan("Sina");
        TestUser u = c.user();
        String path = "/api/loans/" + c.loanId() + "/extra-payment";

        JsonNode preview = postJson(u, path + "/preview", "{\"amountCents\":500000}", 200);
        assertThat(preview.get("remainingBeforeCents").asLong()).isEqualTo(2_000_000);
        assertThat(preview.get("remainingAfterCents").asLong()).isEqualTo(1_500_000);
        assertThat(preview.get("installmentsBefore").asInt()).isEqualTo(60);
        assertThat(preview.get("installmentsAfter").asInt()).isLessThan(60);
        assertThat(preview.get("interestSavedCents").asLong()).isPositive();

        long before = balance(u);
        JsonNode detail = postJson(u, path, "{\"amountCents\":500000}", 200);
        assertThat(balance(u)).isEqualTo(before - 500_000);
        JsonNode loan = detail.get("loan");
        assertThat(loan.get("remainingCents").asLong()).isEqualTo(1_500_000);
        assertThat(loan.get("extraPaidCents").asLong()).isEqualTo(500_000);
        assertThat(loan.get("remainingInstallments").asInt()).isEqualTo(preview.get("installmentsAfter").asInt());
        assertThat(loan.get("monthlyRateCents").asLong()).isEqualTo(38_286);   // Rate bleibt gleich
        assertThat(detail.get("interestSavedCents").asLong()).isEqualTo(preview.get("interestSavedCents").asLong());
        assertThat(detail.get("extraPayments")).hasSize(1);
        JsonNode schedule = detail.get("schedule");
        assertThat(schedule.size()).isEqualTo(preview.get("installmentsAfter").asInt());
        assertThat(schedule.get(schedule.size() - 1).get("remainingCents").asLong()).isZero();
        assertThat(bookingLines.totalOfAllLines()).isZero();

        // nächste Rate: Zins auf die gesenkte Restschuld
        advance("months", 1);
        u = relogin(u);
        JsonNode after = getJson(u, "/api/loans/" + c.loanId()).get("loan");
        long interest = LoanMath.interest(1_500_000, new BigDecimal("5.59"));
        assertThat(after.get("paidInstallments").asInt()).isEqualTo(1);
        assertThat(after.get("remainingCents").asLong()).isEqualTo(1_500_000 - (38_286 - interest));
        assertThat(notificationTexts(u)).anyMatch(s -> s.startsWith("Sondertilgung gebucht"));
    }

    @Test
    void T28_sondertilgungUngueltigeBetraege() throws Exception {
        LoanCtx c = userWithLoan("Timo");
        String path = "/api/loans/" + c.loanId() + "/extra-payment";
        postJson(c.user(), path, "{\"amountCents\":50}", 400);          // unter 1 €
        postJson(c.user(), path, "{\"amountCents\":2000001}", 400);     // über der Restschuld
        TestUser other = registerUser("Fremd");
        postJson(other, path, "{\"amountCents\":10000}", 404);          // fremder Kredit
    }

    @Test
    void T29_sondertilgungZahltKreditKomplettZurueck() throws Exception {
        LoanCtx c = userWithLoan("Vera");
        String path = "/api/loans/" + c.loanId() + "/extra-payment";
        JsonNode detail = postJson(c.user(), path, "{\"amountCents\":2000000}", 200);
        assertThat(detail.get("loan").get("status").asText()).isEqualTo("PAID_OFF");
        assertThat(detail.get("loan").get("remainingCents").asLong()).isZero();
        postJson(c.user(), path, "{\"amountCents\":10000}", 409);          // schon zurückgezahlt
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }
}
