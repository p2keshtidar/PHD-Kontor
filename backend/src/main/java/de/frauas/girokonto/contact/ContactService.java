package de.frauas.girokonto.contact;

import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.transfer.Recipient;
import de.frauas.girokonto.transfer.RecipientResolver;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import de.frauas.girokonto.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adressbuch (/F220/). */
@Service
public class ContactService {

    private final ContactRepository contacts;
    private final AppUserRepository users;
    private final RecipientResolver recipientResolver;
    private final Clock clock;

    public ContactService(ContactRepository contacts, AppUserRepository users,
                          RecipientResolver recipientResolver, Clock clock) {
        this.contacts = contacts;
        this.users = users;
        this.recipientResolver = recipientResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ContactDto> list(Long userId) {
        return contacts.findByOwnerIdOrderByIdAsc(userId).stream()
                .map(this::toDto)
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public ContactDto add(Long userId, AddContactRequest req) {
        Recipient r = recipientResolver.resolve(req.recipient());
        if (r.user().getId().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SELF_CONTACT", "Du kannst dich nicht selbst speichern.");
        }
        if (contacts.existsByOwnerIdAndContactUserId(userId, r.user().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTACT_EXISTS", "Dieser Kontakt ist bereits gespeichert.");
        }
        String nickname = req.nickname() == null || req.nickname().isBlank() ? null : req.nickname().trim();
        Contact saved = contacts.save(new Contact(userId, r.user().getId(), nickname, Instant.now(clock)));
        return toDto(saved);
    }

    @Transactional
    public void delete(Long userId, Long contactId) {
        Contact c = contacts.findByIdAndOwnerId(contactId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONTACT_NOT_FOUND", "Kontakt nicht gefunden."));
        contacts.delete(c);
    }

    /** Findet App-Nutzer zu Telefonnummern aus den Gerätekontakten. */
    @Transactional(readOnly = true)
    public List<MatchDto> match(Long userId, MatchRequest req) {
        Set<String> phones = req.phones().stream()
                .filter(Objects::nonNull)
                .map(UserService::normalizePhone)
                .filter(p -> p.length() >= 6)
                .collect(Collectors.toSet());
        if (phones.isEmpty()) {
            return List.of();
        }
        Set<Long> existing = contacts.findByOwnerIdOrderByIdAsc(userId).stream()
                .map(Contact::getContactUserId).collect(Collectors.toSet());
        return users.findByPhoneIn(phones).stream()
                .filter(u -> !u.getId().equals(userId))
                .map(u -> recipientResolver.forUser(u)
                        .map(r -> new MatchDto(u.getId(), r.fullName(), r.account().getIban(), u.getPhone(),
                                existing.contains(u.getId())))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private ContactDto toDto(Contact c) {
        AppUser u = users.findById(c.getContactUserId()).orElse(null);
        if (u == null) {
            return null;
        }
        return recipientResolver.forUser(u)
                .map(r -> new ContactDto(c.getId(), u.getId(),
                        c.getNickname() != null ? c.getNickname() : r.fullName(), r.fullName(),
                        r.account().getIban()))
                .orElse(null);
    }
}
