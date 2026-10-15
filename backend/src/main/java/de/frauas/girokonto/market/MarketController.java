package de.frauas.girokonto.market;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.market.MarketService.ApplicationDto;
import de.frauas.girokonto.market.MarketService.Catalog;
import de.frauas.girokonto.market.MarketService.Comparison;
import de.frauas.girokonto.market.MarketService.ProductView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Finanz-Marktplatz (nur simulierte Anbieter): Vergleich, Favoriten, Vergleichsliste, simulierte Anträge. */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    public record ApplyRequest(@NotBlank(message = "Angebot fehlt.") String productId, boolean consent) {}

    public record NoteRequest(@Size(max = 500, message = "Die Notiz darf höchstens 500 Zeichen haben.") String note) {}

    private final MarketService service;

    public MarketController(MarketService service) {
        this.service = service;
    }

    /** Angebote einer Kategorie mit Beispielrechnung, z. B. /api/market/products?category=CALL&amountCents=500000 */
    @GetMapping("/products")
    public Catalog products(@AuthenticationPrincipal AuthUser auth,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) Long amountCents,
                            @RequestParam(required = false) Long maxYearlyFeeCents,
                            @RequestParam(required = false) BigDecimal minInterest,
                            @RequestParam(required = false) Integer termMonths) {
        return service.catalog(auth.id(), category, amountCents, maxYearlyFeeCents, minInterest, termMonths);
    }

    @GetMapping("/products/{id}")
    public ProductView product(@AuthenticationPrincipal AuthUser auth, @PathVariable String id,
                               @RequestParam(required = false) Long amountCents) {
        return service.detail(auth.id(), id, amountCents);
    }

    /** Vergleichsliste: 2 bis 4 Angebote nebeneinander, z. B. /api/market/compare?ids=call-kranich,call-mosaik */
    @GetMapping("/compare")
    public Comparison compare(@AuthenticationPrincipal AuthUser auth, @RequestParam List<String> ids,
                              @RequestParam(required = false) Long amountCents) {
        return service.compare(auth.id(), ids, amountCents);
    }

    @GetMapping("/favorites")
    public List<ProductView> favorites(@AuthenticationPrincipal AuthUser auth) {
        return service.favoriteList(auth.id());
    }

    @PutMapping("/favorites/{productId}")
    public ResponseEntity<Void> addFavorite(@AuthenticationPrincipal AuthUser auth, @PathVariable String productId) {
        service.favorite(auth.id(), productId, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/favorites/{productId}")
    public ResponseEntity<Void> removeFavorite(@AuthenticationPrincipal AuthUser auth, @PathVariable String productId) {
        service.favorite(auth.id(), productId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/applications")
    public ResponseEntity<ApplicationDto> apply(@AuthenticationPrincipal AuthUser auth,
                                                @Valid @RequestBody ApplyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(auth.id(), req.productId(), req.consent()));
    }

    @GetMapping("/applications")
    public List<ApplicationDto> applications(@AuthenticationPrincipal AuthUser auth) {
        return service.applications(auth.id());
    }

    @GetMapping("/applications/{id}")
    public ApplicationDto application(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.application(auth.id(), id);
    }

    /** Unterlage als bereitgestellt markieren. */
    @PostMapping("/applications/{id}/documents/{docKey}")
    public ApplicationDto provide(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                  @PathVariable String docKey) {
        return service.provide(auth.id(), id, docKey);
    }

    @PostMapping("/applications/{id}/withdraw")
    public ApplicationDto withdraw(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.withdraw(auth.id(), id);
    }

    /** Eigene Entscheidung/Notiz zum Antrag festhalten. */
    @PutMapping("/applications/{id}/note")
    public ApplicationDto note(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                               @Valid @RequestBody NoteRequest req) {
        return service.note(auth.id(), id, req.note());
    }
}
