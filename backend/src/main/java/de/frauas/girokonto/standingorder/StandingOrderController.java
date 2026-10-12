package de.frauas.girokonto.standingorder;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.standingorder.StandingOrderDtos.CreateRequest;
import de.frauas.girokonto.standingorder.StandingOrderDtos.StandingOrderDto;
import de.frauas.girokonto.standingorder.StandingOrderDtos.UpdateRequest;
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

/** /K030/ Daueraufträge */
@RestController
@RequestMapping("/api/standing-orders")
public class StandingOrderController {

    private final StandingOrderService service;

    public StandingOrderController(StandingOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<StandingOrderDto> list(@AuthenticationPrincipal AuthUser auth) {
        return service.list(auth.id());
    }

    @PostMapping
    public ResponseEntity<StandingOrderDto> create(@AuthenticationPrincipal AuthUser auth,
                                                   @Valid @RequestBody CreateRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), r));
    }

    @PutMapping("/{id}")
    public StandingOrderDto update(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                   @Valid @RequestBody UpdateRequest r) {
        return service.update(auth.id(), id, r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.delete(auth.id(), id);
        return ResponseEntity.noContent().build();
    }
}
