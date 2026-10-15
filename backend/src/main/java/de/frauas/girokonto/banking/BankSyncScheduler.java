package de.frauas.girokonto.banking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ruft fällige Bankverbindungen im Hintergrund ab. Wie oft ein Konto abgerufen wird, bestimmt
 * {@code app.banking.sync-interval-minutes} (mindestens der Abstand, den der Anbieter verlangt).
 */
@Component
@ConditionalOnProperty(prefix = "app.banking", name = "auto-sync", havingValue = "true")
public class BankSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(BankSyncScheduler.class);

    private final BankingService service;

    public BankSyncScheduler(BankingService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT15M")
    public void run() {
        int n = service.syncDue();
        if (n > 0) {
            log.info("Bankverbindungen abgerufen: {}", n);
        }
    }
}
