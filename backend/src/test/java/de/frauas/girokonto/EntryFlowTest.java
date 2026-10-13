package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.booking.BookingLineRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Eigene Einnahmen und Ausgaben (T41–T45): einmalig und regelmäßig, Nachbuchen, Tageslauf, Fehlerfälle. */
class EntryFlowTest extends FlowTestSupport {

    private static final String PATH = "/api/entries";

    @Autowired
    BookingLineRepository bookingLines;

    private JsonNode create(TestUser u, String kind, String title, String category, long cents, int interval,
                            LocalDate date, int expected) throws Exception {
        return postJson(u, PATH, """
                {"kind":"%s","title":"%s",%s"amountCents":%d,"intervalMonths":%d,"date":"%s"}
                """.formatted(kind, title, category == null ? "" : "\"category\":\"" + category + "\",", cents, interval,
                date), expected);
    }

    private JsonNode month(TestUser u, YearMonth ym) throws Exception {
        for (JsonNode m : getJson(u, "/api/insights/months?count=13")) {
            if (m.get("month").asText().equals(ym.toString())) {
                return m;
            }
        }
        throw new AssertionError("Monat fehlt: " + ym);
    }

    private long categoryCents(JsonNode month, String category) {
        for (JsonNode c : month.get("categories")) {
            if (c.get("category").asText().equals(category)) {
                return c.get("cents").asLong();
            }
        }
        return 0;
    }

    @Test
    void T41_einmaligeAusgabeUndEinnahme() throws Exception {
        TestUser u = registerUser("Eva");
        LocalDate today = today(u);
        long before = balance(u);

        // Urlaub vor 10 Tagen: wird sofort gebucht, mit dem damaligen Datum
        JsonNode r = create(u, "EXPENSE", "Urlaub Mallorca", "FREIZEIT", 45_000, 0, today.minusDays(10), 201);
        assertThat(r.get("bookedNow").asInt()).isEqualTo(1);
        assertThat(r.get("bookedNowCents").asLong()).isEqualTo(-45_000);
        assertThat(r.get("entry").get("status").asText()).isEqualTo("DONE");
        assertThat(r.get("entry").get("intervalLabel").asText()).isEqualTo("einmalig");
        assertThat(balance(u)).isEqualTo(before - 45_000);

        // Geldgeschenk heute ohne Kategorie: Vorschlag "Sonstige Einnahmen"
        JsonNode gift = create(u, "INCOME", "Geldgeschenk Oma", null, 10_000, 0, today, 201);
        assertThat(gift.get("entry").get("category").asText()).isEqualTo("SONSTIGE_EINNAHMEN");
        assertThat(balance(u)).isEqualTo(before - 45_000 + 10_000);

        // erscheint in den Umsätzen mit eigenem Typ und Kategorie
        JsonNode items = getJson(u, "/api/accounts/" + u.accountId() + "/bookings?size=10").get("items");
        assertThat(items.findValuesAsText("description")).contains("Urlaub Mallorca", "Geldgeschenk Oma");
        for (JsonNode b : items) {
            if (b.get("description").asText().equals("Urlaub Mallorca")) {
                assertThat(b.get("type").asText()).isEqualTo("ENTRY_EXPENSE");
                assertThat(b.get("category").asText()).isEqualTo("FREIZEIT");
                assertThat(b.get("amountCents").asLong()).isEqualTo(-45_000);
                assertThat(b.get("counterpartyName").asText()).isEqualTo("Urlaub Mallorca");
            }
        }
        // und im Finanzüberblick des richtigen Monats
        JsonNode m = month(u, YearMonth.from(today.minusDays(10)));
        assertThat(categoryCents(m, "FREIZEIT")).isGreaterThanOrEqualTo(45_000);

        // künftige einmalige Ausgabe: noch nicht gebucht, nächster Termin = Datum
        JsonNode later = create(u, "EXPENSE", "Konzertkarten", null, 8_000, 0, today.plusDays(5), 201);
        assertThat(later.get("bookedNow").asInt()).isZero();
        assertThat(later.get("entry").get("nextDate").asText()).isEqualTo(today.plusDays(5).toString());
        assertThat(later.get("entry").get("category").asText()).isEqualTo("FREIZEIT");   // "konzert" erkannt
        advance("days", 5);
        u = relogin(u);
        assertThat(balance(u)).isEqualTo(before - 45_000 + 10_000 - 8_000);
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Ausgabe gebucht") && t.contains("Konzertkarten"));
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void T42_monatlichesGehaltWirdNachgebuchtUndWeiterGebucht() throws Exception {
        TestUser u = registerUser("Gerd");
        LocalDate today = today(u);
        int day = Math.min(today.getDayOfMonth(), 28);
        LocalDate start = today.minusMonths(3).withDayOfMonth(day);
        long before = balance(u);

        JsonNode r = create(u, "INCOME", "Gehalt Firma Main", null, 265_000, 1, start, 201);
        JsonNode e = r.get("entry");
        assertThat(e.get("category").asText()).isEqualTo("GEHALT");
        assertThat(e.get("intervalLabel").asText()).isEqualTo("monatlich");
        int expectedNow = (int) start.datesUntil(today.plusDays(1), java.time.Period.ofMonths(1)).count();
        assertThat(r.get("bookedNow").asInt()).isEqualTo(expectedNow);
        assertThat(balance(u)).isEqualTo(before + expectedNow * 265_000L);

        // Miete: monatlich, ab heute (heute wird sofort gebucht)
        JsonNode rent = create(u, "EXPENSE", "Miete Wohnung", "WOHNEN", 90_000, 1, today.withDayOfMonth(day), 201);
        assertThat(rent.get("bookedNow").asInt()).isEqualTo(1);   // Termin liegt nicht in der Zukunft

        // Übersicht: monatlich +2.650 €, −900 €
        JsonNode list = getJson(u, PATH);
        assertThat(list.get("monthlyIncomeCents").asLong()).isEqualTo(265_000);
        assertThat(list.get("monthlyExpenseCents").asLong()).isEqualTo(90_000);
        assertThat(list.get("monthlyBalanceCents").asLong()).isEqualTo(175_000);
        assertThat(list.get("entries")).hasSize(2);

        // jeder vergangene Monat hat das Gehalt im Finanzüberblick
        for (int i = 1; i <= 3; i++) {
            JsonNode m = month(u, YearMonth.from(today.minusMonths(i)));
            assertThat(categoryCents(m, "GEHALT")).isEqualTo(265_000);
        }

        // nächster Termin: Tageslauf bucht Gehalt vor der Miete
        long mid = balance(u);
        LocalDate next = LocalDate.parse(getJson(u, PATH).get("entries").get(0).get("nextDate").asText());
        advance("days", (int) (next.toEpochDay() - today.toEpochDay()));
        u = relogin(u);
        assertThat(balance(u)).isEqualTo(mid + 265_000 - 90_000);
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Einnahme gebucht") && t.contains("Gehalt Firma Main"));

        // vierteljährlich: Monatswert = Betrag / 3
        JsonNode q = create(u, "EXPENSE", "Versicherung", "VERTRAEGE", 30_000, 3, next.plusDays(1).withDayOfMonth(1).plusMonths(1), 201);
        assertThat(q.get("entry").get("monthlyCents").asLong()).isEqualTo(-10_000);
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void T43_ungueltigeEintraege() throws Exception {
        TestUser u = registerUser("Ines");
        LocalDate today = today(u);
        long before = balance(u);
        int countBefore = getJson(u, PATH).get("entries").size();

        // mehr ausgeben als da ist: Fehler, nichts gespeichert, nichts gebucht
        JsonNode err = create(u, "EXPENSE", "Neues Auto", "MOBILITAET", 2_000_000, 0, today, 400);
        assertThat(err.get("code").asText()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(err.get("message").asText()).contains("Neues Auto");
        // monatlich rückwirkend: der 3. Monat reicht nicht mehr -> alles zurückgerollt
        create(u, "EXPENSE", "Teure Miete", "WOHNEN", 40_000, 1, today.minusMonths(3).withDayOfMonth(1), 400);
        assertThat(balance(u)).isEqualTo(before);
        assertThat(getJson(u, PATH).get("entries")).hasSize(countBefore);

        LocalDate day30 = today.plusMonths(1);
        while (day30.lengthOfMonth() < 30) {
            day30 = day30.plusMonths(1);
        }
        assertThat(create(u, "EXPENSE", "Tag 30", "WOHNEN", 100, 1, day30.withDayOfMonth(30), 400)
                .get("code").asText()).isEqualTo("INVALID_DAY");
        assertThat(create(u, "INCOME", "Gehalt", "WOHNEN", 100, 1, today, 400).get("code").asText())
                .isEqualTo("INVALID_CATEGORY");
        assertThat(create(u, "EXPENSE", "Miete", "GEHALT", 100, 1, today, 400).get("code").asText())
                .isEqualTo("INVALID_CATEGORY");
        assertThat(create(u, "EXPENSE", "x", "KREDIT_AUSZAHLUNG", 100, 0, today, 400).get("code").asText())
                .isEqualTo("INVALID_CATEGORY");
        assertThat(create(u, "EXPENSE", "x", "GIBTSNICHT", 100, 0, today, 400).get("code").asText())
                .isEqualTo("INVALID_CATEGORY");
        assertThat(create(u, "INCOME", "x", null, 100, 2, today, 400).get("code").asText()).isEqualTo("INVALID_INTERVAL");
        assertThat(create(u, "INCOME", "x", null, 100, 0, today.minusMonths(13), 400).get("code").asText())
                .isEqualTo("INVALID_DATE");
        assertThat(create(u, "INCOME", "x", null, 100, 0, today.plusMonths(25), 400).get("code").asText())
                .isEqualTo("INVALID_DATE");
        assertThat(create(u, "INCOME", "x", null, 5_000_001, 0, today, 400).get("code").asText())
                .isEqualTo("AMOUNT_TOO_HIGH");
        create(u, "INCOME", "x", null, 0, 0, today, 400);
        postJson(u, PATH, "{\"kind\":\"INCOME\",\"title\":\" \",\"amountCents\":100,\"date\":\"" + today + "\"}", 400);
        postJson(u, PATH, "{\"kind\":\"KEINE\",\"title\":\"x\",\"amountCents\":100,\"date\":\"" + today + "\"}", 400);
        postJson(u, PATH, "{\"kind\":\"INCOME\",\"title\":\"x\",\"amountCents\":100}", 400);
        assertThat(balance(u)).isEqualTo(before);
    }

    @Test
    void T44_tageslaufPausierenEndeLoeschen() throws Exception {
        TestUser u = registerUser("Jan");
        LocalDate today = today(u);
        LocalDate first = today.plusDays(1).getDayOfMonth() <= 28 ? today.plusDays(1) : today.plusDays(5).withDayOfMonth(1);

        // monatliche Ausgabe höher als das Guthaben: Tageslauf bucht nicht, meldet es aber
        JsonNode big = create(u, "EXPENSE", "Luxus-Abo", "VERTRAEGE", 150_000, 1, first, 201);
        // pausierter Eintrag wird nicht gebucht
        JsonNode p = create(u, "INCOME", "Nebenjob", null, 20_000, 1, first, 201);
        putJson(u, PATH + "/" + p.get("entry").get("id").asLong(), "{\"paused\":true}", 200);
        // mit Enddatum: nach dem letzten Termin erledigt
        JsonNode once = create(u, "INCOME", "Bonus", null, 5_000, 1, first, 201);
        putJson(u, PATH + "/" + once.get("entry").get("id").asLong(), "{\"endDate\":\"" + first + "\"}", 200);

        long before = balance(u);
        advance("days", (int) (first.toEpochDay() - today.toEpochDay()));
        u = relogin(u);
        assertThat(balance(u)).isEqualTo(before + 5_000);   // nur der Bonus
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Ausgabe nicht gebucht") && t.contains("Luxus-Abo")
                && t.contains("Nicht genug Guthaben"));
        JsonNode list = getJson(u, PATH).get("entries");
        for (JsonNode e : list) {
            switch (e.get("title").asText()) {
                case "Bonus" -> {
                    assertThat(e.get("status").asText()).isEqualTo("DONE");
                    assertThat(e.get("bookedCount").asInt()).isEqualTo(1);
                    assertThat(e.get("nextDate").isNull()).isTrue();
                }
                case "Nebenjob" -> {
                    assertThat(e.get("status").asText()).isEqualTo("PAUSED");
                    assertThat(e.get("bookedCount").asInt()).isZero();
                }
                case "Luxus-Abo" -> {
                    assertThat(e.get("status").asText()).isEqualTo("ACTIVE");
                    assertThat(e.get("bookedCount").asInt()).isZero();
                }
                default -> { }
            }
        }
        // Monatswerte zählen nur aktive Einträge
        assertThat(getJson(u, PATH).get("monthlyIncomeCents").asLong()).isZero();

        // ändern: Betrag und Bezeichnung
        long id = big.get("entry").get("id").asLong();
        JsonNode changed = putJson(u, PATH + "/" + id, "{\"amountCents\":1999,\"title\":\"Streaming\"}", 200);
        assertThat(changed.get("amountCents").asLong()).isEqualTo(1999);
        assertThat(changed.get("title").asText()).isEqualTo("Streaming");
        putJson(u, PATH + "/" + id, "{\"category\":\"GEHALT\"}", 400);
        putJson(u, PATH + "/" + id, "{\"endDate\":\"" + today.minusDays(1) + "\"}", 400);

        // löschen: weg aus der Liste, Buchungen bleiben
        long bonusId = once.get("entry").get("id").asLong();
        deletePath(u, PATH + "/" + bonusId, 204);
        deletePath(u, PATH + "/" + bonusId, 404);
        putJson(u, PATH + "/" + bonusId, "{\"paused\":true}", 404);
        assertThat(getJson(u, PATH).get("entries").findValuesAsText("title")).doesNotContain("Bonus");
        assertThat(getJson(u, "/api/accounts/" + u.accountId() + "/bookings?size=20").get("items")
                .findValuesAsText("description")).contains("Bonus");
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void T45_fremdeEintraegeUndKategorien() throws Exception {
        TestUser a = registerUser("Kai");
        TestUser b = registerUser("Lea");
        JsonNode e = create(a, "INCOME", "Gehalt", null, 1_000, 1, today(a).plusDays(1).getDayOfMonth() <= 28
                ? today(a).plusDays(1) : today(a).plusDays(5).withDayOfMonth(1), 201);
        long id = e.get("entry").get("id").asLong();
        putJson(b, PATH + "/" + id, "{\"paused\":true}", 404);
        deletePath(b, PATH + "/" + id, 404);
        assertThat(getJson(b, PATH).get("entries")).isEmpty();

        JsonNode cats = getJson(a, PATH + "/categories");
        assertThat(cats.findValuesAsText("key")).contains("GEHALT", "SONSTIGE_EINNAHMEN", "WOHNEN", "FREIZEIT")
                .doesNotContain("STARTGUTHABEN", "UMBUCHUNG", "KREDIT_AUSZAHLUNG");
        for (JsonNode c : cats) {
            assertThat(c.get("kind").asText()).isIn("INCOME", "EXPENSE");
            assertThat(c.get("label").asText()).isNotBlank();
        }
    }
}
