package de.frauas.girokonto.planner;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.planner.PlannerService.PlannerRequest;
import de.frauas.girokonto.planner.PlannerService.PlannerResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Zentraler Planer: Ansparen, Finanzieren oder beides für Reise, Auto, Immobilie. */
@RestController
public class PlannerController {

    private final PlannerService service;

    public PlannerController(PlannerService service) {
        this.service = service;
    }

    @PostMapping("/api/planner/compare")
    public PlannerResult compare(@AuthenticationPrincipal AuthUser auth, @RequestBody PlannerRequest req) {
        return service.compare(auth.id(), req);
    }
}
