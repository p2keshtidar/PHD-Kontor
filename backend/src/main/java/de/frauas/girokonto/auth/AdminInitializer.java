package de.frauas.girokonto.auth;

import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.user.AppUserRepository;
import de.frauas.girokonto.user.Role;
import java.time.LocalDate;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Legt beim ersten Start das Administrator-Konto an (für den Demo-Modus /F950/). */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AppUserRepository users;
    private final AuthService authService;
    private final AppProperties props;

    public AdminInitializer(AppUserRepository users, AuthService authService, AppProperties props) {
        this.users = users;
        this.authService = authService;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = props.admin().email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            return;
        }
        authService.register(new RegisterRequest("Admin", "Projektbank", email, "0100000000",
                LocalDate.of(1990, 1, 1), props.admin().password()));
        users.findByEmail(email).ifPresent(u -> u.setRole(Role.ADMIN));
        log.info("Administrator {} angelegt", email);
    }
}
