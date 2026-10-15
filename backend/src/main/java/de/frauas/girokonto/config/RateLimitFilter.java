package de.frauas.girokonto.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Antwortet mit 429, wenn zu viele Anfragen in einer Minute kommen. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final RateLimiter limiter;

    public RateLimitFilter(RateLimitProperties props) {
        this.props = props;
        this.limiter = new RateLimiter(Clock.systemUTC());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return props == null || !props.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean auth = uri.startsWith("/api/auth/") || uri.startsWith("/api/me/2fa");
        String key;
        int limit;
        if (auth) {
            key = "auth:" + request.getRemoteAddr();
            limit = props.authPerMinute();
        } else {
            String header = request.getHeader("Authorization");
            key = "api:" + (header != null ? hash(header) : request.getRemoteAddr());
            limit = props.apiPerMinute();
        }
        long retry = limiter.tryAcquire(key, limit);
        if (retry > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retry));
            response.setContentType("application/json;charset=UTF-8");
            String msg = de.frauas.girokonto.i18n.ServerTexts.wantsEnglish(request.getHeader("Accept-Language"))
                    ? "Too many requests. Please try again in " + retry + " seconds."
                    : "Zu viele Anfragen. Bitte in " + retry + " Sekunden erneut versuchen.";
            response.getOutputStream().write(("{\"code\":\"RATE_LIMITED\",\"message\":\"" + msg
                    + "\",\"fieldErrors\":{}}").getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }

    /** Token nicht im Speicher halten, nur einen Hash davon. */
    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
