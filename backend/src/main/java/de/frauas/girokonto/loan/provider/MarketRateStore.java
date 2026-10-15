package de.frauas.girokonto.loan.provider;

import java.time.Instant;
import java.util.Map;

/** Ablage des letzten erfolgreichen Abrufs je Zeitreihe (Datenbank; in Tests im Speicher). */
public interface MarketRateStore {

    record Stored(String period, double ratePercent, Instant fetchedAt) {}

    Map<String, Stored> load();

    void save(String seriesKey, Stored value);
}
