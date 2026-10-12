package de.frauas.girokonto.simulation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClockController {

    private final SimulationService simulation;

    public ClockController(SimulationService simulation) {
        this.simulation = simulation;
    }

    /** Aktuelles (simuliertes) Datum, für die Anzeige in der App. */
    @GetMapping("/api/clock")
    public ClockDto clock() {
        return simulation.current();
    }

    /** Nur Administrator (SecurityConfig: /api/admin/** braucht Rolle ADMIN). */
    @PostMapping("/api/admin/clock/advance")
    public ClockDto advance(@Valid @RequestBody AdvanceRequest req) {
        return simulation.advance(req);
    }
}
