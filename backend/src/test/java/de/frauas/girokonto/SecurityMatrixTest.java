package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Sicherheitstests (S01–S10): Jeder Endpunkt braucht eine gültige Anmeldung, niemand kommt an fremde
 * Daten, Tokens lassen sich nicht fälschen oder nach dem Abmelden weiterverwenden.
 */
class SecurityMatrixTest extends FlowTestSupport {

    // Rücksprung von der Bank: feste Seite ohne Daten, ändert nichts (siehe T60)
    private static final Set<String> PUBLIC = Set.of("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
            "/api/banking/callback");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Autowired AppProperties props;

    private record Endpoint(HttpMethod method, String pattern) {
        String path() {
            return pattern.replaceAll("\\{[^}]+}", "1");
        }

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }

    private List<Endpoint> apiEndpoints() {
        List<Endpoint> list = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues() : info.getPatternValues();
            for (String p : patterns) {
                if (!p.startsWith("/api/")) {
                    continue;
                }
                for (var m : info.getMethodsCondition().getMethods()) {
                    list.add(new Endpoint(HttpMethod.valueOf(m.name()), p));
                }
            }
        }
        return list;
    }

    @Test
    void S01_jederEndpunktBrauchtAnmeldung() throws Exception {
        List<Endpoint> endpoints = apiEndpoints();
        assertThat(endpoints).as("gefundene API-Endpunkte").hasSizeGreaterThan(60);
        Set<String> failures = new TreeSet<>();
        int checked = 0;
        for (Endpoint e : endpoints) {
            if (PUBLIC.contains(e.pattern())) {
                continue;
            }
            int s = mvc.perform(request(e.method(), e.path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            checked++;
            if (s != 401) {
                failures.add(e + " → " + s);
            }
        }
        assertThat(failures).as("Endpunkte, die ohne Anmeldung nicht 401 liefern (%d geprüft)", checked).isEmpty();
    }

    @Test
    void S02_gefaelschteUndAbgelaufeneTokens() throws Exception {
        TestUser u = registerUser("Sicher");
        // Unsinn
        mvc.perform(get("/api/me").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
        // richtiger Inhalt, aber mit fremdem Schlüssel signiert (z. B. Rolle ADMIN untergeschoben)
        String forged = Jwts.builder().subject(String.valueOf(u.id())).claim("email", u.email())
                .claim("role", "ADMIN").claim("ver", 0)
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("ein-ganz-anderer-geheimer-schluessel-1234567890".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/clock/advance").header("Authorization", "Bearer " + forged)
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}")).andExpect(status().isUnauthorized());
        // mit dem richtigen Schlüssel, aber abgelaufen
        String expired = Jwts.builder().subject(String.valueOf(u.id())).claim("email", u.email())
                .claim("role", "USER").claim("ver", 0)
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.jwt().secret())))
                .compact();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        // ohne "Bearer"
        mvc.perform(get("/api/me").header("Authorization", u.token())).andExpect(status().isUnauthorized());
    }

    @Test
    void S03_nachAbmeldenSindTokensUngueltig() throws Exception {
        TestUser u = registerUser("Abmelder");
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + u.email() + "\",\"password\":\"geheim12345\"}"))
                .andReturn().getResponse().getContentAsString();
        String access = json.readTree(body).get("accessToken").asText();
        String refresh = json.readTree(body).get("refreshToken").asText();

        // Refresh-Token nur einmal benutzbar
        String r1 = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());
        String refresh2 = json.readTree(r1).get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access)).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh2 + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void S04_adminFunktionenNurFuerAdmins() throws Exception {
        TestUser u = registerUser("Normal");
        mvc.perform(post("/api/admin/clock/advance").header("Authorization", u.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}")).andExpect(status().isForbidden());
        for (Endpoint e : apiEndpoints()) {
            if (e.pattern().startsWith("/api/admin/")) {
                int s = mvc.perform(request(e.method(), e.path()).header("Authorization", u.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
                assertThat(s).as(e.toString()).isEqualTo(403);
            }
        }
    }

    /** Ergebnis eines Zugriffsversuchs auf fremde Daten: 403 oder 404, niemals Erfolg. */
    private void denied(TestUser intruder, HttpMethod method, String path, String body, Set<String> failures)
            throws Exception {
        int s = mvc.perform(request(method, path).header("Authorization", intruder.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        if (s != 403 && s != 404) {
            failures.add(method + " " + path + " → " + s);
        }
    }

    @Test
    void S05_keinZugriffAufFremdeDaten() throws Exception {
        TestUser anna = userWithDemo("Opfer");
        TestUser bert = registerUser("Kontakt");
        TestUser eve = registerUser("Eve");

        long sub = postJson(anna, "/api/subaccounts", "{\"name\":\"Geheim\"}", 201).get("id").asLong();
        postJson(anna, "/api/subaccounts/move", "{\"fromAccountId\":" + anna.accountId() + ",\"toAccountId\":" + sub
                + ",\"amountCents\":1000}", 204);
        long plan = postJson(anna, "/api/plans", """
                {"type":"INVEST","name":"Depot","monthlyRateCents":5000,"executionDay":10,"riskLevel":"AUSGEWOGEN"}
                """, 201).get("summary").get("id").asLong();
        long loan = postJson(anna, "/api/loans", """
                {"bank":"NORDLICHT","purpose":"AUTO","principalCents":500000,"equityCents":0,"termMonths":24}
                """, 201).get("loan").get("id").asLong();
        long so = postJson(anna, "/api/standing-orders", """
                {"recipient":"%s","amountCents":1000,"executionDay":3}
                """.formatted(bert.email()), 201).get("id").asLong();
        long imp = postJson(anna, "/api/imports", json.writeValueAsString(java.util.Map.of("fileName", "a.csv",
                "content", "Datum;Betrag;Verwendungszweck\n01.09.2026;-10,00;Kino\n")), 201).get("importId").asLong();
        long entry = postJson(anna, "/api/entries", "{\"kind\":\"INCOME\",\"title\":\"Gehalt\",\"amountCents\":1000,"
                + "\"intervalMonths\":1,\"date\":\"" + today(anna).plusMonths(1).withDayOfMonth(1) + "\"}", 201)
                .get("entry").get("id").asLong();
        long contact = postJson(anna, "/api/contacts", "{\"recipient\":\"" + bert.email() + "\"}", 201).get("id").asLong();
        JsonNode bookings = getJson(anna, "/api/accounts/" + anna.accountId() + "/bookings");
        long booking = bookings.get("items").get(0).get("bookingId").asLong();
        long notification = getJson(anna, "/api/notifications").get("items").get(0).get("id").asLong();

        Set<String> f = new TreeSet<>();
        denied(eve, HttpMethod.GET, "/api/accounts/" + anna.accountId() + "/bookings", "", f);
        denied(eve, HttpMethod.GET, "/api/accounts/" + sub + "/bookings", "", f);
        denied(eve, HttpMethod.PUT, "/api/accounts/" + anna.accountId() + "/bookings/" + booking + "/category",
                "{\"category\":\"FREIZEIT\"}", f);
        denied(eve, HttpMethod.PUT, "/api/subaccounts/" + sub, "{\"name\":\"Meins\"}", f);
        denied(eve, HttpMethod.DELETE, "/api/subaccounts/" + sub, "", f);
        denied(eve, HttpMethod.GET, "/api/subaccounts/" + sub + "/members", "", f);
        denied(eve, HttpMethod.POST, "/api/subaccounts/" + sub + "/members", "{\"recipient\":\"" + eve.email() + "\"}", f);
        denied(eve, HttpMethod.POST, "/api/subaccounts/move", "{\"fromAccountId\":" + sub + ",\"toAccountId\":"
                + eve.accountId() + ",\"amountCents\":1000}", f);
        denied(eve, HttpMethod.POST, "/api/subaccounts/move", "{\"fromAccountId\":" + anna.accountId()
                + ",\"toAccountId\":" + eve.accountId() + ",\"amountCents\":1000}", f);
        denied(eve, HttpMethod.GET, "/api/plans/" + plan, "", f);
        denied(eve, HttpMethod.PUT, "/api/plans/" + plan, "{\"monthlyRateCents\":100}", f);
        denied(eve, HttpMethod.POST, "/api/plans/" + plan + "/payout", "{}", f);
        denied(eve, HttpMethod.DELETE, "/api/plans/" + plan, "", f);
        denied(eve, HttpMethod.GET, "/api/loans/" + loan, "", f);
        denied(eve, HttpMethod.POST, "/api/loans/" + loan + "/extra-payment", "{\"amountCents\":100}", f);
        denied(eve, HttpMethod.POST, "/api/loans/" + loan + "/extra-payment/preview", "{\"amountCents\":100}", f);
        denied(eve, HttpMethod.PUT, "/api/standing-orders/" + so, "{\"amountCents\":99999}", f);
        denied(eve, HttpMethod.DELETE, "/api/standing-orders/" + so, "", f);
        denied(eve, HttpMethod.PUT, "/api/entries/" + entry, "{\"amountCents\":99999}", f);
        denied(eve, HttpMethod.DELETE, "/api/entries/" + entry, "", f);
        denied(eve, HttpMethod.GET, "/api/imports/" + imp + "/transactions", "", f);
        denied(eve, HttpMethod.DELETE, "/api/imports/" + imp, "", f);
        denied(eve, HttpMethod.DELETE, "/api/contacts/" + contact, "", f);
        denied(eve, HttpMethod.PUT, "/api/notifications/" + notification + "/read", "", f);
        assertThat(f).as("Zugriffe auf fremde Daten, die nicht abgelehnt wurden").isEmpty();

        // Annas Daten sind unverändert
        assertThat(getJson(anna, "/api/plans/" + plan).get("summary").get("monthlyRateCents").asLong()).isEqualTo(5000);
        assertThat(getJson(anna, "/api/standing-orders").get(0).get("amountCents").asLong()).isEqualTo(1000);
        assertThat(getJson(anna, "/api/imports")).hasSize(1);
        assertThat(getJson(anna, "/api/contacts")).hasSize(1);
        assertThat(getJson(anna, "/api/subaccounts/" + sub + "/members")).hasSize(1);
        // Eve sieht in ihren Listen nichts von Anna
        assertThat(getJson(eve, "/api/accounts")).hasSize(1);
        assertThat(getJson(eve, "/api/plans")).isEmpty();
        assertThat(getJson(eve, "/api/loans")).isEmpty();
        assertThat(getJson(eve, "/api/standing-orders")).isEmpty();
        assertThat(getJson(eve, "/api/imports")).isEmpty();
        assertThat(getJson(eve, "/api/entries").get("entries")).isEmpty();
        assertThat(getJson(anna, "/api/entries").get("entries").get(0).get("amountCents").asLong()).isEqualTo(1000);
    }

    @Test
    void S06_antwortenEnthaltenKeineGeheimnisse() throws Exception {
        TestUser u = registerUser("Diskret");
        String me = mvc.perform(get("/api/me").header("Authorization", u.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).toLowerCase();
        assertThat(me).doesNotContain("password").doesNotContain("hash").doesNotContain("totp_secret")
                .doesNotContain("secret");
        JsonNode card = getJson(u, "/api/cards/me");
        assertThat(card.toString()).doesNotContainPattern("\\d{16}");          // nie die volle Kartennummer
        // Kartendaten nur mit Passwort
        postJson(u, "/api/cards/me/details", "{\"password\":\"falsch\"}", 400);
    }

    @Test
    void S07_sqlInjectionInDerSucheWirkungslos() throws Exception {
        TestUser u = registerUser("Neugierig");
        registerUser("Anderer");
        for (String q : List.of("' OR '1'='1", "'; DROP TABLE booking; --", "%' OR 1=1 --", "\\\"", "%_%")) {
            JsonNode r = json.readTree(mvc.perform(get("/api/accounts/" + u.accountId() + "/bookings")
                            .param("q", q).header("Authorization", u.bearer()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            for (JsonNode item : r.get("items")) {
                assertThat(item.toString()).doesNotContain("Anderer");
            }
        }
        // Tabelle existiert noch
        assertThat(getJson(u, "/api/accounts/" + u.accountId() + "/bookings").get("items")).isNotEmpty();
    }

    @Test
    void S08_corsNurFuerErlaubteHerkunft() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "https://boese.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void S09_sperreNachFehlversuchenGiltAuchFuerRichtigesPasswort() throws Exception {
        TestUser u = registerUser("Gesperrt");
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + u.email() + "\",\"password\":\"falsch" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.email() + "\",\"password\":\"nochfalsch\"}")).andExpect(status().isLocked());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.email() + "\",\"password\":\"geheim12345\"}")).andExpect(status().isLocked());
        // unbekannte E-Mail: gleiche Meldung wie falsches Passwort (keine Rückschlüsse auf Konten)
        String unknown = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gibtsnicht@example.org\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(unknown).contains("INVALID_CREDENTIALS");
    }

    @Test
    void S10_webAppUndSwaggerOhneAnmeldungErreichbar() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        // Ein unbekannter Pfad außerhalb von /api ist kein Sicherheitsproblem (404 statt 401)
        int s = mvc.perform(get("/irgendwas-statisches.txt")).andReturn().getResponse().getStatus();
        assertThat(s).isIn(404, 200);
    }
}
