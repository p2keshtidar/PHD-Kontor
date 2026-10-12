package de.frauas.girokonto.csvimport;

import de.frauas.girokonto.auth.AuthUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /K040/ CSV-Import */
@RestController
@RequestMapping("/api/imports")
public class CsvImportController {

    /** Die App liest die Datei selbst (inkl. Zeichensatz) und schickt den Text. */
    public record ImportRequest(String fileName, String content) {}

    private final CsvImportService service;

    public CsvImportController(CsvImportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CsvImportService.ImportResult> importCsv(@AuthenticationPrincipal AuthUser auth,
                                                                    @RequestBody ImportRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.importCsv(auth.id(), r.fileName(), r.content()));
    }

    @GetMapping
    public List<CsvImportService.ImportDto> list(@AuthenticationPrincipal AuthUser auth) {
        return service.list(auth.id());
    }

    @GetMapping("/{id}/transactions")
    public List<CsvImportService.TransactionDto> transactions(@AuthenticationPrincipal AuthUser auth,
                                                              @PathVariable Long id) {
        return service.transactions(auth.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.delete(auth.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Beispieldatei zum Ausprobieren (Sparkassen-Format). */
    @GetMapping(value = "/sample", produces = "text/csv;charset=UTF-8")
    public String sample() {
        return service.sampleCsv();
    }
}
