package de.frauas.girokonto.contact;

import de.frauas.girokonto.auth.AuthUser;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public List<ContactDto> list(@AuthenticationPrincipal AuthUser auth) {
        return contactService.list(auth.id());
    }

    @PostMapping
    public ResponseEntity<ContactDto> add(@AuthenticationPrincipal AuthUser auth,
                                          @Valid @RequestBody AddContactRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactService.add(auth.id(), req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        contactService.delete(auth.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/match")
    public List<MatchDto> match(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody MatchRequest req) {
        return contactService.match(auth.id(), req);
    }
}
