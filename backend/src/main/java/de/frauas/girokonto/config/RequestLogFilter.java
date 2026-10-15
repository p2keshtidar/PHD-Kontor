package de.frauas.girokonto.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Strukturierte Protokollzeile je API-Aufruf im Format {@code schlüssel=wert} (logfmt), z. B.
 * {@code event=request method=GET path=/api/accounts/{id}/bookings status=200 ms=14 requestId=3f9a…}.
 *
 * Bewusst ohne Query-Parameter, Header, Inhalte oder Nutzerdaten: Zahlen im Pfad werden zu {id}. Langsame Aufrufe
 * (ab {@code app.logging.slow-request-ms}) erscheinen als WARN mit {@code event=slow_request}. Die Request-ID steht
 * im Antwort-Header {@code X-Request-Id} und in allen Logzeilen der Anfrage (MDC), damit Fehler zuzuordnen sind.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestLogFilter extends OncePerRequestFilter {

    static final Logger LOG = LoggerFactory.getLogger("girokonto.request");
    static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern NUMBER_SEGMENT = Pattern.compile("/\\d+(?=/|$)");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final long slowMs;

    public RequestLogFilter(@Value("${app.logging.slow-request-ms:1000}") long slowMs) {
        this.slowMs = slowMs;
    }

    /** "/api/accounts/17/bookings" → "/api/accounts/{id}/bookings" */
    static String template(String path) {
        return NUMBER_SEGMENT.matcher(path).replaceAll("/{id}");
    }

    static String requestId(String incoming) {
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = requestId(request.getHeader(HEADER));
        MDC.put("requestId", id);
        response.setHeader(HEADER, id);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            String path = template(request.getRequestURI().substring(request.getContextPath().length()));
            if (ms >= slowMs) {
                LOG.warn("event=slow_request method={} path={} status={} ms={} requestId={}",
                        request.getMethod(), path, response.getStatus(), ms, id);
            } else {
                LOG.info("event=request method={} path={} status={} ms={} requestId={}",
                        request.getMethod(), path, response.getStatus(), ms, id);
            }
            MDC.remove("requestId");
        }
    }
}
