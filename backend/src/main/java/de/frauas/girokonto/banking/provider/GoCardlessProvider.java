package de.frauas.girokonto.banking.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.frauas.girokonto.banking.BankingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Echte Bankverbindungen über GoCardless Bank Account Data (PSD2-Kontoinformationsdienst, EWR).
 *
 * <p>Nur aktiv, wenn {@code GOCARDLESS_SECRET_ID} und {@code GOCARDLESS_SECRET_KEY} gesetzt sind
 * (Vertrag mit dem Anbieter nötig). Ohne diese Zugangsdaten wird der Anbieter nicht angeboten –
 * es werden keine Antworten erfunden. Abläufe: Vereinbarung (Agreement) → Anfrage (Requisition) mit Link zur
 * Bank → Nutzer meldet sich bei der Bank an → Konten, Salden und Umsätze abrufen.
 */
@Component
public class GoCardlessProvider implements BankingProvider {

    public static final String ID = "GOCARDLESS";
    static final int HISTORY_DAYS = 90;
    static final int ACCESS_DAYS = 90;

    private final RestClient http;
    private final BankingProperties.GoCardless cfg;
    private final ObjectMapper json;
    private final Clock realClock = Clock.systemUTC();   // Ablauf der Zugriffstoken: echte Zeit

    private volatile String accessToken;
    private volatile Instant accessExpires = Instant.EPOCH;

    @Autowired
    public GoCardlessProvider(RestClient.Builder builder, BankingProperties props, ObjectMapper json) {
        this(builder.requestFactory(timeouts()), props, json, true);
    }

    /** Für Tests: der Builder ist bereits an einen Mock-Server gebunden. */
    GoCardlessProvider(RestClient.Builder builder, BankingProperties props, ObjectMapper json, boolean unused) {
        this.cfg = props.gocardless();
        this.json = json;
        String base = cfg == null || cfg.baseUrl() == null || cfg.baseUrl().isBlank()
                ? "https://bankaccountdata.gocardless.com/api/v2" : cfg.baseUrl();
        this.http = builder.baseUrl(base).defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE).build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return f;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "GoCardless Bank Account Data (PSD2)";
    }

    @Override
    public boolean sandbox() {
        return false;
    }

    @Override
    public boolean available() {
        return cfg != null && cfg.configured();
    }

    /** Banken erlauben über PSD2 nur wenige Abrufe pro Tag und Konto (oft 4). */
    @Override
    public Duration minSyncInterval() {
        return Duration.ofHours(6);
    }

    @Override
    public List<Institution> institutions(String country) {
        String c = country == null || !country.matches("[A-Za-z]{2}") ? "DE" : country.toUpperCase(Locale.ROOT);
        JsonNode list = call(false, () -> http.get().uri("/institutions/?country={c}", c)
                .header("Authorization", bearer()).retrieve().body(JsonNode.class));
        List<Institution> out = new ArrayList<>();
        for (JsonNode i : list) {
            out.add(new Institution(i.path("id").asText(), i.path("name").asText(), text(i, "bic"),
                    i.path("transaction_total_days").asInt(HISTORY_DAYS),
                    i.path("max_access_valid_for_days").asInt(ACCESS_DAYS), text(i, "logo")));
        }
        return out;
    }

    @Override
    public ConsentStart startConsent(String institutionId, String redirectUrl, String reference, String language) {
        Map<String, Object> agreement = Map.of(
                "institution_id", institutionId,
                "max_historical_days", HISTORY_DAYS,
                "access_valid_for_days", ACCESS_DAYS,
                "access_scope", List.of("balances", "details", "transactions"));
        JsonNode eua = call(false, () -> http.post().uri("/agreements/enduser/").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).body(agreement).retrieve().body(JsonNode.class));
        Map<String, Object> requisition = new HashMap<>();
        requisition.put("redirect", redirectUrl);
        requisition.put("institution_id", institutionId);
        requisition.put("reference", reference);
        requisition.put("agreement", eua.path("id").asText());
        requisition.put("user_language", "en".equals(language) ? "EN" : "DE");
        JsonNode req = call(false, () -> http.post().uri("/requisitions/").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).body(requisition).retrieve().body(JsonNode.class));
        return new ConsentStart(req.path("id").asText(), text(req, "link"));
    }

    @Override
    public ConsentState consentState(String consentRef) {
        JsonNode req = call(false, () -> http.get().uri("/requisitions/{id}/", consentRef)
                .header("Authorization", bearer()).retrieve().body(JsonNode.class));
        ConsentStatus status = switch (req.path("status").asText()) {
            case "LN" -> ConsentStatus.LINKED;
            case "RJ", "SU" -> ConsentStatus.REJECTED;
            case "EX" -> ConsentStatus.EXPIRED;
            default -> ConsentStatus.PENDING;   // CR, GC, UA, SA, GA
        };
        List<String> ids = new ArrayList<>();
        req.path("accounts").forEach(a -> ids.add(a.asText()));
        Instant expires = Instant.now(realClock).plus(Duration.ofDays(ACCESS_DAYS));
        String agreementId = text(req, "agreement");
        if (agreementId != null) {
            JsonNode eua = call(false, () -> http.get().uri("/agreements/enduser/{id}/", agreementId)
                    .header("Authorization", bearer()).retrieve().body(JsonNode.class));
            String accepted = text(eua, "accepted");
            if (accepted != null) {
                expires = OffsetDateTime.parse(accepted).toInstant()
                        .plus(Duration.ofDays(eua.path("access_valid_for_days").asInt(ACCESS_DAYS)));
            }
        }
        return new ConsentState(status, ids, expires);
    }

    @Override
    public AccountData account(String consentRef, String accountId) {
        JsonNode details = call(true, () -> http.get().uri("/accounts/{id}/details/", accountId)
                .header("Authorization", bearer()).retrieve().body(JsonNode.class)).path("account");
        JsonNode balances = call(true, () -> http.get().uri("/accounts/{id}/balances/", accountId)
                .header("Authorization", bearer()).retrieve().body(JsonNode.class)).path("balances");
        JsonNode best = null;
        for (String type : List.of("interimAvailable", "expected", "closingBooked", "interimBooked")) {
            for (JsonNode b : balances) {
                if (best == null && type.equals(b.path("balanceType").asText())) {
                    best = b;
                }
            }
        }
        if (best == null && balances.size() > 0) {
            best = balances.get(0);
        }
        Long cents = best == null ? null : cents(best.path("balanceAmount").path("amount").asText());
        String name = firstText(details, "name", "product", "displayName");
        return new AccountData(accountId, text(details, "iban"), name == null ? "Konto" : name,
                details.path("currency").asText("EUR"), cents, best == null ? null : text(best, "balanceType"),
                Instant.now(realClock));
    }

    @Override
    public List<TransactionData> transactions(String consentRef, String accountId, LocalDate from) {
        JsonNode body = call(true, () -> http.get()
                .uri("/accounts/{id}/transactions/?date_from={from}", accountId, from.toString())
                .header("Authorization", bearer()).retrieve().body(JsonNode.class)).path("transactions");
        List<TransactionData> out = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (JsonNode t : body.path("booked")) {
            out.add(map(t, false, seen));
        }
        for (JsonNode t : body.path("pending")) {
            out.add(map(t, true, seen));
        }
        return out;
    }

    @Override
    public void revoke(String consentRef) {
        try {
            call(false, () -> http.delete().uri("/requisitions/{id}/", consentRef)
                    .header("Authorization", bearer()).retrieve().toBodilessEntity());
        } catch (ProviderException e) {
            if (e.kind() != ProviderException.Kind.AUTH_EXPIRED) {   // schon gelöscht/abgelaufen ist in Ordnung
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------ Umsätze umwandeln

    TransactionData map(JsonNode t, boolean pending, Map<String, Integer> seen) {
        JsonNode amountNode = t.path("transactionAmount");
        long cents = cents(amountNode.path("amount").asText());
        String currency = amountNode.path("currency").asText("EUR");
        String dateText = firstText(t, "bookingDate", "valueDate");
        if (dateText == null) {
            String dt = firstText(t, "bookingDateTime", "valueDateTime");
            dateText = dt == null ? null : dt.substring(0, 10);
        }
        LocalDate booking = dateText == null ? LocalDate.now(realClock) : LocalDate.parse(dateText);
        String valueText = text(t, "valueDate");
        boolean outgoing = cents < 0;
        String counterparty = outgoing ? firstText(t, "creditorName", "debtorName")
                : firstText(t, "debtorName", "creditorName");
        String cpIban = text(t.path(outgoing ? "creditorAccount" : "debtorAccount"), "iban");
        String remittance = text(t, "remittanceInformationUnstructured");
        if (remittance == null && t.path("remittanceInformationUnstructuredArray").isArray()) {
            List<String> parts = new ArrayList<>();
            t.path("remittanceInformationUnstructuredArray").forEach(p -> parts.add(p.asText()));
            remittance = String.join(" ", parts);
        }
        if (remittance == null) {
            remittance = firstText(t, "remittanceInformationStructured", "additionalInformation");
        }
        String code = firstText(t, "bankTransactionCode", "proprietaryBankTransactionCode");
        String id = firstText(t, "transactionId", "internalTransactionId");
        if (id == null) {
            // keine Kennung geliefert: Prüfwert der Daten, gleiche Umsätze am selben Tag werden durchnummeriert
            String base = (pending ? "p" : "b") + "|" + booking + "|" + cents + "|" + currency + "|"
                    + counterparty + "|" + remittance;
            int n = seen.merge(base, 1, Integer::sum);
            id = "h:" + sha256(base + "|" + n).substring(0, 40);
        }
        return new TransactionData(id, pending, booking, valueText == null ? null : LocalDate.parse(valueText),
                cents, currency, counterparty, cpIban, remittance, code, isReversal(code, remittance), null,
                t.toString());
    }

    /**
     * Rückbuchung erkennen – soweit die Bank es angibt: ISO-20022-Buchungscodes für Rückgaben
     * (…-UPDD Rücklastschrift, …-RRTN Rücküberweisung, …-RPCR Rückruf) oder eindeutiger Text.
     */
    static boolean isReversal(String code, String remittance) {
        if (code != null) {
            String c = code.toUpperCase(Locale.ROOT);
            if (c.endsWith("-UPDD") || c.endsWith("-RRTN") || c.endsWith("-RPCR") || c.endsWith("-ARET")) {
                return true;
            }
        }
        if (remittance != null) {
            String r = remittance.toLowerCase(Locale.ROOT);
            return r.contains("rücklastschrift") || r.contains("ruecklastschrift") || r.contains("rückbuchung")
                    || r.contains("rueckbuchung");
        }
        return false;
    }

    static long cents(String amount) {
        return new BigDecimal(amount.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    // ------------------------------------------------------------------ HTTP

    private String bearer() {
        if (accessToken == null || Instant.now(realClock).isAfter(accessExpires.minusSeconds(60))) {
            refreshToken();
        }
        return "Bearer " + accessToken;
    }

    private synchronized void refreshToken() {
        if (!available()) {
            throw new ProviderException(ProviderException.Kind.PERMANENT, "Anbieter nicht eingerichtet");
        }
        try {
            JsonNode tok = http.post().uri("/token/new/").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("secret_id", cfg.secretId(), "secret_key", cfg.secretKey()))
                    .retrieve().body(JsonNode.class);
            accessToken = tok.path("access").asText();
            accessExpires = Instant.now(realClock).plusSeconds(tok.path("access_expires").asLong(3600));
        } catch (RestClientResponseException e) {
            int s = e.getStatusCode().value();
            if (s == 429 || s >= 500) {
                throw new ProviderException(s == 429 ? ProviderException.Kind.RATE_LIMITED
                        : ProviderException.Kind.TRANSIENT, "Anbieter vorübergehend nicht erreichbar");
            }
            throw new ProviderException(ProviderException.Kind.PERMANENT, "Zugangsdaten des Anbieters ungültig");
        } catch (ResourceAccessException e) {
            throw new ProviderException(ProviderException.Kind.TRANSIENT, "Anbieter nicht erreichbar");
        }
    }

    /** Ruft auf und übersetzt Fehler; bei 401 wird das Token einmal erneuert. */
    private <T> T call(boolean accountData, Supplier<T> request) {
        for (int attempt = 0; ; attempt++) {
            try {
                return request.get();
            } catch (RestClientResponseException e) {
                HttpStatusCode s = e.getStatusCode();
                if (s.value() == 401 && attempt == 0) {
                    accessToken = null;   // Token abgelaufen → neu holen und noch einmal
                    continue;
                }
                throw translate(s.value(), accountData, e.getResponseBodyAsString(StandardCharsets.UTF_8));
            } catch (ResourceAccessException e) {
                throw new ProviderException(ProviderException.Kind.TRANSIENT, "Anbieter nicht erreichbar", e);
            }
        }
    }

    static ProviderException translate(int status, boolean accountData, String body) {
        String b = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (status == 429) {
            return new ProviderException(ProviderException.Kind.RATE_LIMITED, "Abruf-Grenze der Bank erreicht");
        }
        if (status >= 500 || status == 409 || status == 408) {
            return new ProviderException(ProviderException.Kind.TRANSIENT, "Bank oder Anbieter vorübergehend gestört");
        }
        if (status == 401 || status == 403 || (status == 400 && (b.contains("expired") || b.contains("suspended")))
                || (accountData && status == 404)) {
            return new ProviderException(ProviderException.Kind.AUTH_EXPIRED, "Freigabe abgelaufen oder widerrufen");
        }
        return new ProviderException(ProviderException.Kind.PERMANENT, "Anfrage vom Anbieter abgelehnt (" + status + ")");
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static String firstText(JsonNode n, String... fields) {
        for (String f : fields) {
            String v = text(n, f);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
