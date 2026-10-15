package de.frauas.girokonto.banking.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.frauas.girokonto.banking.BankingProperties;
import de.frauas.girokonto.banking.provider.BankingProvider.ConsentStatus;
import de.frauas.girokonto.banking.provider.BankingProvider.TransactionData;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * B10–B17: GoCardless-Anbieter gegen einen nachgebildeten Server (keine echten Aufrufe, keine echten Daten).
 * Die Antworten folgen dem Format der Bank-Account-Data-Schnittstelle (v2).
 */
class GoCardlessProviderTest {

    private static final String BASE = "https://gc.test/api/v2";
    private static final String TOKEN = "{\"access\":\"tok-1\",\"access_expires\":86400,\"refresh\":\"r\",\"refresh_expires\":2592000}";

    private MockRestServiceServer server;
    private GoCardlessProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BankingProperties props = new BankingProperties(true, false, 360, 3, 0, 90, "https://app.test/cb",
                new BankingProperties.GoCardless(BASE, "secret-id-123", "secret-key-456"));
        provider = new GoCardlessProvider(builder, props, new ObjectMapper(), true);
    }

    private void expectToken() {
        server.expect(once(), requestTo(BASE + "/token/new/")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.secret_id").value("secret-id-123"))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
    }

    @Test
    void B10_nurMitZugangsdatenVerfuegbar() {
        assertThat(provider.available()).isTrue();
        assertThat(provider.sandbox()).isFalse();
        GoCardlessProvider without = new GoCardlessProvider(RestClient.builder(),
                new BankingProperties(true, false, 360, 3, 0, 90, null, new BankingProperties.GoCardless(null, "", "")),
                new ObjectMapper(), true);
        assertThat(without.available()).isFalse();
        assertThatThrownBy(() -> without.institutions("DE")).isInstanceOf(ProviderException.class)
                .hasMessage("Anbieter nicht eingerichtet");
    }

    @Test
    void B11_bankenlisteMitTokenImKopf() {
        expectToken();
        server.expect(requestTo(BASE + "/institutions/?country=DE")).andExpect(header("Authorization", "Bearer tok-1"))
                .andRespond(withSuccess("""
                        [{"id":"SANDBOXFINANCE_SFIN0000","name":"Sandbox Finance","bic":"SFIN0000",
                          "transaction_total_days":"730","max_access_valid_for_days":"180","countries":["DE"],
                          "logo":"https://cdn.test/logo.png"}]
                        """, MediaType.APPLICATION_JSON));
        var list = provider.institutions("de");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).id()).isEqualTo("SANDBOXFINANCE_SFIN0000");
        assertThat(list.get(0).historyDays()).isEqualTo(730);
        assertThat(list.get(0).accessDays()).isEqualTo(180);
        server.verify();
    }

    @Test
    void B12_freigabeAnlegenNurLesenderZugriff() {
        expectToken();
        server.expect(requestTo(BASE + "/agreements/enduser/")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.institution_id").value("BANK_X"))
                .andExpect(jsonPath("$.max_historical_days").value(90))
                .andExpect(jsonPath("$.access_valid_for_days").value(90))
                .andExpect(jsonPath("$.access_scope.length()").value(3))
                .andRespond(withSuccess("{\"id\":\"eua-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/requisitions/")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.agreement").value("eua-1"))
                .andExpect(jsonPath("$.redirect").value("https://app.test/cb"))
                .andExpect(jsonPath("$.reference").value("gk-7-abc"))
                .andExpect(jsonPath("$.user_language").value("EN"))
                .andRespond(withSuccess("{\"id\":\"req-1\",\"link\":\"https://bank.test/auth/req-1\",\"status\":\"CR\"}",
                        MediaType.APPLICATION_JSON));
        var start = provider.startConsent("BANK_X", "https://app.test/cb", "gk-7-abc", "en");
        assertThat(start.consentRef()).isEqualTo("req-1");
        assertThat(start.authorizationUrl()).isEqualTo("https://bank.test/auth/req-1");
        server.verify();
    }

    @Test
    void B13_statusDerFreigabeUndAblaufAusVereinbarung() {
        expectToken();
        server.expect(requestTo(BASE + "/requisitions/req-1/")).andRespond(withSuccess(
                "{\"id\":\"req-1\",\"status\":\"LN\",\"agreement\":\"eua-1\",\"accounts\":[\"acc-a\",\"acc-b\"]}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/agreements/enduser/eua-1/")).andRespond(withSuccess(
                "{\"id\":\"eua-1\",\"accepted\":\"2026-06-01T10:00:00Z\",\"access_valid_for_days\":90}",
                MediaType.APPLICATION_JSON));
        var state = provider.consentState("req-1");
        assertThat(state.status()).isEqualTo(ConsentStatus.LINKED);
        assertThat(state.accountIds()).containsExactly("acc-a", "acc-b");
        assertThat(state.expiresAt()).isEqualTo(java.time.Instant.parse("2026-08-30T10:00:00Z"));

        server.expect(requestTo(BASE + "/requisitions/req-2/")).andRespond(withSuccess(
                "{\"id\":\"req-2\",\"status\":\"RJ\",\"accounts\":[]}", MediaType.APPLICATION_JSON));
        assertThat(provider.consentState("req-2").status()).isEqualTo(ConsentStatus.REJECTED);
        server.expect(requestTo(BASE + "/requisitions/req-3/")).andRespond(withSuccess(
                "{\"id\":\"req-3\",\"status\":\"GA\",\"accounts\":[]}", MediaType.APPLICATION_JSON));
        assertThat(provider.consentState("req-3").status()).isEqualTo(ConsentStatus.PENDING);
        server.verify();
    }

    @Test
    void B14_kontoMitVerfuegbaremSaldo() {
        expectToken();
        server.expect(requestTo(BASE + "/accounts/acc-a/details/")).andRespond(withSuccess(
                "{\"account\":{\"iban\":\"DE89370400440532013000\",\"currency\":\"EUR\",\"name\":\"Main Account\"}}",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/accounts/acc-a/balances/")).andRespond(withSuccess("""
                {"balances":[{"balanceAmount":{"amount":"1500.10","currency":"EUR"},"balanceType":"closingBooked"},
                             {"balanceAmount":{"amount":"1234.56","currency":"EUR"},"balanceType":"interimAvailable"}]}
                """, MediaType.APPLICATION_JSON));
        var a = provider.account("req-1", "acc-a");
        assertThat(a.balanceCents()).isEqualTo(123_456);
        assertThat(a.balanceType()).isEqualTo("interimAvailable");
        assertThat(a.name()).isEqualTo("Main Account");
        assertThat(a.iban()).isEqualTo("DE89370400440532013000");
        server.verify();
    }

    @Test
    void B15_umsaetzeUmwandelnKennungenUndRueckbuchung() {
        expectToken();
        server.expect(requestTo(BASE + "/accounts/acc-a/transactions/?date_from=2026-06-01")).andRespond(withSuccess("""
                {"transactions":{
                  "booked":[
                    {"transactionId":"T1","bookingDate":"2026-06-02","valueDate":"2026-06-02",
                     "transactionAmount":{"amount":"-45.30","currency":"EUR"},"creditorName":"REWE",
                     "creditorAccount":{"iban":"DE02100100100006820101"},
                     "remittanceInformationUnstructuredArray":["Einkauf","Filiale 12"],"bankTransactionCode":"PMNT-CCRD-POSD"},
                    {"bookingDate":"2026-06-03","transactionAmount":{"amount":"-2.50","currency":"EUR"},"creditorName":"Kiosk"},
                    {"bookingDate":"2026-06-03","transactionAmount":{"amount":"-2.50","currency":"EUR"},"creditorName":"Kiosk"},
                    {"transactionId":"T4","bookingDate":"2026-06-05","transactionAmount":{"amount":"29.90","currency":"EUR"},
                     "debtorName":"FitX","remittanceInformationUnstructured":"RUECKLASTSCHRIFT","bankTransactionCode":"PMNT-RDDT-UPDD"}],
                  "pending":[
                    {"valueDate":"2026-06-07","transactionAmount":{"amount":"-10","currency":"EUR"},"creditorName":"Taxi"}]}}
                """, MediaType.APPLICATION_JSON));
        List<TransactionData> t = provider.transactions("req-1", "acc-a", LocalDate.of(2026, 6, 1));
        assertThat(t).hasSize(5);
        TransactionData rewe = t.get(0);
        assertThat(rewe.externalId()).isEqualTo("T1");
        assertThat(rewe.amountCents()).isEqualTo(-4_530);
        assertThat(rewe.remittance()).isEqualTo("Einkauf Filiale 12");
        assertThat(rewe.counterpartyIban()).isEqualTo("DE02100100100006820101");
        assertThat(rewe.raw()).contains("\"transactionId\":\"T1\"");   // Originaldaten unverändert
        // ohne Kennung: Prüfwert; zwei gleiche Umsätze am selben Tag bekommen verschiedene Kennungen
        assertThat(t.get(1).externalId()).startsWith("h:").isNotEqualTo(t.get(2).externalId());
        assertThat(t.get(3).reversal()).isTrue();
        assertThat(t.get(4).pending()).isTrue();
        assertThat(t.get(4).bookingDate()).isEqualTo(LocalDate.of(2026, 6, 7));
        assertThat(t.get(4).amountCents()).isEqualTo(-1_000);
        server.verify();

        // gleiche Antwort → gleiche Kennungen (keine doppelten Umsätze beim nächsten Abruf)
        var seen = new java.util.HashMap<String, Integer>();
        var node = new ObjectMapper().createObjectNode();
        node.put("bookingDate", "2026-06-03");
        node.putObject("transactionAmount").put("amount", "-2.50").put("currency", "EUR");
        node.put("creditorName", "Kiosk");
        assertThat(provider.map(node, false, seen).externalId()).isEqualTo(t.get(1).externalId());
    }

    @Test
    void B16_fehlerUebersetzenOhneGeheimnisse() {
        expectToken();
        server.expect(requestTo(BASE + "/accounts/acc-a/details/"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"detail\":\"limit\"}"));
        assertThatThrownBy(() -> provider.account("req-1", "acc-a")).isInstanceOf(ProviderException.class)
                .matches(e -> ((ProviderException) e).kind() == ProviderException.Kind.RATE_LIMITED)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret").doesNotContain("tok-1"));
        server.verify();

        assertThat(GoCardlessProvider.translate(503, true, "").kind()).isEqualTo(ProviderException.Kind.TRANSIENT);
        assertThat(GoCardlessProvider.translate(401, true, "").kind()).isEqualTo(ProviderException.Kind.AUTH_EXPIRED);
        assertThat(GoCardlessProvider.translate(400, true, "{\"summary\":\"EUA expired\"}").kind())
                .isEqualTo(ProviderException.Kind.AUTH_EXPIRED);
        assertThat(GoCardlessProvider.translate(400, false, "{}").kind()).isEqualTo(ProviderException.Kind.PERMANENT);
    }

    @Test
    void B17_abgelaufenesTokenWirdEinmalErneuert() {
        expectToken();
        server.expect(requestTo(BASE + "/institutions/?country=DE"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE + "/token/new/"))
                .andRespond(withSuccess(TOKEN.replace("tok-1", "tok-2"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/institutions/?country=DE")).andExpect(header("Authorization", "Bearer tok-2"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(provider.institutions("DE")).isEmpty();
        server.verify();

        assertThat(GoCardlessProvider.isReversal("PMNT-ICDT-RRTN", null)).isTrue();
        assertThat(GoCardlessProvider.isReversal(null, "Rücklastschrift wg. Widerspruch")).isTrue();
        assertThat(GoCardlessProvider.isReversal("PMNT-RDDT-ESDD", "Miete")).isFalse();
        assertThat(GoCardlessProvider.cents("12.345")).isEqualTo(1_235);
        assertThat(GoCardlessProvider.cents("-0.5")).isEqualTo(-50);
    }
}
