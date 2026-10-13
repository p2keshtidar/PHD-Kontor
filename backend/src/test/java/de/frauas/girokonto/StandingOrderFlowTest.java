package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.booking.BookingLineRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** /K030/ Daueraufträge (T30 bis T33). */
class StandingOrderFlowTest extends FlowTestSupport {

    @Autowired BookingLineRepository bookingLines;

    private static final String PATH = "/api/standing-orders";

    private JsonNode create(TestUser from, TestUser to, long cents, int day, int expected) throws Exception {
        return postJson(from, PATH, """
                {"recipient":"%s","amountCents":%d,"reference":"Miete Anteil","executionDay":%d}
                """.formatted(to.email(), cents, day), expected);
    }

    @Test
    void T30_dauerauftragWirdJedenMonatAusgefuehrt() throws Exception {
        TestUser anna = userWithDemo("Anna");
        TestUser ben = registerUser("Ben");
        long benBefore = balance(ben);

        JsonNode o = create(anna, ben, 5_000, 5, 201);
        assertThat(o.get("recipientName").asText()).startsWith("Ben");
        assertThat(o.get("intervalLabel").asText()).isEqualTo("monatlich");
        assertThat(o.get("status").asText()).isEqualTo("ACTIVE");
        LocalDate next = LocalDate.parse(o.get("nextExecutionDate").asText());
        assertThat(next.getDayOfMonth()).isEqualTo(5);
        // Datum in Meldungen deutsch (01.11.2026), nicht technisch (2026-11-01)
        String german = "%02d.%02d.%d".formatted(next.getDayOfMonth(), next.getMonthValue(), next.getYear());
        assertThat(notificationTexts(anna)).anyMatch(t -> t.startsWith("Dauerauftrag eingerichtet") && t.contains("erstmals am " + german))
                .noneMatch(t -> t.matches("(?s).*\\d{4}-\\d{2}-\\d{2}.*"));

        LocalDate first = advanceToNextDay(anna, 5);
        assertThat(first).isEqualTo(next);
        anna = relogin(anna);
        ben = relogin(ben);
        assertThat(balance(ben)).isEqualTo(benBefore + 5_000);
        assertThat(notificationTexts(ben)).anyMatch(s -> s.startsWith("Geld erhalten") && s.contains("Miete Anteil"));

        JsonNode list = getJson(anna, PATH);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("lastExecutedMonth").asText()).isEqualTo(YearMonth.from(first).toString());
        assertThat(list.get(0).get("nextExecutionDate").asText()).isEqualTo(first.plusMonths(1).toString());

        advanceToNextDay(anna, 5);
        ben = relogin(ben);
        assertThat(balance(ben)).isEqualTo(benBefore + 10_000);
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void T31_ungueltigeDauerauftraege() throws Exception {
        TestUser cem = registerUser("Cem");
        TestUser dana = registerUser("Dana");
        create(cem, cem, 1_000, 5, 400);                                     // an sich selbst
        create(cem, dana, 1_000, 29, 400);                                   // Tag 29
        create(cem, dana, 0, 5, 400);                                        // Betrag 0
        postJson(cem, PATH, """
                {"recipient":"%s","amountCents":1000,"executionDay":5,"intervalMonths":2}
                """.formatted(dana.email()), 400);                         // Intervall 2
        postJson(cem, PATH, """
                {"recipient":"niemand@example.org","amountCents":1000,"executionDay":5}
                """, 404);                                                   // unbekannt
    }

    @Test
    void T32_pausierenAendernLoeschen() throws Exception {
        TestUser eva = registerUser("Eva");
        TestUser finn = registerUser("Finn");
        long finnBefore = balance(finn);
        long id = create(eva, finn, 2_000, 7, 201).get("id").asLong();

        JsonNode paused = putJson(eva, PATH + "/" + id, "{\"paused\":true,\"amountCents\":2500}", 200);
        assertThat(paused.get("status").asText()).isEqualTo("PAUSED");
        assertThat(paused.get("amountCents").asLong()).isEqualTo(2_500);

        TestUser other = registerUser("Gast");
        putJson(other, PATH + "/" + id, "{\"paused\":false}", 404);        // fremder Auftrag

        advanceToNextDay(eva, 7);
        finn = relogin(finn);
        assertThat(balance(finn)).isEqualTo(finnBefore);                   // pausiert: nichts gebucht

        eva = relogin(eva);
        deletePath(eva, PATH + "/" + id, 204);
        assertThat(getJson(eva, PATH)).isEmpty();
        putJson(eva, PATH + "/" + id, "{\"paused\":false}", 409);          // beendet
    }

    @Test
    void T33_zuWenigGuthabenGibtMeldung() throws Exception {
        TestUser hans = registerUser("Hans");                              // nur 1.000 € Startguthaben
        TestUser ida = registerUser("Ida");
        long idaBefore = balance(ida);
        create(hans, ida, 200_000, 9, 201);
        advanceToNextDay(hans, 9);
        hans = relogin(hans);
        ida = relogin(ida);
        assertThat(balance(ida)).isEqualTo(idaBefore);
        assertThat(notificationTexts(hans)).anyMatch(s -> s.startsWith("Dauerauftrag nicht ausgeführt")
                && s.contains("Guthaben"));
        assertThat(getJson(hans, PATH).get(0).get("status").asText()).isEqualTo("ACTIVE");
    }
}
