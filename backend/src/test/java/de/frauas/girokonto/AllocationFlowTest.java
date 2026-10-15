package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.allocation.AllocationService;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T68–T77: Sparregeln und Geldzuweisungen für digitale Konten.
 * Quelle Girokonto = Umbuchung in der Projektbank (Simulation); Quelle verbundenes Bankkonto (Sandbox) = nur Vormerkung.
 */
class AllocationFlowTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;
    @Autowired AllocationService allocations;

    // ------------------------------------------------------------------ Hilfen

    private long digital(TestUser u, String name, Long targetCents) throws Exception {
        return postJson(u, "/api/subaccounts", "{\"name\":\"" + name + "\",\"purpose\":\"SPARZIEL\""
                + (targetCents == null ? "" : ",\"targetCents\":" + targetCents) + "}", 201).get("id").asLong();
    }

    private JsonNode rule(TestUser u, long target, long cents, String interval, String extra, int expected) throws Exception {
        return postJson(u, "/api/allocations/rules", "{\"targetAccountId\":" + target + ",\"amountCents\":" + cents
                + ",\"interval\":\"" + interval + "\"" + (extra == null ? "" : "," + extra) + "}", expected);
    }

    private long accountBalance(TestUser u, long id) throws Exception {
        for (JsonNode a : getJson(u, "/api/accounts")) {
            if (a.get("id").asLong() == id) {
                return a.get("balanceCents").asLong();
            }
        }
        throw new AssertionError("Konto " + id + " fehlt");
    }

    private JsonNode digitalInfo(TestUser u, long id) throws Exception {
        for (JsonNode a : getJson(u, "/api/accounts")) {
            if (a.get("id").asLong() == id) {
                return a.get("digital");
            }
        }
        throw new AssertionError("Konto " + id + " fehlt");
    }

    private List<JsonNode> runs(TestUser u, long account) throws Exception {
        List<JsonNode> list = new ArrayList<>();
        getJson(u, "/api/allocations/runs?accountId=" + account).forEach(list::add);
        return list;
    }

    private long connectSandbox(TestUser u, String institution) throws Exception {
        JsonNode c = postJson(u, "/api/banking/connections",
                "{\"provider\":\"SANDBOX\",\"institutionId\":\"" + institution + "\"}", 201);
        long id = c.get("connection").get("id").asLong();
        postJson(u, "/api/banking/connections/" + id + "/complete", "{\"approve\":true}", 200);
        return id;
    }

    private JsonNode externalSource(TestUser u) throws Exception {
        for (JsonNode s : getJson(u, "/api/allocations/sources")) {
            if (s.get("type").asText().equals("EXTERNAL")) {
                return s;
            }
        }
        throw new AssertionError("keine Quelle");
    }

    private void transfer(TestUser from, TestUser to, long cents) throws Exception {
        mvc.perform(post("/api/transfers").header("Authorization", from.bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"" + to.email() + "\",\"amountCents\":" + cents + ",\"description\":\"Test\"}"))
                .andExpect(status().isCreated());
    }

    private int bookingsWithKey(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM booking WHERE idempotency_key = ?", Integer.class, key);
    }

    // ------------------------------------------------------------------ Tests

    @Test
    void T68_sparregelVomGirokontoSofortUndWoechentlich() throws Exception {
        TestUser u = registerUser("Alma");
        long sub = digital(u, "Urlaub", 100_000L);
        LocalDate today = today(u);
        JsonNode r = rule(u, sub, 20_000, "WEEKLY", null, 201);
        // Start heute: gleich ausgeführt, nächster Termin in einer Woche
        assertThat(r.get("movesMoney").asBoolean()).isTrue();
        assertThat(r.get("sourceExplanation").asText()).contains("Projektbank (Simulation)").contains("keine echte Überweisung");
        assertThat(r.get("lastRun").get("status").asText()).isEqualTo("COMPLETED");
        assertThat(r.get("lastRun").get("kind").asText()).isEqualTo("TRANSFER");
        assertThat(r.get("nextDate").asText()).isEqualTo(today.plusWeeks(1).toString());
        assertThat(accountBalance(u, u.accountId())).isEqualTo(80_000);
        assertThat(accountBalance(u, sub)).isEqualTo(20_000);
        // im Hauptbuch: interne Umbuchung, keine Überweisung
        String type = jdbc.queryForObject("SELECT type FROM booking WHERE id = ?", String.class,
                r.get("lastRun").get("bookingId").asLong());
        assertThat(type).isEqualTo("INTERNAL");

        advance("days", 7);
        u = relogin(u);
        assertThat(accountBalance(u, sub)).isEqualTo(40_000);
        assertThat(accountBalance(u, u.accountId()) + accountBalance(u, sub)).isEqualTo(100_000);
        List<JsonNode> list = runs(u, sub);
        assertThat(list).hasSize(2).allMatch(x -> x.get("status").asText().equals("COMPLETED"));
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Sparregel eingerichtet |"))
                .anyMatch(t -> t.equals("Automatisch gespart | 200,00 € vom Girokonto in „Urlaub“ umgebucht."));
        // Übersicht mit anstehenden Terminen und Prognose
        JsonNode ov = getJson(u, "/api/allocations/accounts/" + sub);
        assertThat(ov.get("upcoming").get(0).get("date").asText()).isEqualTo(today.plusWeeks(2).toString());
        assertThat(ov.get("projectedGoalDate").asText()).isEqualTo(today.plusWeeks(4).toString());
        assertThat(ov.get("projectionText").asText()).startsWith("Voraussichtlich erreicht am");
    }

    @Test
    void T69_keineDeckungWiederholenFehlschlagenManuellNachholen() throws Exception {
        TestUser u = registerUser("Bert");
        TestUser friend = registerUser("Gabi");
        long sub = digital(u, "Auto", null);
        LocalDate start = today(u).plusDays(1);
        rule(u, sub, 120_000, "MONTHLY", "\"startDate\":\"" + start + "\"", 201);   // mehr als das Startguthaben
        advance("days", 1);
        u = relogin(u);
        JsonNode run = runs(u, sub).get(0);
        assertThat(run.get("status").asText()).isEqualTo("RETRYING");
        assertThat(run.get("attempts").asInt()).isEqualTo(1);
        assertThat(run.get("nextAttempt").asText()).isEqualTo(start.plusDays(1).toString());
        assertThat(run.get("failureReason").asText()).startsWith("Nicht genug Guthaben auf „Girokonto“");
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Sparregel: neuer Versuch |"));
        advance("days", 1);
        advance("days", 1);
        u = relogin(u);
        run = runs(u, sub).get(0);
        assertThat(run.get("status").asText()).isEqualTo("FAILED");
        assertThat(run.get("attempts").asInt()).isEqualTo(3);
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Sparregel fehlgeschlagen |"));
        assertThat(accountBalance(u, sub)).isZero();
        assertThat(accountBalance(u, u.accountId())).isEqualTo(100_000);   // nichts abgebucht

        // Geld kommt an → von Hand nachholen, genau einmal
        friend = relogin(friend);
        transfer(friend, u, 50_000);
        long id = run.get("id").asLong();
        JsonNode retried = postJson(u, "/api/allocations/runs/" + id + "/retry", "", 200);
        assertThat(retried.get("status").asText()).isEqualTo("COMPLETED");
        postJson(u, "/api/allocations/runs/" + id + "/retry", "", 409);
        advance("days", 1);
        u = relogin(u);
        assertThat(accountBalance(u, sub)).isEqualTo(120_000);
        assertThat(accountBalance(u, u.accountId())).isEqualTo(30_000);
        assertThat(bookingsWithKey("alloc-" + retried.get("ruleId").asLong() + "-" + start)).isEqualTo(1);
        // fremde Vorgänge: nicht gefunden
        friend = relogin(friend);
        postJson(friend, "/api/allocations/runs/" + id + "/retry", "", 404);
    }

    @Test
    void T70_keineDoppelteAbbuchungBeiParallelenLaeufenUndWiederholung() throws Exception {
        TestUser u = registerUser("Dora");
        long sub = digital(u, "Rad", null);
        LocalDate due = today(u).plusDays(1);
        JsonNode r = rule(u, sub, 10_000, "MONTHLY", "\"startDate\":\"" + due + "\"", 201);
        long ruleId = r.get("id").asLong();
        assertThat(r.get("lastRun").isNull()).isTrue();

        // vier Tagesläufe gleichzeitig für denselben Tag
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(() -> allocations.runFor(due)));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        allocations.runFor(due);   // und noch einmal hinterher
        String key = "alloc-" + ruleId + "-" + due;
        assertThat(bookingsWithKey(key)).isEqualTo(1);
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM allocation_run WHERE rule_id = ?", Integer.class, ruleId);
        assertThat(rows).isEqualTo(1);
        assertThat(accountBalance(u, sub)).isEqualTo(10_000);

        // Abbruch nach der Buchung simuliert: Vorgang steht wieder auf "Wiederholung" → nutzt die vorhandene Buchung
        Long runId = jdbc.queryForObject("SELECT id FROM allocation_run WHERE rule_id = ?", Long.class, ruleId);
        Long bookingId = jdbc.queryForObject("SELECT booking_id FROM allocation_run WHERE id = ?", Long.class, runId);
        jdbc.update("UPDATE allocation_run SET status = 'RETRYING', booking_id = NULL, next_attempt = ? WHERE id = ?",
                Date.valueOf(due), runId);
        allocations.runFor(due);
        assertThat(jdbc.queryForObject("SELECT status FROM allocation_run WHERE id = ?", String.class, runId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT booking_id FROM allocation_run WHERE id = ?", Long.class, runId))
                .isEqualTo(bookingId);
        assertThat(bookingsWithKey(key)).isEqualTo(1);
        assertThat(accountBalance(u, sub)).isEqualTo(10_000);
        assertThat(accountBalance(u, u.accountId())).isEqualTo(90_000);

        // der echte Tageslauf danach bucht nichts mehr
        advance("days", 1);
        u = relogin(u);
        assertThat(accountBalance(u, sub)).isEqualTo(10_000);
    }

    @Test
    void T71_pausierenFortsetzenAendernBeenden() throws Exception {
        TestUser u = registerUser("Emil");
        long sub = digital(u, "Möbel", null);
        LocalDate today = today(u);
        JsonNode r = rule(u, sub, 1_000, "WEEKLY", "\"endDate\":\"" + today.plusWeeks(3) + "\"", 201);
        long id = r.get("id").asLong();
        assertThat(postJson(u, "/api/allocations/rules/" + id + "/pause", "", 200).get("statusLabel").asText())
                .isEqualTo("Pausiert");
        advance("days", 8);
        u = relogin(u);
        assertThat(runs(u, sub)).hasSize(1);   // nur der erste Termin, nichts während der Pause
        // Fortsetzen: nichts nachholen, nächster regulärer Termin
        JsonNode resumed = postJson(u, "/api/allocations/rules/" + id + "/resume", "", 200);
        assertThat(resumed.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(resumed.get("nextDate").asText()).isEqualTo(today.plusWeeks(2).toString());
        assertThat(runs(u, sub)).hasSize(1);
        // ändern: Betrag; Enddatum vor dem nächsten Termin ist nicht erlaubt
        putJson(u, "/api/allocations/rules/" + id, "{\"endDate\":\"" + today.plusDays(9) + "\"}", 400);
        assertThat(putJson(u, "/api/allocations/rules/" + id, "{\"amountCents\":2500}", 200).get("amountCents").asLong())
                .isEqualTo(2_500);
        // Enddatum: nach dem letzten Termin beendet
        advance("days", 14);
        u = relogin(u);
        JsonNode list = getJson(u, "/api/allocations/rules");
        assertThat(list).isEmpty();   // beendete Regeln werden nicht mehr gelistet
        assertThat(jdbc.queryForObject("SELECT status_reason FROM allocation_rule WHERE id = ?", String.class, id))
                .isEqualTo("Enddatum erreicht");
        assertThat(accountBalance(u, sub)).isEqualTo(1_000 + 2_500 + 2_500);
        putJson(u, "/api/allocations/rules/" + id, "{\"amountCents\":100}", 409);

        // Beenden bricht wartende Wiederholungen ab
        JsonNode big = rule(u, sub, 400_000, "MONTHLY", null, 201);
        assertThat(big.get("lastRun").get("status").asText()).isEqualTo("RETRYING");
        deletePath(u, "/api/allocations/rules/" + big.get("id").asLong(), 204);
        assertThat(runs(u, sub).get(0).get("status").asText()).isEqualTo("CANCELLED");
        deletePath(u, "/api/allocations/rules/" + big.get("id").asLong(), 409);
    }

    @Test
    void T72_verbundenesBankkontoNurVormerkungMitDeckungspruefung() throws Exception {
        TestUser u = registerUser("Fiona");
        long sub = digital(u, "Japan", 500_000L);
        connectSandbox(u, "SANDBOX_MAINBANK");
        JsonNode src = externalSource(u);
        assertThat(src.get("movesMoney").asBoolean()).isFalse();
        assertThat(src.get("sandbox").asBoolean()).isTrue();
        assertThat(src.get("sourceExplanation").asText()).startsWith("Nur Vormerkung").contains("keine Zahlung");
        long ext = src.get("id").asLong();
        long available = src.get("availableCents").asLong();

        JsonNode r = rule(u, sub, 15_000, "MONTHLY", "\"sourceType\":\"EXTERNAL\",\"sourceId\":" + ext, 201);
        assertThat(r.get("lastRun").get("kind").asText()).isEqualTo("EARMARK");
        assertThat(r.get("lastRun").get("status").asText()).isEqualTo("COMPLETED");
        assertThat(r.get("lastRun").get("bookingId").isNull()).isTrue();
        // kein Geld bewegt: Projektbank-Konten unverändert, keine Buchung
        assertThat(accountBalance(u, sub)).isZero();
        assertThat(accountBalance(u, u.accountId())).isEqualTo(100_000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking b JOIN booking_line l ON l.booking_id = b.id"
                + " WHERE l.account_id = ?", Long.class, sub)).isZero();
        // zählt zum Ziel, getrennt ausgewiesen
        JsonNode d = digitalInfo(u, sub);
        assertThat(d.get("earmarkedCents").asLong()).isEqualTo(15_000);
        assertThat(d.get("savedCents").asLong()).isEqualTo(15_000);
        assertThat(d.get("progressPercent").asDouble()).isEqualTo(3.0);
        assertThat(externalSource(u).get("availableCents").asLong()).isEqualTo(available - 15_000);

        // Deckung: nie mehr vormerken, als frei ist (Kontostand bei der Bank: 200 €, davon 150 € schon vorgemerkt)
        jdbc.update("UPDATE external_account SET balance_cents = 20000 WHERE id = ?", ext);
        JsonNode refused = postJson(u, "/api/allocations/earmarks", "{\"targetAccountId\":" + sub
                + ",\"externalAccountId\":" + ext + ",\"amountCents\":5001}", 409);
        assertThat(refused.get("code").asText()).isEqualTo("NO_COVERAGE");
        assertThat(refused.get("message").asText()).isEqualTo("Nicht genug frei bei Sandbox-Hausbank (Testdaten): verfügbar 50,00 €.");
        JsonNode extra = postJson(u, "/api/allocations/earmarks", "{\"targetAccountId\":" + sub
                + ",\"externalAccountId\":" + ext + ",\"amountCents\":5000}", 201);
        assertThat(extra.get("manual").asBoolean()).isTrue();
        assertThat(getJson(u, "/api/allocations/accounts/" + sub).get("earmarkedCents").asLong()).isEqualTo(20_000);
        assertThat(externalSource(u).get("availableCents").asLong()).isZero();
        // Kontostand sinkt unter die Vormerkungen: Hinweis statt stiller Annahme
        jdbc.update("UPDATE external_account SET balance_cents = 10000 WHERE id = ?", ext);
        JsonNode warn = externalSource(u);
        assertThat(warn.get("coverageWarning").asBoolean()).isTrue();
        assertThat(warn.get("coverageText").asText()).isEqualTo("Deine Vormerkungen (200,00 €) sind höher als der Kontostand (100,00 €).");
        jdbc.update("UPDATE external_account SET balance_cents = 20000 WHERE id = ?", ext);

        // aufheben: teilweise, zu viel, alles
        postJson(u, "/api/allocations/earmarks/release", "{\"targetAccountId\":" + sub + ",\"externalAccountId\":" + ext
                + ",\"amountCents\":5000}", 200);
        postJson(u, "/api/allocations/earmarks/release", "{\"targetAccountId\":" + sub + ",\"externalAccountId\":" + ext
                + ",\"amountCents\":15001}", 400);
        postJson(u, "/api/allocations/earmarks/release", "{\"targetAccountId\":" + sub + ",\"externalAccountId\":" + ext
                + "}", 200);
        assertThat(getJson(u, "/api/allocations/accounts/" + sub).get("earmarkedCents").asLong()).isZero();
        postJson(u, "/api/allocations/earmarks/release", "{\"targetAccountId\":" + sub + ",\"externalAccountId\":" + ext
                + "}", 409);
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Automatisch vorgemerkt |")
                && t.contains("das Geld bleibt dort"));

        // Girokonto bei der Projektbank als Quelle für Vormerkungen: nicht möglich
        postJson(u, "/api/allocations/earmarks", "{\"targetAccountId\":" + sub + ",\"externalAccountId\":999999"
                + ",\"amountCents\":100}", 404);
    }

    @Test
    void T73_quelleWiderrufenOderVeraltet() throws Exception {
        TestUser u = registerUser("Greta");
        long sub = digital(u, "Notgroschen", null);
        long conn = connectSandbox(u, "SANDBOX_ONLINEBANK");
        long ext = externalSource(u).get("id").asLong();
        jdbc.update("UPDATE external_account SET balance_cents = 50000 WHERE id = ?", ext);
        JsonNode r = rule(u, sub, 2_000, "WEEKLY", "\"sourceType\":\"EXTERNAL\",\"sourceId\":" + ext, 201);
        // Kontostand zu alt und Abruf nicht möglich → Wiederholung
        jdbc.update("UPDATE bank_connection SET last_sync_at = last_sync_at - interval '5 days' WHERE id = ?", conn);
        jdbc.update("UPDATE bank_connection SET provider = 'GONE' WHERE id = ?", conn);   // Anbieter nicht erreichbar
        postJson(u, "/api/allocations/earmarks", "{\"targetAccountId\":" + sub + ",\"externalAccountId\":" + ext
                + ",\"amountCents\":100}", 409);
        jdbc.update("UPDATE bank_connection SET provider = 'SANDBOX' WHERE id = ?", conn);

        // Verbindung samt Daten gelöscht: Vormerkungen entfallen, Regel pausiert mit Grund
        assertThat(getJson(u, "/api/allocations/accounts/" + sub).get("earmarkedCents").asLong()).isEqualTo(2_000);
        deletePath(u, "/api/banking/connections/" + conn + "?deleteData=true", 200);
        assertThat(getJson(u, "/api/allocations/accounts/" + sub).get("earmarkedCents").asLong()).isZero();
        advance("days", 7);
        u = relogin(u);
        JsonNode rules = getJson(u, "/api/allocations/rules");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).get("status").asText()).isEqualTo("PAUSED");
        assertThat(rules.get(0).get("statusReason").asText()).isEqualTo("Das Bankkonto ist nicht mehr verbunden.");
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Sparregel pausiert |"));
        postJson(u, "/api/allocations/rules/" + r.get("id").asLong() + "/resume", "", 409);
    }

    @Test
    void T74_geteiltesKontoRechteUndBenachrichtigung() throws Exception {
        TestUser owner = registerUser("Olga");
        TestUser member = registerUser("Mats");
        TestUser viewer = registerUser("Vito");
        long sub = digital(owner, "WG", null);
        postJson(owner, "/api/subaccounts/" + sub + "/members", "{\"recipient\":\"" + member.email() + "\",\"role\":\"DEPOSIT\"}", 200);
        postJson(owner, "/api/subaccounts/" + sub + "/members", "{\"recipient\":\"" + viewer.email() + "\",\"role\":\"VIEW\"}", 200);

        JsonNode r = rule(member, sub, 3_000, "WEEKLY", null, 201);   // aus dem eigenen Girokonto des Mitglieds
        assertThat(r.get("lastRun").get("status").asText()).isEqualTo("COMPLETED");
        assertThat(accountBalance(member, member.accountId())).isEqualTo(97_000);
        assertThat(notificationTexts(owner)).anyMatch(t -> t.contains("hat 30,00 € eingezahlt in „WG“"));
        rule(viewer, sub, 3_000, "WEEKLY", null, 403);
        JsonNode ov = getJson(owner, "/api/allocations/accounts/" + sub);
        assertThat(ov.get("rules")).isEmpty();
        assertThat(ov.get("otherRules").asInt()).isEqualTo(1);
        assertThat(getJson(viewer, "/api/allocations/accounts/" + sub).get("canAllocate").asBoolean()).isFalse();
        // fremde Regeln: nicht änderbar
        postJson(owner, "/api/allocations/rules/" + r.get("id").asLong() + "/pause", "", 404);

        // Rechte entzogen → nächste Ausführung scheitert, Regel pausiert
        putJson(owner, "/api/subaccounts/" + sub + "/members/" + member.id(), "{\"role\":\"VIEW\"}", 200);
        advance("days", 7);
        member = relogin(member);
        JsonNode rules = getJson(member, "/api/allocations/rules");
        assertThat(rules.get(0).get("status").asText()).isEqualTo("PAUSED");
        assertThat(rules.get(0).get("statusReason").asText()).isEqualTo("Keine Berechtigung mehr für „WG“.");
        assertThat(accountBalance(member, member.accountId())).isEqualTo(97_000);
        postJson(member, "/api/allocations/rules/" + r.get("id").asLong() + "/resume", "", 403);
    }

    @Test
    void T75_eingabenUndZugriffPruefen() throws Exception {
        TestUser u = registerUser("Hanna");
        TestUser other = registerUser("Ivo");
        long sub = digital(u, "Ziel", null);
        long foreign = digital(other, "Fremd", null);
        LocalDate today = today(u);
        assertThat(rule(u, u.accountId(), 100, "MONTHLY", null, 400).get("code").asText()).isEqualTo("NOT_DIGITAL");
        rule(u, foreign, 100, "MONTHLY", null, 403);
        rule(u, sub, 100, "DAILY", null, 400);
        rule(u, sub, 0, "MONTHLY", null, 400);
        rule(u, sub, 500_001, "MONTHLY", null, 400);
        rule(u, sub, 100, "MONTHLY", "\"startDate\":\"" + today.minusDays(1) + "\"", 400);
        rule(u, sub, 100, "MONTHLY", "\"startDate\":\"" + today.plusYears(1).plusDays(1) + "\"", 400);
        rule(u, sub, 100, "MONTHLY", "\"startDate\":\"" + today.plusDays(5) + "\",\"endDate\":\"" + today.plusDays(4) + "\"", 400);
        rule(u, sub, 100, "MONTHLY", "\"sourceType\":\"BITCOIN\"", 400);
        rule(u, sub, 100, "MONTHLY", "\"sourceType\":\"EXTERNAL\",\"sourceId\":999999", 404);
        rule(u, sub, 100, "MONTHLY", "\"sourceId\":" + other.accountId(), 404);
        // Höchstzahl offener Regeln
        for (int i = 0; i < 20; i++) {
            rule(u, sub, 100, "MONTHLY", "\"startDate\":\"" + today.plusDays(10) + "\"", 201);
        }
        assertThat(rule(u, sub, 100, "MONTHLY", null, 400).get("code").asText()).isEqualTo("TOO_MANY_RULES");
        // ohne Anmeldung
        mvc.perform(get("/api/allocations/sources")).andExpect(status().isUnauthorized());
        getJsonStatus(u, "/api/allocations/accounts/" + foreign, 403);
    }

    @Test
    void T76_zielErreichtMitVormerkungUndAufloesenBeendetRegeln() throws Exception {
        TestUser u = registerUser("Jana");
        long sub = digital(u, "Konzert", 30_000L);
        connectSandbox(u, "SANDBOX_ONLINEBANK");
        long ext = externalSource(u).get("id").asLong();
        jdbc.update("UPDATE external_account SET balance_cents = 50000 WHERE id = ?", ext);
        rule(u, sub, 20_000, "MONTHLY", null, 201);   // Umbuchung
        assertThat(notificationTexts(u)).noneMatch(t -> t.startsWith("Ziel erreicht |"));
        postJson(u, "/api/allocations/earmarks", "{\"targetAccountId\":" + sub + ",\"externalAccountId\":" + ext
                + ",\"amountCents\":10000}", 201);   // 200 € + 100 € vorgemerkt = Ziel
        assertThat(notificationTexts(u).stream().filter(t -> t.startsWith("Ziel erreicht |")).count()).isEqualTo(1);
        JsonNode d = digitalInfo(u, sub);
        assertThat(d.get("goalReached").asBoolean()).isTrue();
        assertThat(getJson(u, "/api/allocations/accounts/" + sub).get("projectionText").asText()).isEqualTo("Ziel erreicht.");
        // Vormerkung aufheben → wieder unter dem Ziel
        postJson(u, "/api/allocations/earmarks/release", "{\"targetAccountId\":" + sub + ",\"externalAccountId\":" + ext
                + "}", 200);
        assertThat(digitalInfo(u, sub).get("goalReached").asBoolean()).isFalse();

        deletePath(u, "/api/subaccounts/" + sub, 204);
        assertThat(getJson(u, "/api/allocations/rules")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM allocation_rule WHERE target_account_id = ? AND status <> 'ENDED'",
                Integer.class, sub)).isZero();
    }

    @Test
    void T77_englischeTexte() throws Exception {
        TestUser u = registerUser("Karl");
        long sub = digital(u, "Urlaub", 50_000L);
        rule(u, sub, 5_000, "BIWEEKLY", null, 201);
        String en = mvc.perform(get("/api/allocations/accounts/" + sub).header("Authorization", u.bearer())
                        .header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(en).contains("\"intervalLabel\":\"every 2 weeks\"").contains("\"statusLabel\":\"Active\"")
                .contains("not a real bank transfer").contains("Expected to be reached on")
                .contains("\"targetName\":\"Urlaub\"");
        String err = mvc.perform(post("/api/allocations/rules").header("Authorization", u.bearer())
                        .header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountId\":" + u.accountId() + ",\"amountCents\":100}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(err).contains("Savings rules are only available for digital accounts.");
    }

    private void getJsonStatus(TestUser u, String path, int expected) throws Exception {
        mvc.perform(get(path).header("Authorization", u.bearer())).andExpect(status().is(expected));
    }
}
