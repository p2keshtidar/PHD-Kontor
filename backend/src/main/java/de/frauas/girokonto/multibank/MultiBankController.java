package de.frauas.girokonto.multibank;

import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.multibank.MultiBankDtos.CloseResult;
import de.frauas.girokonto.multibank.MultiBankDtos.OpenRequest;
import de.frauas.girokonto.multibank.MultiBankDtos.Products;
import de.frauas.girokonto.multibank.MultiBankDtos.RenameRequest;
import de.frauas.girokonto.multibank.MultiBankDtos.TxPage;
import de.frauas.girokonto.subaccount.SubaccountRequests;
import de.frauas.girokonto.subaccount.SubaccountService;
import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.i18n.ServerTexts;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Multi-Banking: weitere Konten der Projektbank (Simulation), Umbuchungen, alle Umsätze an einem Ort. */
@RestController
public class MultiBankController {

    private final MultiBankService service;
    private final ConsolidatedService consolidated;
    private final SubaccountService subaccounts;
    private final AuditLog audit;
    private final Clock clock;

    public MultiBankController(MultiBankService service, ConsolidatedService consolidated,
                               SubaccountService subaccounts, AuditLog audit, Clock clock) {
        this.service = service;
        this.consolidated = consolidated;
        this.subaccounts = subaccounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** Kontoangebote der Projektbank (weiteres Girokonto, Tagesgeld) und wie viele noch möglich sind. */
    @GetMapping("/api/accounts/products")
    public Products products(@AuthenticationPrincipal AuthUser auth) {
        return service.products(auth.id());
    }

    @PostMapping("/api/accounts")
    public ResponseEntity<AccountDto> open(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody OpenRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.open(auth.id(), req));
    }

    @PutMapping("/api/accounts/{id}")
    public AccountDto rename(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                             @Valid @RequestBody RenameRequest req) {
        return service.rename(auth.id(), id, req.name());
    }

    @DeleteMapping("/api/accounts/{id}")
    public CloseResult close(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.close(auth.id(), id);
    }

    /** Umbuchung zwischen eigenen Konten der Projektbank (Girokonten, Tagesgeld, digitale Konten). */
    @PostMapping("/api/accounts/move")
    public ResponseEntity<Void> move(@AuthenticationPrincipal AuthUser auth,
                                     @Valid @RequestBody SubaccountRequests.Move req) {
        subaccounts.move(auth.id(), req);
        return ResponseEntity.noContent().build();
    }

    /**
     * Alle Umsätze: Projektbank und verbundene Banken.
     * Beispiel: /api/transactions?source=ALL&q=miete&from=2026-09-01&internal=false&page=0&size=20
     */
    @GetMapping("/api/transactions")
    public TxPage transactions(@AuthenticationPrincipal AuthUser auth,
                               @RequestParam(required = false) String source,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) String direction,
                               @RequestParam(defaultValue = "true") boolean internal,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return consolidated.find(auth.id(), source, q, from, to, direction, internal, page, size);
    }

    /** Dieselben Umsätze als CSV-Datei (höchstens 10.000 Zeilen, neueste zuerst). */
    @GetMapping("/api/transactions/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser auth,
                                         @RequestParam(required = false) String source,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String direction,
                                         @RequestParam(defaultValue = "true") boolean internal,
                                         @RequestHeader(value = "Accept-Language", required = false) String lang) {
        ConsolidatedService.CsvExport csv = consolidated.export(auth.id(), source, q, from, to, direction, internal,
                ServerTexts.wantsEnglish(lang));
        audit.record(auth.id(), "TRANSACTIONS_EXPORTED", null, csv.rows() + " Zeilen");   // keine Inhalte im Protokoll
        String file = (ServerTexts.wantsEnglish(lang) ? "transactions-" : "umsaetze-") + LocalDate.now(clock) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                .header("X-Export-Rows", String.valueOf(csv.rows()))
                .header("X-Export-Truncated", String.valueOf(csv.truncated()))
                .body(csv.content().getBytes(StandardCharsets.UTF_8));
    }

    /** Mögliche doppelte Umsätze der letzten Tage (Standard 90). */
    @GetMapping("/api/transactions/duplicates")
    public ConsolidatedService.Duplicates duplicates(@AuthenticationPrincipal AuthUser auth,
                                                     @RequestParam(defaultValue = "90") int days) {
        return consolidated.duplicates(auth.id(), days);
    }

    /** Ein Paar als „kein Duplikat“ markieren. */
    @PostMapping("/api/transactions/duplicates/dismiss")
    public ResponseEntity<Void> dismiss(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody DismissRequest req) {
        consolidated.dismiss(auth.id(), req.key());
        return ResponseEntity.noContent().build();
    }

    public record DismissRequest(@jakarta.validation.constraints.NotBlank(message = "Paar fehlt.")
                                 @jakarta.validation.constraints.Size(max = 120, message = "Unbekanntes Paar.") String key) {}
}
