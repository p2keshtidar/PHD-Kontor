package de.frauas.girokonto.banking;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.banking.BankingDtos.AccountsOverview;
import de.frauas.girokonto.banking.BankingDtos.CompleteRequest;
import de.frauas.girokonto.banking.BankingDtos.CompleteResult;
import de.frauas.girokonto.banking.BankingDtos.ConnectRequest;
import de.frauas.girokonto.banking.BankingDtos.ConnectResult;
import de.frauas.girokonto.banking.BankingDtos.ConnectionDto;
import de.frauas.girokonto.banking.BankingDtos.InstitutionDto;
import de.frauas.girokonto.banking.BankingDtos.ProviderDto;
import de.frauas.girokonto.banking.BankingDtos.RevokeResult;
import de.frauas.girokonto.banking.BankingDtos.SyncResult;
import de.frauas.girokonto.banking.BankingDtos.TransactionDto;
import de.frauas.girokonto.banking.BankingDtos.UpdateTransactionRequest;
import de.frauas.girokonto.common.PageDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Verbundene Bankkonten (Open Banking/PSD2, nur Lesezugriff). */
@RestController
@RequestMapping("/api/banking")
public class BankingController {

    private final BankingService service;

    public BankingController(BankingService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers() {
        return service.providers();
    }

    @GetMapping("/institutions")
    public List<InstitutionDto> institutions(@RequestParam(defaultValue = "SANDBOX") String provider,
                                             @RequestParam(defaultValue = "DE") String country) {
        return service.institutions(provider, country);
    }

    @GetMapping("/connections")
    public List<ConnectionDto> connections(@AuthenticationPrincipal AuthUser auth) {
        return service.connections(auth.id());
    }

    @GetMapping("/connections/{id}")
    public ConnectionDto connection(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.connection(auth.id(), id);
    }

    @PostMapping("/connections")
    public ResponseEntity<ConnectResult> connect(@AuthenticationPrincipal AuthUser auth,
                                                 @Valid @RequestBody ConnectRequest r,
                                                 @RequestHeader(value = "Accept-Language", required = false) String lang) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.connect(auth.id(), r.provider(), r.institutionId(), language(lang)));
    }

    @PostMapping("/connections/{id}/complete")
    public CompleteResult complete(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                   @RequestBody(required = false) CompleteRequest r) {
        return service.complete(auth.id(), id, r == null ? null : r.approve());
    }

    @PostMapping("/connections/{id}/renew")
    public ConnectResult renew(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                               @RequestHeader(value = "Accept-Language", required = false) String lang) {
        return service.renew(auth.id(), id, language(lang));
    }

    @PostMapping("/connections/{id}/sync")
    public SyncResult sync(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.sync(auth.id(), id);
    }

    @DeleteMapping("/connections/{id}")
    public RevokeResult revoke(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                               @RequestParam(defaultValue = "false") boolean deleteData) {
        return service.revoke(auth.id(), id, deleteData);
    }

    @GetMapping("/accounts")
    public AccountsOverview accounts(@AuthenticationPrincipal AuthUser auth) {
        return service.accounts(auth.id());
    }

    @GetMapping("/transactions")
    public PageDto<TransactionDto> transactions(
            @AuthenticationPrincipal AuthUser auth,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return service.transactions(auth.id(), accountId, q, category, status, tag, from, to, page, size);
    }

    @GetMapping("/transactions/{id}")
    public TransactionDto transaction(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.transaction(auth.id(), id);
    }

    /** Originaldaten der Bank, unverändert. */
    @GetMapping(value = "/transactions/{id}/original", produces = MediaType.APPLICATION_JSON_VALUE)
    public String original(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.original(auth.id(), id);
    }

    @PutMapping("/transactions/{id}")
    public TransactionDto update(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                 @Valid @RequestBody UpdateTransactionRequest r) {
        return service.updateTransaction(auth.id(), id, r);
    }

    private static String language(String acceptLanguage) {
        return acceptLanguage != null && acceptLanguage.toLowerCase().startsWith("en") ? "en" : "de";
    }
}
