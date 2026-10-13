package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.booking.BookingLineRepository;
import de.frauas.girokonto.loan.LoanMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Phase 5: Kannkriterien /K010/ Erinnerung an die Sparrate und /K020/ Sondertilgung (T26 bis T29). */
class Phase5FlowTest extends FlowTestSupport {

    @Autowired BookingLineRepository bookingLines;

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
        LocalDate twelfth = advanceToNextDay(rich, 12);
        rich = relogin(rich);
        poor = relogin(poor);

        List<String> richTexts = notificationTexts(rich);
        assertThat(richTexts).as("Rosa %s→%s: %s", t, twelfth, String.join(" || ", richTexts)).anyMatch(s -> s.startsWith("Investrate in 3 Tagen")
                && s.contains("100,00") && s.contains("„ETF“") && !s.contains("Achtung"));
        List<String> poorTexts = notificationTexts(poor);
        assertThat(poorTexts).as("Piet: %s", String.join(" || ", poorTexts)).anyMatch(s -> s.startsWith("Investrate in 3 Tagen") && s.contains("Achtung"));

        // Erinnerung ausschalten -> wird gespeichert
        long planId = richPlan.get("summary").get("id").asLong();
        JsonNode off = putJson(rich, "/api/plans/" + planId, "{\"reminderDays\":0}", 200);
        assertThat(off.get("summary").get("reminderDays").asInt()).isZero();
        putJson(rich, "/api/plans/" + planId, "{\"reminderDays\":11}", 400);
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
        assertThat(notificationTexts(u)).anyMatch(s -> s.startsWith("Sondertilgung gebucht"))
                .anyMatch(s -> s.startsWith("Kredit ausgezahlt"))
                .noneMatch(s -> s.matches("(?s).*\\d{4}-\\d{2}-\\d{2}.*"));      // Datum immer deutsch
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
