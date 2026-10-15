package de.frauas.girokonto.loan;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.common.ApiException;
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

    /** Schritt 1: Vergleich – unverbindliche Rechenbeispiele aller Anbieter mit Filtern. */
    @PostMapping("/offers")
    public LoanDtos.OfferResult offers(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody LoanDtos.OfferRequest r) {
        CreditClass cls = CreditClass.parse(r.creditClass());
        if (cls == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDIT_CLASS", "Bonität: sehr gut, gut, mittel oder schwach.");
        }
        return service.offers(auth.id(), r.purpose(), r.principalCents(),
                r.equityCents() == null ? 0 : r.equityCents(), r.termMonths(), cls, r.maxMonthlyRateCents(),
                r.maxTotalCents(), Boolean.TRUE.equals(r.onlyEligible()));
    }

    /** Anbieter und ihr Zustand (fiktive Banken, Bundesbank-Referenz, echte Angebote). */
    @GetMapping("/providers")
    public List<LoanDtos.ProviderDto> providers() {
        return service.providerStatus();
    }

    /** Schritt 2: unverbindliche Kreditanfrage. */
    @PostMapping("/applications")
    public ResponseEntity<LoanDtos.ApplicationDto> request(@AuthenticationPrincipal AuthUser auth,
                                                           @Valid @RequestBody LoanDtos.ApplicationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(auth.id(), r));
    }

    @GetMapping("/applications")
    public List<LoanDtos.ApplicationDto> applications(@AuthenticationPrincipal AuthUser auth) {
        return service.applications(auth.id());
    }

    @GetMapping("/applications/{id}")
    public LoanDtos.ApplicationDto application(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.application(auth.id(), id);
    }

    @PostMapping("/applications/{id}/withdraw")
    public LoanDtos.ApplicationDto withdraw(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.withdraw(auth.id(), id);
    }

    /** Schritt 3: Vertrag abschließen (Simulation) – noch keine Auszahlung. */
    @PostMapping("/applications/{id}/accept")
    public ResponseEntity<LoanDtos.LoanDetail> accept(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                                      @RequestBody(required = false) LoanDtos.AcceptRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.accept(auth.id(), id, r == null ? null : r.confirm()));
    }

    /** Schritt 4: Auszahlung aufs Girokonto. */
    @PostMapping("/{id}/payout")
    public LoanDtos.LoanDetail payout(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.payout(auth.id(), id);
    }

    /** Vertrag vor der Auszahlung widerrufen. */
    @PostMapping("/{id}/cancel")
    public LoanDtos.LoanDetail cancel(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.cancelContract(auth.id(), id);
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

    /** Abkürzung (Demo): Anfrage, Vertrag und Auszahlung nacheinander. Die App nutzt die getrennten Schritte. */
    @PostMapping
    public ResponseEntity<LoanDtos.LoanDetail> create(@AuthenticationPrincipal AuthUser auth,
                                                      @Valid @RequestBody LoanDtos.CreateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), r));
    }

    @GetMapping("/{id}")
    public LoanDtos.LoanDetail detail(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.detail(auth.id(), id);
    }

    /** /K020/: Vorschau einer Sondertilgung */
    @PostMapping("/{id}/extra-payment/preview")
    public LoanDtos.ExtraPreview previewExtra(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                              @Valid @RequestBody LoanDtos.ExtraPaymentRequest r) {
        return service.previewExtra(auth.id(), id, r.amountCents());
    }

    /** /K020/: Sondertilgung buchen */
    @PostMapping("/{id}/extra-payment")
    public LoanDtos.LoanDetail extraPayment(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                            @Valid @RequestBody LoanDtos.ExtraPaymentRequest r) {
        return service.extraPayment(auth.id(), id, r.amountCents());
    }
}
