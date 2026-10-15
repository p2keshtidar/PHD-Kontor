package de.frauas.girokonto.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Verhindert, dass der Server im Betrieb (APP_ENV=production) mit den Entwicklungs-Schlüsseln aus
 * application.yml startet. In der Entwicklung gibt es nur eine Warnung.
 */
@Component
public class SecretsGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecretsGuard.class);
    static final String DEV_JWT = "1ne2R3UORTd0dfO73zFIRmS24V7dyK6l18u2fM8IyCPSbV4eztOqvRPT69Yo7u9z";
    static final String DEV_CARD_KEY = "q3Jm0QbN8y4k9tXc2Vh7LpZr5Wd1Fs6Ge0Ua3Ti8Ko4=";
    static final String DEV_ADMIN_PASSWORD = "admin12345";

    static final String DEV_DB_PASSWORD = "girokonto";

    private final AppProperties props;
    private final String environment;
    private final String dbPassword;

    public SecretsGuard(AppProperties props, @Value("${app.environment:development}") String environment,
                        @Value("${spring.datasource.password:}") String dbPassword) {
        this.props = props;
        this.environment = environment;
        this.dbPassword = dbPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        check(environment, props);
        checkDatabase(environment, dbPassword);
    }

    /** Im Betrieb kein leeres oder das Entwicklungs-Passwort der Datenbank. */
    static void checkDatabase(String environment, String dbPassword) {
        if ("production".equalsIgnoreCase(environment)
                && (dbPassword == null || dbPassword.isBlank() || DEV_DB_PASSWORD.equals(dbPassword))) {
            throw new IllegalStateException("APP_ENV=production, aber DB_PASSWORD ist leer oder der Entwicklungswert.");
        }
    }

    static List<String> insecure(AppProperties p) {
        List<String> found = new ArrayList<>();
        if (p.jwt() != null && DEV_JWT.equals(p.jwt().secret())) {
            found.add("JWT_SECRET");
        }
        if (p.card() != null && DEV_CARD_KEY.equals(p.card().encryptionKey())) {
            found.add("CARD_KEY");
        }
        if (p.admin() != null && DEV_ADMIN_PASSWORD.equals(p.admin().password())) {
            found.add("ADMIN_PASSWORD");
        }
        return found;
    }

    static void check(String environment, AppProperties p) {
        List<String> found = insecure(p);
        if (found.isEmpty()) {
            return;
        }
        if ("production".equalsIgnoreCase(environment)) {
            throw new IllegalStateException("APP_ENV=production, aber Entwicklungs-Schlüssel gesetzt: " + found
                    + ". Bitte als Umgebungsvariablen setzen.");
        }
        log.warn("Entwicklungs-Schlüssel aktiv ({}). Für den Betrieb APP_ENV=production und eigene Werte setzen.", found);
    }
}
