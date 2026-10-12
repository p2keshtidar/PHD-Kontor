package de.frauas.girokonto.transfer;

import de.frauas.girokonto.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /** Prüfen ohne zu buchen -> Zusammenfassung (/F240/). */
    @PostMapping("/check")
    public TransferCheckDto check(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody TransferRequest req) {
        return transferService.check(auth.id(), req);
    }

    /** Ausführen (/F250/). Der Header Idempotency-Key verhindert Doppelbuchungen. */
    @PostMapping
    public ResponseEntity<TransferResultDto> execute(@AuthenticationPrincipal AuthUser auth,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                     @Valid @RequestBody TransferRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.execute(auth.id(), key, req));
    }
}
