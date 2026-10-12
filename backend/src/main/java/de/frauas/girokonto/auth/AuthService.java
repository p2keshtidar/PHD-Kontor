package de.frauas.girokonto.auth;

import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.card.CardService;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import de.frauas.girokonto.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registrierung, Anmeldung, Token erneuern und Abmeldung (/F010/, /F020/). */
@Service
public class AuthService {

    public static final int MIN_AGE = 18;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final TokenService tokenService;
    private final CardService cardService;
    private final NotificationService notifications;
    private final AppProperties props;
    private final Clock clock;
    private final TwoFactorService twoFactor;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, AccountService accountService,
                       TokenService tokenService, CardService cardService, NotificationService notifications,
                       AppProperties props, Clock clock, TwoFactorService twoFactor) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
        this.tokenService = tokenService;
        this.cardService = cardService;
        this.notifications = notifications;
        this.props = props;
        this.clock = clock;
        this.twoFactor = twoFactor;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        String phone = UserService.normalizePhone(req.phone());

        if (Period.between(req.birthDate(), LocalDate.now(clock)).getYears() < MIN_AGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNDERAGE", "Ein Konto ist erst ab 18 Jahren möglich.");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Diese E-Mail ist bereits registriert.");
        }
        if (users.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_EXISTS", "Diese Telefonnummer wird bereits verwendet.");
        }

        AppUser user = users.save(new AppUser(email, passwordEncoder.encode(req.password()),
                req.firstName().trim(), req.lastName().trim(), phone, req.birthDate(), Instant.now(clock)));
        accountService.openMainAccount(user);
        cardService.getOrCreate(user.getId());
        notifications.send(user.getId(), NotificationType.WELCOME, "Willkommen bei der " + props.bank().name(),
                "Dein Konto ist eröffnet. Startguthaben: " + Money.format(props.startBalanceCents()), null);
        return tokenService.issue(user);
    }

    /**
     * noRollbackFor: auch wenn wir mit einer ApiException antworten, soll der erhöhte
     * Fehlversuch-Zähler gespeichert werden.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        AppUser user = users.findByEmail(normalizeEmail(req.email()))
                .orElseThrow(AuthService::invalidCredentials);
        Instant now = Instant.now(); // Sperrfrist in echter Zeit

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Zu viele Fehlversuche. Bitte in " + minutes + " Minute(n) erneut versuchen.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw failedAttempt(user, now, invalidCredentials());
        }
        // /K050/: zweiter Faktor
        if (user.isTotpEnabled()) {
            if (req.code() == null || req.code().isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "TOTP_REQUIRED",
                        "Bitte den 6-stelligen Code aus deiner Authenticator-App eingeben.");
            }
            if (!twoFactor.checkLoginCode(user, req.code())) {
                throw failedAttempt(user, now, new ApiException(HttpStatus.UNAUTHORIZED, "TOTP_INVALID",
                        "Der Code ist falsch oder abgelaufen."));
            }
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        return tokenService.issue(user);
    }

    public AuthResponse refresh(String refreshToken) {
        return tokenService.rotate(refreshToken);
    }

    /** Abmeldung auf allen Geräten: alle Refresh-Tokens sperren und alte Access-Tokens ungültig machen. */
    @Transactional
    public void logout(Long userId) {
        users.findById(userId).ifPresent(AppUser::increaseTokenVersion);
        tokenService.revokeAll(userId);
    }

    /** Fehlversuch zählen; nach zu vielen Versuchen sperren (/F030/). */
    private ApiException failedAttempt(AppUser user, Instant now, ApiException error) {
        int failed = user.getFailedLogins() + 1;
        if (failed >= props.login().maxFailedAttempts()) {
            user.setLockedUntil(now.plus(Duration.ofMinutes(props.login().lockMinutes())));
            user.setFailedLogins(0);
            return new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Zu viele Fehlversuche. Die Anmeldung ist für " + props.login().lockMinutes()
                            + " Minuten gesperrt.");
        }
        user.setFailedLogins(failed);
        return error;
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-Mail oder Passwort ist falsch.");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
