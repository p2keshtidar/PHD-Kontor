package de.frauas.girokonto.config;

import de.frauas.girokonto.simulation.SimulationClock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zentrale Uhr der Anwendung. Alle Klassen fragen die Zeit über diese Clock ab,
 * damit sie in Tests fest eingestellt und im Demo-Modus (/F950/) vorgespult werden kann.
 */
@Configuration
@EnableScheduling
public class ClockConfig {

    @Bean
    public SimulationClock clock(AppProperties props) {
        return new SimulationClock(ZoneId.of(props.simulation().zone()));
    }
}
