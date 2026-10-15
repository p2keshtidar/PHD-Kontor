package de.frauas.girokonto.subaccount;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.subaccount.SubaccountCardService.PaymentResult;
import de.frauas.girokonto.subaccount.SubaccountCardService.View;
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

/** Demo-Karten für digitale Konten. */
@RestController
@RequestMapping("/api/subaccounts/{id}/card")
public class SubaccountCardController {

    public record LimitRequest(long dailyLimitCents) {}
    public record PaymentRequest(String merchant, long amountCents) {}

    private final SubaccountCardService service;

    public SubaccountCardController(SubaccountCardService service) {
        this.service = service;
    }

    @GetMapping
    public View view(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.view(auth.id(), id);
    }

    @PostMapping
    public ResponseEntity<View> create(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                       @RequestBody LimitRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), id, req.dailyLimitCents()));
    }

    @PutMapping("/limit")
    public View limit(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @RequestBody LimitRequest req) {
        return service.limit(auth.id(), id, req.dailyLimitCents());
    }

    @PostMapping("/lock")
    public View lock(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.lock(auth.id(), id, true);
    }

    @PostMapping("/unlock")
    public View unlock(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.lock(auth.id(), id, false);
    }

    @PostMapping("/payment")
    public PaymentResult pay(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @RequestBody PaymentRequest req) {
        return service.pay(auth.id(), id, req.merchant(), req.amountCents());
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.delete(auth.id(), id);
        return ResponseEntity.noContent().build();
    }
}
