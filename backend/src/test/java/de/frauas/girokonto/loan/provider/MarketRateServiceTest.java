package de.frauas.girokonto.loan.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import de.frauas.girokonto.loan.LoanPurpose;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * K06–K11: Referenzzinsen der Bundesbank gegen einen nachgebildeten Server (keine echten Aufrufe). Geprüft wird
 * vor allem: ohne erfolgreichen Abruf gibt es keine Werte, alte Werte werden als veraltet gekennzeichnet.
 */
class MarketRateServiceTest {

    private static final String BASE = "https://bbk.test/rest/data";
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final MediaType CSV = MediaType.parseMediaType("text/csv");
    private static final String SDMX_CSV = """
            DATAFLOW,BBK_STD_FREQ,BBK_STD_COUNTRY,TIME_PERIOD,OBS_VALUE,BBK_OBS_STATUS
            BBK:BBIM1(1.0),M,DE,2026-07,8.85,A
            BBK:BBIM1(1.0),M,DE,2026-08,8.99,A
            """;

    static class MemoryStore implements MarketRateStore {
        final Map<String, Stored> map = new HashMap<>();

        @Override
        public Map<String, Stored> load() {
            return new HashMap<>(map);
        }

        @Override
        public void save(String key, Stored v) {
            map.put(key, v);
        }
    }

    private MockRestServiceServer server;

    private MarketRateService service(boolean enabled, MemoryStore store, Instant now) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new MarketRateService(builder, new MarketRateProperties(enabled, BASE, 24, 7), store,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void K06_csvFormateLesen() {
        assertThat(MarketRateService.parse(SDMX_CSV, NOW)).hasValueSatisfying(v -> {
            assertThat(v.period()).isEqualTo("2026-08");
            assertThat(v.ratePercent()).isEqualTo(8.99);
        });
        String semicolon = "﻿\"TIME_PERIOD\";\"OBS_VALUE\";\"OBS_STATUS\"\n\"2026-08\";\"4,05\";\"A\"\n\"2026-06\";\"3,98\";\"A\"\n";
        assertThat(MarketRateService.parse(semicolon, NOW)).hasValueSatisfying(v -> {
            assertThat(v.period()).isEqualTo("2026-08");
            assertThat(v.ratePercent()).isEqualTo(4.05);
        });
        String table = ";BBIM1.M.DE.B.A2B.A.C.A.2250.EUR.N;BBIM1.M.DE.B.A2B.A.C.A.2250.EUR.N_FLAGS\n"
                + "Einheit;% p. a.;\n2026-07;8,85;\n2026-08;8,99;\n2026-09;.;Kein Wert vorhanden\n";
        assertThat(MarketRateService.parse(table, NOW)).hasValueSatisfying(v -> {
            assertThat(v.period()).isEqualTo("2026-08");
            assertThat(v.ratePercent()).isEqualTo(8.99);
        });
    }

    @Test
    void K07_unbrauchbareAntwortenErgebenKeineWerte() {
        assertThat(MarketRateService.parse(null, NOW)).isEmpty();
        assertThat(MarketRateService.parse("<html>Fehler</html>", NOW)).isEmpty();
        assertThat(MarketRateService.parse("TIME_PERIOD,OBS_VALUE\n2026-08,NaN\n", NOW)).isEmpty();
        assertThat(MarketRateService.parse("TIME_PERIOD,OBS_VALUE\n2026-08,999\n", NOW)).isEmpty();   // unplausibel
    }

    @Test
    void K08_abrufAllerReihenUndPassendeReferenz() {
        MemoryStore store = new MemoryStore();
        MarketRateService s = service(true, store, NOW);
        server.expect(times(MarketRateService.SERIES.size()), requestTo(startsWith(BASE + "/BBIM1/M.DE.B.")))
                .andExpect(method(HttpMethod.GET)).andExpect(queryParam("lastNObservations", "1"))
                .andExpect(queryParam("format", "csv"))
                .andRespond(withSuccess(SDMX_CSV, CSV));
        assertThat(s.refresh()).isEqualTo(MarketRateService.SERIES.size());
        server.verify();
        assertThat(store.map).hasSize(MarketRateService.SERIES.size());
        assertThat(s.status().state()).isEqualTo("OK");

        var auto = s.references(LoanPurpose.AUTO, 48);
        assertThat(auto).extracting(MarketRateService.ReferenceRate::key).containsExactly("CONSUMER_ALL", "CONSUMER_1_5Y");
        assertThat(auto).allMatch(r -> !r.stale() && r.period().equals("2026-08") && r.ratePercent() == 8.99
                && r.note().contains("kein Angebot") && r.source().contains("Bundesbank"));
        assertThat(s.references(LoanPurpose.IMMOBILIE, 240)).extracting(MarketRateService.ReferenceRate::key)
                .containsExactly("HOUSING_ALL", "HOUSING_OVER_10Y");
        assertThat(s.references(LoanPurpose.SONSTIGES, 12)).extracting(MarketRateService.ReferenceRate::key)
                .containsExactly("CONSUMER_ALL", "CONSUMER_TO_1Y");
    }

    @Test
    void K09_ohneAbrufKeineErfundenenWerte() {
        MarketRateService s = service(true, new MemoryStore(), NOW);
        server.expect(manyTimes(), requestTo(startsWith(BASE))).andRespond(withServerError());
        assertThat(s.refresh()).isZero();
        assertThat(s.references(LoanPurpose.AUTO, 48)).isEmpty();
        assertThat(s.status().state()).isEqualTo("UNAVAILABLE");
        assertThat(s.status().message()).isEqualTo("Bundesbank nicht erreichbar – keine Referenzzinsen.");
    }

    @Test
    void K10_alteWerteBleibenAberVeraltet() {
        MemoryStore store = new MemoryStore();
        store.save("CONSUMER_ALL", new MarketRateStore.Stored("2026-03", 8.40, Instant.parse("2026-09-01T00:00:00Z")));
        MarketRateService s = service(true, store, NOW);
        server.expect(manyTimes(), requestTo(startsWith(BASE))).andRespond(withServerError());
        s.refresh();
        var refs = s.references(LoanPurpose.AUTO, 48);
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).ratePercent()).isEqualTo(8.40);
        assertThat(refs.get(0).stale()).isTrue();
        assertThat(s.status().state()).isEqualTo("STALE");
        assertThat(s.status().message()).isEqualTo("Daten möglicherweise veraltet (Stand 2026-03).");
    }

    @Test
    void K11_abgeschaltetOhneAufrufe() {
        MarketRateService s = service(false, new MemoryStore(), NOW);
        s.scheduled();
        assertThat(s.references(LoanPurpose.AUTO, 48)).isEmpty();
        assertThat(s.status().state()).isEqualTo("DISABLED");
        server.verify();   // keine Anfrage erwartet und keine gestellt
    }
}
