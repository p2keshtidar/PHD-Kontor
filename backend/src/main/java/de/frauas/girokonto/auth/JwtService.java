package de.frauas.girokonto.auth;

import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Erstellt und prüft Access-Tokens (JWT, 15 Minuten gültig). */
@Service
public class JwtService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_VERSION = "ver";

    private final SecretKey key;
    private final Duration validity;
    private final Clock clock;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.jwt().secret()));
        this.validity = Duration.ofMinutes(props.jwt().accessTokenMinutes());
        // Sicherheitsfristen laufen immer in echter Zeit, auch wenn die Simulationszeit vorgespult wird
        this.clock = Clock.systemUTC();
    }

    public String createAccessToken(AppUser user) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_VERSION, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException wenn das Token ungültig, manipuliert oder abgelaufen ist */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
