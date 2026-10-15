package de.frauas.girokonto.contract;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.contract.ContractService.ContractDto;
import de.frauas.girokonto.contract.ContractService.ContractInput;
import de.frauas.girokonto.contract.ContractService.Overview;
import de.frauas.girokonto.contract.DocumentService.Content;
import de.frauas.girokonto.contract.DocumentService.DocumentDto;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Verträge und Dokumente. */
@RestController
public class ContractController {

    public record CancelRequest(LocalDate effectiveDate) {}

    private final ContractService contracts;
    private final DocumentService documents;

    public ContractController(ContractService contracts, DocumentService documents) {
        this.contracts = contracts;
        this.documents = documents;
    }

    @GetMapping("/api/contracts")
    public Overview overview(@AuthenticationPrincipal AuthUser auth) {
        return contracts.overview(auth.id());
    }

    @GetMapping("/api/contracts/{id}")
    public ContractDto get(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return contracts.get(auth.id(), id);
    }

    @PostMapping("/api/contracts")
    public ResponseEntity<ContractDto> create(@AuthenticationPrincipal AuthUser auth, @RequestBody ContractInput in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contracts.create(auth.id(), in));
    }

    @PutMapping("/api/contracts/{id}")
    public ContractDto update(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @RequestBody ContractInput in) {
        return contracts.update(auth.id(), id, in);
    }

    @PostMapping("/api/contracts/{id}/cancel")
    public ContractDto cancel(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                              @RequestBody(required = false) CancelRequest req) {
        return contracts.markCancelled(auth.id(), id, req == null ? null : req.effectiveDate());
    }

    @PostMapping("/api/contracts/{id}/reactivate")
    public ContractDto reactivate(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return contracts.reactivate(auth.id(), id);
    }

    @DeleteMapping("/api/contracts/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        contracts.delete(auth.id(), id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- Dokumente

    @GetMapping("/api/documents")
    public List<DocumentDto> documents(@AuthenticationPrincipal AuthUser auth,
                                       @RequestParam(required = false) Long contractId) {
        return documents.list(auth.id(), contractId);
    }

    @PostMapping(value = "/api/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(@AuthenticationPrincipal AuthUser auth,
                                              @RequestPart("file") MultipartFile file,
                                              @RequestParam(required = false) String title,
                                              @RequestParam(required = false) Long contractId) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_FAILED", "Die Datei konnte nicht gelesen werden.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documents.upload(auth.id(), file.getOriginalFilename(), bytes, title, contractId));
    }

    /** Immer als Anhang, mit nosniff und ohne Zwischenspeicher. */
    @GetMapping("/api/documents/{id}/content")
    public ResponseEntity<byte[]> content(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        Content c = documents.content(auth.id(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(c.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(c.bytes());
    }

    @DeleteMapping("/api/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        documents.delete(auth.id(), id);
        return ResponseEntity.noContent().build();
    }
}
