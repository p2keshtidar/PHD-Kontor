package de.frauas.girokonto.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.frauas.girokonto.IntegrationTestBase;
import de.frauas.girokonto.booking.BookingLineRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Testfälle T03, T04, T05, T14 und weitere Prüfungen aus /F210/ bis /F250/. */
class TransferFlowTest extends IntegrationTestBase {

    @Autowired BookingLineRepository bookingLines;

    private ResultActions transfer(TestUser from, String recipient, long cents, String key) throws Exception {
        return mvc.perform(post("/api/transfers")
                .header("Authorization", from.bearer())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"" + recipient + "\",\"amountCents\":" + cents
                        + ",\"description\":\"Pizza\"}"));
    }

    @Test
    void T03_ueberweisungPerIbanBuchtBeiSenderUndEmpfaenger() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");

        transfer(a, b.iban(), 2500, UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientName").value("Ben Muster"))
                .andExpect(jsonPath("$.newBalanceCents").value(97500));

        assertThat(balance(a)).isEqualTo(97500);
        assertThat(balance(b)).isEqualTo(102500);
        assertThat(bookingLines.totalOfAllLines()).isZero();
    }

    @Test
    void ueberweisungPerEmailUndTelefon() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");

        transfer(a, b.email().toUpperCase(), 100, UUID.randomUUID().toString()).andExpect(status().isCreated());
        // Telefonnummer im internationalen Format wird erkannt
        String international = "+49 " + b.phone().substring(1);
        transfer(a, international, 100, UUID.randomUUID().toString()).andExpect(status().isCreated());

        assertThat(balance(b)).isEqualTo(100200);
    }

    @Test
    void T04_nichtGenugGuthaben() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        transfer(a, b.iban(), 100001, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(balance(a)).isEqualTo(100000);
    }

    @Test
    void T05_gleicherSchluesselBuchtNurEinmal() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        String key = UUID.randomUUID().toString();

        transfer(a, b.iban(), 1000, key).andExpect(status().isCreated());
        transfer(a, b.iban(), 1000, key).andExpect(status().isCreated());

        assertThat(balance(a)).isEqualTo(99000);
        assertThat(balance(b)).isEqualTo(101000);
    }

    @Test
    void ohneIdempotencyKeyAbgelehnt() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        mvc.perform(post("/api/transfers").header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"" + b.iban() + "\",\"amountCents\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"));
    }

    @Test
    void anSichSelbstNichtMoeglich() throws Exception {
        TestUser a = registerUser("Anna");
        transfer(a, a.iban(), 100, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER"));
    }

    @Test
    void unbekannterEmpfaenger() throws Exception {
        TestUser a = registerUser("Anna");
        transfer(a, "niemand@example.org", 100, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"));
    }

    @Test
    void betragNullOderNegativ() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        transfer(a, b.iban(), 0, UUID.randomUUID().toString()).andExpect(status().isBadRequest());
        transfer(a, b.iban(), -500, UUID.randomUUID().toString()).andExpect(status().isBadRequest());
    }

    @Test
    void tageslimitVon5000Euro() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        TestUser c = registerUser("Clara");
        // Anna bekommt erst genug Geld: Ben und Clara überweisen je 1.000 €, Anna hat dann 3.000 €
        transfer(b, a.iban(), 100000, UUID.randomUUID().toString()).andExpect(status().isCreated());
        transfer(c, a.iban(), 100000, UUID.randomUUID().toString()).andExpect(status().isCreated());
        assertThat(balance(a)).isEqualTo(300000);

        // Das Limit zählt nur Ausgänge des Tages; 3.000 € sind erlaubt
        transfer(a, b.iban(), 300000, UUID.randomUUID().toString()).andExpect(status().isCreated());
        // B schickt 2.500 € zurück, Anna hätte genug, aber 3.000 + 2.500 > 5.000
        transfer(b, a.iban(), 250000, UUID.randomUUID().toString()).andExpect(status().isCreated());
        transfer(a, b.iban(), 250000, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
    }

    @Test
    void pruefenZeigtNameOhneZuBuchen() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        mvc.perform(post("/api/transfers/check").header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"" + b.iban() + "\",\"amountCents\":1234}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Ben Muster"))
                .andExpect(jsonPath("$.description").value("Überweisung"));
        assertThat(balance(a)).isEqualTo(100000);
    }

    @Test
    void umsaetzeMitFilterUndSuche() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        transfer(a, b.iban(), 1500, UUID.randomUUID().toString()).andExpect(status().isCreated());

        String path = "/api/accounts/" + a.accountId() + "/bookings";
        mvc.perform(get(path).header("Authorization", a.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].amountCents").value(-1500))
                .andExpect(jsonPath("$.items[0].counterpartyName").value("Ben Muster"))
                .andExpect(jsonPath("$.items[1].type").value("INITIAL"));

        mvc.perform(get(path).param("direction", "IN").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amountCents").value(100000));

        mvc.perform(get(path).param("q", "pizz").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(path).param("q", "muster").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(path).param("minCents", "50000").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(path).param("size", "1").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void T14_fremdeUmsaetzeVerboten() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        mvc.perform(get("/api/accounts/" + b.accountId() + "/bookings").header("Authorization", a.bearer()))
                .andExpect(status().isForbidden());
    }
}
