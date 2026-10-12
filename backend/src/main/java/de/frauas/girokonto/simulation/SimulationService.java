package de.frauas.girokonto.simulation;

import de.frauas.girokonto.common.ApiException;
import jakarta.annotation.PostConstruct;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Simulationszeit und tägliche Läufe (/F950/, Pflichtenheft Kap. 7.4).
 *
 * runDay(datum) führt alle DailyJobs für ein Datum genau einmal aus (Tabelle job_run).
 * advance(...) verschiebt die Uhr Tag für Tag und ruft für jeden Tag runDay auf.
 */
@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final JdbcTemplate jdbc;
    private final SimulationClock clock;
    private final List<DailyJob> jobs;

    public SimulationService(JdbcTemplate jdbc, SimulationClock clock, List<DailyJob> jobs) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.jobs = jobs.stream().sorted(Comparator.comparingInt(DailyJob::order)).toList();
    }

    @PostConstruct
    void loadOffset() {
        Integer offset = jdbc.queryForObject("SELECT offset_days FROM sim_clock WHERE id = 1", Integer.class);
        clock.setOffsetDays(offset == null ? 0 : offset);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ClockDto current() {
        return new ClockDto(today(), clock.getOffsetDays());
    }

    public synchronized ClockDto advance(AdvanceRequest req) {
        int days;
        if (req.months() != null && req.months() > 0) {
            days = (int) ChronoUnit.DAYS.between(today(), today().plusMonths(req.months()));
        } else if (req.days() != null && req.days() > 0) {
            days = req.days();
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTHING_TO_ADVANCE", "Bitte Tage oder Monate angeben.");
        }
        for (int i = 0; i < days; i++) {
            long newOffset = clock.getOffsetDays() + 1;
            jdbc.update("UPDATE sim_clock SET offset_days = ? WHERE id = 1", newOffset);
            clock.setOffsetDays(newOffset);
            runDay(today());
        }
        log.info("Simulationszeit um {} Tage vorgespult, neues Datum {}", days, today());
        return current();
    }

    /** Führt alle Tagesaufgaben für das Datum aus, falls noch nicht geschehen. */
    public synchronized void runDay(LocalDate date) {
        Integer done = jdbc.queryForObject("SELECT count(*) FROM job_run WHERE run_date = ?", Integer.class,
                Date.valueOf(date));
        if (done != null && done > 0) {
            return;
        }
        for (DailyJob job : jobs) {
            try {
                job.runFor(date);
            } catch (RuntimeException e) {
                // Ein Fehler in einer Aufgabe soll die anderen nicht verhindern
                log.error("Tagesaufgabe {} für {} fehlgeschlagen", job.getClass().getSimpleName(), date, e);
            }
        }
        jdbc.update("INSERT INTO job_run (run_date, finished_at) VALUES (?, ?) ON CONFLICT (run_date) DO NOTHING",
                Date.valueOf(date), java.sql.Timestamp.from(Instant.now(clock)));
    }
}
