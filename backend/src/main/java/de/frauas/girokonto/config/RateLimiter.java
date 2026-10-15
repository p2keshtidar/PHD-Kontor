package de.frauas.girokonto.config;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Einfaches Zählfenster pro Schlüssel und Minute (im Arbeitsspeicher, je Server-Instanz).
 * Für mehrere Instanzen müsste der Zähler in Redis o. Ä. liegen.
 */
public class RateLimiter {

    private record Window(long minute, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @return verbleibende Sekunden bis zum nächsten Fenster, wenn das Limit überschritten ist; sonst 0 */
    public long tryAcquire(String key, int limitPerMinute) {
        long now = clock.millis();
        long minute = now / 60_000;
        Window w = windows.compute(key, (k, old) ->
                old == null || old.minute() != minute ? new Window(minute, 1) : new Window(minute, old.count() + 1));
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        }
        if (w.count() <= limitPerMinute) {
            return 0;
        }
        return Math.max(1, ((minute + 1) * 60_000 - now + 999) / 1000);
    }
}
