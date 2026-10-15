package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** T138–T139: zentraler Planer für Reise, Auto, Immobilie – Ansparen, Finanzieren, Anzahlung + Kredit. */
class PlannerFlowTest extends FlowTestSupport {

    private static JsonNode option(JsonNode r, String kind) {
        for (JsonNode o : r.get("options")) {
            if (o.get("kind").asText().equals(kind)) {
                return o;
            }
        }
        return null;
    }

    @Test
    void T138_reiseAnsparenIstAmGuenstigsten() throws Exception {
        TestUser u = userWithDemo("Pia");
        JsonNode r = postJson(u, "/api/planner/compare", "{\"goal\":\"REISE\",\"priceCents\":300000,\"months\":12}", 200);
        assertThat(r.get("months").asInt()).isEqualTo(12);
        JsonNode save = option(r, "SAVE");
        assertThat(save.get("monthlyCents").asLong()).isEqualTo(25_000);
        assertThat(save.get("extraCostCents").asLong()).isZero();
        assertThat(save.get("possible").asBoolean()).isTrue();
        JsonNode fin = option(r, "FINANCE");
        assertThat(fin.get("extraCostCents").asLong()).isPositive();
        assertThat(fin.get("totalCostCents").asLong()).isGreaterThanOrEqualTo(300_000 + fin.get("extraCostCents").asLong());
        JsonNode mix = option(r, "MIX");
        assertThat(mix.get("savingMonthlyCents").asLong()).isEqualTo(5_000);          // 600 € in 12 Monaten
        assertThat(mix.get("extraCostCents").asLong()).isLessThan(fin.get("extraCostCents").asLong());
        assertThat(r.get("recommendation").asText()).startsWith("Ansparen ist am günstigsten");
        assertThat(r.get("disclaimer").asText()).contains("kein Angebot");
    }

    @Test
    void T139_eingabenPruefenUndOhneDaten() throws Exception {
        TestUser u = registerUser("Paul");
        postJson(u, "/api/planner/compare", "{\"goal\":\"YACHT\",\"priceCents\":300000,\"months\":12}", 400);
        postJson(u, "/api/planner/compare", "{\"goal\":\"AUTO\",\"priceCents\":300000,\"equityCents\":300000,\"months\":12}", 400);
        postJson(u, "/api/planner/compare", "{\"goal\":\"AUTO\",\"priceCents\":300000,\"months\":0}", 400);
        JsonNode r = postJson(u, "/api/planner/compare", "{\"goal\":\"AUTO\",\"priceCents\":1500000,\"months\":24}", 200);
        assertThat(r.get("hasData").asBoolean()).isFalse();
        assertThat(option(r, "SAVE").get("feasibility").asText()).isEqualTo("UNKNOWN");
        assertThat(r.get("recommendation").asText()).contains("fehlen Einnahmen und Ausgaben");
    }
}
