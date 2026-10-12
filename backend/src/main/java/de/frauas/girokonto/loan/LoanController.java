package de.frauas.girokonto.loan;

import de.frauas.girokonto.auth.AuthUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService service;
    private final Clock clock;

    public LoanController(LoanService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/affordability")
    public LoanDtos.Affordability affordability(@AuthenticationPrincipal AuthUser auth) {
        return service.affordability(auth.id());
    }

    @PostMapping("/offers")
    public LoanDtos.OfferResult offers(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody LoanDtos.OfferRequest r) {
        return service.offers(auth.id(), r.purpose(), r.principalCents(),
                r.equityCents() == null ? 0 : r.equityCents(), r.termMonths());
    }

    /** Tilgungsplan für ein Angebot (/F1040/); Start = heute. */
    @PostMapping("/schedule")
    public List<LoanDtos.ScheduleRow> schedule(@Valid @RequestBody LoanDtos.ScheduleRequest r) {
        return service.schedule(r.principalCents(), r.interestRate(), r.termMonths(), LocalDate.now(clock), 0);
    }

    @GetMapping
    public List<LoanDtos.LoanDto> list(@AuthenticationPrincipal AuthUser auth) {
        return service.list(auth.id());
    }

    @PostMapping
    public ResponseEntity<LoanDtos.LoanDetail> create(@AuthenticationPrincipal AuthUser auth,
                                                      @Valid @RequestBody LoanDtos.CreateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), r));
    }

    @GetMapping("/{id}")
    public LoanDtos.LoanDetail detail(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.detail(auth.id(), id);
    }
}
