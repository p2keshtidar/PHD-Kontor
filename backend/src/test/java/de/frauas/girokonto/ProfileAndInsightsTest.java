package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Profil, Passwort, Vermögen und Kategorien (P01–P04). */
class ProfileAndInsightsTest extends FlowTestSupport {

    @Test
    void P01_profilAendern() throws Exception {
        TestUser u = registerUser("Profil");
        TestUser other = registerUser("Belegt");
        JsonNode me = putJson(u, "/api/me", """
                {"firstName":"Erika","lastName":"Mustermann-Schmidt","phone":"+49 151 98765432"}
                """, 200);
        assertThat(me.get("firstName").asText()).isEqualTo("Erika");
        assertThat(me.get("phone").asText()).isEqualTo("015198765432");     // vereinheitlicht
        putJson(u, "/api/me", "{\"firstName\":\"\",\"lastName\":\"X\",\"phone\":\"0151 1\"}", 400);
        putJson(u, "/api/me", "{\"firstName\":\"A\",\"lastName\":\"B\",\"phone\":\"" + other.phone() + "\"}", 409);
        // Empfänger findet mich unter der neuen Nummer
        JsonNode check = postJson(other, "/api/transfers/check",
                "{\"recipient\":\"0151 98765432\",\"amountCents\":100}", 200);
        assertThat(check.get("recipientName").asText()).isEqualTo("Erika Mustermann-Schmidt");
    }

    @Test
    void P02_passwortAendern() throws Exception {
        TestUser u = registerUser("Passwort");
        putJson(u, "/api/me/password", "{\"oldPassword\":\"falsch\",\"newPassword\":\"neuesPasswort1\"}", 400);
        putJson(u, "/api/me/password", "{\"oldPassword\":\"geheim12345\",\"newPassword\":\"kurz\"}", 400);
        putJson(u, "/api/me/password", "{\"oldPassword\":\"geheim12345\",\"newPassword\":\"neuesPasswort1\"}", 204);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.email() + "\",\"password\":\"geheim12345\"}")).andExpect(status().isUnauthorized());
        assertThat(login(u.email(), "neuesPasswort1")).isNotBlank();
    }

    @Test
    void P03_gesamtvermoegen() throws Exception {
        TestUser u = userWithDemo("Vermoegen");
        long main = balance(u);
        long sub = postJson(u, "/api/subaccounts", "{\"name\":\"Topf\"}", 201).get("id").asLong();
        postJson(u, "/api/subaccounts/move",
                "{\"fromAccountId\":" + u.accountId() + ",\"toAccountId\":" + sub + ",\"amountCents\":5000}", 204);
        JsonNode nw = getJson(u, "/api/insights/net-worth");
        assertThat(nw.get("totalCents").asLong()).isEqualTo(main);           // Umbuchung ändert das Vermögen nicht
        postJson(u, "/api/loans", """
                {"bank":"NORDLICHT","purpose":"AUTO","principalCents":500000,"equityCents":0,"termMonths":24}
                """, 201);
        JsonNode after = getJson(u, "/api/insights/net-worth");
        assertThat(after.get("loansCents").asLong()).isEqualTo(-500_000);
        assertThat(after.get("totalCents").asLong()).isEqualTo(main);        // Kredit: +Geld, −Schulden
    }

    @Test
    void P04_kategorienZumAuswaehlen() throws Exception {
        TestUser u = registerUser("Kategorie");
        JsonNode cats = getJson(u, "/api/insights/categories");
        assertThat(cats.findValuesAsText("label")).contains("Lebensmittel", "Wohnen", "Gehalt")
                .doesNotContain("Startguthaben", "Umbuchung", "Sondertilgung");
    }
}
