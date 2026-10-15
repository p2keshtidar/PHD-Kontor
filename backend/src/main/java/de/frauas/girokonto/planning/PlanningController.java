package de.frauas.girokonto.planning;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.planning.PlanningDtos.BudgetOverview;
import de.frauas.girokonto.planning.PlanningDtos.BudgetRequest;
import de.frauas.girokonto.planning.PlanningDtos.Forecast;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Persönliche Finanzplanung: Budgets je Kategorie, ungewöhnliche Ausgaben, Prognose des Girokontos. */
@RestController
public class PlanningController {

    private final BudgetService budgets;
    private final ForecastService forecasts;

    public PlanningController(BudgetService budgets, ForecastService forecasts) {
        this.budgets = budgets;
        this.forecasts = forecasts;
    }

    @GetMapping("/api/budgets")
    public BudgetOverview budgets(@AuthenticationPrincipal AuthUser auth) {
        return budgets.overview(auth.id());
    }

    /** Budget für eine Ausgabenkategorie setzen oder ändern (Betrag pro Monat). */
    @PutMapping("/api/budgets/{category}")
    public BudgetOverview set(@AuthenticationPrincipal AuthUser auth, @PathVariable String category,
                              @Valid @RequestBody BudgetRequest req) {
        return budgets.set(auth.id(), category, req.monthlyCents());
    }

    @DeleteMapping("/api/budgets/{category}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth, @PathVariable String category) {
        budgets.delete(auth.id(), category);
        return ResponseEntity.noContent().build();
    }

    /** Kontostand des Girokontos für die nächsten Tage (Standard 90, höchstens 180). */
    @GetMapping("/api/forecast")
    public Forecast forecast(@AuthenticationPrincipal AuthUser auth, @RequestParam(defaultValue = "90") int days) {
        return forecasts.forecast(auth.id(), days);
    }
}
