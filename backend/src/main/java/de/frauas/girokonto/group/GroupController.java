package de.frauas.girokonto.group;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.group.GroupService.ExpenseInput;
import de.frauas.girokonto.group.GroupService.GroupDetail;
import de.frauas.girokonto.group.GroupService.GroupSummary;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gemeinsame Ausgaben (WG, Reise, Paar). */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    public record NameRequest(String name) {}
    public record MemberRequest(String recipient) {}
    public record TransferSettlement(Long toUserId, long amountCents) {}
    public record CashSettlement(Long fromUserId, long amountCents) {}

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<GroupSummary> list(@AuthenticationPrincipal AuthUser auth) {
        return service.list(auth.id());
    }

    @PostMapping
    public ResponseEntity<GroupDetail> create(@AuthenticationPrincipal AuthUser auth, @RequestBody NameRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(auth.id(), req.name()));
    }

    @GetMapping("/{id}")
    public GroupDetail detail(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        return service.detail(auth.id(), id);
    }

    @PutMapping("/{id}")
    public GroupDetail rename(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id, @RequestBody NameRequest req) {
        return service.rename(auth.id(), id, req.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        service.delete(auth.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    public GroupDetail addMember(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                 @RequestBody MemberRequest req) {
        return service.addMember(auth.id(), id, req.recipient());
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                             @PathVariable Long userId) {
        service.removeMember(auth.id(), id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/expenses")
    public ResponseEntity<GroupDetail> addExpense(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                                  @RequestBody ExpenseInput req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addExpense(auth.id(), id, req));
    }

    @DeleteMapping("/{id}/expenses/{expenseId}")
    public GroupDetail deleteExpense(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                                     @PathVariable Long expenseId) {
        return service.deleteExpense(auth.id(), id, expenseId);
    }

    @PostMapping("/{id}/settlements")
    public GroupDetail settle(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                              @RequestHeader(value = "Idempotency-Key", required = false) String key,
                              @RequestBody TransferSettlement req) {
        return service.settleByTransfer(auth.id(), id, req.toUserId(), req.amountCents(), key);
    }

    @PostMapping("/{id}/settlements/cash")
    public GroupDetail cash(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id,
                            @RequestBody CashSettlement req) {
        return service.confirmCash(auth.id(), id, req.fromUserId(), req.amountCents());
    }
}
