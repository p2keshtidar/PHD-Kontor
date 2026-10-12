package de.frauas.girokonto.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /K050/ Zwei-Faktor-Anmeldung einrichten und ausschalten. */
@RestController
@RequestMapping("/api/me/2fa")
public class TwoFactorController {

    public record CodeRequest(String code) {}

    public record DisableRequest(String password, String code) {}

    private final TwoFactorService service;

    public TwoFactorController(TwoFactorService service) {
        this.service = service;
    }

    @PostMapping("/setup")
    public TwoFactorService.SetupDto setup(@AuthenticationPrincipal AuthUser auth) {
        return service.setup(auth.id());
    }

    @PostMapping("/enable")
    public ResponseEntity<Void> enable(@AuthenticationPrincipal AuthUser auth, @RequestBody CodeRequest r) {
        service.enable(auth.id(), r.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal AuthUser auth, @RequestBody DisableRequest r) {
        service.disable(auth.id(), r.password(), r.code());
        return ResponseEntity.noContent().build();
    }
}
