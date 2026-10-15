package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/** T129–T133: Verträge (Kosten, Fristen, Erinnerung, Kündigung vermerken) und verschlüsselte Dokumente. */
class ContractFlowTest extends FlowTestSupport {

    private static final byte[] PDF = "%PDF-1.4\n%Test\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode contract(TestUser u, String body, int expected) throws Exception {
        return postJson(u, "/api/contracts", body, expected);
    }

    private MvcResult upload(TestUser u, String name, byte[] bytes, Long contractId, int expected) throws Exception {
        var req = multipart("/api/documents").file(new MockMultipartFile("file", name, "application/octet-stream", bytes))
                .header("Authorization", u.bearer());
        if (contractId != null) {
            req.param("contractId", String.valueOf(contractId));
        }
        return mvc.perform(req).andExpect(status().is(expected)).andReturn();
    }

    @Test
    void T129_kostenUndFristen() throws Exception {
        TestUser u = registerUser("Vera");
        LocalDate today = today(u);
        JsonNode c = contract(u, "{\"name\":\"Handy\",\"kind\":\"HANDY_INTERNET\",\"costCents\":2999,\"billing\":\"MONTHLY\","
                + "\"startDate\":\"" + today.minusYears(1) + "\",\"minTermMonths\":24,\"renewalMonths\":1,"
                + "\"noticeValue\":1,\"noticeUnit\":\"MONTHS\"}", 201);
        assertThat(c.get("nextEndDate").asText()).isEqualTo(today.minusYears(1).plusMonths(24).minusDays(1).toString());
        assertThat(c.get("cancelByDate").asText()).isEqualTo(today.minusYears(1).plusMonths(24).minusDays(1).minusMonths(1).toString());
        assertThat(c.get("yearlyCents").asLong()).isEqualTo(35_988);
        contract(u, "{\"name\":\"Versicherung\",\"kind\":\"VERSICHERUNG\",\"costCents\":12000,\"billing\":\"YEARLY\","
                + "\"minTermMonths\":12,\"renewalMonths\":12,\"noticeValue\":3,\"noticeUnit\":\"MONTHS\"}", 201);
        JsonNode o = getJson(u, "/api/contracts");
        assertThat(o.get("monthlyTotalCents").asLong()).isEqualTo(2_999 + 1_000);
        assertThat(o.get("yearlyTotalCents").asLong()).isEqualTo(35_988 + 12_000);
        assertThat(o.get("note").asText()).contains("Kündigen musst du selbst");
        assertThat(contract(u, "{\"name\":\"X\",\"costCents\":100,\"kind\":\"RAKETE\"}", 400).get("code").asText())
                .isEqualTo("INVALID_KIND");
        contract(u, "{\"name\":\"X\",\"costCents\":100,\"noticeValue\":30}", 400);
    }

    @Test
    void T130_erinnerungEinmalVorFristende() throws Exception {
        TestUser u = registerUser("Rita");
        LocalDate today = today(u);
        // Ende in ~40 Tagen, Frist 1 Monat → Kündigen bis in ~10 Tagen; Erinnerung 14 Tage vorher → sofort fällig
        LocalDate start = today.plusDays(40).plusDays(1).minusMonths(12);
        contract(u, "{\"name\":\"Fitnessstudio\",\"kind\":\"FITNESS\",\"costCents\":3500,\"startDate\":\"" + start
                + "\",\"minTermMonths\":12,\"renewalMonths\":12,\"noticeValue\":1,\"noticeUnit\":\"MONTHS\",\"reminderDays\":14}", 201);
        advance("days", 1);
        u = relogin(u);
        assertThat(notificationTexts(u).stream().filter(t -> t.startsWith("Kündigungsfrist") && t.contains("„Fitnessstudio“")))
                .hasSize(1);
        advance("days", 2);
        u = relogin(u);
        assertThat(notificationTexts(u).stream().filter(t -> t.startsWith("Kündigungsfrist"))).hasSize(1);
    }

    @Test
    void T131_kuendigungVermerkenUndBeenden() throws Exception {
        TestUser u = registerUser("Kurt");
        JsonNode c = contract(u, "{\"name\":\"Streaming\",\"kind\":\"STREAMING\",\"costCents\":1299,\"minTermMonths\":0,"
                + "\"renewalMonths\":1,\"noticeValue\":0,\"noticeUnit\":\"DAYS\"}", 201);
        long id = c.get("id").asLong();
        JsonNode k = postJson(u, "/api/contracts/" + id + "/cancel", "{}", 200);
        assertThat(k.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(k.get("endsOn").asText()).isEqualTo(c.get("nextEndDate").asText());
        postJson(u, "/api/contracts/" + id + "/cancel", "{}", 409);
        advance("days", 40);
        u = relogin(u);
        JsonNode o = getJson(u, "/api/contracts");
        assertThat(o.get("contracts").get(0).get("status").asText()).isEqualTo("ENDED");
        assertThat(o.get("monthlyTotalCents").asLong()).isZero();
    }

    @Test
    void T132_dokumentVerschluesseltNurFuerBesitzer() throws Exception {
        TestUser u = registerUser("Doris");
        TestUser x = registerUser("Fremder");
        long cid = contract(u, "{\"name\":\"Strom\",\"kind\":\"STROM_GAS\",\"costCents\":8000}", 201).get("id").asLong();
        JsonNode d = json.readTree(upload(u, "../Rechnung Mai.pdf", PDF, cid, 201).getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(d.get("fileName").asText()).isEqualTo("Rechnung Mai.pdf");
        assertThat(d.get("contentType").asText()).isEqualTo("application/pdf");
        long id = d.get("id").asLong();
        byte[] stored = jdbc.queryForObject("SELECT content FROM document WHERE id = ?", byte[].class, id);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
        MvcResult r = mvc.perform(get("/api/documents/" + id + "/content").header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertThat(r.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        mvc.perform(get("/api/documents/" + id + "/content").header("Authorization", x.bearer())).andExpect(status().isNotFound());
        deletePath(x, "/api/documents/" + id, 404);
        upload(x, "a.pdf", PDF, cid, 404);                                   // fremder Vertrag
        assertThat(getJson(u, "/api/contracts/" + cid).get("documentCount").asInt()).isEqualTo(1);
        deletePath(u, "/api/documents/" + id, 204);
        assertThat(getJson(u, "/api/documents")).isEmpty();
    }

    @Test
    void T133_nurErlaubteDateitypen() throws Exception {
        TestUser u = registerUser("Udo");
        upload(u, "seite.pdf", "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8), null, 400);
        upload(u, "leer.pdf", new byte[0], null, 400);
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        upload(u, "gross.pdf", big, null, 400);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        JsonNode d = json.readTree(upload(u, "bild.jpg", png, null, 201).getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(d.get("contentType").asText()).isEqualTo("image/png");
        assertThat(d.get("fileName").asText()).isEqualTo("bild.png");
    }
}
