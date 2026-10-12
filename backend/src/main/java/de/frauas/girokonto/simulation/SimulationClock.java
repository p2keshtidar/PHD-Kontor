package de.frauas.girokonto.simulation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Uhr der Anwendung mit verschiebbarer Zeit (/F950/).
 * Alle Klassen fragen die Zeit über diese Clock ab. Im Demo-Modus wird die Verschiebung erhöht,
 * dadurch "vergehen" Tage und Monate, ohne dass man warten muss.
 */
public class SimulationClock extends Clock {

    private final ZoneId zone;
    private final AtomicLong offsetDays;
    private final Clock base;

    public SimulationClock(ZoneId zone) {
        this(zone, new AtomicLong(), Clock.systemUTC());
    }

    /** Für Tests: mit fester Grunduhr. */
    SimulationClock(ZoneId zone, Clock base) {
        this(zone, new AtomicLong(), base);
    }

    private SimulationClock(ZoneId zone, AtomicLong offsetDays, Clock base) {
        this.zone = zone;
        this.offsetDays = offsetDays;
        this.base = base;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new SimulationClock(newZone, offsetDays, base);
    }

    @Override
    public Instant instant() {
        long days = offsetDays.get();
        if (days == 0) {
            return base.instant();
        }
        // Um KALENDERTAGE verschieben, nicht um n × 24 Stunden: sonst landet man über eine
        // Zeitumstellung (Sommer-/Winterzeit) hinweg kurz nach Mitternacht am Vortag.
        return ZonedDateTime.ofInstant(base.instant(), zone).plusDays(days).toInstant();
    }

    public long getOffsetDays() {
        return offsetDays.get();
    }

    public void setOffsetDays(long days) {
        offsetDays.set(days);
    }
}
