package de.frauas.girokonto;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Basis für alle Integrationstests: eine echte PostgreSQL-Datenbank in Docker (Testcontainers),
 * die für alle Testklassen nur einmal gestartet wird.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private static final AtomicLong PHONE_COUNTER = new AtomicLong(System.nanoTime() % 1_000_000L);

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    /** Ein registrierter Testnutzer mit Token und Daten seines Girokontos. */
    public record TestUser(Long id, String email, String phone, String token, Long accountId, String iban) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected static String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.org";
    }

    protected static String uniquePhone() {
        return "0170" + String.format("%07d", PHONE_COUNTER.incrementAndGet() % 10_000_000L);
    }

    protected String registerBody(String email, String phone, LocalDate birthDate) {
        return """
                {"firstName":"Max","lastName":"Muster","email":"%s","phone":"%s",
                 "birthDate":"%s","password":"geheim12345"}
                """.formatted(email, phone, birthDate);
    }

    protected TestUser registerUser(String firstName) throws Exception {
        String email = uniqueEmail();
        String phone = uniquePhone();
        String body = registerBody(email, phone, LocalDate.now().minusYears(25)).replace("\"Max\"", "\"" + firstName + "\"");
        JsonNode auth = json.readTree(mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String token = auth.get("accessToken").asText();
        JsonNode accounts = json.readTree(mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return new TestUser(auth.get("user").get("id").asLong(), email, phone, token,
                accounts.get(0).get("id").asLong(), accounts.get(0).get("iban").asText());
    }

    protected long balance(TestUser u) throws Exception {
        JsonNode accounts = json.readTree(mvc.perform(get("/api/accounts").header("Authorization", u.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return accounts.get(0).get("balanceCents").asLong();
    }
}
