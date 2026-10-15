package de.frauas.girokonto.dashboard;

import de.frauas.girokonto.auth.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Zentrales Finanz-Dashboard. */
@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/dashboard")
    public DashboardDtos.Dashboard dashboard(@AuthenticationPrincipal AuthUser auth) {
        return service.dashboard(auth.id());
    }
}
