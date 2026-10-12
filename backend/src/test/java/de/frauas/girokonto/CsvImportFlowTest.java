package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** /K040/ CSV-Import (T36, T37). */
class CsvImportFlowTest extends FlowTestSupport {

    private String sample(TestUser u) throws Exception {
        return mvc.perform(get("/api/imports/sample").header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode upload(TestUser u, String name, String csv, int expected) throws Exception {
        return postJson(u, "/api/imports", json.writeValueAsString(
                java.util.Map.of("fileName", name, "content", csv)), expected);
    }

    @Test
    void T36_importZaehltImFinanzueberblickOhneKontostand() throws Exception {
        TestUser u = registerUser("Jana");
        long balanceBefore = balance(u);
        assertThat(getJson(u, "/api/insights/overview").get("hasData").asBoolean()).isFalse();

        String csv = sample(u);
        JsonNode r = upload(u, "umsaetze-sparkasse.csv", csv, 201);
        assertThat(r.get("imported").asInt()).isEqualTo(33);                 // 3 Monate × 11 Umsätze
        assertThat(r.get("skipped").asInt()).isZero();
        assertThat(r.get("incomeCents").asLong()).isEqualTo(3 * 240_000);
        assertThat(r.get("expenseCents").asLong()).isEqualTo(3 * 122_956);

        // Kontostand unverändert, Finanzüberblick hat jetzt Daten
        assertThat(balance(u)).isEqualTo(balanceBefore);
        JsonNode ov = getJson(u, "/api/insights/overview");
        assertThat(ov.get("hasData").asBoolean()).isTrue();
        assertThat(ov.get("avgIncomeCents").asLong()).isEqualTo(240_000);
        assertThat(ov.get("avgExpenseCents").asLong()).isEqualTo(122_956);
        assertThat(ov.get("avgFixedCents").asLong()).isEqualTo(92_899);          // Miete + Strom + Netflix

        JsonNode months = getJson(u, "/api/insights/months?count=4");
        String lastFull = YearMonth.parse(months.get(3).get("month").asText()).minusMonths(1).toString();
        assertThat(months.get(2).get("month").asText()).isEqualTo(lastFull);
        assertThat(months.get(2).get("incomeCents").asLong()).isEqualTo(240_000);

        // gleiche Datei nochmal: nichts doppelt
        JsonNode again = upload(u, "nochmal.csv", csv, 201);
        assertThat(again.get("imported").asInt()).isZero();
        assertThat(again.get("skipped").asInt()).isEqualTo(33);

        // Liste, Umsätze, Kategorien
        JsonNode list = getJson(u, "/api/imports");
        assertThat(list).hasSize(1);
        long id = list.get(0).get("id").asLong();
        JsonNode tx = getJson(u, "/api/imports/" + id + "/transactions");
        assertThat(tx).hasSize(33);
        assertThat(tx.findValuesAsText("categoryLabel")).contains("Gehalt", "Wohnen", "Lebensmittel", "Freizeit");

        // Import löschen -> Überblick wieder leer
        deletePath(u, "/api/imports/" + id, 204);
        assertThat(getJson(u, "/api/imports")).isEmpty();
        assertThat(getJson(u, "/api/insights/overview").get("hasData").asBoolean()).isFalse();
    }

    @Test
    void T37_ungueltigeDateienUndFremdeImporte() throws Exception {
        TestUser u = registerUser("Kurt");
        upload(u, "leer.csv", " ", 400);
        upload(u, "bild.csv", "GIF89a nur Unsinn ohne Kopfzeile", 400);
        JsonNode ok = upload(u, "mini.csv", "Datum;Betrag;Verwendungszweck\n01.09.2026;-10,00;Kino\n", 201);
        TestUser other = registerUser("Lena");
        deletePath(other, "/api/imports/" + ok.get("importId").asLong(), 404);
        mvc.perform(get("/api/imports/" + ok.get("importId").asLong() + "/transactions")
                .header("Authorization", other.bearer())).andExpect(status().isNotFound());
    }
}
