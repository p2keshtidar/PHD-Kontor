package de.frauas.girokonto.audit;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.common.PageDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Eigenes Prüfprotokoll: was wurde wann mit dem Konto gemacht (Transparenz, DSGVO Art. 15). */
@RestController
public class AuditController {

    private final AuditLog audit;

    public AuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping("/api/me/audit-log")
    public PageDto<AuditLog.EntryDto> list(@AuthenticationPrincipal AuthUser auth,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "30") int size) {
        return audit.list(auth.id(), page, size);
    }
}
