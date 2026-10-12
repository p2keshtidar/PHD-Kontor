package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.frauas.girokonto.booking.BookingLineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** /K060/ Geteilte Unterkonten (T38 bis T40). */
class SharedSubaccountFlowTest extends FlowTestSupport {

    @Autowired BookingLineRepository bookingLines;

    private long createSub(TestUser owner, String name) throws Exception {
        return postJson(owner, "/api/subaccounts", "{\"name\":\"" + name + "\",\"icon\":\"home\"}", 201).get("id").asLong();
    }

    private void move(TestUser u, long from, long to, long cents, int expected) throws Exception {
        postJson(u, "/api/subaccounts/move",
                "{\"fromAccountId\":" + from + ",\"toAccountId\":" + to + ",\"amountCents\":" + cents + "}", expected);
    }

    private JsonNode account(TestUser u, long id) throws Exception {
        for (JsonNode a : getJson(u, "/api/accounts")) {
            if (a.get("id").asLong() == id) {
                return a;
            }
        }
        return null;
    }

    private void share(TestUser owner, long sub, TestUser member, int expected) throws Exception {
        postJson(owner, "/api/subaccounts/" + sub + "/members", "{\"recipient\":\"" + member.email() + "\"}", expected);
    }

    @Test
    void T38_mitgliederZahlenEinUndAus() throws Exception {
        TestUser mia = registerUser("Mia");
        TestUser nico = registerUser("Nico");
        long sub = createSub(mia, "WG-Kasse");
        share(mia, sub, nico, 200);

        JsonNode members = getJson(nico, "/api/subaccounts/" + sub + "/members");
        assertThat(members).hasSize(2);
        assertThat(members.get(0).get("owner").asBoolean()).isTrue();
        assertThat(members.get(1).get("you").asBoolean()).isTrue();

        JsonNode seen = account(nico, sub);
        assertThat(seen).isNotNull();
        assertThat(seen.get("shared").asBoolean()).isTrue();
        assertThat(seen.get("ownerName").asText()).startsWith("Mia");
        assertThat(account(mia, sub).get("memberCount").asInt()).isEqualTo(1);

        long nicoBefore = balance(nico);
        move(nico, nico.accountId(), sub, 3_000, 204);                      // Nico zahlt ein
        assertThat(account(mia, sub).get("balanceCents").asLong()).isEqualTo(3_000);
        assertThat(notificationTexts(mia)).anyMatch(s -> s.contains("Nico") && s.contains("eingezahlt in „WG-Kasse“"));

        move(mia, mia.accountId(), sub, 2_000, 204);                        // Mia zahlt ein
        move(nico, sub, nico.accountId(), 1_000, 204);                      // Nico nimmt 10 € heraus
        assertThat(balance(nico)).isEqualTo(nicoBefore - 2_000);
        assertThat(notificationTexts(mia)).anyMatch(s -> s.contains("ausgezahlt aus „WG-Kasse“"));

        // Nico sieht die Umsätze des geteilten Kontos
        JsonNode bookings = getJson(nico, "/api/accounts/" + sub + "/bookings");
        assertThat(bookings.get("items")).hasSize(3);
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void T39_rechteUndAustreten() throws Exception {
        TestUser olga = registerUser("Olga");
        TestUser paul = registerUser("Paul");
        TestUser fremd = registerUser("Fremd");
        long sub = createSub(olga, "Urlaub");

        share(olga, sub, olga, 400);                                         // sich selbst
        postJson(olga, "/api/subaccounts/" + sub + "/members", "{\"recipient\":\"keiner@example.org\"}", 404);
        share(olga, sub, paul, 200);
        share(olga, sub, paul, 409);                                         // doppelt
        share(paul, sub, fremd, 403);                                        // nur der Besitzer lädt ein

        // Fremde: kein Zugriff
        mvc.perform(get("/api/accounts/" + sub + "/bookings").header("Authorization", fremd.bearer()))
                .andExpect(status().isForbidden());
        move(fremd, fremd.accountId(), sub, 100, 403);

        // Mitglied: nicht umbenennen, nicht auflösen, niemanden entfernen
        mvc.perform(put("/api/subaccounts/" + sub).header("Authorization", paul.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Meins\"}")).andExpect(status().isForbidden());
        deletePath(paul, "/api/subaccounts/" + sub, 403);
        deletePath(paul, "/api/subaccounts/" + sub + "/members/" + olga.id(), 403);

        // austreten -> kein Zugriff mehr
        deletePath(paul, "/api/subaccounts/" + sub + "/members/" + paul.id(), 204);
        assertThat(account(paul, sub)).isNull();
        mvc.perform(get("/api/accounts/" + sub + "/bookings").header("Authorization", paul.bearer()))
                .andExpect(status().isForbidden());
        assertThat(notificationTexts(olga)).anyMatch(s -> s.contains("ausgetreten"));
    }

    @Test
    void T40_aufloesenZahltDemBesitzerAus() throws Exception {
        TestUser rita = registerUser("Rita");
        TestUser sam = registerUser("Sam");
        long sub = createSub(rita, "Geschenk");
        share(rita, sub, sam, 200);
        move(sam, sam.accountId(), sub, 5_000, 204);
        long ritaBefore = balance(rita);

        deletePath(rita, "/api/subaccounts/" + sub, 204);
        assertThat(balance(rita)).isEqualTo(ritaBefore + 5_000);
        assertThat(account(sam, sub)).isNull();
        assertThat(notificationTexts(sam)).anyMatch(s -> s.contains("„Geschenk“ wurde von Rita"));
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }
}
