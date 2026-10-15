package de.frauas.girokonto.loan.provider;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMarketRateStore implements MarketRateStore {

    private final JdbcTemplate jdbc;

    public JdbcMarketRateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Stored> load() {
        Map<String, Stored> map = new HashMap<>();
        jdbc.query("SELECT series_key, period, rate_percent, fetched_at FROM market_rate", rs -> {
            map.put(rs.getString("series_key"), new Stored(rs.getString("period"), rs.getDouble("rate_percent"),
                    rs.getTimestamp("fetched_at").toInstant()));
        });
        return map;
    }

    @Override
    public void save(String seriesKey, Stored v) {
        jdbc.update("""
                INSERT INTO market_rate (series_key, period, rate_percent, fetched_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (series_key) DO UPDATE SET period = EXCLUDED.period, rate_percent = EXCLUDED.rate_percent,
                       fetched_at = EXCLUDED.fetched_at
                """, seriesKey, v.period(), v.ratePercent(), Timestamp.from(v.fetchedAt()));
    }
}
