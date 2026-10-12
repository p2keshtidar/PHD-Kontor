package de.frauas.girokonto.auth;

import de.frauas.girokonto.card.CardCrypto;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * /K050/ Zwei-Faktor-Anmeldung. Der Schlüssel liegt verschlüsselt in der Datenbank (AES-GCM wie die
 * Kartennummern). Codes werden in ECHTER Zeit geprüft – die Simulationsuhr spielt hier keine Rolle,
 * weil die Authenticator-App auf dem Handy die echte Uhrzeit benutzt.
 */
@Service
public class TwoFactorService {

    public record SetupDto(String secret, String otpauthUri) {}

    private final AppUserRepository users;
    private final CardCrypto crypto;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();
    private final Clock realClock = Clock.systemUTC();

    public TwoFactorService(AppUserRepository users, CardCrypto crypto, PasswordEncoder passwordEncoder,
                            NotificationService notifications, AppProperties props) {
        this.users = users;
        this.crypto = crypto;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
        this.props = props;
    }

    /** Schritt 1: neuen Schlüssel erzeugen (noch nicht aktiv). */
    @Transactional
    public SetupDto setup(Long userId) {
        AppUser u = user(userId);
        if (u.isTotpEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "TOTP_ALREADY_ENABLED", "Die Zwei-Faktor-Anmeldung ist schon aktiv.");
        }
        String secret = Totp.newSecret(random);
        u.setTotpPendingSecret(crypto.encrypt(secret));
        return new SetupDto(secret, Totp.otpauthUri(props.bank().name(), u.getEmail(), secret));
    }

    /** Schritt 2: mit einem Code aus der App bestätigen → aktiv. */
    @Transactional
    public void enable(Long userId, String code) {
        AppUser u = user(userId);
        if (u.getTotpPendingSecret() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOTP_NOT_SET_UP", "Bitte zuerst die Einrichtung starten.");
        }
        String secret = crypto.decrypt(u.getTotpPendingSecret());
        long step = Totp.verify(secret, code, realClock.instant(), null);
        if (step < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOTP_INVALID",
                    "Der Code stimmt nicht. Prüfe, ob die Uhrzeit auf dem Handy stimmt.");
        }
        u.setTotpSecret(u.getTotpPendingSecret());
        u.setTotpPendingSecret(null);
        u.setTotpEnabled(true);
        u.setTotpLastStep(step);
        notifications.send(userId, NotificationType.INFO, "Zwei-Faktor-Anmeldung aktiviert",
                "Bei der Anmeldung brauchst du ab jetzt zusätzlich den Code aus deiner Authenticator-App.", null);
    }

    /** Ausschalten: Passwort und aktueller Code nötig. */
    @Transactional
    public void disable(Long userId, String password, String code) {
        AppUser u = user(userId);
        if (!u.isTotpEnabled()) {
            return;
        }
        if (password == null || !passwordEncoder.matches(password, u.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Das Passwort ist falsch.");
        }
        if (!checkLoginCode(u, code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOTP_INVALID", "Der Code stimmt nicht.");
        }
        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        u.setTotpLastStep(null);
        notifications.send(userId, NotificationType.INFO, "Zwei-Faktor-Anmeldung ausgeschaltet",
                "Die Anmeldung braucht jetzt nur noch E-Mail und Passwort.", null);
    }

    /** Für den Login: Code prüfen und als benutzt merken. */
    public boolean checkLoginCode(AppUser u, String code) {
        long step = Totp.verify(crypto.decrypt(u.getTotpSecret()), code, realClock.instant(), u.getTotpLastStep());
        if (step < 0) {
            return false;
        }
        u.setTotpLastStep(step);
        return true;
    }

    private AppUser user(Long id) {
        return users.findById(id).orElseThrow();
    }
}
