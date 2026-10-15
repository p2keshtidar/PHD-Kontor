package de.frauas.girokonto.creditcard;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.creditcard.CreditCardService.PaymentResult;
import de.frauas.girokonto.creditcard.CreditCardService.View;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Virtuelle Kreditkarte (Simulation mit Spielgeld). */
@RestController
@RequestMapping("/api/credit-card")
public class CreditCardController {

    public record LimitRequest(long limitCents) {}

    public record PaymentRequest(@Size(max = 100, message = "Der Händlername darf höchstens 100 Zeichen haben.") String merchant,
                                 @Positive(message = "Der Betrag muss größer als 0 sein.")
                                 @Max(value = 500_000, message = "Höchstens 5.000 € pro Zahlung.") long amountCents,
                                 boolean foreignCurrency) {}

    private final CreditCardService service;

    public CreditCardController(CreditCardService service) {
        this.service = service;
    }

    @GetMapping
    public View view(@AuthenticationPrincipal AuthUser auth) {
        return service.view(auth.id());
    }

    @PostMapping
    public ResponseEntity<View> create(@AuthenticationPrincipal AuthUser auth, @RequestBody LimitRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), req.limitCents()));
    }

    @PutMapping("/limit")
    public View limit(@AuthenticationPrincipal AuthUser auth, @RequestBody LimitRequest req) {
        return service.limit(auth.id(), req.limitCents());
    }

    @PostMapping("/lock")
    public View lock(@AuthenticationPrincipal AuthUser auth) {
        return service.lock(auth.id(), true);
    }

    @PostMapping("/unlock")
    public View unlock(@AuthenticationPrincipal AuthUser auth) {
        return service.lock(auth.id(), false);
    }

    /** Demo-Zahlung (Händler-Simulator). */
    @PostMapping("/payment")
    public PaymentResult pay(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody PaymentRequest req) {
        return service.pay(auth.id(), req.merchant(), req.amountCents(), req.foreignCurrency());
    }

    @DeleteMapping
    public View close(@AuthenticationPrincipal AuthUser auth) {
        return service.close(auth.id());
    }
}
