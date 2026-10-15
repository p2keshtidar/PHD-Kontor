package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** T89–T96: Multi-Banking – weitere Konten der Projektbank (Simulation), Zinsen, Umbuchungen, alle Umsätze. */
class MultiBankingFlowTest extends FlowTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private long open(TestUser u, String type, String name) throws Exception {
        String body = name == null ? "{\"type\":\"" + type + "\"}" : "{\"type\":\"" + type + "\",\"name\":\"" + name + "\"}";
        return postJson(u, "/api/accounts", body, 201).get("id").asLong();
    }

    private void move(TestUser u, long from, long to, long cents, int expected) throws Exception {
        postJson(u, "/api/accounts/move", "{\"fromAccountId\":" + from + ",\"toAccountId\":" + to + ",\"amountCents\":"
                + cents + "}", expected);
    }

    private long balanceOf(TestUser u, long accountId) throws Exception {
        for (JsonNode a : getJson(u, "/api/accounts")) {
            if (a.get("id").asLong() == accountId) {
                return a.get("balanceCents").asLong();
            }
        }
        return Long.MIN_VALUE;   // nicht (mehr) in der Liste
    }

    private int transfer(TestUser from, String recipient, long cents) throws Exception {
        return mvc.perform(post("/api/transfers").header("Authorization", from.bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"" + recipient + "\",\"amountCents\":" + cents + ",\"description\":\"Test\"}"))
                .andReturn().getResponse().getStatus();
    }

    private long interestBooked(long accountId) {
        Long sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.amount_cents), 0) FROM booking_line l JOIN booking b ON b.id = l.booking_id
                 WHERE l.account_id = ? AND b.type = 'INTEREST'
                """, Long.class, accountId);
        return sum == null ? 0 : sum;
    }

    /** Erwartete Zinsen bei konstantem Guthaben: Guthaben × Satz × Tage / Tage des Jahres, auf Cent gerundet. */
    private static long expectedInterest(long cents, String percent, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            sum = sum.add(BigDecimal.valueOf(cents).multiply(new BigDecimal(percent))
                    .divide(BigDecimal.valueOf(100L * d.lengthOfYear()), 20, RoundingMode.HALF_EVEN));
        }
        return sum.setScale(0, RoundingMode.HALF_EVEN).longValueExact();
    }

    @Test
    void T89_kontenEroeffnenMitAngebotenUndGrenze() throws Exception {
        TestUser u = registerUser("Mara");
        JsonNode p = getJson(u, "/api/accounts/products");
        assertThat(p.get("canOpen").asBoolean()).isTrue();
        assertThat(p.get("maxCount").asInt()).isEqualTo(3);
        assertThat(p.get("disclaimer").asText()).contains("Simulation").contains("fiktiv");
        assertThat(p.get("products").get(1).get("type").asText()).isEqualTo("CALL");
        assertThat(p.get("products").get(1).get("interestPercent").decimalValue()).isEqualByComparingTo("2.00");

        JsonNode call = postJson(u, "/api/accounts", "{\"type\":\"CALL\"}", 201);
        assertThat(call.get("name").asText()).isEqualTo("Tagesgeld");
        assertThat(call.get("iban").asText()).startsWith("DE");
        assertThat(call.get("product").get("receivesTransfers").asBoolean()).isFalse();
        assertThat(call.get("product").get("note").asText()).contains("Fiktiver Zinssatz");
        long current = open(u, "current", "Haushalt");
        open(u, "CALL", "Rücklage");
        postJson(u, "/api/accounts", "{\"type\":\"CALL\"}", 409);                 // Grenze: 3 weitere Konten
        postJson(u, "/api/accounts", "{\"type\":\"LOAN\"}", 400);
        assertThat(getJson(u, "/api/accounts/products").get("canOpen").asBoolean()).isFalse();
        putJson(u, "/api/accounts/" + current, "{\"name\":\"" + "x".repeat(41) + "\"}", 400);
        assertThat(putJson(u, "/api/accounts/" + current, "{\"name\":\"Haushaltskonto\"}", 200).get("name").asText())
                .isEqualTo("Haushaltskonto");
        putJson(u, "/api/accounts/" + u.accountId(), "{\"name\":\"Meins\"}", 400);   // Girokonto bleibt
        deletePath(u, "/api/accounts/" + u.accountId(), 400);
        assertThat(getJson(u, "/api/accounts")).hasSize(4);
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Konto eröffnet | „Haushalt“"));
    }

    @Test
    void T90_umbuchenZwischenEigenenKonten() throws Exception {
        TestUser u = registerUser("Udo");
        long call = open(u, "CALL", null);
        long current = open(u, "CURRENT", null);
        long sub = postJson(u, "/api/subaccounts", "{\"name\":\"Urlaub\"}", 201).get("id").asLong();
        move(u, u.accountId(), call, 40_000, 204);
        move(u, call, current, 15_000, 204);
        move(u, current, sub, 5_000, 204);
        move(u, call, current, 30_000, 400);   // nur noch 250 € auf dem Tagesgeld
        move(u, call, call, 100, 400);
        assertThat(balanceOf(u, u.accountId())).isEqualTo(60_000);
        assertThat(balanceOf(u, call)).isEqualTo(25_000);
        assertThat(balanceOf(u, current)).isEqualTo(10_000);
        assertThat(balanceOf(u, sub)).isEqualTo(5_000);
        // Konten der Bank selbst sind tabu
        long bankAccount = jdbc.queryForObject("SELECT id FROM account WHERE type = 'SYSTEM' ORDER BY id LIMIT 1", Long.class);
        move(u, bankAccount, current, 100, 403);
    }

    @Test
    void T91_ueberweisungPerIbanNurAnGirokonten() throws Exception {
        TestUser u = registerUser("Iris");
        TestUser other = registerUser("Olaf");
        long current = open(u, "CURRENT", null);
        long call = open(u, "CALL", null);
        JsonNode accounts = getJson(u, "/api/accounts");
        String currentIban = null;
        String callIban = null;
        for (JsonNode a : accounts) {
            if (a.get("id").asLong() == current) {
                currentIban = a.get("iban").asText();
            } else if (a.get("id").asLong() == call) {
                callIban = a.get("iban").asText();
            }
        }
        assertThat(transfer(other, currentIban, 1_234)).isEqualTo(201);
        assertThat(balanceOf(u, current)).isEqualTo(1_234);
        assertThat(transfer(other, callIban, 1_000)).isEqualTo(400);
        assertThat(transfer(u, currentIban, 100)).isEqualTo(400);      // an sich selbst: Umbuchen nutzen
        assertThat(balanceOf(u, call)).isZero();
        assertThat(balance(other)).isEqualTo(100_000 - 1_234);
    }

    @Test
    void T92_zinsenMonatlichGenauEinmal() throws Exception {
        TestUser u = registerUser("Zora");
        long call = open(u, "CALL", null);
        LocalDate opened = today(u);
        move(u, u.accountId(), call, 73_000, 204);
        LocalDate first = advanceToNextDay(u, 1);
        u = relogin(u);
        long expected = expectedInterest(73_000, "2.00", opened, first.minusDays(1));
        assertThat(expected).isPositive();
        assertThat(interestBooked(call)).isEqualTo(expected);
        String key = "interest:" + call + ":" + first.minusMonths(1).toString().substring(0, 7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking WHERE idempotency_key = ?", Integer.class, key))
                .isEqualTo(1);
        advance("days", 3);
        u = relogin(u);
        assertThat(interestBooked(call)).isEqualTo(expected);           // nicht doppelt
        JsonNode items = getJson(u, "/api/accounts/" + call + "/bookings").get("items");
        assertThat(items.get(0).get("type").asText()).isEqualTo("INTEREST");
        assertThat(items.get(0).get("description").asText()).startsWith("Zinsen Tagesgeld").endsWith("(Simulation)");
        assertThat(items.get(0).get("counterpartyName").asText()).isEqualTo("Projektbank");
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Zinsen gutgeschrieben | „Tagesgeld“"));
        assertThat(balanceOf(u, call)).isEqualTo(73_000 + expected);
    }

    @Test
    void T93_aufloesenMitZinsenBisGesternUndRestguthaben() throws Exception {
        TestUser u = registerUser("Kai");
        long call = open(u, "CALL", null);
        move(u, u.accountId(), call, 36_500, 204);
        advance("days", 12);
        u = relogin(u);
        long mainBefore = balance(u);
        JsonNode r = json.readTree(mvc.perform(delete("/api/accounts/" + call).header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        long interest = interestBooked(call);
        assertThat(interest).as("12 Tage Zinsen auf 365 €").isPositive();
        assertThat(r.get("movedCents").asLong()).isEqualTo(36_500 + interest);
        assertThat(r.get("message").asText()).contains("aufs Girokonto gebucht");
        assertThat(balance(u)).isEqualTo(mainBefore + 36_500 + interest);
        assertThat(balanceOf(u, call)).isEqualTo(Long.MIN_VALUE);       // aufgelöst
        deletePath(u, "/api/accounts/" + call, 400);
        advanceToNextDay(u, 1);
        assertThat(interestBooked(call)).isEqualTo(interest);           // keine Zinsen nach dem Auflösen
    }

    @Test
    void T94_alleUmsaetzeAusProjektbankUndVerbundenerBank() throws Exception {
        TestUser u = userWithDemo("Vera");
        long call = open(u, "CALL", null);
        move(u, u.accountId(), call, 20_000, 204);
        JsonNode c = postJson(u, "/api/banking/connections", "{\"provider\":\"SANDBOX\",\"institutionId\":\"SANDBOX_MAINBANK\"}", 201);
        postJson(u, "/api/banking/connections/" + c.get("connection").get("id").asLong() + "/complete", "{\"approve\":true}", 200);

        List<JsonNode> all = new ArrayList<>();
        JsonNode page;
        int p = 0;
        do {
            page = getJson(u, "/api/transactions?size=50&page=" + p++);
            page.get("items").forEach(all::add);
        } while (page.get("hasMore").asBoolean());
        Set<String> sources = new java.util.HashSet<>();
        long income = 0;
        long expense = 0;
        Set<String> neutral = Set.of("STARTGUTHABEN", "UMBUCHUNG", "SPAREN", "KREDIT_AUSZAHLUNG", "SONDERTILGUNG");
        String previous = "9999-12-31";
        for (JsonNode t : all) {
            sources.add(t.get("source").asText());
            assertThat(t.get("date").asText()).isLessThanOrEqualTo(previous);   // neueste zuerst
            previous = t.get("date").asText();
            boolean counts = t.get("status").asText().equals("BOOKED") && t.get("currency").asText().equals("EUR")
                    && !t.get("excluded").asBoolean() && !neutral.contains(t.get("category").asText());
            if (counts && t.get("amountCents").asLong() > 0) {
                income += t.get("amountCents").asLong();
            } else if (counts) {
                expense -= t.get("amountCents").asLong();
            }
            assertThat(t.get("internal").asBoolean()).isEqualTo(t.get("category").asText().equals("UMBUCHUNG"));
        }
        assertThat(sources).containsExactlyInAnyOrder("PROJEKTBANK", "EXTERNAL");
        assertThat(page.get("incomeCents").asLong()).isEqualTo(income);
        assertThat(page.get("expenseCents").asLong()).isEqualTo(expense);
        assertThat(all).anyMatch(t -> t.get("accountName").asText().equals("Tagesgeld") && t.get("internal").asBoolean());

        JsonNode ext = getJson(u, "/api/transactions?source=EXTERNAL&size=50");
        ext.get("items").forEach(t -> assertThat(t.get("source").asText()).isEqualTo("EXTERNAL"));
        assertThat(ext.get("items")).isNotEmpty();
        JsonNode noInternal = getJson(u, "/api/transactions?internal=false&size=50");
        noInternal.get("items").forEach(t -> assertThat(t.get("internal").asBoolean()).isFalse());
        JsonNode search = getJson(u, "/api/transactions?q=tagesgeld&source=PROJEKTBANK");
        assertThat(search.get("items")).isNotEmpty();
        search.get("items").forEach(t -> assertThat(t.toString().toLowerCase()).contains("tagesgeld"));
        JsonNode small = getJson(u, "/api/transactions?size=2");
        assertThat(small.get("items")).hasSize(2);
        assertThat(small.get("hasMore").asBoolean()).isTrue();
        mvc.perform(get("/api/transactions?source=XX").header("Authorization", u.bearer())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/transactions?q=" + "a".repeat(101)).header("Authorization", u.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void T95_dashboardUndGesamtvermoegenZaehlenWeitereKonten() throws Exception {
        TestUser u = registerUser("Dora");
        long call = open(u, "CALL", null);
        long current = open(u, "CURRENT", null);
        move(u, u.accountId(), call, 30_000, 204);
        move(u, u.accountId(), current, 10_000, 204);
        JsonNode d = getJson(u, "/api/dashboard");
        assertThat(d.get("netWorth").get("accountsCents").asLong()).isEqualTo(100_000);
        assertThat(d.get("netWorth").get("totalCents").asLong())
                .isEqualTo(getJson(u, "/api/insights/net-worth").get("totalCents").asLong());
        assertThat(d.get("liquidity").get("availableCents").asLong()).isEqualTo(70_000);   // Girokonten
        assertThat(d.get("liquidity").get("reservedCents").asLong()).isEqualTo(30_000);    // Tagesgeld
        assertThat(d.get("accounts").toString()).contains("\"note\":\"Tagesgeld\"").contains("\"note\":\"Weiteres Girokonto\"");
        assertThat(d.get("notes").toString()).contains("fiktiver Zinssatz");
    }

    @Test
    void T96_englisch() throws Exception {
        TestUser u = registerUser("Erin");
        String p = mvc.perform(get("/api/accounts/products").header("Authorization", u.bearer())
                        .header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(p).contains("Additional current account").contains("Instant-access savings")
                .contains("no deposit protection");
        String err = mvc.perform(post("/api/accounts").header("Authorization", u.bearer()).header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"GOLD\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(err).contains("This account type does not exist");
    }
}
