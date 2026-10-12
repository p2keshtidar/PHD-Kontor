package de.frauas.girokonto.account;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.booking.BookingFilter;
import de.frauas.girokonto.booking.BookingItemDto;
import de.frauas.girokonto.booking.BookingQueryService;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.common.PageDto;
import de.frauas.girokonto.insights.Category;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    public record CategoryRequest(Category category) {}

    private final AccountService accountService;
    private final BookingQueryService bookingQueryService;

    public AccountController(AccountService accountService, BookingQueryService bookingQueryService) {
        this.accountService = accountService;
        this.bookingQueryService = bookingQueryService;
    }

    /** Kategorie eines Umsatzes ändern (/F560/). Gilt nur für die Zeile dieses (eigenen) Kontos. */
    @PutMapping("/{accountId}/bookings/{bookingId}/category")
    public ResponseEntity<Void> changeCategory(@AuthenticationPrincipal AuthUser auth, @PathVariable Long accountId,
                                               @PathVariable Long bookingId, @RequestBody CategoryRequest req) {
        accountService.changeCategory(auth.id(), accountId, bookingId, req == null ? null : req.category());
        return ResponseEntity.noContent().build();
    }

    /** Liefert nur die Konten des angemeldeten Nutzers (/NF010/). */
    @GetMapping
    public List<AccountDto> myAccounts(@AuthenticationPrincipal AuthUser auth) {
        List<AccountDto> all = new java.util.ArrayList<>(accountService.listForUser(auth.id()));
        all.addAll(accountService.sharedWithUser(auth.id()));   // /K060/ geteilte Unterkonten anderer
        return all;
    }

    /**
     * Umsätze eines eigenen Kontos mit Filter und Suche (/F120/, /F130/).
     * Beispiel: /api/accounts/5/bookings?direction=OUT&from=2026-10-01&q=miete&page=0&size=20
     */
    @GetMapping("/{accountId}/bookings")
    public PageDto<BookingItemDto> bookings(
            @AuthenticationPrincipal AuthUser auth,
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BookingFilter.Direction direction,
            @RequestParam(required = false) Long minCents,
            @RequestParam(required = false) Long maxCents,
            @RequestParam(required = false) BookingType type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        accountService.requireAccess(accountId, auth.id());
        BookingFilter filter = new BookingFilter(from, to, direction, minCents, maxCents, type, q);
        return bookingQueryService.find(accountId, auth.id(), filter, page, size);
    }
}
