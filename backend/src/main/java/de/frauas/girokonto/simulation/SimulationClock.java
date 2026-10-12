package de.frauas.girokonto.simulation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Uhr der Anwendung mit verschiebbarer Zeit (/F950/).
 * Alle Klassen fragen die Zeit über diese Clock ab. Im Demo-Modus wird die Verschiebung erhöht,
 * dadurch "vergehen" Tage und Monate, ohne dass man warten muss.
 */
public class SimulationClock extends Clock {

    private final ZoneId zone;
    private final AtomicLong offsetDays;

    public SimulationClock(ZoneId zone) {
        this(zone, new AtomicLong());
    }

    private SimulationClock(ZoneId zone, AtomicLong offsetDays) {
        this.zone = zone;
        this.offsetDays = offsetDays;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new SimulationClock(newZone, offsetDays);
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(Duration.ofDays(offsetDays.get()));
    }

    public long getOffsetDays() {
        return offsetDays.get();
    }

    public void setOffsetDays(long days) {
        offsetDays.set(days);
    }
}
