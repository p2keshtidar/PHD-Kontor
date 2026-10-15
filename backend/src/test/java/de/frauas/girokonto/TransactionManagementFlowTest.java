package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/** T97–T101: Transaktionsmanagement – Notizen, CSV-Importe in „Alle Umsätze“, CSV-Export, doppelte Umsätze. */
class TransactionManagementFlowTest extends FlowTestSupport {

    private static final DateTimeFormatter DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Autowired
    JdbcTemplate jdbc;

    private long firstBooking(TestUser u, long accountId) throws Exception {
        return getJson(u, "/api/accounts/" + accountId + "/bookings").get("items").get(0).get("bookingId").asLong();
    }

    private JsonNode note(TestUser u, long accountId, long bookingId, String note, int expected) throws Exception {
        return putJson(u, "/api/accounts/" + accountId + "/bookings/" + bookingId + "/note",
                json.writeValueAsString(Map.of("note", note)), expected);
    }

    private void importCsv(TestUser u, String name, String rows) throws Exception {
        postJson(u, "/api/imports", json.writeValueAsString(Map.of("fileName", name,
                "content", "Datum;Betrag;Verwendungszweck\n" + rows)), 201);
    }

    private void expense(TestUser u, String title, long cents, LocalDate date) throws Exception {
        postJson(u, "/api/entries", "{\"kind\":\"EXPENSE\",\"title\":\"" + title + "\",\"amountCents\":" + cents
                + ",\"intervalMonths\":0,\"date\":\"" + date + "\"}", 201);
    }

    @Test
    void T97_notizenPrivatDurchsuchbarUndLoeschbar() throws Exception {
        TestUser u = registerUser("Nora");
        long booking = firstBooking(u, u.accountId());
        assertThat(note(u, u.accountId(), booking, "  Startgeld von der Bank  ", 200).get("note").asText())
                .isEqualTo("Startgeld von der Bank");
        assertThat(getJson(u, "/api/accounts/" + u.accountId() + "/bookings").get("items").get(0).get("note").asText())
                .isEqualTo("Startgeld von der Bank");
        assertThat(getJson(u, "/api/accounts/" + u.accountId() + "/bookings?q=startgeld").get("items")).hasSize(1);
        JsonNode all = getJson(u, "/api/transactions?q=startgeld");
        assertThat(all.get("items")).hasSize(1);
        assertThat(all.get("items").get(0).get("note").asText()).isEqualTo("Startgeld von der Bank");
        note(u, u.accountId(), booking, "x".repeat(501), 400);
        note(u, u.accountId(), 999_999_999L, "Fremd", 404);

        // geteiltes digitales Konto: jeder sieht nur seine eigene Notiz
        TestUser member = registerUser("Mitglied");
        long sub = postJson(u, "/api/subaccounts", "{\"name\":\"WG-Kasse\"}", 201).get("id").asLong();
        postJson(u, "/api/subaccounts/" + sub + "/members", "{\"recipient\":\"" + member.email()
                + "\",\"role\":\"FULL\"}", 200);
        postJson(u, "/api/accounts/move", "{\"fromAccountId\":" + u.accountId() + ",\"toAccountId\":" + sub
                + ",\"amountCents\":2000}", 204);
        long move = firstBooking(u, sub);
        note(u, sub, move, "Meine Notiz", 200);
        assertThat(getJson(member, "/api/accounts/" + sub + "/bookings").get("items").get(0).get("note").isNull()).isTrue();
        note(member, sub, move, "Notiz des Mitglieds", 200);
        assertThat(getJson(u, "/api/accounts/" + sub + "/bookings").get("items").get(0).get("note").asText())
                .isEqualTo("Meine Notiz");
        TestUser stranger = registerUser("Fremd");
        note(stranger, u.accountId(), booking, "Hallo", 403);

        note(u, u.accountId(), booking, "   ", 200);   // leer = löschen
        assertThat(getJson(u, "/api/accounts/" + u.accountId() + "/bookings").get("items").get(0).get("note").isNull())
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_note WHERE booking_id = ?", Integer.class, booking))
                .isZero();
    }

    @Test
    void T98_csvImporteErscheinenInAllenUmsaetzen() throws Exception {
        TestUser u = registerUser("Ines");
        LocalDate d = today(u);
        importCsv(u, "andere-bank.csv", d.format(DE) + ";-12,34;Bäckerei Korn\n" + d.format(DE) + ";250,00;Rückzahlung Tom\n");
        JsonNode imports = getJson(u, "/api/transactions?source=IMPORT");
        assertThat(imports.get("items")).hasSize(2);
        imports.get("items").forEach(t -> {
            assertThat(t.get("source").asText()).isEqualTo("IMPORT");
            assertThat(t.get("accountName").asText()).isEqualTo("andere-bank.csv");
        });
        assertThat(imports.get("incomeCents").asLong()).isEqualTo(25_000);
        assertThat(imports.get("expenseCents").asLong()).isEqualTo(1_234);
        assertThat(imports.get("notes").toString()).contains("ändern keine Kontostände");
        assertThat(getJson(u, "/api/transactions").get("items")).hasSize(3);   // + Startguthaben
        assertThat(balance(u)).isEqualTo(100_000);                             // Import ändert keinen Kontostand
    }

    @Test
    void T99_csvExportMitSchutzVorFormeln() throws Exception {
        TestUser u = registerUser("Emil");
        LocalDate d = today(u);
        importCsv(u, "konto.csv", d.format(DE) + ";-12,50;=HYPERLINK(\"http://x\")\n");
        long booking = firstBooking(u, u.accountId());
        note(u, u.accountId(), booking, "Mit; Semikolon", 200);
        MockHttpServletResponse r = mvc.perform(get("/api/transactions/export").header("Authorization", u.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(r.getContentType()).startsWith("text/csv");
        assertThat(r.getHeader("Content-Disposition")).contains("attachment").contains("umsaetze-" + d + ".csv");
        assertThat(r.getHeader("X-Export-Rows")).isEqualTo("2");
        String csv = r.getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Datum;Quelle;Konto;Bank;Gegenpartei;Verwendungszweck;Kategorie;Betrag;Währung;Status;Notiz\r\n");
        assertThat(csv).contains(";-12,50;EUR;gebucht;");
        assertThat(csv).contains(";1000,00;EUR;gebucht;\"Mit; Semikolon\"");
        assertThat(csv).contains(";'=HYPERLINK(").doesNotContain(";=HYPERLINK");   // Formel entschärft

        String en = mvc.perform(get("/api/transactions/export?source=IMPORT").header("Authorization", u.bearer())
                        .header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(en).startsWith("﻿Date,Source,Account,Bank,Counterparty,Reference,Category,Amount,Currency,Status,Note");
        assertThat(en).contains(d + ",CSV import,konto.csv,CSV import,").contains(",-12.50,EUR,booked,");
        // Prüfprotokoll: nur die Anzahl, keine Inhalte
        assertThat(jdbc.queryForList("SELECT details FROM audit_log WHERE user_id = ? AND action = 'TRANSACTIONS_EXPORTED' ORDER BY id",
                String.class, u.id())).containsExactly("2 Zeilen", "1 Zeilen");
        mvc.perform(get("/api/transactions/export?source=GOLD").header("Authorization", u.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void T100_doppelteUmsaetzeErkennenUndAlsKeinDuplikatMarkieren() throws Exception {
        TestUser u = registerUser("Doris");
        LocalDate d = today(u);
        expense(u, "Miete Oktober", 85_000, d);
        importCsv(u, "konto.csv", d.minusDays(1).format(DE) + ";-850,00;Hausverwaltung Miete\n"
                + d.format(DE) + ";-4,20;Café Mocca\n"
                + d.minusDays(2).format(DE) + ";-4,20;Café Mocca\n"
                + d.format(DE) + ";-850,00;Autohaus Rate\n"
                + d.minusDays(10).format(DE) + ";-850,00;Miete September\n");
        JsonNode dup = getJson(u, "/api/transactions/duplicates");
        JsonNode pairs = dup.get("pairs");
        assertThat(pairs).hasSize(2);
        JsonNode cross = null;
        JsonNode same = null;
        for (JsonNode p : pairs) {
            if (p.get("kind").asText().equals("CROSS_SOURCE")) {
                cross = p;
            } else {
                same = p;
            }
        }
        assertThat(cross).isNotNull();
        assertThat(cross.get("first").get("source").asText()).isEqualTo("IMPORT");    // ältere zuerst
        assertThat(cross.get("second").get("description").asText()).isEqualTo("Miete Oktober");
        assertThat(cross.get("reason").asText()).contains("„konto.csv“").contains("„Girokonto“");
        assertThat(same).isNotNull();
        assertThat(same.get("first").get("amountCents").asLong()).isEqualTo(-420);
        assertThat(dup.get("explanation").asText()).contains("3 Tage");

        postJson(u, "/api/transactions/duplicates/dismiss", "{\"key\":\"" + same.get("key").asText() + "\"}", 204);
        postJson(u, "/api/transactions/duplicates/dismiss", "{\"key\":\"" + same.get("key").asText() + "\"}", 204);
        assertThat(getJson(u, "/api/transactions/duplicates").get("pairs")).hasSize(1);
        postJson(u, "/api/transactions/duplicates/dismiss", "{\"key\":\"DROP TABLE\"}", 400);
        postJson(u, "/api/transactions/duplicates/dismiss", "{\"key\":\"\"}", 400);
        // Umbuchungen zwischen eigenen Konten sind nie Duplikate
        long call = postJson(u, "/api/accounts", "{\"type\":\"CALL\"}", 201).get("id").asLong();
        postJson(u, "/api/accounts/move", "{\"fromAccountId\":" + u.accountId() + ",\"toAccountId\":" + call
                + ",\"amountCents\":5000}", 204);
        assertThat(getJson(u, "/api/transactions/duplicates").get("pairs")).hasSize(1);
        // andere Nutzer sehen nichts davon
        assertThat(getJson(registerUser("Anders"), "/api/transactions/duplicates").get("pairs")).isEmpty();
    }

    @Test
    void T101_englisch() throws Exception {
        TestUser u = registerUser("Ella");
        String body = mvc.perform(put("/api/accounts/" + u.accountId() + "/bookings/" + firstBooking(u, u.accountId())
                        + "/note").header("Authorization", u.bearer()).header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + "y".repeat(600) + "\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("The note may have at most 500 characters.");
        String dup = mvc.perform(get("/api/transactions/duplicates").header("Authorization", u.bearer())
                        .header("Accept-Language", "en"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(dup).contains("Check yourself");
    }
}
