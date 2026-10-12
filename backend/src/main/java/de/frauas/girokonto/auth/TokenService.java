package de.frauas.girokonto.auth;

import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import de.frauas.girokonto.user.UserDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gibt Token-Paare aus. Access-Token: kurzlebiges JWT. Refresh-Token: Zufallswert,
 * von dem nur der SHA-256-Hash in der Datenbank liegt. Jedes Refresh-Token ist nur einmal nutzbar (Rotation).
 */
@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokens;
    private final AppUserRepository users;
    private final Duration refreshValidity;
    private final Clock clock;

    public TokenService(JwtService jwtService, RefreshTokenRepository refreshTokens, AppUserRepository users,
                        AppProperties props) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.refreshValidity = Duration.ofDays(props.jwt().refreshTokenDays());
        // echte Zeit, unabhängig von der Simulationszeit (Demo-Modus)
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public AuthResponse issue(AppUser user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now(clock);
        refreshTokens.save(new RefreshToken(user.getId(), sha256(raw), now.plus(refreshValidity), now));
        return new AuthResponse(jwtService.createAccessToken(user), raw, UserDto.from(user));
    }

    @Transactional
    public AuthResponse rotate(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(sha256(rawRefreshToken))
                .filter(t -> !t.isRevoked())
                .filter(t -> t.getExpiresAt().isAfter(Instant.now(clock)))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Sitzung abgelaufen. Bitte erneut anmelden."));
        token.revoke();
        AppUser user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Sitzung abgelaufen. Bitte erneut anmelden."));
        return issue(user);
    }

    @Transactional
    public void revokeAll(Long userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
