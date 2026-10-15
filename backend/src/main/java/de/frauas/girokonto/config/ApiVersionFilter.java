package de.frauas.girokonto.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API-Versionierung: Version 1 ist unter {@code /api/v1/…} erreichbar; {@code /api/…} bleibt als Kurzform für v1
 * bestehen (die App nutzt sie). Jede API-Antwort trägt den Header {@code API-Version}. Inkompatible Änderungen
 * kommen später unter {@code /api/v2/…}, v1 bleibt dann parallel erhalten.
 *
 * Läuft vor allen anderen Filtern (auch Sicherheit und Rate-Limit), damit beide Formen gleich behandelt werden.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiVersionFilter extends OncePerRequestFilter {

    public static final String CURRENT = "1";
    public static final String HEADER = "API-Version";
    private static final String V1 = "/api/v1";

    /** "/api/v1/accounts" → "/api/accounts"; alles andere bleibt. */
    static String unversioned(String path) {
        if (path.equals(V1) || path.startsWith(V1 + "/")) {
            return "/api" + path.substring(V1.length());
        }
        return path;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String context = request.getContextPath();
        String path = request.getRequestURI().substring(context.length());
        if (path.startsWith("/api/")) {
            response.setHeader(HEADER, CURRENT);
        }
        String target = unversioned(path);
        if (target.equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                return context + target;
            }

            @Override
            public StringBuffer getRequestURL() {
                StringBuffer url = new StringBuffer();
                url.append(getScheme()).append("://").append(getServerName());
                int port = getServerPort();
                if (port > 0 && !(("http".equals(getScheme()) && port == 80) || ("https".equals(getScheme()) && port == 443))) {
                    url.append(':').append(port);
                }
                return url.append(context).append(target);
            }

            @Override
            public String getServletPath() {
                String sp = super.getServletPath();
                return sp == null ? null : unversioned(sp);
            }
        }, response);
    }
}
