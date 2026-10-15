package de.frauas.girokonto.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** P01–P03: strukturierte Logzeile je Aufruf ohne sensible Daten; Request-ID; API-Version-Pfade. */
@ExtendWith(OutputCaptureExtension.class)
class RequestLogFilterTest {

    @Test
    void P01_logzeileOhneQueryUndMitPlatzhaltern(CapturedOutput out) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/accounts/42/bookings");
        req.setQueryString("q=geheime-suche");
        req.addHeader("Authorization", "Bearer abc.def.ghi");
        MockHttpServletResponse res = new MockHttpServletResponse();
        new RequestLogFilter(0).doFilter(req, res, new MockFilterChain());
        assertThat(out.getOut()).contains("event=slow_request method=GET path=/api/accounts/{id}/bookings status=200")
                .doesNotContain("geheime-suche").doesNotContain("abc.def.ghi").doesNotContain("/42/");
        assertThat(res.getHeader("X-Request-Id")).matches("[0-9a-f]{16}");
    }

    @Test
    void P02_requestIdNurWennUnbedenklich() {
        assertThat(RequestLogFilter.requestId("abc-123")).isEqualTo("abc-123");
        assertThat(RequestLogFilter.requestId("x\nInjected: 1")).matches("[0-9a-f]{16}");
        assertThat(RequestLogFilter.requestId(null)).matches("[0-9a-f]{16}");
        assertThat(RequestLogFilter.template("/api/groups/7/expenses/1234")).isEqualTo("/api/groups/{id}/expenses/{id}");
    }

    @Test
    void P03_versionsPfade() {
        assertThat(ApiVersionFilter.unversioned("/api/v1/accounts")).isEqualTo("/api/accounts");
        assertThat(ApiVersionFilter.unversioned("/api/v1")).isEqualTo("/api");
        assertThat(ApiVersionFilter.unversioned("/api/v10/accounts")).isEqualTo("/api/v10/accounts");
        assertThat(ApiVersionFilter.unversioned("/api/accounts")).isEqualTo("/api/accounts");
    }
}
