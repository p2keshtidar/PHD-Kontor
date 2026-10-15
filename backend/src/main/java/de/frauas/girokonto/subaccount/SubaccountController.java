package de.frauas.girokonto.subaccount;

import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.auth.AuthUser;
import jakarta.validation.Valid;
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

/** Digitale Konten (Töpfe). Die Liste aller Konten (inkl. digitaler Konten) liefert GET /api/accounts. */
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

    // ------------------------------------------------------------------ /K060/ Teilen

    /** @param role FULL (Standard), DEPOSIT oder VIEW */
    public record AddMemberRequest(String recipient, String role) {}

    public record RoleRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Pattern(regexp = "FULL|DEPOSIT|VIEW", message = "Unbekannte Rolle.")
            String role) {}

    @GetMapping("/{id}/members")
    public java.util.List<SubaccountService.MemberDto> members(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.members(auth.id(), id);
    }

    @PostMapping("/{id}/members")
    public java.util.List<SubaccountService.MemberDto> addMember(@AuthenticationPrincipal AuthUser auth,
                                                                @PathVariable Long id,
                                                                @RequestBody AddMemberRequest r) {
        if (r.recipient() == null || r.recipient().isBlank()) {
            throw new de.frauas.girokonto.common.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "RECIPIENT_MISSING", "Bitte E-Mail, Telefonnummer oder IBAN eingeben.");
        }
        return service.addMember(auth.id(), id, r.recipient(), r.role());
    }

    /** Rolle eines Mitglieds ändern (nur Besitzer). */
    @PutMapping("/{id}/members/{userId}")
    public java.util.List<SubaccountService.MemberDto> setRole(@AuthenticationPrincipal AuthUser auth,
                                                              @PathVariable Long id, @PathVariable Long userId,
                                                              @Valid @RequestBody RoleRequest r) {
        return service.setRole(auth.id(), id, userId, r.role());
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                             @PathVariable Long userId) {
        service.removeMember(auth.id(), id, userId);
        return ResponseEntity.noContent().build();
    }
}
