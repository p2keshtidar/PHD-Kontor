package de.frauas.girokonto.entry;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.entry.EntryDtos.CategoryOption;
import de.frauas.girokonto.entry.EntryDtos.CreateRequest;
import de.frauas.girokonto.entry.EntryDtos.CreateResult;
import de.frauas.girokonto.entry.EntryDtos.EntryDto;
import de.frauas.girokonto.entry.EntryDtos.Summary;
import de.frauas.girokonto.entry.EntryDtos.UpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
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

/** Eigene Einnahmen und Ausgaben (einmalig oder regelmäßig). */
@RestController
@RequestMapping("/api/entries")
public class EntryController {

    private final EntryService service;

    public EntryController(EntryService service) {
        this.service = service;
    }

    @GetMapping
    public Summary list(@AuthenticationPrincipal AuthUser auth) {
        return service.list(auth.id());
    }

    @GetMapping("/categories")
    public List<CategoryOption> categories() {
        return service.categories();
    }

    @PostMapping
    public ResponseEntity<CreateResult> create(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody CreateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), r));
    }

    @PutMapping("/{id}")
    public EntryDto update(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @Valid @RequestBody UpdateRequest r) {
        return service.update(auth.id(), id, r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.delete(auth.id(), id);
        return ResponseEntity.noContent().build();
    }
}
