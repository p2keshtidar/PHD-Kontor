package de.frauas.girokonto.allocation;

import de.frauas.girokonto.allocation.AllocationDtos.AccountAllocations;
import de.frauas.girokonto.allocation.AllocationDtos.CreateRuleRequest;
import de.frauas.girokonto.allocation.AllocationDtos.EarmarkRequest;
import de.frauas.girokonto.allocation.AllocationDtos.ReleaseRequest;
import de.frauas.girokonto.allocation.AllocationDtos.RuleDto;
import de.frauas.girokonto.allocation.AllocationDtos.RunDto;
import de.frauas.girokonto.allocation.AllocationDtos.SourceDto;
import de.frauas.girokonto.allocation.AllocationDtos.UpdateRuleRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sparregeln und Geldzuweisungen für digitale Konten. */
@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final AllocationService service;

    public AllocationController(AllocationService service) {
        this.service = service;
    }

    /** Finanzierungsquellen: Girokonto (Umbuchung, Simulation) und verbundene Bankkonten (nur Vormerkung). */
    @GetMapping("/sources")
    public List<SourceDto> sources(@AuthenticationPrincipal AuthUser auth) {
        return service.sources(auth.id());
    }

    /** Alles zu einem digitalen Konto: Regeln, Vorgänge, anstehende Termine, Vormerkungen, Prognose. */
    @GetMapping("/accounts/{accountId}")
    public AccountAllocations forAccount(@AuthenticationPrincipal AuthUser auth, @PathVariable Long accountId) {
        return service.forAccount(auth.id(), accountId);
    }

    @GetMapping("/rules")
    public List<RuleDto> rules(@AuthenticationPrincipal AuthUser auth) {
        return service.rules(auth.id());
    }

    @PostMapping("/rules")
    public ResponseEntity<RuleDto> create(@AuthenticationPrincipal AuthUser auth,
                                          @Valid @RequestBody CreateRuleRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), r));
    }

    @PutMapping("/rules/{id}")
    public RuleDto update(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                          @Valid @RequestBody UpdateRuleRequest r) {
        return service.update(auth.id(), id, r);
    }

    @PostMapping("/rules/{id}/pause")
    public RuleDto pause(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.pause(auth.id(), id);
    }

    @PostMapping("/rules/{id}/resume")
    public RuleDto resume(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.resume(auth.id(), id);
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> end(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.end(auth.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Eigene Vorgänge (ausgeführt, vorgemerkt, ausstehend, Wiederholung geplant, fehlgeschlagen). */
    @GetMapping("/runs")
    public List<RunDto> runs(@AuthenticationPrincipal AuthUser auth, @RequestParam(required = false) Long accountId,
                             @RequestParam(defaultValue = "50") int limit) {
        return service.runs(auth.id(), accountId, limit);
    }

    @PostMapping("/runs/{id}/retry")
    public RunDto retry(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.retry(auth.id(), id);
    }

    @PostMapping("/earmarks")
    public ResponseEntity<RunDto> earmark(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody EarmarkRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.earmark(auth.id(), r));
    }

    @PostMapping("/earmarks/release")
    public RunDto release(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody ReleaseRequest r) {
        return service.release(auth.id(), r);
    }
}
