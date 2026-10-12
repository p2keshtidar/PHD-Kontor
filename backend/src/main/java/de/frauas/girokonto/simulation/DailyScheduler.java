package de.frauas.girokonto.simulation;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Startet den Tageslauf jede Nacht um 00:05 und einmal beim Start der Anwendung. */
@Component
public class DailyScheduler {

    private final SimulationService simulation;

    public DailyScheduler(SimulationService simulation) {
        this.simulation = simulation;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        simulation.runDay(simulation.today());
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Berlin")
    public void nightly() {
        simulation.runDay(simulation.today());
    }
}
