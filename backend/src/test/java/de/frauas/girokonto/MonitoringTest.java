package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

/** M01–M02: Gesundheitsprüfung öffentlich, Kennzahlen nur für Administratoren, ohne sensible Daten. */
@AutoConfigureObservability   // in Tests sind Kennzahlen-Exporte sonst abgeschaltet
class MonitoringTest extends FlowTestSupport {

    @Test
    void M01_healthIstOeffentlichUndOhneDetails() throws Exception {
        String body = mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(body).contains("\"status\":\"UP\"").doesNotContain("postgres").doesNotContain("jdbc");
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void M02_kennzahlenNurFuerAdmin() throws Exception {
        TestUser u = registerUser("Metrik");
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header("Authorization", u.bearer())).andExpect(status().isForbidden());
        String admin = login("admin@projektbank.de", "admin12345");
        String metrics = mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(metrics).contains("girokonto_bookings_total").contains("type=\"INITIAL\"")
                .contains("http_server_requests");
        assertThat(metrics).doesNotContain(u.email()).doesNotContain(u.iban()).doesNotContain("Bearer");
    }
}
