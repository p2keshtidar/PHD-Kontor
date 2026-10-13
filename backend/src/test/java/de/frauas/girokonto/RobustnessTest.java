package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.booking.BookingLineRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Robustheit (R01–R08): kaputte Eingaben führen nie zu Serverfehlern, Grenzwerte stimmen,
 * gleichzeitige Überweisungen überziehen kein Konto, und die doppelte Buchführung bleibt nach
 * vielen zufälligen Aktionen im Gleichgewicht.
 */
class RobustnessTest extends FlowTestSupport {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Autowired BookingLineRepository bookingLines;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------- R01 Fuzzing aller Endpunkte

    private static final List<String> BAD_BODIES = List.of(
            "", "{", "}", "[]", "null", "\"text\"", "12345", "{\"x\":1}",
            "{\"amountCents\":\"abc\"}", "{\"amountCents\":-1}", "{\"amountCents\":9223372036854775807}",
            "{\"amountCents\":1e400}", "{\"name\":\"" + "A".repeat(10_000) + "\"}",
            "{\"recipient\":null,\"code\":null,\"password\":null}", "{\"executionDay\":0}", "{\"executionDay\":99}",
            "{\"type\":\"GIBTSNICHT\",\"riskLevel\":\"X\",\"purpose\":\"Y\",\"bank\":\"Z\",\"category\":\"Q\"}",
            "{\"fileName\":\"\\u0000\",\"content\":\"\\u0000\\u0001\"}",
            "{\"name\":\"😀🏦\",\"reference\":\"<script>alert(1)</script>\"}");

    @Test
    void R01_kaputteEingabenNieServerfehler() throws Exception {
        TestUser u = userWithDemo("Fuzzer");
        Set<String> serverErrors = new TreeSet<>();
        int requests = 0;
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues() : info.getPatternValues();
            for (String p : patterns) {
                if (!p.startsWith("/api/") || p.startsWith("/api/admin/")) {
                    continue;
                }
                for (var m : info.getMethodsCondition().getMethods()) {
                    HttpMethod method = HttpMethod.valueOf(m.name());
                    if (method == HttpMethod.DELETE || p.endsWith("/logout")) {
                        continue;   // würde eigene Daten bzw. die Anmeldung löschen
                    }
                    for (String pathValue : List.of("1", "999999999", "abc", "-1")) {
                        String path = p.replaceAll("\\{[^}]+}", pathValue);
                        List<String> bodies = method == HttpMethod.GET ? List.of("") : BAD_BODIES;
                        for (String body : bodies) {
                            int s = mvc.perform(request(method, path).header("Authorization", u.bearer())
                                            .header("Idempotency-Key", UUID.randomUUID().toString())
                                            .contentType(MediaType.APPLICATION_JSON).content(body))
                                    .andReturn().getResponse().getStatus();
                            requests++;
                            if (s >= 500) {
                                serverErrors.add(method + " " + path + " body=" + abbreviate(body) + " → " + s);
                            }
                        }
                        if (!p.contains("{")) {
                            break;   // ohne Pfadvariable reicht ein Durchlauf
                        }
                    }
                }
            }
        }
        assertThat(requests).isGreaterThan(500);
        assertThat(serverErrors).as("Serverfehler bei %d Anfragen", requests).isEmpty();
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void R02_kaputteQueryParameter() throws Exception {
        TestUser u = registerUser("Parameter");
        String base = "/api/accounts/" + u.accountId() + "/bookings";
        for (String query : List.of("?direction=SEITWAERTS", "?from=gestern", "?to=2026-13-45", "?minCents=abc",
                "?page=-1", "?size=100000", "?type=UNBEKANNT", "?q=" + "x".repeat(5000), "?size=0&page=999999")) {
            int s = mvc.perform(get(base + query).header("Authorization", u.bearer())).andReturn().getResponse().getStatus();
            assertThat(s).as(query).isLessThan(500);
        }
        for (String path : List.of("/api/insights/months?count=-5", "/api/insights/months?count=abc",
                "/api/insights/months?count=100000", "/api/notifications?size=-1&page=-1")) {
            int s = mvc.perform(get(path).header("Authorization", u.bearer())).andReturn().getResponse().getStatus();
            assertThat(s).as(path).isLessThan(500);
        }
    }

    // ---------------------------------------------------------------- R03/R04 Nebenläufigkeit

    private int transfer(TestUser from, TestUser to, long cents, String key) throws Exception {
        return mvc.perform(post("/api/transfers").header("Authorization", from.bearer())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"" + to.email() + "\",\"amountCents\":" + cents + "}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void R03_gleichzeitigeUeberweisungenUeberziehenNie() throws Exception {
        TestUser a = registerUser("Schnell");          // 1.000 €
        TestUser b = registerUser("Ziel");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Callable<Integer> c = () -> transfer(a, b, 10_000, UUID.randomUUID().toString());
            results.add(pool.submit(c));
        }
        int ok = 0;
        for (Future<Integer> f : results) {
            int s = f.get();
            assertThat(s).isIn(201, 200, 400);
            if (s < 300) {
                ok++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(10);                     // genau 10 × 100 € passen in 1.000 €
        assertThat(balance(a)).isZero();
        assertThat(balance(b)).isEqualTo(200_000);
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void R04_gleicherIdempotenzSchluesselNurEinmalGebucht() throws Exception {
        TestUser a = registerUser("Doppelt");
        TestUser b = registerUser("Einmal");
        String key = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> transfer(a, b, 12_345, key)));
        }
        for (Future<Integer> f : results) {
            assertThat(f.get()).isLessThan(300);
        }
        pool.shutdown();
        assertThat(balance(a)).isEqualTo(100_000 - 12_345);
        assertThat(balance(b)).isEqualTo(100_000 + 12_345);
    }

    // ---------------------------------------------------------------- R05 Grenzwerte

    @Test
    void R05_grenzwerte() throws Exception {
        TestUser rich = userWithDemo("Grenze");
        postJson(rich, "/api/loans", """
                {"bank":"NORDLICHT","purpose":"AUTO","principalCents":1000000,"equityCents":0,"termMonths":48}
                """, 201);                                      // genug Guthaben für das Tageslimit
        TestUser other = registerUser("Empfang");
        // Tageslimit 5.000 €: genau 5.000 € geht, danach 1 Cent nicht mehr
        assertThat(transfer(rich, other, 500_000, UUID.randomUUID().toString())).isLessThan(300);
        assertThat(transfer(rich, other, 1, UUID.randomUUID().toString())).isEqualTo(400);
        // Betrag 0 und negativ
        assertThat(transfer(other, rich, 0, UUID.randomUUID().toString())).isEqualTo(400);
        assertThat(transfer(other, rich, -100, UUID.randomUUID().toString())).isEqualTo(400);
        // Unterkonten: höchstens 10
        TestUser s = registerUser("Sammler");
        for (int i = 1; i <= 10; i++) {
            postJson(s, "/api/subaccounts", "{\"name\":\"Topf " + i + "\"}", 201);
        }
        postJson(s, "/api/subaccounts", "{\"name\":\"Topf 11\"}", 400);
        // Ausführungstag 28 ja, 29 nein
        postJson(s, "/api/plans", """
                {"type":"INVEST","name":"T28","monthlyRateCents":100,"executionDay":28,"riskLevel":"SICHER"}
                """, 201);
        postJson(s, "/api/plans", """
                {"type":"INVEST","name":"T29","monthlyRateCents":100,"executionDay":29,"riskLevel":"SICHER"}
                """, 400);
    }

    @Test
    void R06_registrierungGrenzwerte() throws Exception {
        LocalDate today = today(registerUser("Uhr"));   // Simulationsdatum (Alter wird damit geprüft)
        assertThat(register("genau18", today.minusYears(18))).isEqualTo(201);
        assertThat(register("fast18", today.minusYears(18).plusDays(1))).isEqualTo(400);
        assertThat(register("zukunft", today.plusDays(1))).isEqualTo(400);
        assertThat(register("kurzpw", today.minusYears(30), "kurz")).isEqualTo(400);
        assertThat(register("keinemail", today.minusYears(30), "geheim12345", "keine-email")).isEqualTo(400);
    }

    private int register(String name, LocalDate birth) throws Exception {
        return register(name, birth, "geheim12345");
    }

    private int register(String name, LocalDate birth, String password) throws Exception {
        return register(name, birth, password, uniqueEmail());
    }

    private int register(String name, LocalDate birth, String password, String email) throws Exception {
        String body = registerBody(email, uniquePhone(), birth).replace("\"Max\"", "\"" + name + "\"")
                .replace("geheim12345", password);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    // ---------------------------------------------------------------- R07 Zufallsaktionen + Invarianten

    @Test
    void R07_buchhaltungBleibtNachVielenZufallsaktionenImGleichgewicht() throws Exception {
        Random rnd = new Random(42);          // fester Startwert: jeder Lauf gleich, Fehler reproduzierbar
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            users.add(i % 2 == 0 ? userWithDemo("Zufall" + i) : registerUser("Zufall" + i));
        }
        List<Long> subs = new ArrayList<>();
        for (TestUser u : users) {
            subs.add(postJson(u, "/api/subaccounts", "{\"name\":\"Topf\"}", 201).get("id").asLong());
        }
        int actions = 0;
        for (int round = 0; round < 4; round++) {
            for (int i = 0; i < 40; i++) {
                int who = rnd.nextInt(users.size());
                TestUser u = users.get(who);
                switch (rnd.nextInt(6)) {
                    case 0 -> transfer(u, users.get((who + 1 + rnd.nextInt(users.size() - 1)) % users.size()),
                            1 + rnd.nextInt(30_000), UUID.randomUUID().toString());
                    case 1 -> mvc.perform(post("/api/subaccounts/move").header("Authorization", u.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"fromAccountId\":" + u.accountId()
                                    + ",\"toAccountId\":" + subs.get(who) + ",\"amountCents\":" + (1 + rnd.nextInt(20_000)) + "}"));
                    case 2 -> mvc.perform(post("/api/subaccounts/move").header("Authorization", u.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"fromAccountId\":" + subs.get(who)
                                    + ",\"toAccountId\":" + u.accountId() + ",\"amountCents\":" + (1 + rnd.nextInt(20_000)) + "}"));
                    case 3 -> mvc.perform(post("/api/simulator/card-payment").header("Authorization", u.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"merchant\":\"Testladen\",\"amountCents\":" + (1 + rnd.nextInt(15_000)) + "}"));
                    case 4 -> mvc.perform(post("/api/plans").header("Authorization", u.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"type":"INVEST","name":"Zufall","monthlyRateCents":%d,"executionDay":%d,"riskLevel":"MUTIG"}
                                    """.formatted(100 + rnd.nextInt(30_000), 1 + rnd.nextInt(28))));
                    default -> postJson(u, "/api/standing-orders", """
                            {"recipient":"%s","amountCents":%d,"executionDay":%d}
                            """.formatted(users.get((who + 1) % users.size()).email(), 100 + rnd.nextInt(20_000),
                            1 + rnd.nextInt(28)), 201);
                }
                actions++;
            }
            advance("days", 9 + rnd.nextInt(10));   // Tagesläufe: Sparraten, Daueraufträge, Demo-Buchungen
            users.replaceAll(u -> {
                try {
                    return relogin(u);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertInvariants("nach Runde " + round + " (" + actions + " Aktionen)");
        }
    }

    private void assertInvariants(String when) {
        assertThat(bookingLines.totalOfAllLines()).as("Summe aller Buchungszeilen " + when).isZero();
        List<String> negative = jdbc.queryForList("""
                SELECT a.id || ' ' || a.type || ' ' || SUM(l.amount_cents)
                  FROM account a JOIN booking_line l ON l.account_id = a.id
                 WHERE a.type IN ('MAIN', 'SUB', 'SAVINGS')
                 GROUP BY a.id, a.type HAVING SUM(l.amount_cents) < 0
                """, String.class);
        assertThat(negative).as("Konten im Minus " + when).isEmpty();
        Integer unbalanced = jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT booking_id FROM booking_line GROUP BY booking_id HAVING SUM(amount_cents) <> 0) x",
                Integer.class);
        assertThat(unbalanced).as("Buchungen, deren Zeilen nicht 0 ergeben " + when).isZero();
        Integer single = jdbc.queryForObject(
                "SELECT count(*) FROM (SELECT booking_id FROM booking_line GROUP BY booking_id HAVING count(*) < 2) x",
                Integer.class);
        assertThat(single).as("Buchungen mit nur einer Zeile " + when).isZero();
    }

    @Test
    void R08_sehrLangeUndBesondereTexte() throws Exception {
        TestUser u = registerUser("Text");
        TestUser v = registerUser("Leser");
        // Verwendungszweck mit Sonderzeichen kommt unverändert an
        String zweck = "Miete Ä Ö Ü ß € 😀 <b>fett</b> \\\"Zitat\\\"";
        mvc.perform(post("/api/transfers").header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"" + v.email() + "\",\"amountCents\":100,\"description\":\"" + zweck + "\"}"))
                .andExpect(status().isCreated());
        JsonNode items = getJson(v, "/api/accounts/" + v.accountId() + "/bookings").get("items");
        assertThat(items.toString()).contains("Ä Ö Ü ß € 😀").contains("<b>fett</b>");
        // 141 Zeichen sind zu lang
        mvc.perform(post("/api/transfers").header("Authorization", u.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"" + v.email() + "\",\"amountCents\":100,\"description\":\"" + "x".repeat(141) + "\"}"))
                .andExpect(status().isBadRequest());
        String body = mvc.perform(get("/api/me").header("Authorization", u.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("Text");
    }

    private static String abbreviate(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
