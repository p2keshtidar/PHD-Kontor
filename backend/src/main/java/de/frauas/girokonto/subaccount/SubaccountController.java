package de.frauas.girokonto.subaccount;

import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unterkonten. Die Liste aller Konten (inkl. Unterkonten) liefert GET /api/accounts. */
@RestController
@RequestMapping("/api/subaccounts")
public class SubaccountController {

    private final SubaccountService service;

    public SubaccountController(SubaccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountDto> create(@AuthenticationPrincipal AuthUser auth,
                                             @Valid @RequestBody SubaccountRequests.Save req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), req));
    }

    @PutMapping("/{id}")
    public AccountDto rename(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                             @Valid @RequestBody SubaccountRequests.Save req) {
        return service.rename(auth.id(), id, req);
    }

    @PostMapping("/move")
    public ResponseEntity<Void> move(@AuthenticationPrincipal AuthUser auth,
                                     @Valid @RequestBody SubaccountRequests.Move req) {
        service.move(auth.id(), req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> close(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.close(auth.id(), id);
        return ResponseEntity.noContent().build();
    }
}
