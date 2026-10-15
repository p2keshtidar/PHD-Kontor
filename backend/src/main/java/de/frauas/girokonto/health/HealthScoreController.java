package de.frauas.girokonto.health;

import de.frauas.girokonto.auth.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Interner Finanz-Check (keine SCHUFA-Auskunft). */
@RestController
public class HealthScoreController {

    private final HealthScoreService service;

    public HealthScoreController(HealthScoreService service) {
        this.service = service;
    }

    @GetMapping("/api/health-score")
    public HealthScoreService.Score score(@AuthenticationPrincipal AuthUser auth) {
        return service.score(auth.id());
    }
}
