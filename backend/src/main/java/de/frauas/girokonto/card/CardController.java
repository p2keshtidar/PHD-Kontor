package de.frauas.girokonto.card;

import de.frauas.girokonto.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/api/cards/me")
    public CardDto myCard(@AuthenticationPrincipal AuthUser auth) {
        return cardService.view(auth.id());
    }

    /** Volle Nummer und Prüfnummer nur mit Passwort (POST, damit das Passwort nicht in der URL steht). */
    @PostMapping("/api/cards/me/details")
    public CardDetailsDto details(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody CardRequests.Details req) {
        return cardService.details(auth.id(), req.password());
    }

    @PostMapping("/api/cards/me/lock")
    public CardDto lock(@AuthenticationPrincipal AuthUser auth) {
        return cardService.setLocked(auth.id(), true);
    }

    @PostMapping("/api/cards/me/unlock")
    public CardDto unlock(@AuthenticationPrincipal AuthUser auth) {
        return cardService.setLocked(auth.id(), false);
    }

    @PutMapping("/api/cards/me/limits")
    public CardDto limits(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody CardRequests.Limits req) {
        return cardService.setLimits(auth.id(), req);
    }

    /**
     * Händler-Simulator (/F340/): löst eine Kartenzahlung mit der eigenen Karte aus ("Demo-Taste" in der App).
     */
    @PostMapping("/api/simulator/card-payment")
    public CardPaymentResultDto simulatePayment(@AuthenticationPrincipal AuthUser auth,
                                                @Valid @RequestBody CardRequests.Payment req) {
        return cardService.pay(auth.id(), req);
    }
}
