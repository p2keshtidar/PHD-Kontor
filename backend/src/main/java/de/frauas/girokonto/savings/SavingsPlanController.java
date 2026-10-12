package de.frauas.girokonto.savings;

import de.frauas.girokonto.auth.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
public class SavingsPlanController {

    private final SavingsPlanService service;

    public SavingsPlanController(SavingsPlanService service) {
        this.service = service;
    }

    @PostMapping("/check")
    public PlanDtos.CheckResult check(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody PlanDtos.CheckRequest req) {
        return service.check(auth.id(), req);
    }

    @GetMapping
    public List<PlanDtos.Summary> list(@AuthenticationPrincipal AuthUser auth) {
        return service.list(auth.id());
    }

    @PostMapping
    public ResponseEntity<PlanDtos.Detail> create(@AuthenticationPrincipal AuthUser auth,
                                                  @Valid @RequestBody PlanDtos.CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), req));
    }

    @GetMapping("/{id}")
    public PlanDtos.Detail detail(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.detail(auth.id(), id);
    }

    @PutMapping("/{id}")
    public PlanDtos.Detail update(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                  @Valid @RequestBody PlanDtos.UpdateRequest req) {
        return service.update(auth.id(), id, req);
    }

    @PostMapping("/{id}/payout")
    public PlanDtos.Detail payout(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.payout(auth.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> close(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.close(auth.id(), id);
        return ResponseEntity.noContent().build();
    }
}
