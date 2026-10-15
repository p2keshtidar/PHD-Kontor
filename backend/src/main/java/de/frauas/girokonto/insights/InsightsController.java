package de.frauas.girokonto.insights;

import de.frauas.girokonto.account.AccountDto;
import de.frauas.girokonto.account.AccountService;
import de.frauas.girokonto.account.AccountType;
import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.savings.SavingsPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InsightsController {

    public record SampleRequest(@NotNull(message = "Profil fehlt.") DemoProfile profile) {}

    private final InsightsService insights;
    private final SavingsPlanService savings;
    private final DemoDataService demoData;
    private final AccountService accountService;
    private final de.frauas.girokonto.banking.BankingService banking;

    public InsightsController(InsightsService insights, SavingsPlanService savings, DemoDataService demoData,
                              AccountService accountService, de.frauas.girokonto.banking.BankingService banking) {
        this.insights = insights;
        this.savings = savings;
        this.demoData = demoData;
        this.accountService = accountService;
        this.banking = banking;
    }

    /** Monatsübersicht (/F570/) */
    @GetMapping("/api/insights/months")
    public List<MonthSummaryDto> months(@AuthenticationPrincipal AuthUser auth,
                                        @RequestParam(defaultValue = "6") int count) {
        return insights.months(auth.id(), count);
    }

    /** Durchschnitte und "verfügbar für Pläne" (/F580/) */
    @GetMapping("/api/insights/overview")
    public OverviewDto overview(@AuthenticationPrincipal AuthUser auth) {
        return savings.overview(auth.id());
    }

    /** Gesamtvermögen inkl. Depotwerte und Kredite (/F110/) */
    @GetMapping("/api/insights/net-worth")
    public NetWorthDto netWorth(@AuthenticationPrincipal AuthUser auth) {
        List<AccountDto> accounts = accountService.listForUser(auth.id());
        long loans = accounts.stream().filter(a -> a.type() == AccountType.LOAN).mapToLong(AccountDto::balanceCents).sum();
        long other = accounts.stream().filter(a -> a.type() != AccountType.LOAN).mapToLong(AccountDto::balanceCents).sum();
        long depot = savings.totalDepotValue(auth.id());
        long external = banking.accounts(auth.id()).totalBalanceCents();   // verbundene Banken (nur Euro)
        return new NetWorthDto(other, depot, loans, other + depot + loans + external, external);
    }

    /** Alle Kategorien, die der Nutzer wählen kann (für "Kategorie ändern"). */
    @GetMapping("/api/insights/categories")
    public List<Map<String, String>> categories() {
        return java.util.Arrays.stream(Category.values())
                .filter(Category::isUserSelectable)
                .map(c -> Map.of("category", c.name(), "label", c.label(), "kind", c.kind().name()))
                .toList();
    }

    /** Beispielmonate erzeugen (/F590/) */
    @PostMapping("/api/demo/sample-months")
    public ResponseEntity<Map<String, Object>> sampleMonths(@AuthenticationPrincipal AuthUser auth,
                                                            @Valid @RequestBody SampleRequest req) {
        int count = demoData.createSampleMonths(auth.id(), req.profile());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("bookings", count, "profile", req.profile().name()));
    }
}
