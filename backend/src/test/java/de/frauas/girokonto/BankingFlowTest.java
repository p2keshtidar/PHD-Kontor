package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T49–T60: Mehrere Bankkonten verbinden (Sandbox mit Testdaten – keine echte Bank, keine echten Zahlungen).
 */
class BankingFlowTest extends FlowTestSupport {

    @Autowired JdbcTemplate jdbc;

    private long connect(TestUser u, String institution) throws Exception {
        JsonNode c = postJson(u, "/api/banking/connections",
                "{\"provider\":\"SANDBOX\",\"institutionId\":\"" + institution + "\"}", 201);
        assertThat(c.get("sandbox").asBoolean()).isTrue();
        assertThat(c.get("authorizationUrl").isNull()).isTrue();   // keine echte Bankseite
        long id = c.get("connection").get("id").asLong();
        JsonNode done = postJson(u, "/api/banking/connections/" + id + "/complete", "{\"approve\":true}", 200);
        assertThat(done.get("connection").get("status").asText()).isEqualTo("ACTIVE");
        return id;
    }

    private List<JsonNode> transactions(TestUser u, String query) throws Exception {
        List<JsonNode> all = new ArrayList<>();
        for (int page = 0; page < 20; page++) {
            JsonNode p = getJson(u, "/api/banking/transactions?size=100&page=" + page + (query.isEmpty() ? "" : "&" + query));
            p.get("items").forEach(all::add);
            if (!p.get("hasMore").asBoolean()) {
                break;
            }
        }
        return all;
    }

    private int rows(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM external_transaction WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void T49_bankVerbindenErsterImportUndKeineDoppeltenUmsaetze() throws Exception {
        TestUser u = registerUser("Bianca");
        assertThat(getJson(u, "/api/banking/providers").get(0).get("sandbox").asBoolean()).isTrue();
        long id = connect(u, "SANDBOX_MAINBANK");

        JsonNode overview = getJson(u, "/api/banking/accounts");
        assertThat(overview.get("accounts")).hasSize(2);
        assertThat(overview.get("anySandbox").asBoolean()).isTrue();
        long sum = 0;
        for (JsonNode a : overview.get("accounts")) {
            assertThat(a.get("ibanMasked").asText()).matches("DE\\d{2} •••• \\d{4}");
            assertThat(a.get("lastSyncStatus").asText()).isEqualTo("OK");
            sum += a.get("balanceCents").asLong();
        }
        assertThat(overview.get("totalBalanceCents").asLong()).isEqualTo(sum);

        int first = rows(u.id());
        assertThat(first).isGreaterThan(80);
        // erneuter Abruf: nichts Neues, nichts doppelt
        JsonNode again = postJson(u, "/api/banking/connections/" + id + "/sync", "", 200);
        assertThat(again.get("status").asText()).isEqualTo("OK");
        assertThat(again.get("newTransactions").asInt()).isZero();
        assertThat(rows(u.id())).isEqualTo(first);
        Integer distinct = jdbc.queryForObject(
                "SELECT count(DISTINCT (external_account_id, external_id)) FROM external_transaction WHERE user_id = ?",
                Integer.class, u.id());
        assertThat(distinct).isEqualTo(first);
        // zweimal "abschließen" geht nicht
        postJson(u, "/api/banking/connections/" + id + "/complete", "{\"approve\":true}", 409);
        // dieselbe Bank noch einmal verbinden: abgelehnt
        postJson(u, "/api/banking/connections", "{\"provider\":\"SANDBOX\",\"institutionId\":\"SANDBOX_MAINBANK\"}", 409);
    }

    @Test
    void T50_vormerkungenWerdenGebuchtOderStorniert() throws Exception {
        TestUser u = registerUser("Paul");
        long id = connect(u, "SANDBOX_MAINBANK");
        List<JsonNode> pending = transactions(u, "status=PENDING");
        assertThat(pending).isNotEmpty();
        assertThat(pending).anyMatch(t -> t.get("counterparty").asText().equals("Hotel am Main"));

        advance("days", 5);
        u = relogin(u);
        JsonNode s = postJson(u, "/api/banking/connections/" + id + "/sync", "", 200);
        assertThat(s.get("status").asText()).isEqualTo("OK");

        Map<Long, JsonNode> after = new HashMap<>();
        for (JsonNode t : transactions(u, "status=PENDING,BOOKED,CANCELLED,SUPERSEDED,REVERSED")) {
            after.put(t.get("id").asLong(), t);
        }
        Set<Long> replacements = new HashSet<>();
        for (JsonNode p : pending) {
            JsonNode now = after.get(p.get("id").asLong());
            if (p.get("counterparty").asText().equals("Hotel am Main")) {
                assertThat(now.get("status").asText()).isEqualTo("CANCELLED");   // Reservierung aufgehoben
            } else {
                assertThat(now.get("status").asText()).isEqualTo("SUPERSEDED");
                JsonNode booked = after.get(now.get("relatedId").asLong());
                assertThat(booked.get("status").asText()).isIn("BOOKED", "REVERSED");
                assertThat(booked.get("amountCents").asLong()).isEqualTo(p.get("amountCents").asLong());
                assertThat(replacements.add(booked.get("id").asLong())).isTrue();   // je Buchung nur eine Vormerkung
            }
        }
        // Standardliste zeigt ersetzte Vormerkungen nicht doppelt
        assertThat(transactions(u, "")).noneMatch(t -> t.get("status").asText().equals("SUPERSEDED"));
        LocalDate today = today(u);
        assertThat(transactions(u, "status=PENDING"))
                .allMatch(t -> !LocalDate.parse(t.get("bookingDate").asText()).isBefore(today.minusDays(2)));
    }

    @Test
    void T51_ruecklastschriftUndEigeneUmbuchungZaehlenNichtImUeberblick() throws Exception {
        TestUser u = registerUser("Rita");
        connect(u, "SANDBOX_MAINBANK");
        // Rücklastschrift: im Abrufzeitraum (90 Tage) liegt mindestens ein 13. eines durch 4 teilbaren Monats?
        LocalDate today = today(u);
        boolean expectReversal = false;
        for (LocalDate d = today.minusDays(87); !d.isAfter(today.minusDays(3)); d = d.plusDays(1)) {
            if (d.getDayOfMonth() == 13 && d.getMonthValue() % 4 == 0) {
                expectReversal = true;
            }
        }
        List<JsonNode> reversed = transactions(u, "status=REVERSED");
        if (expectReversal) {
            assertThat(reversed).isNotEmpty();
            for (JsonNode o : reversed) {
                JsonNode r = getJson(u, "/api/banking/transactions/" + o.get("relatedId").asLong());
                assertThat(r.get("reversal").asBoolean()).isTrue();
                assertThat(r.get("amountCents").asLong()).isEqualTo(-o.get("amountCents").asLong());
                assertThat(r.get("relatedId").asLong()).isEqualTo(o.get("id").asLong());
            }
        }
        // Sparrate an das eigene Tagesgeld: automatisch "Umbuchung"
        List<JsonNode> savings = transactions(u, "q=Sparrate");
        assertThat(savings).isNotEmpty().allMatch(t -> t.get("category").asText().equals("UMBUCHUNG"));

        // Finanzüberblick: Monat = Gehalt + Zinsen, keine Umbuchungen und keine Rückbuchungen
        LocalDate lastMonth = today.minusMonths(1);
        JsonNode month = null;
        for (JsonNode m : getJson(u, "/api/insights/months?count=3")) {
            if (m.get("month").asText().equals(lastMonth.toString().substring(0, 7))) {
                month = m;
            }
        }
        assertThat(month).isNotNull();
        long income = 0;
        long expected = 0;
        for (JsonNode t : transactions(u, "from=" + lastMonth.withDayOfMonth(1) + "&to="
                + lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()) + "&status=BOOKED")) {
            String cat = t.get("category").asText();
            if (t.get("reversal").asBoolean()) {
                continue;   // Rückbuchung: zählt wie das zurückgebuchte Original nicht
            }
            if (cat.equals("GEHALT") || cat.equals("SONSTIGE_EINNAHMEN")) {
                income += t.get("amountCents").asLong();
            } else if (!cat.equals("UMBUCHUNG")) {
                expected -= t.get("amountCents").asLong();
            }
        }
        assertThat(income).isGreaterThanOrEqualTo(265_000);
        assertThat(month.get("incomeCents").asLong()).isEqualTo(income);
        assertThat(month.get("expenseCents").asLong()).isEqualTo(expected);
    }

    @Test
    void T52_eigeneBearbeitungenGetrenntVonOriginaldaten() throws Exception {
        TestUser u = registerUser("Eddi");
        long id = connect(u, "SANDBOX_ONLINEBANK");
        JsonNode t = transactions(u, "q=Streaming&status=BOOKED").get(0);
        long tid = t.get("id").asLong();
        String original = mvc.perform(get("/api/banking/transactions/" + tid + "/original").header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode e = putJson(u, "/api/banking/transactions/" + tid,
                "{\"category\":\"FREIZEIT\",\"note\":\"Familienabo\",\"tags\":[\"Abo\",\" abo \",\"Kinder\"]}", 200);
        assertThat(e.get("category").asText()).isEqualTo("FREIZEIT");
        assertThat(e.get("autoCategory").asText()).isEqualTo(t.get("autoCategory").asText());
        assertThat(e.get("tags")).hasSize(2);
        assertThat(e.get("edited").asBoolean()).isTrue();
        assertThat(e.get("counterparty").asText()).isEqualTo(t.get("counterparty").asText());

        // Abruf überschreibt die eigene Bearbeitung nicht, Originaldaten bleiben gleich
        advance("days", 1);
        u = relogin(u);
        postJson(u, "/api/banking/connections/" + id + "/sync", "", 200);
        JsonNode again = getJson(u, "/api/banking/transactions/" + tid);
        assertThat(again.get("category").asText()).isEqualTo("FREIZEIT");
        assertThat(again.get("note").asText()).isEqualTo("Familienabo");
        String original2 = mvc.perform(get("/api/banking/transactions/" + tid + "/original").header("Authorization", u.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(original2)).isEqualTo(json.readTree(original));
        assertThat(transactions(u, "tag=kinder")).extracting(x -> x.get("id").asLong()).containsExactly(tid);
        assertThat(transactions(u, "q=familienabo")).hasSize(1);

        // zurücksetzen: leere Kategorie = wieder automatisch; ausschließen aus dem Überblick
        JsonNode reset = putJson(u, "/api/banking/transactions/" + tid, "{\"category\":\"\",\"excluded\":true}", 200);
        assertThat(reset.get("category").asText()).isEqualTo(t.get("autoCategory").asText());
        assertThat(reset.get("excluded").asBoolean()).isTrue();
        putJson(u, "/api/banking/transactions/" + tid, "{\"category\":\"STARTGUTHABEN\"}", 400);
        putJson(u, "/api/banking/transactions/" + tid,
                "{\"tags\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\"]}", 400);
    }

    @Test
    void T53_fremdeVerbindungenUndUmsaetzeSindNichtSichtbar() throws Exception {
        TestUser owner = registerUser("Olga");
        long id = connect(owner, "SANDBOX_ONLINEBANK");
        long tid = transactions(owner, "").get(0).get("id").asLong();
        TestUser other = registerUser("Mallory");
        getJsonStatus(other, "/api/banking/connections/" + id, 404);
        getJsonStatus(other, "/api/banking/transactions/" + tid, 404);
        getJsonStatus(other, "/api/banking/transactions/" + tid + "/original", 404);
        putJson(other, "/api/banking/transactions/" + tid, "{\"note\":\"x\"}", 404);
        postJson(other, "/api/banking/connections/" + id + "/sync", "", 404);
        postJson(other, "/api/banking/connections/" + id + "/complete", "{\"approve\":true}", 404);
        deletePath(other, "/api/banking/connections/" + id, 404);
        assertThat(getJson(other, "/api/banking/connections")).isEmpty();
        assertThat(getJson(other, "/api/banking/transactions").get("items")).isEmpty();
        assertThat(getJson(other, "/api/banking/accounts").get("accounts")).isEmpty();
        assertThat(getJson(owner, "/api/banking/connections/" + id).get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void T54_wiederholungBeiStoerungUndFehlerstatus() throws Exception {
        TestUser u = registerUser("Fritz");
        connect(u, "SANDBOX_FLAKY");   // scheitert zweimal, dritter Versuch klappt
        assertThat(getJson(u, "/api/banking/accounts").get("accounts").get(0).get("lastSyncStatus").asText()).isEqualTo("OK");
        assertThat(rows(u.id())).isGreaterThan(0);

        JsonNode c = postJson(u, "/api/banking/connections",
                "{\"provider\":\"SANDBOX\",\"institutionId\":\"SANDBOX_DOWN\"}", 201);
        long id = c.get("connection").get("id").asLong();
        JsonNode done = postJson(u, "/api/banking/connections/" + id + "/complete", "{\"approve\":true}", 200);
        assertThat(done.get("sync").get("status").asText()).isEqualTo("FAILED");
        JsonNode conn = done.get("connection");
        assertThat(conn.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(conn.get("lastSyncStatus").asText()).isEqualTo("FAILED");
        assertThat(conn.get("lastErrorMessage").asText()).contains("nicht erreichbar");
        assertThat(getJson(u, "/api/banking/accounts").get("anyStale").asBoolean()).isTrue();
        // erneuter Abruf: weiterhin Fehler, Zähler steigt
        postJson(u, "/api/banking/connections/" + id + "/sync", "", 200);
        assertThat(jdbc.queryForObject("SELECT failed_syncs FROM bank_connection WHERE id = ?", Integer.class, id))
                .isEqualTo(2);
    }

    @Test
    void T55_freigabeLaeuftAbUndWirdErneuertOhneDoppelteUmsaetze() throws Exception {
        TestUser u = registerUser("Kurt");
        long id = connect(u, "SANDBOX_SHORT");
        int before = rows(u.id());
        assertThat(getJson(u, "/api/banking/connections/" + id).get("daysLeft").asInt()).isLessThanOrEqualTo(1);

        advance("days", 2);
        u = relogin(u);
        assertThat(getJson(u, "/api/banking/connections/" + id).get("status").asText()).isEqualTo("EXPIRED");
        postJson(u, "/api/banking/connections/" + id + "/sync", "", 409);
        assertThat(notificationTexts(u)).anyMatch(t -> t.startsWith("Bankfreigabe abgelaufen"));
        assertThat(getJson(u, "/api/banking/connections/" + id).get("permissions")).isEmpty();

        JsonNode renew = postJson(u, "/api/banking/connections/" + id + "/renew", "", 200);
        assertThat(renew.get("connection").get("status").asText()).isEqualTo("PENDING");
        JsonNode done = postJson(u, "/api/banking/connections/" + id + "/complete", "{\"approve\":true}", 200);
        assertThat(done.get("connection").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(done.get("connection").get("accounts")).hasSize(1);   // dasselbe Konto, kein zweites
        Integer distinct = jdbc.queryForObject(
                "SELECT count(DISTINCT (external_account_id, external_id)) FROM external_transaction WHERE user_id = ?",
                Integer.class, u.id());
        assertThat(distinct).isEqualTo(rows(u.id())).isGreaterThan(before);
    }

    @Test
    void T56_widerrufenMitUndOhneLoeschenDerDaten() throws Exception {
        TestUser u = registerUser("Wanda");
        long keep = connect(u, "SANDBOX_ONLINEBANK");
        long drop = connect(u, "SANDBOX_MAINBANK");

        JsonNode r1 = deletePathJson(u, "/api/banking/connections/" + keep + "?deleteData=false");
        assertThat(r1.get("dataDeleted").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT consent_ref_enc FROM bank_connection WHERE id = ?", String.class, keep)).isNull();
        assertThat(getJson(u, "/api/banking/connections/" + keep).get("status").asText()).isEqualTo("REVOKED");
        postJson(u, "/api/banking/connections/" + keep + "/sync", "", 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM external_transaction t JOIN external_account a"
                + " ON a.id = t.external_account_id WHERE a.connection_id = ?", Integer.class, keep)).isGreaterThan(0);

        JsonNode r2 = deletePathJson(u, "/api/banking/connections/" + drop + "?deleteData=true");
        assertThat(r2.get("dataDeleted").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_connection WHERE id = ?", Integer.class, drop)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM external_account WHERE connection_id = ?", Integer.class, drop))
                .isZero();
        getJsonStatus(u, "/api/banking/connections/" + drop, 404);

        List<String> actions = new ArrayList<>();
        getJson(u, "/api/me/audit-log").get("items").forEach(a -> actions.add(a.get("action").asText()));
        assertThat(actions).contains("BANK_CONSENT_STARTED", "BANK_CONNECTED", "BANK_REVOKED");
    }

    @Test
    void T57_keineZugangsdatenOderBankdatenImKlartext() throws Exception {
        TestUser u = registerUser("Sven");
        long id = connect(u, "SANDBOX_MAINBANK");
        String ref = jdbc.queryForObject("SELECT consent_ref_enc FROM bank_connection WHERE id = ?", String.class, id);
        assertThat(ref).doesNotContain("sbx_").doesNotContain("SANDBOX");
        for (String raw : jdbc.queryForList("SELECT raw_enc FROM external_transaction WHERE user_id = ? LIMIT 20",
                String.class, u.id())) {
            assertThat(raw).doesNotContain("transactionAmount").doesNotContain("{");
        }
        assertThat(jdbc.queryForList("SELECT iban_masked FROM external_account WHERE user_id = ?", String.class, u.id()))
                .allMatch(s -> s.contains("••••"));
        String body = mvc.perform(get("/api/banking/connections").header("Authorization", u.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("sbx_").doesNotContain("consentRef").doesNotContain("00099001");   // volle IBAN
        String audit = mvc.perform(get("/api/me/audit-log").header("Authorization", u.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(audit).doesNotContain("sbx_");
    }

    @Test
    void T58_eingabenPruefenUndEnglischeTexte() throws Exception {
        TestUser u = registerUser("Nina");
        postJson(u, "/api/banking/connections", "{\"provider\":\"SANDBOX\",\"institutionId\":\"GIBT_ES_NICHT\"}", 400);
        postJson(u, "/api/banking/connections", "{\"provider\":\"GOCARDLESS\",\"institutionId\":\"X\"}", 404);
        postJson(u, "/api/banking/connections", "{\"provider\":\"\",\"institutionId\":\"\"}", 400);
        getJsonStatus(u, "/api/banking/transactions?status=ERFUNDEN", 400);
        getJsonStatus(u, "/api/banking/institutions?provider=GOCARDLESS", 404);   // ohne Zugangsdaten nicht angeboten

        JsonNode c = postJson(u, "/api/banking/connections",
                "{\"provider\":\"SANDBOX\",\"institutionId\":\"SANDBOX_ONLINEBANK\"}", 201);
        long id = c.get("connection").get("id").asLong();
        postJson(u, "/api/banking/connections/" + id + "/complete", "{}", 409);   // noch nicht freigegeben
        JsonNode rejected = postJson(u, "/api/banking/connections/" + id + "/complete", "{\"approve\":false}", 200);
        assertThat(rejected.get("connection").get("status").asText()).isEqualTo("REJECTED");

        connect(u, "SANDBOX_MAINBANK");
        String en = mvc.perform(get("/api/banking/connections").header("Authorization", u.bearer())
                        .header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(en).contains("Read transactions").contains("Connected").contains("Sandbox main bank (test data)")
                .doesNotContain("Umsätze lesen");
        String enTx = mvc.perform(get("/api/banking/transactions?size=5").header("Authorization", u.bearer())
                        .header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(enTx).containsAnyOf("\"Pending\"", "\"Booked\"").doesNotContain("Vorgemerkt");
    }

    @Test
    void T59_ablaufErinnerungEinmalUndHintergrundAbruf() throws Exception {
        TestUser u = registerUser("Erin");
        long id = connect(u, "SANDBOX_MAINBANK");
        // Freigabe künstlich auf "in 5 Tagen" setzen
        jdbc.update("UPDATE bank_connection SET consent_expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(today(u).plusDays(5).atStartOfDay(java.time.ZoneId.of("Europe/Berlin"))
                        .toInstant()), id);
        advance("days", 1);
        advance("days", 1);
        u = relogin(u);
        long reminders = notificationTexts(u).stream().filter(t -> t.startsWith("Bankfreigabe läuft bald ab")).count();
        assertThat(reminders).isEqualTo(1);
    }

    private void getJsonStatus(TestUser u, String path, int expected) throws Exception {
        mvc.perform(get(path).header("Authorization", u.bearer())).andExpect(status().is(expected));
    }

    private JsonNode deletePathJson(TestUser u, String path) throws Exception {
        String r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path)
                        .header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(r);
    }
}
